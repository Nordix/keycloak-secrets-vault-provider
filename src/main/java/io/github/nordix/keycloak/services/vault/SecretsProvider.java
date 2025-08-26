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

import org.keycloak.models.KeycloakSession;
import org.keycloak.connections.infinispan.InfinispanConnectionProvider;
import org.infinispan.Cache;

import io.github.nordix.baoclient.BaoClient;
import io.github.nordix.keycloak.common.ProviderConfig;

public class SecretsProvider implements VaultProvider {

    private static Logger logger = Logger.getLogger(SecretsProvider.class);

    private final String realm;
    private final ProviderConfig config;
    private String pathPrefix;
    private final Cache<String, String> secretsCache;

    public SecretsProvider(String realm, ProviderConfig config, KeycloakSession session) {
        logger.debugv("Initializing SecretsProvider for realm {0} with config: {1}", realm, config);
        this.realm = realm;
        this.config = config;

        pathPrefix = config.getKvPathPrefix().replace("%realm%", realm);

        // Remove trailing slash if present.
        if (pathPrefix.endsWith("/")) {
            pathPrefix = pathPrefix.substring(0, pathPrefix.length() - 1);
        }

        // Get the Infinispan cache for client secrets.
        if (config.getCacheName() != null && !config.getCacheName().isEmpty()) {
            this.secretsCache = session.getProvider(InfinispanConnectionProvider.class).getCache(config.getCacheName());
        } else {
            this.secretsCache = null;
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
     * Retrieves a secret value from OpenBao/HashiCorp Vault KV secrets engine using the provided secret identifier.
     * <p>
     * The {@code vaultSecretId} parameter should follow the syntax:
     *
     * <pre>
     *   [path/to/secret]:[field]
     * </pre>
     *
     * If the field is not specified, it defaults to {@code secret}.
     * The special token {@code %realm%} in the {@code vaultSecretId} will be replaced with the current realm.
     * The prefix for the path is derived from the configuration's K/V path prefix, which is also replaced
     * with the current realm.
     *
     * If caching is enabled, the secret value may be retrieved from Keycloak's Infinispan cache instead of
     * fetching it from KV secrets engine.
     * The cache key is the full path to the KV secrets engine and the value is the secret itself.
     *
     * @param vaultSecretId the identifier of the secret in the format
     *                      {@code [path/to/secret].[field]}, with optional
     *                      {@code %realm%} token
     * @return a {@link VaultRawSecret} containing the secret value as a byte buffer
     * @throws IOException              if an I/O error occurs during Vault
     *                                  communication
     * @throws IllegalArgumentException if the configured KV version is unsupported
     * @throws RuntimeException         if the secret value is empty or cannot be
     *                                  retrieved
     */
    private VaultRawSecret obtainSecretInternal(String vaultSecretId) throws IOException {
        final String pathSuffix = vaultSecretId.replace("%realm%", realm);
        final String fieldName;
        final String fullPath;

        int separatorIndex = pathSuffix.lastIndexOf(':');
        if (separatorIndex > 0) {
            fullPath = pathPrefix + "/" + pathSuffix.substring(0, separatorIndex);
            fieldName = pathSuffix.substring(separatorIndex + 1);
        } else {
            fullPath = pathPrefix + "/" + pathSuffix;
            fieldName = "secret";
        }

        logger.debugv("vaultSecretId={0} resolved to path={1} field={2} {3}", vaultSecretId, fullPath, fieldName,
                secretsCache != null ? "using cache" : "not using cache");

        String secretValue;
        if (secretsCache != null) {
            // Note: this cache-population approach has a race condition:
            //
            // 1. Keycloak A checks the cache -> miss; fetches the secret from the server
            // 2. Keycloak B checks the cache -> miss; also fetches the secret from the server
            // 3. Keycloak A writes the secret into the cache
            // 4. Keycloak B writes the secret into the cache
            //
            // The extra fetch performed by B is wasteful but acceptable here. The cache will end up
            // containing the most recently stored value, which is fine for our use case.
            //
            // We could consider using secretsCache.computeIfAbsent(...) to avoid duplicate fetches.
            // However, that may cause the fetch function to be executed by Infinispan on another node (?)
            // This class, nor the attributes are not serializable which is required by computeIfAbsent().
            // If making them serializable, it might have risks when software is updated and nodes run
            // different versions of the class?
            //
            // For having well understood behavior, just we use simple get/put intead.
            secretValue = secretsCache.get(fullPath);
            if (secretValue == null) {
                secretValue = fetchSecretFromServer(fullPath, fieldName);
                secretsCache.put(fullPath, secretValue);
            }
        } else {
            secretValue = fetchSecretFromServer(fullPath, fieldName);
        }

        if (secretValue == null || secretValue.isEmpty()) {
            logger.errorv("Secret value for path {0} and field {1} is empty", fullPath, fieldName);
            throw new RuntimeException("Secret value is empty");
        }

        return DefaultVaultRawSecret.forBuffer(Optional.of(ByteBuffer.wrap(secretValue.getBytes())));
    }

    private String fetchSecretFromServer(String fullPath, String fieldName) {
        BaoClient client = new BaoClient(config.getAddress());

        if (config.getCaCertificateFile() != null) {
            client.withCaCertificateFile(config.getCaCertificateFile());
        }

        try {
            client.loginWithKubernetes(config.getServiceAccountFile(), config.getRole());
        } catch (IOException e) {
            logger.errorv("IOException while logging in to Kubernetes for path {0} and field {1}", fullPath, fieldName,
                    e);
            throw new RuntimeException("IOException while logging in to Kubernetes", e);
        }

        Map<String, String> secretValues = client.kv1Get(config.getKvMount(), fullPath);

        return secretValues.get(fieldName);
    }

    @Override
    public void close() {
        // Intentionally left empty.
    }

}
