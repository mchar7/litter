# These are the configurations for the backend of the terraform state file
# These values can't be parameterized, so ensure they match your environment
terraform {
  backend "azurerm" {
    resource_group_name  = "litter-state-rg"
    storage_account_name = "litterstatestorage"
    container_name       = "tfstate"
    key                  = "terraform.tfstate"
  }
}
