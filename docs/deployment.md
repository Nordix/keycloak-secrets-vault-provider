# Deployment

## Building and Deploying the Extension

This project is not available in Maven Central or any other public repository.
It must be built and deployed manually.

To deploy the Vault SPI Secrets Provider and Secrets Manager REST API extension, follow these steps:

1. Compile the extensions

   Ensure you have JDK and Git installed. Clone the repository and build the project:

   ```bash
   ./mvnw clean package -DskipTests=true
   ```

   This will produce a JAR file in the `target/` directory.

2. Copy the JAR to Keycloak

   Copy the compiled JAR file to the `providers/` directory of your Keycloak installation.
   For example, if using the official [Keycloak container image](https://www.keycloak.org/server/containers), the extension should be placed in the `/opt/keycloak/providers/` directory.

## Configuration

### ⚠️ Keycloak SPI option names now follow a new format

When reading the following chapters, keep in mind the new naming scheme for SPI options.
The new double-dash `--` syntax is now used to separate components of the SPI option names.
The following table gives an example of the change:

| Parameter naming prior Keycloak 26.3.0 | Keycloak 26.3.0 and later                |
| -------------------------------------- | ---------------------------------------- |
| `--spi-vault-provider`                 | `--spi-vault--provider`                  |
| `--spi-vault-secrets-provider-address` | `--spi-vault--secrets-provider--address` |

For further details, refer to Keycloak's [Configuring providers](https://www.keycloak.org/server/configuration-provider) documentation and the chapter "Deprecated features" in Keycloak's [Migrating to 26.3.0](https://www.keycloak.org/docs/latest/upgrading/index.html#migrating-to-26-3-0).

### Enable Vault Provider (Mandatory)

Add the following command line parameter to `kc.sh` to choose the provider:

```
--spi-vault--provider=secrets-provider
```

The provider works with both OpenBao and HashiCorp Vault, since both implement the same REST API.

### Parameters

#### Vault Secrets Provider

| Parameter                                             | Description                                                               | Default Value                                         |
| ----------------------------------------------------- | ------------------------------------------------------------------------- | ----------------------------------------------------- |
| `--spi-vault--secrets-provider--enabled`              | Enable or disable the secrets provider extension.                         | `true`                                                |
| `--spi-vault--secrets-provider--address`              | Address (URL) of the OpenBao or HashiCorp Vault server. Must be provided. | N/A                                                   |
| `--spi-vault--secrets-provider--auth-method`          | Authentication method to use (only `kubernetes` is supported).            | `kubernetes`                                          |
| `--spi-vault--secrets-provider--service-account-file` | Path to the Kubernetes service account token file for authentication.     | `/var/run/secrets/kubernetes.io/serviceaccount/token` |
| `--spi-vault--secrets-provider--kv-mount`             | KV secrets engine mount point.                                            | `secret`                                              |
| `--spi-vault--secrets-provider--kv-path-prefix`       | Path prefix for secrets. Supports `%realm%` variable.                     | `keycloak/%realm%`                                    |
| `--spi-vault--secrets-provider--kv-version`           | KV secrets engine version (only `1` is supported).                        | `1`                                                   |
| `--spi-vault--secrets-provider--ca-certificate-file`  | Path to CA certificate file for HTTPS connections. Optional.              | N/A                                                   |
| `--spi-vault--secrets-provider--role`                 | Role to use for authentication.                                           | N/A                                                   |
| `--spi-vault--secrets-provider--cache-name`           | Name of the Infinispan cache to use for storing secrets.                  | Caching is disabled                                   |

The `%realm%` variable will be replaced with the actual realm name at runtime.

#### Secrets Manager

| Parameter                                                                    | Description                                                                                       | Default Value                                         |
| ---------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------- | ----------------------------------------------------- |
| `--spi-admin-realm-restapi-extension--secrets-manager--enabled`              | Enable or disable the secrets manager extension.                                                  | `true`                                                |
| `--spi-admin-realm-restapi-extension--secrets-manager--address`              | Address (URL) of the OpenBao or HashiCorp Vault server for the secrets manager. Must be provided. | N/A                                                   |
| `--spi-admin-realm-restapi-extension--secrets-manager--auth-method`          | Authentication method to use (only `kubernetes` is supported).                                    | `kubernetes`                                          |
| `--spi-admin-realm-restapi-extension--secrets-manager--service-account-file` | Path to the Kubernetes service account token file for secrets manager authentication.             | `/var/run/secrets/kubernetes.io/serviceaccount/token` |
| `--spi-admin-realm-restapi-extension--secrets-manager--kv-mount`             | KV secrets engine mount point.                                                                    | `secret`                                              |
| `--spi-admin-realm-restapi-extension--secrets-manager--kv-path-prefix`       | Path prefix for secrets. Supports `%realm%` variable.                                             | `keycloak/%realm%`                                    |
| `--spi-admin-realm-restapi-extension--secrets-manager--kv-version`           | KV secrets engine version (only `1` is supported).                                                | `1`                                                   |
| `--spi-admin-realm-restapi-extension--secrets-manager--ca-certificate-file`  | Path to CA certificate file for HTTPS connections. Optional.                                      | N/A                                                   |
| `--spi-admin-realm-restapi-extension--secrets-manager--role`                 | Role to use for authentication.                                                                   | N/A                                                   |
| `--spi-admin-realm-restapi-extension--secrets-manager--cache-name`           | Name of the Infinispan cache to use for storing secrets.                                          | Caching is disabled                                   |

The `%realm%` variable will be replaced with the actual realm name at runtime.

### Enabling and Configuring Secret Caching (Optional)

If Vault secrets are read frequently, contacting OpenBao or HashiCorp Vault for every access can add significant latency and load.
Enabling caching reduces requests and improves performance by keeping secrets in Keycloak's Infinispan cache.

To enable caching:

1. Define a cache in custom Infinispan configuration file.
2. Point Keycloak to that file using the `--cache-config-file` parameter (or the corresponding environment variable/property).
3. Set both `--spi-vault--secrets-provider--cache-name` and `--spi-admin-realm-restapi-extension--secrets-manager--cache-name` to the name of the Infinispan cache. Both parameters must use the same cache name for caching to work across the Vault provider and the Secrets Manager.

See Keycloak's [Configuring distributed caches](https://www.keycloak.org/server/caching) for more details on configuring caches.

Example Infinispan cache configuration:

```xml
<replicated-cache name="vaultExtensionSecrets">
    <expiration lifespan="-1"/>
    <memory max-count="1000"/>
    <encoding>
        <key media-type="text/plain; charset=UTF-8"/>
        <value media-type="text/plain; charset=UTF-8"/>
    </encoding>
</replicated-cache>
```

This will have the following cache behavior:

- A replicated cache distributes entries across the Keycloak cluster while each node keeps its own copy.
- The eviction policy is set for maximum of 1000 cached secrets. When the cache reaches this limit, the least recently used entries will be removed from the cache.
- Cached secrets remain in memory as long as at least one Keycloak instance is alive.
- The cache key and value are both stored as UTF-8 encoded strings. The cache key used by the extension is the path to KV secrets engine and the cache value is the secret itself.

When secrets are updated or deleted through the Secrets Manager API, the replicated cache ensures that entries are invalidated across the entire Keycloak cluster, so subsequent reads retrieve the latest values.
If secrets are changed directly in OpenBao or HashiCorp Vault (not via the Secrets Manager API), cached values become stale.
To work around this, expiration policy can be configured for the cache.
With expiration enabled, stale secrets will only remain in the cache until their configured lifespan elapses, after which they will be refreshed on the next access.

Example with a 60 second expiration:

```xml
<replicated-cache name="vaultExtensionSecrets">
    <expiration lifespan="60000"/>
    <memory max-count="1000"/>
    <encoding>
        <key media-type="text/plain; charset=UTF-8"/>
        <value media-type="text/plain; charset=UTF-8"/>
    </encoding>
</replicated-cache>
```

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

- [OpenBao Kubernetes Auth Documentation](https://openbao.org/docs/auth/kubernetes/)
- [HashiCorp Vault Kubernetes Auth Documentation](https://developer.hashicorp.com/vault/docs/auth/kubernetes)

#### Access Control

The following permissions are required for the extension to function:

- The Keycloak Vault SPI provider extension requires `read` permission on the KV secrets engine path where secrets are stored.
- The Secrets Manager REST API extension requires `create`, `read`, `update`, `delete`, and `list` permissions on the same path.

To grant these permissions, create the following policies:

```bash
bao policy write keycloak/reader - <<EOF
path "secret/keycloak/*" {
    capabilities = ["read"]
}
EOF

bao policy write keycloak/admin - <<EOF
path "secret/keycloak/*" {
    capabilities = ["create", "read", "update", "delete", "list"]
}
EOF
```

The `keycloak/reader` policy grants read-only access to all secrets under `secret/*`, while the `keycloak/admin` policy grants create, read, update, delete and list permissions.
Alternatively, you can combine the two policies into a single policy that grants both read and write permissions and use it for both extensions.

Assuming your Keycloak pod is running in the `my-application-ns` namespace, and is configured with `spec.template.spec.serviceAccountName: keycloak`, and the `keycloak` service account exists in that namespace, you can associate the policies with the service account by creating the following roles:

```bash
bao write auth/kubernetes/role/keycloak-reader bound_service_account_names="keycloak" bound_service_account_namespaces="my-application-ns" policies=keycloak/reader
bao write auth/kubernetes/role/keycloak-admin bound_service_account_names="keycloak" bound_service_account_namespaces="my-application-ns" policies=keycloak/admin
```

Configure the extensions with the following parameters to use these roles:

- `--spi-vault-secrets-provider-role=keycloak-reader` for the Keycloak Vault SPI provider extension
- `--spi-admin-realm-restapi-extension-secrets-manager-role=keycloak-admin` for the Secrets Manager REST API extension

The extensions use the specified role during authentication to obtain a token.

- [OpenBao Policies Documentation](https://openbao.org/docs/concepts/policies/)
- [HashiCorp Vault Policies Documentation](https://developer.hashicorp.com/vault/docs/concepts/policies)
- [OpenBao Kubernetes Auth Configuration](https://openbao.org/docs/auth/kubernetes/#configuration)
- [HashiCorp Vault Kubernetes Auth Configuration](https://developer.hashicorp.com/vault/docs/auth/kubernetes#configuration)

#### KV Secrets Engine

Enable the KV secrets engine at your desired path:

```
bao secrets enable --path=secret/ kv
```

This project currently supports only KV version 1.

- [OpenBao KV Secrets Engine Documentation](https://openbao.org/docs/secrets/kv/)
- [HashiCorp Vault KV Secrets Engine Documentation](https://developer.hashicorp.com/vault/docs/secrets/kv)
