/**
 * Copyright (c) 2026 OpenInfra Foundation Europe and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License, Version 2.0
 * which accompanies this distribution, and is available at
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.nordix.keycloak.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.keycloak.Config.Scope;

class ProviderConfigTest {

    @TempDir
    Path tempDir;

    private Path writeTokenFile() throws IOException {
        Path tokenFile = tempDir.resolve("token");
        Files.writeString(tokenFile, "dummy-jwt-token");
        return tokenFile;
    }

    /**
     * Minimal map-backed Config.Scope test double. ProviderConfig reads options only via
     * get(key) and get(key, defaultValue); the remaining Scope methods are not exercised.
     */
    @SuppressWarnings("deprecation") // A deprecated Scope method is overridden but never used by ProviderConfig.
    private static final class MapScope implements Scope {

        private final Map<String, String> values;

        MapScope(Map<String, String> values) {
            this.values = values;
        }

        @Override
        public String get(String key) {
            return values.get(key);
        }

        @Override
        public String get(String key, String defaultValue) {
            return values.getOrDefault(key, defaultValue);
        }

        @Override
        public String[] getArray(String key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Integer getInt(String key, Integer defaultValue) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Long getLong(String key, Long defaultValue) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Boolean getBoolean(String key, Boolean defaultValue) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Scope scope(String... scope) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<String> getPropertyNames() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Scope root() {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Build the minimum set of options required to satisfy the ProviderConfig constructor
     * validation (a reachable address and a readable service-account token file).
     */
    private Map<String, String> baseOptions() throws IOException {
        Map<String, String> options = new HashMap<>();
        options.put("address", "http://localhost:8200");
        options.put("service-account-file", writeTokenFile().toString());
        return options;
    }

    /**
     * With none of the resilience options set, the defaults must preserve the previous
     * behaviour: connect 3s, request 10s, and retry disabled (retry-max = 0).
     */
    @Test
    void testResilienceDefaults() throws IOException {
        ProviderConfig config = new ProviderConfig(new MapScope(baseOptions()), "test-");

        Assertions.assertEquals(3, config.getConnectTimeoutSeconds());
        Assertions.assertEquals(10, config.getRequestTimeoutSeconds());
        Assertions.assertEquals(0, config.getRetryMax());
        Assertions.assertEquals(2, config.getRetryBackoffSeconds());
    }

    /**
     * When the resilience options are provided, they are parsed and exposed via the getters.
     */
    @Test
    void testResilienceOptionsAreParsed() throws IOException {
        Map<String, String> options = baseOptions();
        options.put("connect-timeout-seconds", "5");
        options.put("request-timeout-seconds", "20");
        options.put("retry-max", "3");
        options.put("retry-backoff-seconds", "1");

        ProviderConfig config = new ProviderConfig(new MapScope(options), "test-");

        Assertions.assertEquals(5, config.getConnectTimeoutSeconds());
        Assertions.assertEquals(20, config.getRequestTimeoutSeconds());
        Assertions.assertEquals(3, config.getRetryMax());
        Assertions.assertEquals(1, config.getRetryBackoffSeconds());
    }
}
