# Secrets Manager Admin REST API

The Secrets Manager is an optional extension to Keycloak that allows managing secrets via Keycloak's Admin REST API.
When enabled, the extension provides a "Secrets Manager" API endpoint that allows realm administrators to list, create, read, update, and delete secrets stored in OpenBao or HashiCorp Vault.
Realm administrators can manage secrets without requiring direct access to OpenBao or HashiCorp Vault.
This can help simplify architecture and improve security, especially in environments with multiple administrators or applications managing Keycloak realms and their associated secrets.

The Secrets Manager Admin API supports the following operations:

- List all secret IDs for a realm
- Create a new secret (with a random auto-generated or provided value)
- Retrieve a secret by ID
- Update a secret (with a random auto-generated or provided value)
- Delete a secret

Secrets are stored externally in OpenBao or HashiCorp Vault and referenced in Keycloak configuration using the `${vault.<id>}` syntax.
The Vault Secrets Provider implemented in this project retrieves the actual secret value when needed.
See the [Overview](overview.md) for more information, including the list of Vault SPI use cases supported by Keycloak.

## API Documentation

For detailed API documentation, including request and response schemas, see

- [API documentation](https://petstore.swagger.io/?url=https://raw.githubusercontent.com/Nordix/keycloak-secrets-vault-provider/refs/heads/main/docs/openapi.json), rendered by Swagger "Petstore".

## Naming Convention for Secrets

When creating secrets with the Secrets Manager, it is recommended to follow a naming convention that reflects the use case of the secret.
This helps maintain clarity in environments with multiple secrets per realm.

| Use case                                      | Secret name     | Keycloak reference       |
| --------------------------------------------- | --------------- | ------------------------ |
| Client secret for OAuth2 clients <sup>1</sup> | `client.<name>` | `${vault.client.<name>}` |
| Client secret for Identity Brokering          | `idp.<name>`    | `${vault.idp.<name>}`    |
| Bind password for LDAP federation             | `ldap.<name>`   | `${vault.ldap.<name>}`   |
| SMTP server password for email confirmations  | `smtp.<name>`   | `${vault.smtp.<name>}`   |

Vault secrets are automatically restricted to the specific Keycloak realm in which they are created.
A secret named `my-client` created in `realm1` is not the same as `my-client` in `realm2`.

<sup>1</sup> Client secrets for OAuth2 clients are not currently supported but a pull request has been submitted to upstream Keycloak: [keycloak#39650](https://github.com/keycloak/keycloak/pull/39650).
