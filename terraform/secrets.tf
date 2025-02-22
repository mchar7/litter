############################################################
# RETRIEVE SECRETS FROM AZURE KEY VAULT
############################################################
# Note: These secrets must also exist, but are not accessed by Terraform:
#   - db-user-username  (MongoDB application username)
#   - db-user-password  (MongoDB application password)
#   - jwt-secret        (JWT secret for the app)

data "azurerm_key_vault" "litter_kv" {
  name                = var.kv_name
  resource_group_name = var.kv_rg
}

# The Key Vault secret for the ACME email
data "azurerm_key_vault_secret" "kv_acme_email" {
  name         = "acme-email"
  key_vault_id = data.azurerm_key_vault.litter_kv.id
}

# The Key Vault secret for the Docker config JSON. It should contain your docker login credentials.
# You do not need to set this secret in the Key Vault if you are using a public registry.
data "azurerm_key_vault_secret" "kv_docker_cfg" {
  count        = var.use_kv_docker_cfg ? 1 : 0
  name         = "docker-cfg"
  key_vault_id = data.azurerm_key_vault.litter_kv.id
}

# secret for the TLS certificate
data "azurerm_key_vault_secret" "kv_tls_cert" {
  count        = var.use_kv_tls_cert ? 1 : 0
  name         = "${var.app_name}-${var.app_environment}-tls-cert"
  key_vault_id = data.azurerm_key_vault.litter_kv.id
}

data "azurerm_key_vault_secret" "kv_tls_key" {
  count        = var.use_kv_tls_cert ? 1 : 0
  name         = "${var.app_name}-${var.app_environment}-tls-key"
  key_vault_id = data.azurerm_key_vault.litter_kv.id
}
