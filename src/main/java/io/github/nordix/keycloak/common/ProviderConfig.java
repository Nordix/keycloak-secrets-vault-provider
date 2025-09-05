/**
 * Copyright (c) 2025 OpenInfra Foundation Europe and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License, Version 2.0
 * which accompanies this distribution, and is available at
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.nordix.keycloak.common;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.jboss.logging.Logger;
import org.keycloak.Config.Scope;

public class ProviderConfig {

    private static Logger logger = Logger.getLogger(ProviderConfig.class);

    private String authMethod;
    private String serviceAccountFile;
    private URI address;
    private String kvMount;
    private String kvPathPrefix;
    private int kvVersion;
    private String caCertificateFile;
    private String role;
    private String cacheName;

    public ProviderConfig(Scope configScope, String cmdLineOptionPrefix) {
        this.authMethod = configScope.get("auth-method", "kubernetes");
        this.serviceAccountFile = configScope.get("service-account-file",
                "/var/run/secrets/kubernetes.io/serviceaccount/token");
        this.address = configScope.get("address") != null ? URI.create(configScope.get("address")) : null;
        this.kvMount = configScope.get("kv-mount", "secret");
        this.kvPathPrefix = configScope.get("kv-path-prefix", "keycloak/%realm%");
        this.kvVersion = Integer.parseInt(configScope.get("kv-version", "1"));
        this.caCertificateFile = configScope.get("ca-certificate-file");
        this.role = configScope.get("role", "");
        this.cacheName = configScope.get("cache-name");

        if (address == null) {
            logger.error(cmdLineOptionPrefix + "address + must be provided");
            throw new IllegalArgumentException(cmdLineOptionPrefix + "address must be provided");
        }

        if (serviceAccountFile != null && !fileExistsAndReadable(serviceAccountFile)) {
            logger.errorv(cmdLineOptionPrefix + "service-account-file does not exist or is not readable: {0}",
                    serviceAccountFile);
            throw new IllegalArgumentException(cmdLineOptionPrefix
                    + "service-account-file does not exist or is not readable: " + serviceAccountFile);
        }

        if (address.getScheme().equalsIgnoreCase("https")) {
            if (caCertificateFile == null) {
                logger.warn(cmdLineOptionPrefix + "ca-certificate-file is not provided for HTTPS connection");
            } else if (!fileExistsAndReadable(caCertificateFile)) {
                logger.errorv(cmdLineOptionPrefix + "ca-certificate-file does not exist or is not readable: {0}",
                        caCertificateFile);
                throw new IllegalArgumentException(cmdLineOptionPrefix
                        + "ca-certificate-file does not exist or is not readable: " + caCertificateFile);
            }
        }

        if (!authMethod.equals("kubernetes")) {
            logger.error(cmdLineOptionPrefix + "auth-method only 'kubernetes' is supported");
            throw new IllegalArgumentException(cmdLineOptionPrefix + "auth-method only 'kubernetes' is supported");
        }

        if (kvVersion != 1) {
            logger.error(cmdLineOptionPrefix + "kv-version only '1' is supported");
            throw new IllegalArgumentException(cmdLineOptionPrefix + "kv-version only '1' is supported");
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

    public String getKvMount() {
        return kvMount;
    }

    public String getKvPathPrefix() {
        return kvPathPrefix;
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

    public String getCacheName() {
        return cacheName;
    }

    @Override
    public String toString() {
        return "SecretsProviderConfig{" +
                "authMethod='" + authMethod + '\'' +
                ", serviceAccountFile='" + serviceAccountFile + '\'' +
                ", address=" + address +
                ", kvMount='" + kvMount + '\'' +
                ", kvPathPrefix='" + kvPathPrefix + '\'' +
                ", kvVersion=" + kvVersion +
                ", caCertificateFile='" + caCertificateFile + '\'' +
                ", role='" + role + '\'' +
                ", cacheName=" + (cacheName == null || cacheName.isEmpty() ? "<disabled>" : "'" + cacheName + "'") +
                '}';
    }
}
