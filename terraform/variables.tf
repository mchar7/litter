############################################################
# INFRASTRUCTURE CONFIGURATION
############################################################
variable "az_subscription_id" {
  type        = string
  description = "Azure subscription ID"
}

variable "k8s_rg_name" {
  type        = string
  description = "Name of the resource group"
  default     = "litter-k8s-rg"
}

variable "k8s_location" {
  type        = string
  description = "Azure region for the AKS cluster"
  default     = "eastus"
}

variable "k8s_cluster_name" {
  type        = string
  description = "AKS cluster name"
  default     = "litter-k8s-cluster"
}

variable "k8s_node_count" {
  type        = number
  description = "Number of nodes for the AKS cluster"
  default     = 1
}

variable "k8s_node_az_sku" {
  type        = string
  description = "VM size for the AKS cluster nodes"
  default     = "Standard_B2s"
}

variable "k8s_lb_az_sku" {
  type        = string
  description = "Load balancer SKU (basic or standard)"
  default     = "basic"
}

variable "k8s_node_disk_size_gb" {
  type        = number
  description = "OS disk size in GB for AKS nodes"
  default     = 40
}

variable "mongo_db_disk_size_gb" {
  type        = number
  description = "Disk size in GB for MongoDB"
  default     = 4
}

############################################################
# APP CONFIGURATION
############################################################
variable "app_name" {
  type        = string
  description = "Application name (lowercase only)"
  default     = "litter"

  validation {
    condition = can(regex("^[a-z]+$", var.app_name))
    error_message = "The app_name variable must contain only lowercase letters."
  }
}

variable "app_environment" {
  type        = string
  description = "Deployment environment (e.g., dev, staging, prod)"
  default     = "dev"
}

variable "app_image_repo_url" {
  type        = string
  description = "Image repository for the Litter app"
}

variable "app_image_tag" {
  type        = string
  description = "Image tag for the Litter app"
  default     = "latest"
}

variable "app_helm_overrides_path" {
  type        = string
  description = "Path to the Helm override file (use to override 'values.common.yaml', relative to 'chart' directory)"
  default     = null
  nullable    = true
}

# health probe path for Azure Load Balancer
variable "app_health_probe_path" {
  type        = string
  description = "The HTTP path used for the Azure Load Balancer health probe"
  default     = "/healthz"
}

############################################################
# DATABASE CONFIGURATION
############################################################
variable "mongo_version_tag" {
  type        = string
  description = "MongoDB version to use"
  default     = "8.0"
}

variable "mongo_db_name" {
  type        = string
  description = "Name of the MongoDB database to check and restore"
  default     = "litter"
}

variable "mongo_sample_collections_dir" {
  type        = string
  description = "Path to the MongoDB .archive backup file"
  default     = "./terraform/resources/sample_data"
}

variable "mongo_restore_db" {
  type        = bool
  description = "If true, populate the MongoDB database with the sample collections in mongo_sample_collections_dir"
  default     = false
}

variable "mongo_overwrite_existing_db" {
  type        = bool
  description = "(CAUTION!) If true, force overwriting the database even if it already exists"
  default     = false
}

############################################################
# KEY VAULT & SECRET NAME CONFIGURATION
############################################################
# NOTE: secret names have been de-parameterized and are now hard-coded
#   into the secrets.tf file to avoid making this variable file too verbose.
variable "kv_rg" {
  type        = string
  description = "Resource group name for the Azure Key Vault"
}

variable "kv_name" {
  type        = string
  description = "Name of the Azure Key Vault"
}

variable "use_kv_docker_cfg" {
  type        = bool
  description = "Set to true to read the optional docker-cfg Key Vault secret (for private registries)"
  default     = false
}

############################################################
# ACME & IMAGE CONFIGURATION
############################################################
variable "az_dns_rg" {
  type        = string
  description = "Azure resource group for the DNS zone"
  default     = "litter-dns-rg"
}

variable "az_dns_zone_name" {
  type        = string
  description = "Azure DNS zone name (e.g., litter.dev)"
  default     = "litter.dev"
}

variable "acme_provider_url" {
  type        = string
  description = "ACME server URL"
  default     = "https://acme-v02.api.letsencrypt.org/directory" # can switch to staging for testing
}
