############################################################
# TERRAFORM SETTINGS & PROVIDERS
############################################################
terraform {
  required_version = "~> 1.10.0"
  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 4.20.0"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.35.1"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.17.0"
    }
    kubectl = {
      source  = "alekc/kubectl"
      version = "~> 2.1.3"
    }
  }
}

# share the kubeconfig credentials with all relevant providers instead of duplicating them
locals {
  kubeconfig = {
    host = azurerm_kubernetes_cluster.aks.kube_config.0.host
    client_certificate = base64decode(azurerm_kubernetes_cluster.aks.kube_config.0.client_certificate)
    client_key = base64decode(azurerm_kubernetes_cluster.aks.kube_config.0.client_key)
    cluster_ca_certificate = base64decode(azurerm_kubernetes_cluster.aks.kube_config.0.cluster_ca_certificate)
  }
}

provider "azurerm" {
  features {}
  # when using Azure CLI authentication; no need to explicitly set client_id, client_secret, or tenant_id
  # when using a service principal (needed for GitHub Actions); they must be environment variables
  #   see: https://registry.terraform.io/providers/hashicorp/azurerm/latest/docs/guides/service_principal_client_secret
  subscription_id = var.az_subscription_id
}

provider "kubernetes" {
  host                   = local.kubeconfig.host
  client_certificate     = local.kubeconfig.client_certificate
  client_key             = local.kubeconfig.client_key
  cluster_ca_certificate = local.kubeconfig.cluster_ca_certificate
}

provider "helm" {
  kubernetes {
    host                   = local.kubeconfig.host
    client_certificate     = local.kubeconfig.client_certificate
    client_key             = local.kubeconfig.client_key
    cluster_ca_certificate = local.kubeconfig.cluster_ca_certificate
  }
}

provider "kubectl" {
  host                   = local.kubeconfig.host
  client_certificate     = local.kubeconfig.client_certificate
  client_key             = local.kubeconfig.client_key
  cluster_ca_certificate = local.kubeconfig.cluster_ca_certificate
  load_config_file       = false
}
