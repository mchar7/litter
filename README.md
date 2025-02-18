![Litter Banner](res/img/litter_banner_2880x754.webp)
![CI/CD](https://img.shields.io/github/actions/workflow/status/mchar7/litter/ci_build-test-push.yml?branch=main&style=for-the-badge&logo=github&label=CI/CD)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.1-brightgreen?style=for-the-badge&logo=spring-boot)
![Java](https://img.shields.io/badge/Java-Corretto%2021-orange?style=for-the-badge&logo=openjdk)
![MongoDB](https://img.shields.io/badge/MongoDB-8.0-green?style=for-the-badge&logo=mongodb)
![K8S](https://img.shields.io/badge/K8S-Azure%20AKS-blue?style=for-the-badge&logo=kubernetes)
![License](https://img.shields.io/badge/License-GPL%20v3-blue?style=for-the-badge&logo=gnu)

Litter is a (backend-only for now) API for a Twitter-like social media site. Built with Spring Boot, MongoDB, and enough
cloud infrastructure to make me cry when I get my monthly Azure bill.

> [!TIP]
> **TL;DR:** It's like Twitter, but smaller.
> And ~~better~~.
> It deploys itself to Azure AKS with one Terraform command ([*almost*](terraform/README.md)).
> Want to run it? Skip to the [deployment section](#rocket-deployment-instructions).

## Table of Contents

- [Features](#rocket-features)
- [Tech Stack](#wrench-tech-stack)
- [Deployment Instructions](#rocket-deployment-instructions)
- [API Documentation](#book-api-documentation)
- [Project Structure](#building_construction-project-structure)
- [Infrastructure](#cloud-infrastructure)
- [License](#scroll-license)
- [Acknowledgments](#heart-acknowledgments)

## :rocket: Features

* Reactive Spring Boot backend with WebFlux
* Token-based authentication with JWT
* Message publishing and subscription system
* Automated deployment to Azure AKS
* Full CI/CD pipeline with GitHub Actions
* Comprehensive test suite with JUnit 5
* Infrastructure as Code with Terraform
* Kubernetes deployment via Helm chart
* Docker Compose for local development
* API documentation with Postman

## :wrench: Tech Stack

| Category                                | Technologies                                                                                  |
|-----------------------------------------|-----------------------------------------------------------------------------------------------|
| Backend                                 | Spring Boot, WebFlux                                                                          |
| Frontend                                | N/A (maybe later if I still care — I've been messing about with a possible Angular front end) |
| Database                                | MongoDB 8.*x* via Spring Data (Reactive)                                                      |
| Cloud Platform                          | Azure: _Azure Kubernetes Service (AKS), Key Vault, DNS Zone_                                  |
| [Infrastructure](#cloud-infrastructure) | Terraform, Helm, Kubernetes, Docker                                                           |
| Build Tools                             | Gradle, GitHub Actions                                                                        |
| Testing                                 | JUnit 5, TestContainers, Postman                                                              |
| Security                                | JWT, Azure Key Vault, TLS/SSL, Argon2 password hashing (Spring Security)                      |

## :rocket: Deployment Instructions

Pick your deployment method:

* [**Terraform**](terraform/README.md) (Recommended) - Full cloud deployment
* [**Helm**](chart/README.md) - Manual Kubernetes deployment
* [**Docker Compose**](compose/README.md) - Local development

## :book: API Documentation

TODO: Document the API endpoints at the controller level using `springdoc-openapi` or with AsciiDoc using
`asciidoctor-spring-autoconfigure`.

## :building_construction: Project Structure

```
litter-test
├── chart/              # Helm templates and values
│
├── compose/            # Docker Compose files
│
├── src/
│    ├── main/          # Application source code
│    └── test/          # Unit and integration tests
│
├── terraform/          # IaC configuration
│
├── build.gradle        # Gradle build file
├── gradle.properties   # Gradle properties
├── settings.gradle     # Gradle settings
│
├── Dockerfile          # Docker build file
│
└── README.md           # Wait a minute...this is pretty meta
```

## :cloud: Infrastructure

![Azure infrastructure as Mermaid diagram](res/img/litter_architecture_diagram.png)

> [!TIP]
> The infrastructure is defined entirely in code:
>
> * See [Terraform Configuration](terraform/README.md)

## :scroll: License

This project is licensed under the GNU General Public License v3.0 in case someone wants to clone this for some reason;
see the [LICENSE](LICENSE) file for details.

## :heart: Acknowledgments

* My caffeine addiction, for making this possible
* The Azure free tier, for not bankrupting me during development
* The original Twitter, for being so bad that I had to try to make my own

> [!IMPORTANT]
> Found a bug? Have a feature request? Just want to let me know that more than 2 eyeballs have seen this? Open an issue!
