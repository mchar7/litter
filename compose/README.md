# Docker Compose Setup for Local Development

This directory contains Docker Compose configurations for running the Litter application locally.
While suitable for development and testing, consider the [Terraform-based Kubernetes deployment](../terraform/README.md)
for staging and production environments.

> [!IMPORTANT]
> ### Prerequisites
> - [x] Unix-based terminal (Linux, macOS, WSL, etc.)
> - [x] [Docker](https://docs.docker.com/get-docker/) and [Docker Compose](https://docs.docker.com/compose/install/) installed and running
> - [x] The JAR file has already been built via the Gradle wrapper and is located at
    `build/libs/litter.jar` (from the project root)

## Quick Start

### 1. Environment Setup

Copy the example environment file and adjust the values:

```bash
cd compose # navigate to the compose directory
cp example.env .env
vim .env # edit .env with your preferred text editor
```

> [!TIP]
> You'll also need to edit the
`docker-compose_build-image.yml` file to set the correct image name and tag (if using a forked repo).

### 2. Choose Your Deployment Method

You have two options for running the application:

#### Option A: Build Image Locally

Uses the local `Dockerfile` to build the image:

```bash
docker compose -f docker-compose_build-image.yml up -d
```

#### Option B: Pull Pre-built Image

Pulls the image from a container registry:

```bash
docker compose -f docker-compose_pull-image.yml up -d
```

> [!TIP]
> Option A is good for local development when making rapid changes.
>
> Option B is better for testing a specific version or when you don't need to modify the code.

## Development Features

### Remote Debugging

You can include configuration for remote debugging (usually on port 5005).
To connect your IDE:

1. Create a Remote JVM Debug configuration
2. Set the port to 5005
3. Start debugging

> [!TIP]
> - `docker-compose_build-image.yml` includes the JVM remote debugging options by default.
> - `docker-compose_pull-image.yml` does _not_ have this included by default.

### Database Persistence

MongoDB data is persisted in a named volume (`mongodb_data`).
To start fresh, remove the volume:

```bash
docker compose down -v
```

## Common Tasks

### Viewing Logs

```bash
# all containers
docker compose logs -f

# specific container
docker compose logs -f litter-app
```

### Stopping the Application

```bash
docker compose down
```

### Rebuilding After Changes

```bash
docker compose -f docker-compose_build-image.yml up -d --build
```

> [!WARNING]
> Remember that Compose is primarily for development.
> For production deployments, I'd recommend the [Terraform configuration](../terraform/README.md) to deploy to Kubernetes.

## Troubleshooting

If you encounter issues:

1. Ensure all ports (8080, 27017, 5005) are available
2. Check your `.env` file for correct values
3. Verify Docker has sufficient resources
4. Use `docker compose logs` to investigate errors

> [!CAUTION]
> Do not commit your `.env` file to version control!
> In production (e.g. using the Terraform setup), you'll use Azure Key Vault or similar for secrets.
