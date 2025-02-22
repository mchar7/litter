###############################################################################
# EXAMPLE CONFIGURATION FILE
# Fill in your values and add "-var-file=(this file)" to your Terraform command
###############################################################################

# Azure configuration
az_dns_rg        = "litter-dns-rg"
az_dns_zone_name = "litter.dev"
az_subscription_id = "00000000-0000-0000-0000-000000000000"

# core resource settings
k8s_location          = "eastus"
k8s_lb_az_sku         = "basic"
k8s_node_az_sku       = "Standard_B2s"
k8s_node_count        = 1
k8s_node_disk_size_gb = 40
mongo_db_disk_size_gb = 4

# app configuration
app_environment       = "dev"
app_name              = "litter"
app_image_tag         = "latest"
app_image_repo_url    = "ghcr.io/mchar7/litter"
app_health_probe_path = "/actuator/health"
# leave commented out to only use values from 'values.common.yaml':
# app_helm_overrides_filename = "values.overrides.yaml"

# mongo configuration
mongo_version_tag            = "8.0"
mongo_db_name                = "litter"
mongo_restore_db             = true
mongo_overwrite_existing_db  = false
mongo_sample_collections_dir = "./resources/sample_data"

# ACME and domain configuration
# acme_provider_url = "https://acme-v02.api.letsencrypt.org/directory" # Let's Encrypt production server
acme_provider_url = "https://acme-staging-v02.api.letsencrypt.org/directory" # staging server

# key vault configuration (all sensitive secrets are stored in the KV)
kv_rg             = "litter-kv-rg"
kv_name           = "litter-kv"
use_kv_docker_cfg = false
use_kv_tls_cert   = false
