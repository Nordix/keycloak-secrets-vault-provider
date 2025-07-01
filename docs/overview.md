# Keycloak Vault Provider for OpenBao and HashiCorp Vault

## Keycloak Vault SPI Overview

### The Problem: Secrets in Cleartext

By default, Keycloak stores sensitive configuration values, such as passwords or client secrets, in its SQL database in cleartext.

Example workflow (**Figure 1**):

1. The realm administrator configures e.g. LDAP federation in Keycloak and sends the LDAP bind password as part of the configuration.
2. The password is stored in the SQL database in cleartext.

![image](assets/secrets-in-database.drawio.svg)

**Figure 1:** Keycloak stores sensitive configuration values in its SQL database in cleartext.

### The Solution: Using Vault SPI

To avoid storing sensitive data in cleartext, Keycloak provides a Vault SPI (Service Provider Interface).
It is a plugin API that allows realm administrators to reference secrets using a special syntax: `${vault.<id>}`.
The data stored in the SQL database is only a reference to the secret, not the secret itself.
When Keycloak needs to use the secret, it calls the plugin, which fetches the actual value from an external source.
Plugins can be developed to retrieve secrets from different sources, including OpenBao and the HashiCorp Vault KV secrets engine which are supported by this project.
For use cases where secret references can be used, see [Limitations of the Vault SPI](#limitations-of-the-vault-spi).

The Vault SPI is read-only, meaning that Keycloak can only read secrets from it, not write them.
The realm administrator is responsible for creating and storing secret values using external tools or systems outside of Keycloak.

After enabling the Vault SPI, it is still possible to store sensitive configuration values in cleartext in the configuration.
The use of `${vault.<id>}` references is optional and the realm administrator can choose to continue storing secrets in cleartext if desired.

Example workflow (**Figure 2**):

1. The realm administrator stores the LDAP bind password in the OpenBao KV secrets engine at the path `secret/keycloak/my-realm/ldap-bindpw`.
   The password is written to disk in encrypted form by OpenBao.
2. The realm administrator configures LDAP federation in Keycloak and sets the LDAP bind password to `${vault.ldap-bindpw}`.
   The reference is stored as part of Keycloak's configuration in the SQL database.
3. A user logs in.
4. Keycloak resolves the `${vault.ldap-bindpw}` reference by calling the Vault SPI provider which fetches the actual password from OpenBao KV secrets engine path `secret/keycloak/my-realm/ldap-bindpw`.
5. Keycloak uses the cleartext password to bind to the LDAP server for user federation.

![image](assets/secrets-via-vault-spi.drawio.svg)

**Figure 2:** Keycloak fetches sensitive configuration values from OpenBao.
<br><br>

See Keycloak's [Vault SPI documentation](https://www.keycloak.org/server/vault) for more details.

#### Limitations of the Vault SPI

The Vault SPI does have some notable limitations:

The Vault SPI is suitable for secrets used in configuration data, but not for dynamically generated secrets such as realm keys.
Currently supported use cases for using `${vault.<id>}` references are:

- User federation: LDAP bind password
- Identity brokering: OAuth2 identity provider client secret
- Social login: Twitter identity provider client secret
- Email confirmations: SMTP server password
- Client secrets for OAuth2 clients are not currently supported but a pull request has been submitted to upstream Keycloak: [keycloak#39650](https://github.com/keycloak/keycloak/pull/39650).

Another limitation is visible in the example workflow (**Figure 2**): the secure storage of sensitive configuration data is not transparent for the user.
The realm administrator must separately store the secret value and configure Keycloak with the reference to that value.
For an optimal user experience, the realm administrator should not need to be aware of how secrets are stored.
Keycloak would automatically manage the secure storage of sensitive configuration data behind the scenes.

Finally, when secrets are stored externally, Keycloak configuration is distributed across two databases, which can complicate backups and upgrades/rollbacks.

## Secrets Manager REST API Extension

This project extends Keycloak with a custom REST API extension that allows managing secrets via Keycloak's Admin REST API.
When enabled, the extension provides a Secrets Manager API endpoint that allows realm administrators to list, create, read, update, and delete secrets stored in OpenBao or HashiCorp Vault.
Realm administrators can manage secrets without requiring direct access to OpenBao or HashiCorp Vault.
This can help simplify architecture and improve security, especially in environments with multiple administrators or applications managing Keycloak realms and their associated secrets.

See [API documentation](docs/api.md) for detailed usage.

Example workflow (**Figure 3**):

1. The realm administrator stores the LDAP bind password by sending a PUT request to `https://<KEYCLOAK_URL>/admin/realms/my-realm/secrets-manager/ldap-bindpw`.
   The Secrets Manager writes the password to OpenBao KV secrets engine at the path `secret/keycloak/my-realm/ldap-bindpw`, which encrypts it and stores it on disk.
2. The realm administrator configures LDAP federation in Keycloak and sets the LDAP bind password to `${vault.ldap-bindpw}`.
   The reference is stored as part of Keycloak's configuration in the SQL database.
3. A user logs in.
4. Keycloak resolves the `${vault.ldap-bindpw}` reference by calling the Vault SPI provider which fetches the actual password from OpenBao KV secrets engine path `secret/keycloak/my-realm/ldap-bindpw`.
5. Keycloak uses the cleartext password to bind to the LDAP server for user federation.

Steps (1) and (2) can be performed in any order.
The realm administrator may first configure LDAP federation with the `${vault.ldap-bindpw}` reference, and then store the password using the Secrets Manager.
However, Keycloak will not be able to use the `${vault.ldap-bindpw}` reference until the secret has been stored using the Secrets Manager.
Therefore the configuration will not work until the secret is created.

![image](assets/secrets-manager.drawio.svg)

**Figure 3:** Using a Secrets Manager extension for the Keycloak Admin REST API.
<br><br>

Secrets are automatically restricted to the specific Keycloak realm in which they are created.
A secret named `ldap-bindpw` created in `my-realm` is not the same as `ldap-bindpw` in `your-realm`.

Secrets Manager is accessible only via the REST API and a user interface within Keycloak Admin Console is not available.
