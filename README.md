# Keycloak Vault Provider for OpenBao and HashiCorp Vault

This project implements a [Keycloak Vault SPI](https://www.keycloak.org/server/vault) provider that integrates Keycloak with [OpenBao](https://openbao.org/) or [HashiCorp Vault](https://developer.hashicorp.com/vault) secret management systems.

With this provider, Keycloak can retrieve secrets directly from OpenBao/Vault rather than storing them in its internal database, enhancing security through external secret management. The implementation leverages the [kv secrets engine](https://openbao.org/docs/secrets/kv/) for secure secret storage and retrieval.


## Installation

This project is not available in Maven Central.
To compile it locally, ensure you have JDK and Git installed. Clone the repository and execute:

```bash
./mvnw clean package -DskipTests=true
```

Copy the JAR file to the `providers` directory in your Keycloak distribution.
For instance, in the official Keycloak container image releases, place the JAR file in the `/opt/keycloak/providers/`.


## Configuration

### Enable client certificate lookup (mandatory)

Add the following command line parameter to kc.sh to choose the provider:

```
--spi-vault-provider=secrets-provider
```

### Parameters


| Parameter                                      | Description                                                 | Default Value                                         |
| --------------------------------------------- | ----------------------------------------------------------- | ----------------------------------------------------- |
| `--spi-vault-secrets-provider-auth-method`    | Authentication method to use (only `kubernetes` supported). | `kubernetes`                                          |
| `--spi-vault-secrets-provider-service-account-file` | Path to the Kubernetes service account token file.          | `/var/run/secrets/kubernetes.io/serviceaccount/token` |
| `--spi-vault-secrets-provider-address`        | URL of the OpenBao/Vault server (required).                 | None                                                  |
| `--spi-vault-secrets-provider-kv-mount`       | Mount point of the KV secrets engine.                       | `secret`                                              |
| `--spi-vault-secrets-provider-kv-path-prefix` | Path prefix for secrets in the KV store.                    | `keycloak/%realm%`                                    |
| `--spi-vault-secrets-provider-kv-version`     | Version of the KV secrets engine (`1` or `2`).              | `2`                                                   |
| `--spi-vault-secrets-provider-ca-certificate-file` | CA certificate file path for server validation.             | None                                                  |
| `--spi-vault-secrets-provider-role`           | Role to use for authentication.                             | None                                                  |


## Usage

### Referencing Secrets

To reference a secret in your Keycloak realm, use the following format:

```
${vault.<path_to_secret>.<key>}
```

## Development

This section is for developers who wish to contribute to the project.


### Building the Project

To build the project without running tests, use the following command:

```bash
./mvnw clean install -DskipTests
```

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



### Running Integration Tests

> **⚠️ Note:**
> Integration tests require `kind` (Kubernetes in Docker) and `kubectl` to be installed on your system.

Run the integration tests with:

```bash
./mvnw clean verify
```
