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

## Contributing

Please refer to the [Development Guide](docs/development.md) for instructions.
