/**
 * Copyright (c) 2025 OpenInfra Foundation Europe and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License, Version 2.0
 * which accompanies this distribution, and is available at
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.nordix.keycloak.services.vault;

import org.jboss.logging.Logger;
import org.keycloak.Config.Scope;
import org.keycloak.models.KeycloakSession;
import org.keycloak.vault.VaultProvider;
import org.keycloak.vault.VaultProviderFactory;

import io.github.nordix.keycloak.common.ProviderConfig;

public class SecretsProviderFactory implements VaultProviderFactory {

    private static final String PROVIDER_ID = "secrets-provider";
    private static final String CMD_LINE_OPTION_PREFIX = "--spi-vault-secrets-provider-";
    private static Logger logger = Logger.getLogger(SecretsProviderFactory.class);

    private ProviderConfig config;


    @Override
    public void init(Scope scopedConfig) {
        config = new ProviderConfig(scopedConfig, CMD_LINE_OPTION_PREFIX);
        logger.debugv("Initializing secrets-provider (Vault SPI) with {0}", this.config);
        if (!config.getAuthMethod().equals("kubernetes")) {
            throw new IllegalArgumentException("Only 'kubernetes' auth method is supported by the secrets-provider.");
        }
        if (config.getKvVersion() != 1) {
            throw new IllegalArgumentException("Only KV version 1 is supported by the secrets-provider.");
        }
    }


    @Override
    public VaultProvider create(KeycloakSession session) {
        return new SecretsProvider(session.getContext().getRealm().getName(), config);
    }

    @Override
    public void postInit(org.keycloak.models.KeycloakSessionFactory factory) {
        // Intentionally left empty.
    }

    @Override
    public void close() {
        // Intentionally left empty.
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }


}
