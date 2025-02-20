############################################################
# MAIN INFRASTRUCTURE
############################################################
data "azurerm_key_vault" "litter_kv" {
  name                = var.kv_name
  resource_group_name = var.kv_rg
}

resource "azurerm_resource_group" "litter_k8s_rg" {
  # named like: aks-litter-dev-eastus-rg-001
  # see: https://learn.microsoft.com/en-us/azure/cloud-adoption-framework/ready/azure-best-practices/resource-naming
  name     = "aks-${var.app_name}-${var.app_environment}-${var.k8s_location}-rg-001"
  location = var.k8s_location
}

resource "azurerm_kubernetes_cluster" "aks" {
  name                      = "aks-cluster-${var.app_name}"
  dns_prefix                = "${var.app_name}-${var.app_environment}"
  location                  = azurerm_resource_group.litter_k8s_rg.location
  resource_group_name       = azurerm_resource_group.litter_k8s_rg.name
  # OIDC and workload identity are enabled to allow cert-manager to authenticate with Azure DNS
  #   see: https://cert-manager.io/docs/configuration/acme/dns01/azuredns/#managed-identity-using-aad-pod-identity
  oidc_issuer_enabled       = true
  workload_identity_enabled = true
  default_node_pool {
    name            = "nodepool"
    node_count      = var.k8s_node_count
    vm_size         = var.k8s_node_az_sku
    os_disk_size_gb = var.k8s_node_disk_size_gb
    # this gets around an annoying bug whereby these values are updated on every apply
    #   see: https://github.com/hashicorp/terraform-provider-azurerm/issues/24020
    upgrade_settings {
      max_surge = "10%"
    }
  }
  identity {
    type = "SystemAssigned"
  }
  network_profile {
    network_plugin    = "azure"
    load_balancer_sku = var.k8s_lb_az_sku
  }
  key_vault_secrets_provider {
    secret_rotation_enabled = true
  }
}

resource "azurerm_public_ip" "litter_ip" {
  name                = "${var.app_name}-ip"
  sku                 = "Basic"
  allocation_method   = "Static"
  location            = azurerm_kubernetes_cluster.aks.location
  resource_group_name = azurerm_kubernetes_cluster.aks.node_resource_group
}

# create/adjust the DNS A record to point to the app's public IP
resource "azurerm_dns_a_record" "litter_dns" {
  name                = var.app_environment
  zone_name           = var.az_dns_zone_name
  resource_group_name = var.az_dns_rg
  ttl = 300 # keep low for rapid testing/iteration
  records = [azurerm_public_ip.litter_ip.ip_address]
}

############################################################
# HELM: NGINX INGRESS CONTROLLER
############################################################
resource "helm_release" "nginx_ingress" {
  name             = "ingress-nginx"
  repository       = "https://kubernetes.github.io/ingress-nginx"
  chart            = "ingress-nginx"
  version          = "~> 4.12.0"
  namespace        = "ingress-nginx"
  create_namespace = true
  wait             = true
  wait_for_jobs = true # needed for Cert Manager to startup correctly
  timeout          = 600
  set {
    name  = "controller.service.type"
    value = "LoadBalancer"
  }
  set {
    name  = "controller.service.loadBalancerIP"
    value = azurerm_public_ip.litter_ip.ip_address
  }
  set {
    name  = "controller.service.annotations.service\\.beta\\.kubernetes\\.io/azure-load-balancer-health-probe-request-path"
    value = var.app_health_probe_path
  }
  depends_on = [azurerm_public_ip.litter_ip]
}

############################################################
# NAMESPACES & SECRETS
############################################################
resource "kubernetes_namespace" "env" {
  metadata {
    name = "${var.app_name}-${var.app_environment}"
  }
  depends_on = [
    azurerm_kubernetes_cluster.aks,
    azurerm_dns_a_record.litter_dns # ensure the DNS record is created early on to allow time to propagate
  ]
}

locals {
  # key vault provider name:
  kv_provider_name = "${var.app_name}-kv-provider"
}

resource "kubectl_manifest" "az_kv_k8s_provider" {
  # Using kubectl as a workaround for kubernetes_manifest requiring an active cluster/k8s API
  # see: https://github.com/hashicorp/terraform-provider-kubernetes/issues/1775
  yaml_body = <<EOF
apiVersion: secrets-store.csi.x-k8s.io/v1
kind: SecretProviderClass
metadata:
  name: ${local.kv_provider_name}
  namespace: ${kubernetes_namespace.env.metadata[0].name}
spec:
  provider: azure
  parameters:
    usePodIdentity: "false"
    useVMManagedIdentity: "true"
    keyvaultName: "${var.kv_name}"
    tenantId: "${data.azurerm_key_vault.litter_kv.tenant_id}"
    objects: |-
      array:
        - |
          objectName: db-user-username
          objectType: secret
        - |
          objectName: db-user-password
          objectType: secret
        - |
          objectName: jwt-secret
          objectType: secret
EOF
  depends_on = [kubernetes_namespace.env]
}

