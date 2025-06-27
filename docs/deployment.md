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

The provider works with both OpenBao and HashiCorp Vault, since both implement the same REST API.

### Parameters

#### Vault Secrets Provider

| Parameter                                           | Description                                                           | Default Value                                         |
| --------------------------------------------------- | --------------------------------------------------------------------- | ----------------------------------------------------- |
| `--spi-vault-secrets-provider-address`              | Address (URL) of the OpenBao/Vault server. Must be provided.          | _none_                                                |
| `--spi-vault-secrets-provider-auth-method`          | Authentication method to use. Supported: `kubernetes`.                | `kubernetes`                                          |
| `--spi-vault-secrets-provider-service-account-file` | Path to the Kubernetes service account token file for authentication. | `/var/run/secrets/kubernetes.io/serviceaccount/token` |
| `--spi-vault-secrets-provider-kv-mount`             | KV secrets engine mount point.                                        | `secret`                                              |
| `--spi-vault-secrets-provider-kv-path-prefix`       | Path prefix for secrets. Supports `%realm%` variable.                 | `keycloak/%realm%`                                    |
| `--spi-vault-secrets-provider-kv-version`           | KV secrets engine version (1 or 2).                                   | `1`                                                   |
| `--spi-vault-secrets-provider-ca-certificate-file`  | Path to CA certificate file for HTTPS connections. Optional.          | _none_                                                |
| `--spi-vault-secrets-provider-role`                 | Role to use for authentication.                                       | _none_                                                |

#### Secrets Manager

| Parameter                                                                  | Description                                                                           | Default Value                                         |
| -------------------------------------------------------------------------- | ------------------------------------------------------------------------------------- | ----------------------------------------------------- |
| `--spi-admin-realm-restapi-extension-secrets-manager-address`              | Address (URL) of the OpenBao/Vault server for the secrets manager. Must be provided.  | _none_                                                |
| `--spi-admin-realm-restapi-extension-secrets-manager-auth-method`          | Authentication method to use. Supported: `kubernetes`.                                | `kubernetes`                                          |
| `--spi-admin-realm-restapi-extension-secrets-manager-service-account-file` | Path to the Kubernetes service account token file for secrets manager authentication. | `/var/run/secrets/kubernetes.io/serviceaccount/token` |
| `--spi-admin-realm-restapi-extension-secrets-manager-kv-mount`             | KV secrets engine mount point.                                                        | `secret`                                              |
| `--spi-admin-realm-restapi-extension-secrets-manager-kv-path-prefix`       | Path prefix for secrets. Supports `%realm%` variable.                                 | `keycloak/%realm%`                                    |
| `--spi-admin-realm-restapi-extension-secrets-manager-kv-version`           | KV secrets engine version (1 or 2).                                                   | `1`                                                   |
| `--spi-admin-realm-restapi-extension-secrets-manager-ca-certificate-file`  | Path to CA certificate file for HTTPS connections. Optional.                          | _none_                                                |
| `--spi-admin-realm-restapi-extension-secrets-manager-role`                 | Role to use for authentication.                                                       | _none_                                                |


### Configuring OpenBao or HashiCorp Vault for the Extension

This section explains how to configure OpenBao or HashiCorp Vault so the extension can access secrets stored in the [KV secrets engine](https://openbao.org/docs/secrets/kv/).
The examples are given using CLI but the same steps can be performed using the REST API.


#### Authentication

Currently, the extensions support only the `kubernetes` authentication method. In this mode, the Kubernetes service account token is used for authentication.
To enable Kubernetes authentication, run the following commands:

```
bao auth enable kubernetes
bao write auth/kubernetes/config kubernetes_host=https://$KUBERNETES_SERVICE_HOST:$KUBERNETES_SERVICE_PORT
```

If you are using HashiCorp Vault, replace `bao` with `vault`.

* [OpenBao Kubernetes Auth Documentation](https://openbao.org/docs/auth/kubernetes/)
* [HashiCorp Vault Kubernetes Auth Documentation](https://developer.hashicorp.com/vault/docs/auth/kubernetes)

#### Access Control

The following permissions are required for the extension to function:

* The Vault provider extension requires `read` permission on the KV secrets engine path where secrets are stored.
* The Secrets Manager REST API extension requires `create`, `read`, `update`, `delete`, and `list` permissions on the same path.

To grant these permissions, create the following policies:

```bash
bao policy write keycloak/reader - <<EOF
path "secret/*" {
    capabilities = ["read"]
}
EOF

bao policy write keycloak/admin - <<EOF
path "secret/*" {
    capabilities = ["create", "read", "update", "delete", "list"]
}
EOF
```

The `keycloak/reader` policy grants read-only access to all secrets under `secret/*`, while the `keycloak/admin` policy grants create, read, update, delete and list.
Alternatively, you can combine the two policies into a single policy that grants both read and write permissions and use it for both extensions.

Assuming your Keycloak pod is running in the `my-application-ns` namespace, and is configured with `spec.template.spec.serviceAccountName: keycloak`, and the `keycloak` service account exists in that namespace, you can associate the policies with the service account by creating the following roles:

```bash
bao write auth/kubernetes/role/keycloak-reader bound_service_account_names="keycloak" bound_service_account_namespaces="my-application-ns" policies=keycloak/reader
bao write auth/kubernetes/role/keycloak-admin bound_service_account_names="keycloak" bound_service_account_namespaces="my-application-ns" policies=keycloak/admin
```

Configure the extensions with the following parameters to use these roles:
- `--spi-vault-secrets-provider-role=keycloak-reader` for the Vault provider extension
- `--spi-admin-realm-restapi-extension-secrets-manager-role=keycloak-admin` for the Secrets Manager REST API extension

The extensions use the specified role during authentication to obtain a token.

* [OpenBao Policies Documentation](https://openbao.org/docs/concepts/policies/)
* [HashiCorp Vault Policies Documentation](https://developer.hashicorp.com/vault/docs/concepts/policies)
* [OpenBao Kubernetes Auth Configuration](https://openbao.org/docs/auth/kubernetes/#configuration)
* [HashiCorp Vault Kubernetes Auth Configuration](https://developer.hashicorp.com/vault/docs/auth/kubernetes#configuration)

#### KV Secrets Engine

Enable the KV secrets engine at your desired path:

```
bao secrets enable kv --path secret/
```
