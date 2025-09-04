# Keycloak Vault Provider for OpenBao and HashiCorp Vault

> **⚠️ Note:**
> This project is under development and not yet production-ready.

## Overview

This project provides two extensions for Keycloak that integrate with [OpenBao](https://openbao.org/) and [HashiCorp Vault](https://developer.hashicorp.com/vault):

- Vault secrets provider
- Secrets Manager REST API extension

By implementing a [Vault SPI](https://www.keycloak.org/server/vault) provider, this extension allows Keycloak to retrieve secrets from OpenBao or HashiCorp Vault.
It uses the [KV secrets engine](https://openbao.org/docs/secrets/kv/) as the secure storage for sensitive configuration data.
This avoids storing sensitive data in the SQL database in cleartext.

The project also implements a custom REST API extension called Secrets Manager for Keycloak's Admin REST API.
This enables realm administrators to manage Vault SPI secret values through Keycloak, without needing direct access to OpenBao or HashiCorp Vault.

## Documentation

For more information, see:

- [Project Overview](docs/overview.md)
- [Deployment Guide](docs/deployment.md)
- [Secrets Manager REST API Documentation](docs/api.md)

## Known Issues

### Extension is using Keycloak internal SPIs

When this extension is loaded, Keycloak displays the following warnings:

```
WARN  [org.keycloak.services] (build-47) KC-SERVICES0047: secrets-manager (io.github.nordix.keycloak.services.secretsmanager.SecretsManagerProviderFactory) is implementing the internal SPI admin-realm-restapi-extension. This SPI is internal and may change without notice
WARN  [org.keycloak.services] (build-47) KC-SERVICES0047: secrets-provider (io.github.nordix.keycloak.services.vault.SecretsProviderFactory) is implementing the internal SPI vault. This SPI is internal and may change without notice
```

Both the `vault` and `admin-realm-restapi-extension` SPIs are internal to Keycloak and may change without warning between releases.
Currently, no stable public alternatives exist for this functionality.
Users should be aware that future Keycloak updates could introduce breaking changes.
Some additional internal Keycloak APIs, such as those used for caching, were also required to achieve full functionality.

## Contributing

Please refer to the [Development Guide](docs/development.md) for instructions.