# Docker registry secret is created only if the Docker config JSON secret (from Key Vault)
# contains a non-empty value.
resource "kubernetes_secret" "registry_secret" {
  count = var.use_kv_docker_cfg ? 1 : 0
  type  = "kubernetes.io/dockerconfigjson"
  metadata {
    name      = "container-registry-secret"
    namespace = kubernetes_namespace.env.metadata[0].name
  }
  data = {
    ".dockerconfigjson" = data.azurerm_key_vault_secret.kv_docker_cfg[0].value
  }
  depends_on = [kubernetes_namespace.env]
}

resource "kubernetes_service_account" "app_sa" {
  metadata {
    name      = "${var.app_name}-app-sa"
    namespace = kubernetes_namespace.env.metadata[0].name
  }
  # only specify the image pull secret on the SA if the respective secret exists in the Azure KV
  dynamic "image_pull_secret" {
    for_each = kubernetes_secret.registry_secret
    content {
      name = kubernetes_secret.registry_secret[0].metadata[0].name
    }
  }
  automount_service_account_token = false
  depends_on = [kubernetes_namespace.env]
}

# service account for the database
resource "kubernetes_service_account" "db_sa" {
  metadata {
    name      = "${var.app_name}-db-sa"
    namespace = kubernetes_namespace.env.metadata[0].name
  }
  automount_service_account_token = false
  depends_on = [kubernetes_namespace.env]
}

############################################################
# STORAGE: PVC with dynamic provisioning
############################################################
resource "kubernetes_persistent_volume_claim" "mongo_db_pvc" {
  wait_until_bound = false # since it's dynamically provisioned, it won't bind until app chart is first deployed
  metadata {
    name      = "${var.app_name}-db-pvc"
    namespace = kubernetes_namespace.env.metadata[0].name
  }
  spec {
    storage_class_name = "managed-csi"
    access_modes = ["ReadWriteOnce"]
    resources {
      requests = {
        storage = "${var.mongo_db_disk_size_gb}Gi"
      }
    }
  }
  depends_on = [kubernetes_namespace.env]
}

# look up the existing Azure DNS zone
data "azurerm_dns_zone" "litter_dns" {
  name                = var.az_dns_zone_name
  resource_group_name = var.az_dns_rg
}

# create a managed identity for cert-manager to update DNS records via AzureDNS
resource "azurerm_user_assigned_identity" "cert_manager_identity" {
  name                = "${var.app_name}-cert-manager-identity"
  resource_group_name = azurerm_resource_group.litter_k8s_rg.name
  location            = azurerm_resource_group.litter_k8s_rg.location
}

# grant the managed identity permission to manage DNS records (DNS Zone Contributor)
resource "azurerm_role_assignment" "cert_manager_dns" {
  scope                = data.azurerm_dns_zone.litter_dns.id
  role_definition_name = "DNS Zone Contributor"
  principal_id         = azurerm_user_assigned_identity.cert_manager_identity.principal_id
}

# create a federated identity credential for cert-manager to authenticate with Azure AD
resource "azurerm_federated_identity_credential" "cert_manager_federated" {
  name                = "cert-manager-federated-credential"
  resource_group_name = azurerm_resource_group.litter_k8s_rg.name
  issuer              = azurerm_kubernetes_cluster.aks.oidc_issuer_url
  subject             = "system:serviceaccount:cert-manager:cert-manager"
  audience = ["api://AzureADTokenExchange"]
  parent_id           = azurerm_user_assigned_identity.cert_manager_identity.id
}

