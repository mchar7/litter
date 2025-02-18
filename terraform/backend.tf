# These are the configurations for the backend of the terraform state file
# These values can't be parameterized, so ensure they match your environment
# If ran via the CD workflow via GitHub Actions, key will be changed programmatically
#   to match the code of the deployment environment.
# This is to get around the 'azurerm' backend not key prefixing the workspace name
#   See: https://github.com/hashicorp/terraform/issues/28985
terraform {
  backend "azurerm" {
    resource_group_name  = "litter-state-rg"
    storage_account_name = "litterstatestorage"
    container_name       = "tfstate"
    key                  = "env-dev.tfstate" # this is the default key
  }
}
