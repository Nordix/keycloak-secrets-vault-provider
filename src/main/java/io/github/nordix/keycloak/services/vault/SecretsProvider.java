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
import java.util.Map;
import java.util.Optional;

import org.jboss.logging.Logger;
import org.keycloak.vault.DefaultVaultRawSecret;
import org.keycloak.vault.VaultProvider;
import org.keycloak.vault.VaultRawSecret;

import io.github.nordix.baoclient.BaoClient;
import io.github.nordix.keycloak.common.ProviderConfig;

public class SecretsProvider implements VaultProvider {

    private static Logger logger = Logger.getLogger(SecretsProvider.class);

    private final String realm;
    private final ProviderConfig config;
    private String pathPrefix;

    public SecretsProvider(String realm, ProviderConfig config) {
        logger.debugv("Initializing SecretsProvider for realm {0} with config: {1}", realm, config);
        this.realm = realm;
        this.config = config;

        pathPrefix = config.getKvPathPrefix().replace("%realm%", realm);

        // Remove trailing slash if present.
        if (pathPrefix.endsWith("/")) {
            pathPrefix = pathPrefix.substring(0, pathPrefix.length() - 1);
        }
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

    /**
     * Retrieves a secret value from a Vault Key/Value (K/V) secrets engine using the provided secret identifier.
     * <p>
     * The {@code vaultSecretId} parameter should follow the syntax:
     * <pre>
     *   [path/to/secret]:[field]
     * </pre>
     * if the field is not specified, it defaults to {@code secret}.
     * The special token {@code %realm%} in the {@code vaultSecretId} will be replaced with the current realm.
     * The prefix for the path is derived from the configuration's K/V path prefix, which is also replaced with the current realm.
     *
     * @param vaultSecretId the identifier of the secret in the format {@code [path/to/secret].[field]}, with optional {@code %realm%} token
     * @return a {@link VaultRawSecret} containing the secret value as a byte buffer
     * @throws IOException if an I/O error occurs during Vault communication
     * @throws IllegalArgumentException if the configured KV version is unsupported
     * @throws RuntimeException if the secret value is empty or cannot be retrieved
     */
    private VaultRawSecret obtainSecretInternal(String vaultSecretId) throws IOException {
        String pathSuffix = vaultSecretId.replace("%realm%", realm);
        String fieldName = null;
        String fullPath = null;

        int separatorIndex = pathSuffix.lastIndexOf(':');
        if (separatorIndex > 0) {
            fullPath = pathPrefix + "/" + pathSuffix.substring(0, separatorIndex);
            fieldName = pathSuffix.substring(separatorIndex + 1);
        } else {
            fullPath = pathPrefix + "/" + pathSuffix;
            fieldName = "secret";
        }

        logger.debugv("vaultSecretId={0} resolved to path={1} field={2}", vaultSecretId, fullPath, fieldName);

        BaoClient client = new BaoClient(config.getAddress());

        if (config.getCaCertificateFile() != null) {
            client.withCaCertificateFile(config.getCaCertificateFile());
        }

        client.loginWithKubernetes(config.getServiceAccountFile(), config.getRole());

        Map<String, String> secretValues;
        switch (config.getKvVersion()) {
            case 1:
                secretValues = client.kv1Get(config.getKvMount(), fullPath);
                break;
            case 2:
                secretValues = client.kv2Get(config.getKvMount(), fullPath);
                break;
            default:
                throw new IllegalArgumentException("Unsupported kv-version: " + config.getKvVersion());
        }

        String secretValue = secretValues.get(fieldName);

        if (secretValue == null || secretValue.isEmpty()) {
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
