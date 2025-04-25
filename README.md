# Keycloak Vault Provider for OpenBao and HashiCorp Vaul

## Installation

## Configuration

| Parameter              | Description                                                 | Default Value                                         |
| ---------------------- | ----------------------------------------------------------- | ----------------------------------------------------- |
| `auth-method`          | Authentication method to use (only `kubernetes` supported). | `kubernetes`                                          |
| `service-account-file` | Path to the Kubernetes service account token file.          | `/var/run/secrets/kubernetes.io/serviceaccount/token` |
| `url`                  | URL of the OpenBao/Vault server (required).                 | None                                                  |
| `kv-secret-path`       | Path to the KV secret (required),                           | None                                                  |
| `kv-version`           | Version of the KV secrets engine (`1` or `2`).              | `2`                                                   |
| `ca-certificate-file`  | CA certificate file path for server validation.             | None                                                  |
| `role`                 | Role to use for authentication.                             | None                                                  |

## Development
