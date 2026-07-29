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
    private int connectTimeoutSeconds;
    private int requestTimeoutSeconds;
    private int retryMax;
    private int retryBackoffSeconds;

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
        // Connection settings for the backend (OpenBao/Vault). connect-timeout-seconds (3)
        // and request-timeout-seconds (10) are fast-fail defaults matching the previous
        // hard-coded values. Retry is opt-in and OFF by default: retry-max defaults to 0
        // (disabled), so behaviour is unchanged unless the caller sets retry-max > 0 to ride
        // out a transient connection failure (for example while the backend is briefly
        // unavailable during a restart or failover). retry-backoff-seconds only applies
        // when retry-max > 0.
        this.connectTimeoutSeconds = Integer.parseInt(configScope.get("connect-timeout-seconds", "3"));
        this.requestTimeoutSeconds = Integer.parseInt(configScope.get("request-timeout-seconds", "10"));
        this.retryMax = Integer.parseInt(configScope.get("retry-max", "0"));
        this.retryBackoffSeconds = Integer.parseInt(configScope.get("retry-backoff-seconds", "2"));

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

    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    public int getRequestTimeoutSeconds() {
        return requestTimeoutSeconds;
    }

    public int getRetryMax() {
        return retryMax;
    }

    public int getRetryBackoffSeconds() {
        return retryBackoffSeconds;
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
