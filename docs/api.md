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


## Example Usage

### Configure LDAP Federation with vault secret

1. Create a secret with the LDAP bind password

    ```bash
    curl -X POST \
      https://${KEYCLOAK_ADDR}/admin/realms/my-realm/secrets-manager/ldap.my-ldap-federation \
      -H "Authorization: Bearer ${KEYCLOAK_TOKEN}" \
      -H "Content-Type: application/json" \
      -d '
      {
        "secret": "my-ldap-bind-password"
      }'
    ```

2. Create the LDAP federation configuration using the secret reference

    ```bash
    curl -X POST \
      https://${KEYCLOAK_ADDR}/admin/realms/my-realm/components \
      -H "Authorization: Bearer ${KEYCLOAK_TOKEN}" \
      -H "Content-Type: application/json" \
      -d '
      {
        "config": {
          "authType": [
              "simple"
          ],
          "bindCredential": [
              "${vault.ldap.my-ldap-federation}"
          ],
          "bindDn": [
              "cn=admin,dc=example,dc=com"
          ],
          "connectionUrl": [
              "ldap://localhost"
          ],
          "editMode": [
              "WRITABLE"
          ],
          "enabled": [
              "true"
          ],
          "usernameLDAPAttribute": [
              "uid"
          ],
          "userObjectClasses": [
              "inetOrgPerson, organizationalPerson"
          ],
          "usersDn": [
              "ou=users,dc=example,dc=com"
          ],
          "vendor": [
              "other"
          ]
        },
        "id": "my-ldap-federation",
        "name": "My LDAP Federation",
        "providerId": "ldap",
        "providerType": "org.keycloak.storage.UserStorageProvider"
      }'
    ```

Steps (1) and (2) can be performed in any order.
For example, the realm administrator may first configure LDAP federation with the `${vault.ldap.my-ldap-federation}` reference, and then store the password using the Secrets Manager.
However, Keycloak will not be able to use the `${vault.ldap.my-ldap-federation}` reference until the secret has been stored using the Secrets Manager, therefore the configuration will not work until the secret is created.

### Modify bind password for LDAP Federation with vault secret

The LDAP federation bind password can be modified by updating the secret value in the Secrets Manager.

```bash
curl -X PUT \
  https://${KEYCLOAK_ADDR}/admin/realms/my-realm/secrets-manager/ldap.my-ldap-federation \
  -H "Authorization: Bearer ${KEYCLOAK_TOKEN}" \
  -H "Content-Type: application/json" \
  -d '
  {
    "secret": "new-ldap-bind-password"
  }'
```

There is no need to modify the LDAP federation configuration itself, as the reference `${vault.ldap.my-ldap-federation}` remains the same.


### Delete LDAP Federation with vault secret

1. Delete the federation configuration:

    ```bash
    curl -X DELETE \
    https://${KEYCLOAK_ADDR}/admin/realms/my-realm/components/my-ldap-federation \
    -H "Authorization: Bearer ${KEYCLOAK_TOKEN}"
    ```

2. Delete the secret from the Secrets Manager:

    ```bash
    curl -X DELETE \
    https://${KEYCLOAK_ADDR}/admin/realms/my-realm/secrets-manager/ldap.my-ldap-federation \
    -H "Authorization: Bearer ${KEYCLOAK_TOKEN}"
    ```

Similar to the previous example, steps (1) and (2) can be performed in any order.
The LDAP federation configuration will stop working until the secret is deleted from the Secrets Manager.
