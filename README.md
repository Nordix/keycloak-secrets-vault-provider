# Keycloak Vault Provider for OpenBao and HashiCorp Vault

> **⚠️ Note:**
> This project is under development and not yet production-ready.

This project extends Keycloak by implementing a [Vault SPI](https://www.keycloak.org/server/vault) provider, integrating with [OpenBao](https://openbao.org/) and [HashiCorp Vault](https://developer.hashicorp.com/vault).

With this provider, Keycloak accesses secrets stored in OpenBao or Vault rather than in its SQL database.
This enables encryption of sensitive data at rest by utilizing the [KV secrets engine](https://openbao.org/docs/secrets/kv/) for external secret management.

Key features:

* Vault SPI provider for Keycloak to delegate secret storage and retrieval to OpenBao or Vault.
* Admin REST API extension for managing secrets via Keycloak's REST API, removing the need for clients to access OpenBao or Vault directly.

## Documentation

The following documentation provides more information about the project and its usage:

- For a high-level overview of the project, see [Overview](docs/overview.md).
- For installation and configuration instructions, refer to [Deployment](docs/deployment.md).
- For secrets management REST API, see the [API documentation](docs/api.md).

## Contributing

If you are interested in contributing, please read the [Development](docs/development.md) guide.

## License

This project is licensed under the Apache License 2.0. See the [LICENSE](LICENSE) file for details.
