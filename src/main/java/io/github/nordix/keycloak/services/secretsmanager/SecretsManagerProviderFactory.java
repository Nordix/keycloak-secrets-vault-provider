/**
 * Copyright (c) 2025 OpenInfra Foundation Europe and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License, Version 2.0
 * which accompanies this distribution, and is available at
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.nordix.keycloak.services.secretsmanager;

import org.jboss.logging.Logger;
import org.keycloak.Config.Scope;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.services.resources.admin.ext.AdminRealmResourceProvider;
import org.keycloak.services.resources.admin.ext.AdminRealmResourceProviderFactory;

import io.github.nordix.keycloak.common.ProviderConfig;

public class SecretsManagerProviderFactory implements AdminRealmResourceProviderFactory {

    private static Logger logger = Logger.getLogger(SecretsManagerProviderFactory.class);
    private static final String PROVIDER_ID = "secrets-manager";
    private static final String CMD_LINE_OPTION_PREFIX = "--spi-admin-realm-restapi-extension--secrets-manager--";

    private ProviderConfig config;

    @Override
    public void init(Scope scopedConfig) {
        config = new ProviderConfig(scopedConfig, CMD_LINE_OPTION_PREFIX);
        logger.debugv("Initializing secrets-manager with {0}", config);
    }

    @Override
    public AdminRealmResourceProvider create(KeycloakSession session) {
        logger.debug("Creating SecretManagerProvider");
        return new SecretsManagerProvider(config);
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
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