module "cert_manager" {
  source                                 = "terraform-iaac/cert-manager/kubernetes"
  cluster_issuer_email                   = data.azurerm_key_vault_secret.kv_acme_email.value
  cluster_issuer_name                    = "${var.app_name}-cluster-issuer"
  cluster_issuer_server                  = var.acme_provider_url
  cluster_issuer_private_key_secret_name = "${var.app_name}-acme-key"
  solvers = [
    {
      dns01 = {
        azureDNS = {
          subscriptionID    = var.az_subscription_id
          resourceGroupName = var.az_dns_rg
          hostedZoneName    = var.az_dns_zone_name
          managedIdentity = {
            clientID = azurerm_user_assigned_identity.cert_manager_identity.client_id
          }
        }
      }
      selector = {
        dnsZones = [var.az_dns_zone_name]
      }
    }
  ]
  additional_set = [
    {
      name  = "podLabels.azure\\.workload\\.identity\\/use"
      value = "true"
      type  = "string" # otherwise this is interpreted as a bool which causes an error
    },
    {
      name  = "serviceAccount.labels.azure\\.workload\\.identity\\/use"
      value = "true"
      type  = "string" # otherwise this is interpreted as a bool which causes an error
    },
    {
      name  = "serviceAccount.annotations.azure\\.workload\\.identity\\/client-id"
      value = azurerm_user_assigned_identity.cert_manager_identity.client_id
    },
    {
      name  = "serviceAccount.annotations.azure\\.workload\\.identity\\/tenant-id"
      value = azurerm_user_assigned_identity.cert_manager_identity.tenant_id
    }
  ]
  certificates = {
    (kubernetes_namespace.env.metadata[0].name) = {
      namespace   = kubernetes_namespace.env.metadata[0].name
      dns_names = ["${var.app_environment}.${var.az_dns_zone_name}"]
      secret_name = "${kubernetes_namespace.env.metadata[0].name}-tls"
    }
  }
  depends_on = [
    kubernetes_namespace.env,
    helm_release.nginx_ingress,
    azurerm_federated_identity_credential.cert_manager_federated
  ]
}

# ingress for application traffic (enforce HTTPS)
resource "kubernetes_ingress_v1" "litter_ingress" {
  metadata {
    name      = "${kubernetes_namespace.env.metadata[0].name}-ingress"
    namespace = kubernetes_namespace.env.metadata[0].name
    annotations = {
      "cert-manager.io/cluster-issuer"           = module.cert_manager.cluster_issuer_name
      "nginx.ingress.kubernetes.io/ssl-redirect" = "true"
    }
  }
  spec {
    ingress_class_name = "nginx"
    tls {
      hosts = ["${var.app_environment}.${var.az_dns_zone_name}"]
      secret_name = module.cert_manager.certificates[kubernetes_namespace.env.metadata[0].name].secret_name
    }
    rule {
      host = "${var.app_environment}.${var.az_dns_zone_name}"
      http {
        path {
          path      = "/"
          path_type = "Prefix"
          backend {
            service {
              name = "${var.app_name}-app"
              port {
                number = 8080
              }
            }
          }
        }
      }
    }
  }
  wait_for_load_balancer = true
  depends_on = [
    module.cert_manager,
    helm_release.litter,
    azurerm_dns_a_record.litter_dns
  ]
}

############################################################
# HELM: LITTER APP DEPLOYMENT
############################################################
resource "helm_release" "litter" {
  name             = var.app_name
  chart            = "${path.root}/../chart"
  namespace        = kubernetes_namespace.env.metadata[0].name
  create_namespace = false
  timeout          = 600
  wait             = true
  wait_for_jobs = true
  # pass the values files to the Helm chart (files placed lower override those above them)
  values = [
    file("${path.root}/../chart/values.common.yaml"), # common (shared) values file
    # if app_helm_overrides_filename is set/not null, use the provided file:
      var.app_helm_overrides_filename != null ? file("${path.root}/../chart/${var.app_helm_overrides_filename}") : "",
  ]
  set {
    name  = "app.image.repository"
    value = var.app_image_repo_url
  }
  set {
    name  = "app.image.tag"
    value = var.app_image_tag
  }
  set {
    name  = "app.env.MONGO_DB"
    value = var.mongo_db_name
  }
  set {
    name  = "app.secrets.mode"
    value = "key-vault"
  }
  set {
    name  = "app.secrets.kvClassName"
    value = local.kv_provider_name
  }
  depends_on = [
    helm_release.nginx_ingress,
    kubernetes_service_account.db_sa,
    kubernetes_service_account.app_sa,
    kubernetes_persistent_volume_claim.mongo_db_pvc,
    kubectl_manifest.az_kv_k8s_provider
  ]
}

############################################################
# DATABASE RESTORE JOB
############################################################
# configmap to store sample JSON files for MongoDB collections
resource "kubernetes_config_map" "mongo_sample_collections_cm" {
  metadata {
    name      = "mongo-sample-collections"
    namespace = kubernetes_namespace.env.metadata[0].name
  }
  data = {
    "litter_sample_users.json" = file("${var.mongo_sample_collections_dir}/litter_sample_users.json")
    "litter_sample_messages.json" = file("${var.mongo_sample_collections_dir}/litter_sample_messages.json")
    "litter_sample_subscriptions.json" = file("${var.mongo_sample_collections_dir}/litter_sample_subscriptions.json")
  }
  depends_on = [kubernetes_namespace.env]
}

