# Keycloak Vault Provider for OpenBao and HashiCorp Vault

## Keycloak Vault SPI Overview

By default, Keycloak stores sensitive configuration values, such as passwords or client secrets, in its SQL database in cleartext (Figure 1).

![image](assets/secrets-in-database.drawio.svg)

*Figure 1: Keycloak stores sensitive configuration values in its SQL database in cleartext.*
<br><br>

Keycloak's Vault SPI (Service Provider Interface) is a plugin API that allows Keycloak to reference secrets in its configuration using a special syntax: `${vault.<reference>}`.
When Keycloak needs to use a secret, it calls the plugin, which fetches the actual value from an external source at runtime.
The data stored in the SQL database is only a reference to the secret, not the secret itself.
Plugins can be implemented to fetch secrets from various external systems, such as OpenBao or HashiCorp Vault (Figure 2).
See Keycloak's [Vault SPI documentation](https://www.keycloak.org/server/vault) for more details.

![image](assets/secrets-via-vault-spi.drawio.svg)

*Figure 2: Keycloak fetches sensitive configuration values from an external secrets manager via the Vault SPI.*
<br><br>

The Vault SPI is read-only, meaning that Keycloak can only read secrets from it, not write them.
The administrator must write the secret values using external mechanisms, which are not part of Keycloak's functionality.

**Example workflow:**

1. The administrator stores the LDAP bind password in the OpenBao KV secrets engine at the path `secret/keycloak/<realm>/ldap-bindpw`.
   The password is written to disk in encrypted form by OpenBao.
2. The administrator configures LDAP federation in Keycloak and sets the LDAP bind password to `${vault.ldap-bindpw}`.
   The reference is stored as part of Keycloak's configuration in the SQL database, but the actual password is not stored there.
3. A user logs in to Keycloak.
4. Keycloak resolves the `${vault.ldap-bindpw}` reference and fetches the actual password from OpenBao in cleartext.
5. Keycloak uses the fetched cleartext password to bind to the LDAP server for user federation.

The Vault SPI is suitable for secrets used in configuration data, but not for dynamically generated secrets such as realm keys.
Current use cases in Keycloak include:

- **User federation:** LDAP bind password
- **Identity brokering:** OAuth2 identity provider client secret
- **Social login:** Twitter identity provider client secret
- **Email confirmations:** SMTP server password

Client secrets for OAuth2 clients are not currently supported by the Vault SPI.
A pull request has been submitted to upstream Keycloak to add support for OAuth2 clients: [keycloak#39650](https://github.com/keycloak/keycloak/pull/39650).


## Secrets Manager REST API Extension

This project extends Keycloak with a custom REST API extension that allows managing secrets via Keycloak's Admin REST API.
When activated, the extension provides a "Secrets Manager" endpoint that allows administrators to list, create, read, update, and delete secrets stored in OpenBao or HashiCorp Vault.

This allows users to interact with secrets without needing direct access to OpenBao or Vault, simplifying the management of secrets in Keycloak (Figure 3).
See [API documentation](docs/api.md) for details on the REST API endpoints and usage.

The endpoint is available at `https://<KEYCLOAK_URL>/auth/realms/<REALM_NAME>/secrets-manager/`.
It requires realm admin permissions to access.

![image](assets/secrets-manager.drawio.svg)

*Figure 3: Managing secrets using a custom "Secrets Manager" extension for the Keycloak Admin REST API.*
