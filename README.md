# Keycloak Vault Provider for OpenBao and HashiCorp Vault

> **⚠️ Note:**
> This project is under development and not yet production-ready.

This project implements a [Keycloak Vault SPI](https://www.keycloak.org/server/vault) provider that integrates Keycloak with [OpenBao](https://openbao.org/) or [HashiCorp Vault](https://developer.hashicorp.com/vault).

With this provider, Keycloak can retrieve secrets directly from OpenBao/Vault rather than storing them in its internal database, enhancing security through external secret management.
The implementation leverages the [KV secrets engine](https://openbao.org/docs/secrets/kv/) for secure secret storage.


## Installation

This project is not available in Maven Central.
To compile it locally, ensure you have JDK and Git installed. Clone the repository and execute:

```bash
./mvnw clean package -DskipTests=true
```

Copy the JAR file to the `secrets-provider/target/` directory in your Keycloak distribution.
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
| `--spi-vault-secrets-provider-ca-certificate-file` | CA certificate file path for server validation (required for HTTPS). | None                                                  |
| `--spi-vault-secrets-provider-role`           | Role to use for authentication (required).                  | None                                                  |


## Usage

### Referencing Secrets

To reference a secret in your Keycloak realm configuration, use the following format:

```
${vault.<path_to_secret>.<key>}
```
Where:
- The `<path_to_secret>` is the path to the secret under the [KV secrets engine](https://openbao.org/docs/secrets/kv/) mount point.
- The `<key>` is the field in the key/value secret that contains the secret.

Globally, in the provider configuration, the administrator can configure:
- The mount point of the KV secrets engine (reflecting the configuration in OpenBao/HashiCorp Vault).
- The prefix path that will be prepended to the path specified in the client secret reference.
- The KV secrets engine version (v1 or v2).

The format supports the template variable `%realm%` which will be replaced with the current realm name.

#### Configuration Example

Keycloak configuration parameters:

```
--spi-vault-secrets-provider-address="http://bao-service.mynamespace.svc.cluster.local:8200"
--spi-vault-secrets-provider-kv-mount="secret"
--spi-vault-secrets-provider-kv-path-prefix="keycloak/%realm%"
--spi-vault-secrets-provider-kv-version=2
```

Client secret reference in Keycloak:

```
${vault.clients/29a2de9b-9389-4287-b097-c79dd12469d9.client_secret}
```

#### How it works

When this reference is used in the `master` realm:

1. The provider constructs the full path:
   - Base URL: `http://bao-service.mynamespace.svc.cluster.local:8200`
   - API version path: `/v1/`
   - Mount point: `secret/`
   - Data path (for KV v2): `data/`
   - Prefix with realm substitution: `keycloak/master/`
   - Reference path: `clients/29a2de9b-9389-4287-b097-c79dd12469d9`

2. The complete URL becomes:
   `http://bao-service.mynamespace.svc.cluster.local:8200/v1/secret/data/keycloak/master/clients/29a2de9b-9389-4287-b097-c79dd12469d9`

3. The provider extracts the `client_secret` field from the response, for example:

```json
{
    "data": {
        "data": {
            "client_secret": "my-client-secret"
        }
    }
}
```

Note: The JSON structure shown above reflects HashiCorp Vault's [KV v2 response format](https://openbao.org/api-docs/secret/kv/kv-v2/), where the actual secret data is nested under `data.data`.

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
