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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Optional;

public class SecretsProvider implements VaultProvider {

    private static Logger logger = Logger.getLogger(SecretsProvider.class);

    private final String realm;
    private final SecretsProviderConfig config;

    public SecretsProvider(String realm, SecretsProviderConfig config) {
        logger.debugv("Initializing SecretsProvider for realm {0} with config: {1}", realm, config);
        this.realm = realm;
        this.config = config;
    }

    @Override
    public VaultRawSecret obtainSecret(String vaultSecretId) {
        try {
            return obtainSecretInternal(vaultSecretId);
        } catch (Exception e) {
            logger.errorv("Failed to obtain secret for vaultSecretId: {0} from realm: {1}", vaultSecretId, realm, e);
            throw new RuntimeException("Failed to obtain secret", e);
        }
    }

    private VaultRawSecret obtainSecretInternal(String vaultSecretId) throws IOException {
        String secretPath = String.format("%s/%s", config.getKvSecretPath(), realm);
        logger.debugv("Obtaining secret for vaultSecretId: {0} from path: {1}", vaultSecretId, secretPath);

        BaoClient client = new BaoClient(config.getAddress())
                .withCaCertificateFile(config.getCaCertificateFile())
                .withKubernetesServiceAccount(config.getServiceAccountFile())
                .login(config.getAuthMethod());

        String secretValue;
        switch (config.getKvVersion()) {
            case 1:
                secretValue = client.getSecretFromKv1(secretPath, vaultSecretId);
                break;
            case 2:
                secretValue = client.getSecretFromKv2(secretPath, vaultSecretId);
                break;
            default:
                throw new IllegalArgumentException("Unsupported kv-version: " + config.getKvVersion());
        }

        return DefaultVaultRawSecret.forBuffer(Optional.of(ByteBuffer.wrap(secretValue.getBytes())));
    }

    @Override
    public void close() {
        // Intentionally left empty.
    }

}
