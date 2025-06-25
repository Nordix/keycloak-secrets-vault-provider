# Secrets Manager Admin REST API

The Secrets Manager is an optional extension to Keycloak's admin REST API.
It enables users to manage secrets stored in OpenBao or HashiCorp Vault through the Keycloak admin REST API interface.
All necessary details such as the secrets manager's access URL, authentication method, permissions, and secrets engine configuration are provided via Keycloak's global configuration parameters.
This means that applications configuring Keycloak with Vault SPI references do not need to interact directly with the external secrets management system to store their secrets.
Instead, they can use the Secrets Manager API to manage secrets in a Keycloak realm.


## Overview

The Secrets Manager Admin API supports the following operations:

- List all secret IDs for a realm
- Create a new secret (with an auto-generated or provided value)
- Retrieve a secret by ID
- Update a secret (with an auto-generated or provided value)
- Delete a secret

Secrets are stored externally in OpenBao or HashiCorp Vault and referenced in Keycloak configuration using the `${vault.<id>}` syntax.
See the [Overview](overview.md) for high-level information, including the list of supported use cases and naming conventions for secrets.

## API Documentation

For detailed API documentation, including request and response schemas, see

- [API documentation](https://petstore.swagger.io/?url=https://raw.githubusercontent.com/Nordix/keycloak-secrets-vault-provider/refs/heads/main/docs/openapi.json), rendered by Swagger Petstore.


## Naming Convention for Secrets

Secrets are scoped to a Keycloak realm.
A secret named `my-client` created in `realm1` is distinct from `my-client` in `realm2`.

Within a realm, it is recommended to use a naming convention with a use-case-specific prefix.

| Use case                              | Secret name         | Keycloak reference              |
|----------------------------------------|---------------------|---------------------------------|
| Client secret for OAuth2 clients       | `client.<name>`     | `${vault.client.<name>}`        |
| Client secret for Identity Brokering   | `idp.<name>`        | `${vault.idp.<name>}`           |
| Bind password for LDAP federation      | `ldap.<name>`       | `${vault.ldap.<name>}`          |
