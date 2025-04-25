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
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;

public class OpenBaoClient {

    private static Logger logger = Logger.getLogger(OpenBaoClient.class);

    private static final String AUTH_URL_KUBERNETES = "/v1/auth/kubernetes/login";

    private final URI url;

    private String caCertificateFile;
    private String serviceAccountFile;
    private String openBaoToken;

    public OpenBaoClient(URI url) {
        this.url = url;
    }

    public OpenBaoClient withCaCertificateFile(String caCertificateFile) {
        this.caCertificateFile = caCertificateFile;
        return this;
    }

    public OpenBaoClient withKubernetesServiceAccount(String serviceAccountFile) {
        this.serviceAccountFile = serviceAccountFile;
        return this;
    }

    public OpenBaoClient login(String role) throws Exception {
        loginWithServiceAccount(url, serviceAccountFile, role);
        return this;
    }

    public OpenBaoClient loginWithServiceAccount(URI url, String serviceAccountFile, String role)
            throws Exception {
        logger.debug("Attempting to log in using service account token and role.");

        String kubernetesSaToken = new String(Files.readAllBytes(Paths.get(serviceAccountFile)));
        logger.debug("Service account token successfully read.");

        Client client = getHttpClient();

        String requestBody = String.format("{\"role\": \"%s\", \"jwt\": \"%s\"}", role, kubernetesSaToken);

        Response response = client.target(UriBuilder.fromUri(url).path(AUTH_URL_KUBERNETES))
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.entity(requestBody, MediaType.APPLICATION_JSON));

        if (response.getStatusInfo().getFamily() == Response.Status.Family.SUCCESSFUL) {
            String responseBody = response.readEntity(String.class);
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(responseBody);
            openBaoToken = rootNode.path("auth").path("client_token").asText();

            logger.debug("Login successful. Token obtained.");

            response.close();
            return this;
        } else {
            logger.errorv("Login failed with response code: {0}", response.getStatus());
            response.close();
            throw new IOException("Failed to log to " + url + ". HTTP response code: " + response.getStatus());
        }
    }

    public String getSecretFromKv1(String secretPath, String secretKey) throws Exception {
        logger.debug("Fetching secret from KV1 store at path: " + secretPath);

        Client client = getHttpClient();

        Response response = client.target(UriBuilder.fromUri(url).path("v1").path(secretPath))
                .request(MediaType.APPLICATION_JSON)
                .header("X-Vault-Token", getToken())
                .get();

        if (response.getStatusInfo().getFamily() == Response.Status.Family.SUCCESSFUL) {
            String responseBody = response.readEntity(String.class);
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(responseBody);
            String secretValue = rootNode.path("data").path(secretKey).asText();

            if (secretValue == null || secretValue.isEmpty()) {
                logger.error("Secret key not found: " + secretKey);
                throw new IOException("Secret key not found: " + secretKey);
            }

            logger.debug("Secret successfully retrieved for key: " + secretKey);
            response.close();
            return secretValue;
        } else {
            logger.errorv("Failed to fetch secret. Response code: {0}", response.getStatus());
            response.close();
            throw new IOException(
                    "Failed to fetch secret from " + secretPath + ". HTTP response code: " + response.getStatus());
        }
    }

    public String getSecretFromKv2(String secretPath, String secretKey) throws Exception {
        logger.debug("Fetching secret from KV2 store at path: " + secretPath);

        Client client = getHttpClient();

        Response response = client.target(UriBuilder.fromUri(url).path("v1").path(secretPath))
                .request(MediaType.APPLICATION_JSON)
                .header("X-Vault-Token", getToken())
                .get();

        if (response.getStatusInfo().getFamily() == Response.Status.Family.SUCCESSFUL) {
            String responseBody = response.readEntity(String.class);
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(responseBody);
            String secretValue = rootNode.at("/data/data/" + secretKey).asText();

            if (secretValue == null || secretValue.isEmpty()) {
                logger.error("Secret key not found: " + secretKey);
                throw new IOException("Secret key not found: " + secretKey);
            }

            logger.debug("Secret successfully retrieved for key: " + secretKey);
            response.close();
            return secretValue;
        } else {
            logger.errorv("Failed to fetch secret. Response code: {0}", response.getStatus());
            response.close();
            throw new IOException(
                    "Failed to fetch secret from " + secretPath + ". HTTP response code: " + response.getStatus());
        }
    }

    private String getToken() {
        if (openBaoToken == null) {
            throw new IllegalStateException("Login has not been performed. Token is not available.");
        }
        return openBaoToken;
    }

    private Client getHttpClient() throws Exception {
        ClientBuilder builder = ClientBuilder.newBuilder();

        // Load the CA certificate if provided.
        if (caCertificateFile != null) {
            String caPem = new String(Files.readAllBytes(Paths.get(caCertificateFile)));
            KeyStore trustStore = PemUtils.createTrustStoreFromPem(caPem);

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), null);
            builder.sslContext(sslContext);
        }

        return builder.build();
    }
}
