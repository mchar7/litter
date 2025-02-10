# Terraform Deployment Instructions

Below are the steps to deploy this project onto Azure using Terraform.

> [!IMPORTANT]
> ### Prerequisites
> - [x] Azure account and subscription
> - [x] Unix-based terminal (Linux, macOS, WSL, etc.)
> - [x] Terraform [installed](https://learn.hashicorp.com/tutorials/terraform/install-cli)
> - [x] Azure CLI [installed](https://learn.microsoft.com/en-us/cli/azure/install-azure-cli) and logged in
> - [x] A domain name, with the ability to set custom nameservers (they will need to point to Azure DNS)
> - [x] Docker image repository (e.g., Docker Hub, Azure Container Registry, etc.) for the app, with the image pushed

## Before You Begin

Before running Terraform, note that the backend configuration for remote state management is hard-coded in the file
`./terraform/backend.tf`.
You'll have to review and update these settings to match your environment.
Removing the backend block will cause Terraform to default to using a local state file, so if you prefer to maintain
your Terraform state locally you can just remove `/terraform/backend.tf`.

<details>
<summary>Example Azure CLI commands for setting up remote backend resources</summary>

```bash
# Create the resource group for your Terraform state backend
az group create --name litter-state-rg --location EastUS

# Create the storage account for storing the Terraform state file
az storage account create \
  --name litterstateacct \
  --resource-group litter-state-rg \
  --location EastUS \
  --sku Standard_LRS

# Create the blob container for storing the Terraform state file
az storage container create \
  --account-name litterstateacct \
  --name tfstate
```

</details>

Before running Terraform, you must also set up Azure DNS.
Ensure that you create a resource group for your DNS zone, create the DNS zone,
and [adjust your registrar's nameservers to point to Azure DNS](https://learn.microsoft.com/en-us/azure/dns/dns-delegate-domain-azure-dns).

<details>
<summary>Example Azure CLI commands for setting up your Azure DNS zone</summary>

```bash
# Create the resource group for your DNS zone
az group create --name litter-dns-rg --location EastUS

# Create the DNS zone (e.g., litter.dev)
az network dns zone create \
  --resource-group litter-dns-rg \
  --name litter.dev
```

</details>

## Instructions

### 1. [Set up your Azure Key Vault](https://learn.microsoft.com/en-us/azure/key-vault/secrets/quick-create-cli)

Set environment variables in your terminal for the key vault and its resource group's names, as well as the Azure
location:

```bash
KV_NAME="litter-kv"
KV_RESOURCE_GROUP="litter-kv-rg"
KV_LOCATION="EastUS"
```

Create a resource group (separate from the AKS cluster):

```bash
az group create --name $KV_RESOURCE_GROUP --location $KV_LOCATION
```

Create your key vault:

```bash
az keyvault create --name $KV_NAME --resource-group $KV_RESOURCE_GROUP --location $KV_LOCATION
```

Since the vault is created with RBAC enabled, grant your current (signed‑in) user full read/write access using the *Key
Vault Secrets Officer* role:

```bash
CURRENT_USER_OBJECT_ID=$(az ad signed-in-user show --query id -o tsv)
KEYVAULT_ID=$(az keyvault show --name $KV_NAME --resource-group $KV_RESOURCE_GROUP --query id -o tsv)
az role assignment create --assignee $CURRENT_USER_OBJECT_ID --role "Key Vault Secrets Officer" --scope $KEYVAULT_ID
```

### 2. Set the required secrets

This Terraform setup pulls sensitive values (such as credentials and application secrets) from your Azure Key Vault.

Make sure you set them with the following secret names:

```bash
az keyvault secret set --vault-name $KV_NAME --name "db-user-username"  --value "<mongo-username>"
az keyvault secret set --vault-name $KV_NAME --name "db-user-password"  --value "<mongo-password>"
az keyvault secret set --vault-name $KV_NAME --name "jwt-secret" --value "<jwt-secret>"
az keyvault secret set --vault-name $KV_NAME --name "acme-email" --value "<your-email@example.com>"
```

> [!NOTE]
> If you have a private container registry, create a [Docker config JSON](https://docs.docker.com/reference/cli/docker/login/#credential-stores) and store it as a Key Vault secret:
> ```bash
> az keyvault secret set --vault-name $KV_NAME --name "docker-cfg" --file "<your-docker.json>"
> ```

### 3. Create Your .tfvars file

This Terraform setup relies on variables; review and edit the sample provided in `example.tfvars`.
Copy it to a new file (e.g., `litter.tfvars`), and fill in your details.

The Terraform configuration deploys to a single environment.
Set the "app_environment" variable in the tfvars file to your target environment (e.g., dev, staging, prod).
If you change this value often, you can override it at the command line with `-var="app_environment=dev"`.
This should be placed after the `-var-file="litter.tfvars"` argument so that it takes precedence.

<details>
<summary>-var override example</summary>

```bash
terraform apply -var-file="litter.tfvars" -var="app_environment=dev"
```

</details>

### 4. Deploy Your Infrastructure

From the `./terraform` directory, initialize and apply:

```bash
cd terraform
terraform init
terraform apply -var-file="litter.tfvars" # or whatever you named your .tfvars file
```

Terraform will show you a plan of what it's going to create or update.
Confirm by typing `yes`, and then let it do its thing.

Once complete, Terraform will have provisioned:

1. An AKS cluster (with the specified number of nodes, load balancer, etc.)
2. A namespace, secret(s), database storage, and a Helm chart deployment of the Litter app & database
3. An ingress controller, public IP, and DNS records managed via Azure DNS
4. A Key Vault-based secret retrieval flow

### 5 (Optional): Create a CI/CD Service Principal with General Contributor Role

If you'll run Terraform in a CI/CD pipeline, you're going to need a service principal with broad permissions to manage
the Terraform state, AKS cluster, and public IP.
For convenience during initial deployments, we can assign the built‑in Contributor role at the subscription level.

<details>
<summary>Service principal creation commands:</summary>

```bash
# Choose a name for the CI/CD service principal.
CI_SP_NAME="ci-principal"

# Get the subscription ID.
SUBSCRIPTION_ID=$(az account show --query id -o tsv)

# Create the service principal and capture its credentials.
CI_SP_OUTPUT=$(az ad sp create-for-rbac --name "$CI_SP_NAME" --skip-assignment --output json)
echo "Store these credentials securely (e.g. as a GitHub Actions Secret):"
echo "$CI_SP_OUTPUT"

# Extract the App ID.
CI_SP_APP_ID=$(echo "$CI_SP_OUTPUT" | sed -n 's/.*"appId": *"\([^"]*\)".*/\1/p')

# Get the service principal's Object ID.
CI_SP_OBJECT_ID=$(az ad sp show --id $CI_SP_APP_ID --query id -o tsv)

# Assign the Contributor role to the service principal at the subscription level.
az role assignment create --assignee $CI_SP_OBJECT_ID --role "Contributor" --scope "/subscriptions/$SUBSCRIPTION_ID"

echo "CI/CD service principal created and Contributor role assigned."
```

</details>

> [!NOTE]
> In production, you may wish to refine the scope of the Contributor permission.
> For long‑term core resources that you don't expect to tear down often, consider narrowing the role assignment to just those resources rather than using a subscription‑wide assignment.
>
> I tried using very narrow service principal permissions for the initial deployment, but the Azure service principal requires a lot of permissions when the app's infrastructure is being provisioned for the first time.

## That's It!

You should now have a running Kubernetes cluster with the Litter app deployed.
You should be able to access it over HTTPS at `environment subdomain`.`your DNS zone`.
For example, if your environment is "dev" and your DNS zone is "litter.dev", the URL would be https://dev.litter.dev.

> [!WARNING]
> Keep your secrets safe.
> If you fork this repository, remember not to commit any sensitive data.
> Also, store your .tfvars file securely or add it to `.gitignore`.
