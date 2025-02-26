# Helm Chart for Litter

This chart provides the Kubernetes application manifests for the Litter app and a MongoDB instance.
It doesn't manage all the peripheral details (like PVC creation, certificates, or DNS), however.
I'd recommend using the Terraform setup in this repository to handle the entire end-to-end infrastructure.

> [!IMPORTANT]
> ### Prerequisites
> - [x] Unix-based terminal (Linux, macOS, WSL, etc.)
> - [x] Helm [installed](https://helm.sh/docs/intro/install/) and configured on your local machine
> - [x] Cluster running and configured with:
>> - [x] A valid StorageClass (and PVC already created if you need MongoDB data persistence)
>> - [x] Certificate management (if you require HTTPS) and ingress setup (this chart assumes 'nginx-ingress' is on the cluster)
>> - [x] Any other dependencies your environment might require

## Instructions

### 1. Usage

* You'll need to adjust the environment overrides in the `values.common.yaml` file, as well as the override file for the
  environment you'll be deploying to (e.g., `values.dev.yaml`, `values.staging.yaml`, `values.prod.yaml`).
* You'll need to set overrides for the app's image repository and tag (they are left blank in the common values file as
  they are usually overridden during the automated CI/CD workflow).

<details>
<summary>Example command</summary>

```bash
helm install litter ./chart \
  --namespace your-namespace \
  --create-namespace \
  -f ./chart/values.common.yaml \
  -f ./chart/values.dev.yaml \
  --set app.image.repository=<your-repo> \
  --set app.image.tag="latest" # or a specific tag
```

</details>

> [!NOTE]
> If you set custom resource requests/limits or environment variables, place them in your override YAML.
> The required secrets (e.g., for MongoDB URI and the JWT secret) should already be available in the target namespace.

### 2. Configuration

The main configuration is in:

* *values.common.yaml*: Shared default values used across all environments.
* *values.(env).yaml*: Environment-specific overrides (e.g., dev vs. staging vs. prod).

> [!TIP]
> If you don't need separate environments, you can just stick to `values.common.yaml` and pass in minimal overrides.
> But I find these environment overrides to be a good way to keep things organized.

## Recommended Approach

> [!CAUTION]
> Manually deploying this chart bypasses automatic certificate generation, ingress constructs, persistent volumes, and DNS management.
> You'll be on your own for these parts of the puzzle.

*The preferred (_and simpler_) route* is to use the Terraform IaC approach, which automates the entire environment—AKS,
Key Vault, certificate provisioning, DNS, and more.
See the [Terraform README](../terraform/README.md).

## That's it!

If you run into trouble:

1. Check your environment overrides.
   Make sure values for `app.image.repository`/`tag` are correct.
2. Confirm your secrets (ex. `mongo-uri`) exist in the cluster's Key Vault references or are manually defined in a
   secret.
3. For DNS/cert or PVC/Storage issues, see the [Terraform section](../terraform/README.md) or open an issue in this
   repository.

> [!WARNING]
> This chart won't magically handle cluster-level configurations.
> If you add or remove stuff manually, keep track of it.
