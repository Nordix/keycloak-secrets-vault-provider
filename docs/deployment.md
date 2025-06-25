# Deployment

## Building and Deploying the Extension

This project is not available in Maven Central or any other public repository.
It must be built and deployed manually.

To deploy the Keycloak Vault Provider extension, follow these steps:

1. Compile the Extension

   Ensure you have JDK and Git installed. Clone the repository and build the project:

   ```bash
   ./mvnw clean package -DskipTests=true
   ```

   This will produce a JAR file in the `target/` directory.

2. Copy the JAR to Keycloak

   Copy the compiled JAR file to the `providers/` directory of your Keycloak installation.
   For example, if using the official [Keycloak container image](https://www.keycloak.org/server/containers), the extension should be placed in the `/opt/keycloak/providers/` directory.

## Configuration

### Enable vault provider (mandatory)

Add the following command line parameter to `kc.sh` to choose the provider:

```
--spi-vault-provider=secrets-provider
```

### Parameters

#### Vault Secrets Provider

| Parameter | Description | Default Value |
|-----------|-------------|--------------|
| `--spi-vault-secrets-provider-address` | Address (URL) of the OpenBao/Vault server. Must be provided. | _none_ |
| `--spi-vault-secrets-provider-auth-method` | Authentication method to use. Supported: `kubernetes`. | `kubernetes` |
| `--spi-vault-secrets-provider-service-account-file` | Path to the Kubernetes service account token file for authentication. | `/var/run/secrets/kubernetes.io/serviceaccount/token` |
| `--spi-vault-secrets-provider-kv-mount` | KV secrets engine mount point. | `secret` |
| `--spi-vault-secrets-provider-kv-path-prefix` | Path prefix for secrets. Supports `%realm%` variable. | `keycloak/%realm%` |
| `--spi-vault-secrets-provider-kv-version` | KV secrets engine version (1 or 2). | `1` |
| `--spi-vault-secrets-provider-ca-certificate-file` | Path to CA certificate file for HTTPS connections. Optional. | _none_ |
| `--spi-vault-secrets-provider-role` | Role to use for authentication. | (empty) |

#### Secrets Manager

| Parameter | Description | Default Value |
|-----------|-------------|--------------|
| `--spi-admin-realm-restapi-extension-secrets-manager-address` | Address (URL) of the OpenBao/Vault server for the secrets manager. Must be provided. | _none_ |
| `--spi-admin-realm-restapi-extension-secrets-manager-auth-method` | Authentication method to use. Supported: `kubernetes`. | `kubernetes` |
| `--spi-admin-realm-restapi-extension-secrets-manager-service-account-file` | Path to the Kubernetes service account token file for secrets manager authentication. | `/var/run/secrets/kubernetes.io/serviceaccount/token` |
| `--spi-admin-realm-restapi-extension-secrets-manager-kv-mount` | KV secrets engine mount point. | `secret` |
| `--spi-admin-realm-restapi-extension-secrets-manager-kv-path-prefix` | Path prefix for secrets. Supports `%realm%` variable. | `keycloak/%realm%` |
| `--spi-admin-realm-restapi-extension-secrets-manager-kv-version` | KV secrets engine version (1 or 2). | `1` |
| `--spi-admin-realm-restapi-extension-secrets-manager-ca-certificate-file` | Path to CA certificate file for HTTPS connections. Optional. | _none_ |
| `--spi-admin-realm-restapi-extension-secrets-manager-role` | Role to use for authentication. | (empty) |
