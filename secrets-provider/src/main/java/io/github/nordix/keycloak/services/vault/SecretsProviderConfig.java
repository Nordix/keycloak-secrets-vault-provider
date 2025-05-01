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

public class SecretsProviderConfig {

    private static Logger logger = Logger.getLogger(SecretsProviderConfig.class);

    private static final String CMD_LINE_OPTION_PREFIX = "--spi-vault-secrets-provider-";

    private String authMethod;
    private String serviceAccountFile;
    private URI address;
    private String kvSecretPath;
    private int kvVersion;
    private String caCertificateFile;
    private String role;

    public SecretsProviderConfig(org.keycloak.Config.Scope configScope) {
        this.authMethod = configScope.get("auth-method", "kubernetes");
        this.serviceAccountFile = configScope.get("service-account-file",
                "/var/run/secrets/kubernetes.io/serviceaccount/token");
        this.address = configScope.get("address") != null ? URI.create(configScope.get("address")) : null;
        this.kvSecretPath = configScope.get("kv-secret-path");
        this.kvVersion = Integer.parseInt(configScope.get("kv-version", "2"));
        this.caCertificateFile = configScope.get("ca-certificate-file");
        this.role = configScope.get("role");

        if (address == null) {
            logger.error(CMD_LINE_OPTION_PREFIX + "address + must be provided");
            throw new IllegalArgumentException(CMD_LINE_OPTION_PREFIX + "address must be provided");
        }

        if (kvSecretPath == null || kvSecretPath.isBlank()) {
            logger.error(CMD_LINE_OPTION_PREFIX + "kv-secret-path + must be provided");
            throw new IllegalArgumentException(CMD_LINE_OPTION_PREFIX + "kv-secret-path must be provided");
        }

        if (serviceAccountFile != null && !fileExistsAndReadable(serviceAccountFile)) {
            logger.errorv(CMD_LINE_OPTION_PREFIX + "service-account-file does not exist or is not readable: {0}",
                    serviceAccountFile);
            throw new IllegalArgumentException(CMD_LINE_OPTION_PREFIX
                    + "service-account-file does not exist or is not readable: " + serviceAccountFile);
        }

        if (caCertificateFile != null && !fileExistsAndReadable(caCertificateFile)) {
            logger.errorv(CMD_LINE_OPTION_PREFIX + "ca-certificate-file does not exist or is not readable: {0}",
                    caCertificateFile);
            throw new IllegalArgumentException(CMD_LINE_OPTION_PREFIX
                    + "ca-certificate-file does not exist or is not readable: " + caCertificateFile);
        }

        if (kvVersion < 1 || kvVersion > 2) {
            logger.errorv("--spi-vault-secrets-provider-kv-version must be either 1 or 2. Was {0}", kvVersion);
            throw new IllegalArgumentException("kv-version must be either 1 or 2. Was " + kvVersion);
        }
    }

    private boolean fileExistsAndReadable(String filePath) {
        return Files.exists(Paths.get(filePath)) && Files.isReadable(Paths.get(filePath));
    }

    public String getAuthMethod() {
        return authMethod;
    }

    public String getServiceAccountFile() {
        return serviceAccountFile;
    }

    public URI getAddress() {
        return address;
    }

    public String getKvSecretPath() {
        return kvSecretPath;
    }

    public int getKvVersion() {
        return kvVersion;
    }

    public String getCaCertificateFile() {
        return caCertificateFile;
    }

    public String getRole() {
        return role;
    }

    @Override
    public String toString() {
        return "SecretsProviderConfig{" +
                "authMethod='" + authMethod + '\'' +
                ", serviceAccountFile='" + serviceAccountFile + '\'' +
                ", address=" + address +
                ", kvSecretPath='" + kvSecretPath + '\'' +
                ", kvVersion=" + kvVersion +
                ", caCertificateFile='" + caCertificateFile + '\'' +
                ", role='" + role + '\'' +
                '}';
    }
}
