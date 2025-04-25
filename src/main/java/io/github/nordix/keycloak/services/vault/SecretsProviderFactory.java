/**
 * Copyright (c) 2025 OpenInfra Foundation Europe and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License, Version 2.0
 * which accompanies this distribution, and is available at
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.nordix.keycloak.services.vault;

import java.net.URI;

import org.keycloak.vault.VaultProvider;
import org.keycloak.vault.VaultProviderFactory;
import org.keycloak.models.KeycloakSession;


public class SecretsProviderFactory implements VaultProviderFactory {

    private static final String PROVIDER_ID = "secrets-provider";

    private SecretsProviderConfig config;

    @Override
    public void init(org.keycloak.Config.Scope configScope) {
        this.config = new SecretsProviderConfig(
            configScope.get("auth-method", "kubernetes"),
            configScope.get("service-account-file", "/var/run/secrets/kubernetes.io/serviceaccount/token"),
            configScope.get("url") != null ? URI.create(configScope.get("url")) : null,
            configScope.get("kv-secret-path"),
            Integer.parseInt(configScope.get("kv-version", "2")),
            configScope.get("ca-certificate-file"),
            configScope.get("role"));
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
