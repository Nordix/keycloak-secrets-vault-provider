/**
 * Copyright (c) 2025 OpenInfra Foundation Europe and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License, Version 2.0
 * which accompanies this distribution, and is available at
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.nordix.keycloak.services.vault;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Optional;

import org.jboss.logging.Logger;
import org.keycloak.vault.DefaultVaultRawSecret;
import org.keycloak.vault.VaultProvider;
import org.keycloak.vault.VaultRawSecret;

import io.github.nordix.baoclient.BaoClient;

public class SecretsProvider implements VaultProvider {

    private static Logger logger = Logger.getLogger(SecretsProvider.class);

    private final String realm;
    private final SecretsProviderConfig config;
    private String pathPrefix;

    public SecretsProvider(String realm, SecretsProviderConfig config) {
        logger.debugv("Initializing SecretsProvider for realm {0} with config: {1}", realm, config);
        this.realm = realm;
        this.config = config;

        pathPrefix = config.getKvPathPrefix().replace("%realm%", realm);
        // Remove trailing slash if present
        if (pathPrefix.endsWith("/")) {
            pathPrefix = pathPrefix.substring(0, pathPrefix.length() - 1);
        }
    }

    @Override
    public VaultRawSecret obtainSecret(String vaultSecretId) {
        logger.debugv("Obtaining secret for vaultSecretId={0} realm={1}", vaultSecretId, realm);
        try {
            return obtainSecretInternal(vaultSecretId);
        } catch (Exception e) {
            logger.errorv("Failed to obtain secret for vaultSecretId: {0} from realm: {1}", vaultSecretId, realm, e);
            throw new RuntimeException("Failed to obtain secret", e);
        }
    }

    private VaultRawSecret obtainSecretInternal(String vaultSecretId) throws IOException {
        // Syntax ${vault.path/to/secret,field_name}
        // The path is combined with the kv-path-prefix
        // The field name is the key to the secret in the data map returned in the response body.

        String pathSuffix = vaultSecretId.replace("%realm%", realm);

        String fieldName = null;
        String fullPath = null;

        int separatorIndex = pathSuffix.lastIndexOf('.');
        if (separatorIndex > 0) {
            fullPath = pathPrefix + "/" + pathSuffix.substring(0, separatorIndex);
            fieldName = pathSuffix.substring(separatorIndex + 1);
        } else {
            fullPath = pathPrefix;
            fieldName = pathSuffix;
        }

        logger.debugv("vaultSecretId={0} resolved to path={1} field={2}", vaultSecretId, fullPath, fieldName);

        BaoClient client = new BaoClient(config.getAddress());

        if (config.getCaCertificateFile() != null) {
            client.withCaCertificateFile(config.getCaCertificateFile());
        }

        client.loginWithKubernetes(config.getServiceAccountFile(), config.getRole());

        String secretValue;
        switch (config.getKvVersion()) {
            case 1:
                secretValue = client.kv1Get(config.getKvMount(), fullPath, fieldName);
                break;
            case 2:
                secretValue = client.kv2Get(config.getKvMount(), fullPath, fieldName);
                break;
            default:
                throw new IllegalArgumentException("Unsupported kv-version: " + config.getKvVersion());
        }

        if (secretValue.isEmpty()) {
            logger.errorv("Secret value for path {0} and field {1} is empty", fullPath, fieldName);
            throw new RuntimeException("Secret value is empty");
        }

        return DefaultVaultRawSecret.forBuffer(Optional.of(ByteBuffer.wrap(secretValue.getBytes())));
    }

    @Override
    public void close() {
        // Intentionally left empty.
    }

}
