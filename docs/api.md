# Secrets Manager Admin REST API

The Secrets Manager Admin REST API is provided by this project as an extension to Keycloak's Admin REST API.
It allows users to manage secrets stored in OpenBao or HashiCorp Vault without requiring direct access to those systems.
Details such as access URL, authentication method, and secrets engine configuration are provided via Keycloak's global configuration parameters. Applications that configure Keycloak do not need to know the details of the external secrets management system when managing secrets via the Admin REST API.

Secrets managed by this API are intended for use with configuration data that supports Keycloak's Vault SPI.
See the [Overview](docs/overview.md) for more details.

## Overview

The Secrets Manager Admin API enables administrators to:

- List all secret IDs for a realm
- Create a new secret (with an auto-generated or provided value)
- Retrieve a secret by ID
- Update a secret (with an auto-generated or provided value)
- Delete a secret

Secrets are stored externally in OpenBao or HashiCorp Vault and referenced in Keycloak using the `${vault.<id>}` syntax.

## API Documentation

For detailed request and response schemas, see

- [API documentation](https://petstore.swagger.io/?url=https://raw.githubusercontent.com/Nordix/keycloak-secrets-vault-provider/refs/heads/main/docs/openapi.json) rendered by Swagger Petstore.

To download the API specification files, use the following links:
- [openapi.json](openapi.json)
- [openapi.yaml](openapi.yaml)


## Naming Convention for Secrets

Secrets are scoped to a Keycloak realm.
A secret named `my-client` created in `realm1` is distinct from `my-client` in `realm2`.

Within a realm, it is recommended to use a naming convention with a use-case-specific prefix.

| Use case                              | Secret name         | Keycloak reference              |
|----------------------------------------|---------------------|---------------------------------|
| Client secret for OAuth2 clients       | `client.<name>`     | `${vault.client.<name>}`        |
| Client secret for Identity Brokering   | `idp.<name>`        | `${vault.idp.<name>}`           |
| Bind password for LDAP federation      | `ldap.<name>`       | `${vault.ldap.<name>}`          |
