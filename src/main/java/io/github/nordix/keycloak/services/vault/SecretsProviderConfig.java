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
import java.nio.file.Files;
import java.nio.file.Paths;

import org.jboss.logging.Logger;

public record SecretsProviderConfig(
    String authMethod,
    String serviceAccountFile,
    URI uri,
    String kvSecretPath,
    int kvVersion,
    String caCertificateFile,
    String role
) {
    private static Logger logger = Logger.getLogger(SecretsProviderConfig.class);

    public SecretsProviderConfig {
        if (uri == null) {
            throw new IllegalArgumentException("uri must be provided");
        }

        if (kvSecretPath == null || kvSecretPath.isBlank()) {
            throw new IllegalArgumentException("kv-secret-path must be provided");
        }

        if (serviceAccountFile() != null) {
            validateFile(serviceAccountFile(), "Service account file");
        }

        if (caCertificateFile() != null) {
            validateFile(caCertificateFile(), "CA certificate file");
        }

        if (kvVersion() < 1 || kvVersion() > 2) {
            throw new IllegalArgumentException("kv-version must be either 1 or 2");
        }
    }

    private void validateFile(String filePath, String errorMessage) {
        if (!Files.exists(Paths.get(filePath))) {
            logger.error(errorMessage + ": File does not exist - " + filePath);
            throw new IllegalArgumentException(errorMessage + ": File does not exist - " + filePath);
        }
        if (!Files.isReadable(Paths.get(filePath))) {
            logger.error(errorMessage + ": File is not readable - " + filePath);
            throw new IllegalArgumentException(errorMessage + ": File is not readable - " + filePath);
        }
    }
}
