# Development Guide

This guide is for developers who wish to contribute to the Keycloak Vault Provider for OpenBao and HashiCorp Vault.

## Building the Project

To build the project without running tests:

```bash
./mvnw clean install -DskipTests
```

## Local Development Environment

You can set up a local Kubernetes cluster using [kind](https://kind.sigs.k8s.io/) and deploy the required services for development and testing.

1. **Create a kind cluster:**
    ```bash
    kind create cluster --name secrets-provider --config testing/configs/kind-cluster-config.yaml
    ```

2. **Deploy OpenBao and Keycloak:**
    ```bash
    kubectl apply -f testing/manifests
    ```

This setup will:
- Deploy OpenBao and Keycloak with two realms.
- Create a user in the "second realm" for testing the IdP secret in the Vault SPI.
- Include the compiled JAR file in the Keycloak deployment (ensure you have built the project first).

**Access Information:**
- **OpenBao:** [http://127.0.0.127:8200](http://127.0.0.127:8200)
  Root token: `my-root-token`
- **Keycloak:** [http://127.0.0.127:8080](http://127.0.0.127:8080)
  Admin credentials: `admin` / `admin`
- **Federated realm login:** [http://127.0.0.127:8080/realms/first/account/](http://127.0.0.127:8080/realms/first/account/)
  User in "second realm": `joe` / `joe`

## Running Integration Tests

> **Note:** Integration tests require `kind` and `kubectl` to be installed.

To run integration tests:

```bash
./mvnw clean verify
```

## Running Checkstyle

To check code style compliance using Checkstyle, run:

```bash
./mvnw checkstyle:check
```

This will analyze the codebase and report any style violations.
