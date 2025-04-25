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
import org.keycloak.vault.DefaultVaultRawSecret;
import org.keycloak.vault.VaultProvider;
import org.keycloak.vault.VaultRawSecret;

import java.nio.ByteBuffer;
import java.util.Optional;

public class SecretsProvider implements VaultProvider {

    private static Logger logger = Logger.getLogger(SecretsProvider.class);

    private final String realm;
    private final SecretsProviderConfig config;

    public SecretsProvider(String realm, SecretsProviderConfig config) {
        logger.debugv("Initializing SecretsProvider for realm {0} with parameters: {1}", realm, config);
        this.realm = realm;
        this.config = config;
    }

    @Override
    public VaultRawSecret obtainSecret(String vaultSecretId) {
        try {
            String secretPath = String.format("%s/%s", config.kvSecretPath(), realm);

            OpenBaoClient client = new OpenBaoClient(config.uri())
                    .withCaCertificateFile(config.caCertificateFile())
                    .withKubernetesServiceAccount(config.serviceAccountFile())
                    .login(config.role());

            String secretValue;
            if (config.kvVersion() == 1) {
                secretValue = client.getSecretFromKv1(secretPath, vaultSecretId);
            } else if (config.kvVersion() == 2) {
                secretValue = client.getSecretFromKv2(secretPath, vaultSecretId);
            } else {
                throw new IllegalArgumentException("Unsupported kv-version: " + config.kvVersion());
            }
            return DefaultVaultRawSecret.forBuffer(Optional.of(ByteBuffer.wrap(secretValue.getBytes())));
        } catch (Exception e) {
            logger.errorv("Failed to obtain secret for vaultSecretId: {0}", vaultSecretId, e);
            throw new RuntimeException("Failed to obtain secret", e);
        }
    }

    @Override
    public void close() {
        // Intentionally left empty.
    }

}