# restore job (for non-prod environment only)
resource "kubernetes_job" "mongo_restore_job" {
  count = (var.mongo_restore_db && var.app_environment != "prod") ? 1 : 0
  metadata {
    name      = "mongo-restore-job"
    namespace = kubernetes_namespace.env.metadata[0].name
  }
  spec {
    backoff_limit = 1   # only try once
    ttl_seconds_after_finished = 300 # clean up job after 5 minutes
    template {
      metadata {
        name = "mongo-restore-job"
      }
      spec {
        restart_policy = "Never"
        container {
          name  = "mongo-restore"
          image = "mongo:${var.mongo_version_tag}"
          command = ["/bin/sh", "-c"]
          args = [
            <<-EOF
              # set secrets from the CSI-mounted volume (files created by the Azure Key Vault provider)
              export MONGO_APP_USERNAME=$(cat /mnt/secrets/db-user-username)
              export MONGO_APP_PASSWORD=$(cat /mnt/secrets/db-user-password)

              # set secrets from the mounted volume
              export MONGO_APP_USERNAME=$(cat /mnt/secrets/db-user-username)
              export MONGO_APP_PASSWORD=$(cat /mnt/secrets/db-user-password)

              # wait for MongoDB to be ready
              while ! mongosh --host ${var.app_name}-db --eval "db.adminCommand('ping')" >/dev/null 2>&1; do
                echo "Waiting for MongoDB to be ready..."
                sleep 2
              done

              # check if the target database already has data (unless overwrite is forced)
              if [ "${var.mongo_overwrite_existing_db}" != "true" ]; then
                COUNT=$(mongosh --host ${var.app_name}-db --eval "db.getSiblingDB('${var.mongo_db_name}').getCollectionNames().length" --quiet)
                if [ "$COUNT" -gt 0 ]; then
                  echo "Database already contains data. Skipping restore."
                  exit 0
                fi
              fi

              echo "Starting MongoDB collections import..."
              mongoimport --host ${var.app_name}-db --db ${var.mongo_db_name} --collection users --file /workspace/litter_sample_users.json --jsonArray
              mongoimport --host ${var.app_name}-db --db ${var.mongo_db_name} --collection messages --file /workspace/litter_sample_messages.json --jsonArray
              mongoimport --host ${var.app_name}-db --db ${var.mongo_db_name} --collection subscriptions --file /workspace/litter_sample_subscriptions.json --jsonArray
              echo "MongoDB collections imported successfully"

              # create the application database user as defined in the secrets
              echo "Creating database user..."
              mongosh --host ${var.app_name}-db --eval "
                db = db.getSiblingDB('${var.mongo_db_name}');
                db.createUser({
                  user: '$MONGO_APP_USERNAME',
                  pwd: '$MONGO_APP_PASSWORD',
                  roles: [{ role: 'readWrite', db: '${var.mongo_db_name}' }]}
              );"
              echo "Database user created successfully"
            EOF
          ]
          # mount the sample collections ConfigMap
          volume_mount {
            name       = "mongo-sample-collections"
            mount_path = "/workspace"
          }
          volume_mount {
            name       = "secrets-volume"
            mount_path = "/mnt/secrets"
            read_only  = true
          }
          # mount the CSI volume for Key Vault secrets
          volume_mount {
            name       = "secrets-volume"
            mount_path = "/mnt/secrets"
            read_only  = true
          }
          resources {
            requests = {
              cpu    = "100m"
              memory = "256Mi"
            }
            limits = {
              cpu    = "200m"
              memory = "512Mi"
            }
          }
        }
        # volume from the ConfigMap which contains our sample JSON files
        volume {
          name = "mongo-sample-collections"
          config_map {
            name = kubernetes_config_map.mongo_sample_collections_cm.metadata[0].name
          }
        }
        volume {
          name = "secrets-volume"
          csi {
            driver    = "secrets-store.csi.k8s.io"
            read_only = true
            volume_attributes = {
              secretProviderClass = local.kv_provider_name
            }
          }
        }
        # CSI volume to mount Azure Key Vault secrets (provided by the SecretProviderClass)
        volume {
          name = "secrets-volume"
          csi {
            driver    = "secrets-store.csi.k8s.io"
            read_only = true
            volume_attributes = {
              secretProviderClass = local.kv_provider_name
            }
          }
        }
      }
    }
  }
  depends_on = [
    helm_release.litter,
    kubernetes_config_map.mongo_sample_collections_cm,
    kubectl_manifest.az_kv_k8s_provider
  ]
}
