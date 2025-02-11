############################################################
# MAIN INFRASTRUCTURE
############################################################
resource "azurerm_resource_group" "litter_k8s_rg" {
  name     = var.k8s_rg_name
  location = var.k8s_location
}

resource "azurerm_kubernetes_cluster" "aks" {
  name                = var.k8s_cluster_name
  location            = azurerm_resource_group.litter_k8s_rg.location
  resource_group_name = azurerm_resource_group.litter_k8s_rg.name
  dns_prefix          = var.k8s_cluster_name
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

# create a secret to store the app secrets (mongo DB credentials, jwt-secret, etc.)
resource "kubernetes_secret" "app_secret_values" {
  type = "Opaque"
  metadata {
    name      = "${var.app_name}-secrets"
    namespace = "${var.app_name}-${var.app_environment}"
  }
  data = {
    "mongo-app-username" = data.azurerm_key_vault_secret.kv_db_user_username.value
    "mongo-app-password" = data.azurerm_key_vault_secret.kv_db_user_password.value
    "jwt-secret"         = data.azurerm_key_vault_secret.kv_jwt_secret.value
  }
  depends_on = [kubernetes_namespace.env]
}

# Docker registry secret is created only if the Docker config JSON secret (from Key Vault)
# contains a non-empty value.
resource "kubernetes_secret" "registry_secret" {
  count = var.use_kv_docker_cfg ? 1 : 0
  type  = "kubernetes.io/dockerconfigjson"
  metadata {
    name      = "container-registry-secret"
    namespace = "${var.app_name}-${var.app_environment}"
  }
  data = {
    ".dockerconfigjson" = data.azurerm_key_vault_secret.kv_docker_cfg[0].value
  }
  depends_on = [kubernetes_namespace.env]
}

resource "kubernetes_service_account" "app_sa" {
  metadata {
    name      = "${var.app_name}-app-sa"
    namespace = "${var.app_name}-${var.app_environment}"
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
    namespace = "${var.app_name}-${var.app_environment}"
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
    namespace = "${var.app_name}-${var.app_environment}"
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

############################################################
# TLS: Cert-Manager & Ingress
############################################################
# dynamic wait using a provisioner to repeatedly check DNS propagation success
resource "null_resource" "wait_for_dns" {
  provisioner "local-exec" {
    command = <<-EOT
      #!/bin/bash
      TARGET="${var.app_environment}.${var.az_dns_zone_name}"
      EXPECTED="${azurerm_public_ip.litter_ip.ip_address}"
      TIMEOUT=600 # maximum total wait time (in seconds)
      INTERVAL=30 # time between checks (in seconds)
      ELAPSED=0
      echo "Waiting for DNS record $TARGET to propagate to $EXPECTED..."
      while [ $ELAPSED -lt $TIMEOUT ]; do
        RESOLVED=$(dig +short $TARGET | tr -d '[:space:]')
        if [ "$RESOLVED" = "$EXPECTED" ]; then
          echo "DNS propagation complete: $RESOLVED matches expected IP."
          exit 0
        fi
        echo "DNS not yet propagated. Resolved to '$RESOLVED', expected '$EXPECTED'. Sleeping for $INTERVAL s..."
        sleep $INTERVAL
        ELAPSED=$((ELAPSED + INTERVAL))
      done
      echo "Timeout ($TIMEOUT s) reached without successful DNS propagation. Womp womp."
      exit 1
    EOT
    interpreter = ["/bin/bash", "-c"]
  }
  depends_on = [azurerm_dns_a_record.litter_dns]
}

# create all the Cert-Manager related resources
module "cert_manager" {
  source                                 = "terraform-iaac/cert-manager/kubernetes"
  cluster_issuer_email                   = data.azurerm_key_vault_secret.kv_acme_email.value
  cluster_issuer_name                    = "${var.app_name}-cluster-issuer"
  cluster_issuer_server                  = var.acme_server
  cluster_issuer_private_key_secret_name = "${var.app_name}-acme-key"
  solvers = [
    {
      http01 = {
        ingress = {
          class = "nginx"
        }
      }
    }
  ]
  certificates = {
    "${var.app_name}-${var.app_environment}" = {
      namespace   = "${var.app_name}-${var.app_environment}"
      dns_names = ["${var.app_environment}.${var.az_dns_zone_name}"]
      secret_name = "${var.app_name}-tls-${var.app_environment}"
    }
  }
  depends_on = [
    kubernetes_namespace.env,
    helm_release.nginx_ingress,
    azurerm_dns_a_record.litter_dns,
  ]
}

# ingress for the ACME challenge to a dummy service
# (this is required for the ACME challenge to work)
resource "kubernetes_ingress_v1" "acme_challenge_ingress" {
  metadata {
    name      = "${var.app_name}-acme-challenge-ingress"
    namespace = "${var.app_name}-${var.app_environment}"
    annotations = {
      "cert-manager.io/cluster-issuer"            = module.cert_manager.cluster_issuer_name
      "acme.cert-manager.io/http01-edit-in-place" = "true"
      "nginx.ingress.kubernetes.io/ssl-redirect"  = "false"
    }
  }
  spec {
    ingress_class_name = "nginx"
    rule {
      host = "${var.app_environment}.${var.az_dns_zone_name}"
      http {
        path {
          path      = "/.well-known/acme-challenge"
          path_type = "ImplementationSpecific"
          backend {
            service {
              name = "dummy-service"
              port {
                number = 80
              }
            }
          }
        }
      }
    }
  }
  depends_on = [
    module.cert_manager,
    helm_release.litter,
    azurerm_dns_a_record.litter_dns
  ]
}

# ingress for application traffic (enforce HTTPS)
resource "kubernetes_ingress_v1" "litter_ingress" {
  metadata {
    name      = "${var.app_name}-ingress-${var.app_environment}"
    namespace = "${var.app_name}-${var.app_environment}"
    annotations = {
      "nginx.ingress.kubernetes.io/ssl-redirect" = "true"
    }
  }
  spec {
    ingress_class_name = "nginx"
    tls {
      hosts = ["${var.app_environment}.${var.az_dns_zone_name}"]
      secret_name = "${var.app_name}-tls-${var.app_environment}"
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
  namespace        = "${var.app_name}-${var.app_environment}"
  create_namespace = false
  timeout          = 600
  wait             = true
  wait_for_jobs = true
  # pass the values files to the Helm chart (files placed lower override those above them)
  values = [
    file("${path.root}/../chart/values.common.yaml"), # common (shared) values file
    file("${path.root}/../chart/values.${var.app_environment}.yaml"), # override with environment-specific values file
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

  depends_on = [
    helm_release.nginx_ingress,
    kubernetes_service_account.db_sa,
    kubernetes_service_account.app_sa,
    kubernetes_persistent_volume_claim.mongo_db_pvc,
  ]
}

############################################################
# DATABASE RESTORE JOB
############################################################
# configmap to store sample JSON files for MongoDB collections
resource "kubernetes_config_map" "mongo_sample_collections_cm" {
  metadata {
    name      = "mongo-sample-collections"
    namespace = "${var.app_name}-${var.app_environment}"
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
    namespace = "${var.app_name}-${var.app_environment}"
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
          env {
            name = "MONGO_APP_USERNAME"
            value_from {
              secret_key_ref {
                name = kubernetes_secret.app_secret_values.metadata[0].name
                key  = "mongo-app-username"
              }
            }
          }
          env {
            name = "MONGO_APP_PASSWORD"
            value_from {
              secret_key_ref {
                name = kubernetes_secret.app_secret_values.metadata[0].name
                key  = "mongo-app-password"
              }
            }
          }
          args = [
            <<-EOF
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
      }
    }
  }
  depends_on = [
    helm_release.litter,
    kubernetes_config_map.mongo_sample_collections_cm
  ]
}
