# Keycloak Vault Provider for OpenBao and HashiCorp Vault

## Installation

## Configuration

| Parameter                                      | Description                                                 | Default Value                                         |
| --------------------------------------------- | ----------------------------------------------------------- | ----------------------------------------------------- |
| `--spi-vault-secrets-provider-auth-method`    | Authentication method to use (only `kubernetes` supported). | `kubernetes`                                          |
| `--spi-vault-secrets-provider-service-account-file` | Path to the Kubernetes service account token file.          | `/var/run/secrets/kubernetes.io/serviceaccount/token` |
| `--spi-vault-secrets-provider-address`        | URL of the OpenBao/Vault server (required).                 | None                                                  |
| `--spi-vault-secrets-provider-kv-secret-path` | Path to the KV secret (required).                           | None                                                  |
| `--spi-vault-secrets-provider-kv-version`     | Version of the KV secrets engine (`1` or `2`).              | `2`                                                   |
| `--spi-vault-secrets-provider-ca-certificate-file` | CA certificate file path for server validation.             | None                                                  |
| `--spi-vault-secrets-provider-role`           | Role to use for authentication.                             | None                                                  |

## Development

### Building the Project

To build the project without running tests, use the following command:

```bash
./mvnw clean install -DskipTests
```

### Running Integration Tests

> **⚠️ Note:**
> Integration tests require `kind` (Kubernetes in Docker) and `kubectl` to be installed on your system.

Run the integration tests with:

```bash
./mvnw clean verify
```

### Setting Up a Local Development Environment

For local development, you can set up a Kubernetes cluster using `kind` and deploy the necessary software with the following commands:

```bash
kind create cluster --name secrets-provider --config testing/configs/kind-cluster-config.yaml
kubectl apply -f testing/manifests
```

This will deploy OpenBao and Keycloak with two realms. Additionally, it will create a user in the "second realm" for testing the IdP secret in the Vault SPI.
Make sure to compile the project first, as the Keycloak deployment includes the compiled JAR file.

- OpenBao is accessible at: `http://127.0.0.127:8200`
  Root token: `my-root-token`
- Keycloak is accessible at: `http://127.0.0.127:8080`
  Admin credentials: `admin` / `admin`
- Login URL for the federated realm: `http://127.0.0.127:8080/realms/first/account/`
  User in the "second realm": `joe` / `joe`

### Running Integration Tests Without Environment Setup

If the environment is already set up, you can skip the setup step and run the integration tests directly:

```bash
./mvnw clean install -DskipEnvSetup=true
```

### Cleaning Up

To delete the local Kubernetes cluster, run:

```bash
kind delete cluster --name secrets-provider
```
