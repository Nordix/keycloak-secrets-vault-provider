/**
 * Copyright (c) 2025 OpenInfra Foundation Europe and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License, Version 2.0
 * which accompanies this distribution, and is available at
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.nordix.baoclient;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;

public class BaoClient {

    private static Logger logger = Logger.getLogger(BaoClient.class);

    private static final String AUTH_URL_KUBERNETES = "/v1/auth/kubernetes/login";

    private String serviceAccountFile;
    private RestClient httpClient;

    public BaoClient(URI url) {
        this.httpClient = new RestClient(url);
    }

    public BaoClient withCaCertificateFile(String caCertificateFile) {
        httpClient.withCaCertificateFile(caCertificateFile);
        return this;
    }

    public BaoClient withKubernetesServiceAccount(String serviceAccountFile) {
        this.serviceAccountFile = serviceAccountFile;
        return this;
    }

    public BaoClient withToken(String token) {
        httpClient.withToken(token);
        return this;
    }

    public BaoClient login(String role) throws IOException {
        loginWithKubernetes(serviceAccountFile, role);
        return this;
    }

    public BaoClient loginWithKubernetes(String serviceAccountFile, String role) throws IOException {
        logger.debug("Attempting to log in using Kubernetes auth method, service account token and role.");

        String kubernetesSaToken = new String(Files.readAllBytes(Paths.get(serviceAccountFile)));
        if (kubernetesSaToken.isEmpty()) {
            logger.error("Service account token is empty or null.");
            throw new BaoClientException("Service account token is empty or null.");
        }

        logger.debug("Service account token successfully read.");

        HttpResponse<JsonNode> response = httpClient.sendRequest(
                AUTH_URL_KUBERNETES,
                "POST",
                String.format("{\"role\": \"%s\", \"jwt\": \"%s\"}", role, kubernetesSaToken));

        if (!RestClient.isSuccessfulResponse(response)) {
            logger.errorv(
                    "Login failed with response code: {0} body: {1}",
                    response.statusCode(),
                    response.body());
            throw new BaoClientException(
                    "Failed to log in to " + httpClient.getBaseUrl() + ". HTTP response code " + response.statusCode());
        }

        logger.debug("Login successful. Token obtained.");
        httpClient.withToken(response.body().path("auth").path("client_token").asText());
        return this;
    }

    public String getSecretFromKv1(String secretPath, String secretKey) {
        return getSecret(secretPath, secretKey, "/data/");
    }

    public String getSecretFromKv2(String secretPath, String secretKey) {
        return getSecret(secretPath, secretKey, "/data/data/");
    }

    public String getSecret(String secretPath, String secretKey, String dataPath) {
        if (secretKey == null || secretKey.isEmpty()) {
            logger.error("Secret key is empty or null.");
            throw new IllegalArgumentException("Secret key is empty or null.");
        }

        if (secretPath == null || secretPath.isEmpty()) {
            logger.error("Secret path is empty or null.");
            throw new IllegalArgumentException("Secret path is empty or null.");
        }

        logger.debug("Fetching secret from store at path: " + secretPath + " with key: " + secretKey);

        HttpResponse<JsonNode> response = httpClient.sendRequest(
                "v1/" + secretPath,
                "GET",
                null);

        if (!RestClient.isSuccessfulResponse(response)) {
            logger.errorv(
                    "Failed to fetch secret. Response code: {0} body: {1}",
                    response.statusCode(),
                    response.body());
            throw new BaoClientException(
                    "Failed to fetch secret from " + secretPath + ". HTTP response code " + response.statusCode());
        }

        String secretValue = response.body().at(dataPath + secretKey).asText();
        if (secretValue == null || secretValue.isEmpty()) {
            logger.error("Secret key not found: " + secretKey);
            throw new BaoClientException("Secret key not found: " + secretKey);
        }

        logger.debug("Secret successfully retrieved for key: " + secretKey);
        return secretValue;
    }

    public boolean isReady() {
        HttpResponse<JsonNode> response = httpClient.sendRequest(
                "v1/sys/health",
                "GET",
                null);
        return RestClient.isSuccessfulResponse(response);
    }

    public void enableKubernetesAuth() {
        HttpResponse<JsonNode> response = httpClient.sendRequest(
                "v1/sys/auth/kubernetes",
                "POST",
                "{\"type\":\"kubernetes\"}");

        if (!RestClient.isSuccessfulResponse(response)) {
            throw new BaoClientException("Failed to enable Kubernetes auth: " + response.body());
        }
        logger.debug("Successfully enabled Kubernetes auth.");
    }

    public void configureKubernetesAuth(String kubernetesHost) {
        String body = String.format("{\"kubernetes_host\":\"%s\"}", kubernetesHost);
        HttpResponse<JsonNode> response = httpClient.sendRequest(
                "v1/auth/kubernetes/config",
                "POST",
                body);

        if (!RestClient.isSuccessfulResponse(response)) {
            throw new BaoClientException("Failed to configure Kubernetes auth: " + response.body());
        }
        logger.debug("Successfully configured Kubernetes auth.");
    }

    public void enableAuditToStdout() {
        HttpResponse<JsonNode> response = httpClient.sendRequest(
                "v1/sys/audit/stdout",
                "POST",
                "{\"type\":\"file\", \"options\":{\"file_path\":\"stdout\"}}");

        if (!RestClient.isSuccessfulResponse(response)) {
            throw new BaoClientException("Failed to enable audit to stdout: " + response.body());
        }
        logger.debug("Successfully enabled audit to stdout.");
    }

    public void writeSecretKv2(String key, Map<String, Object> data) {
        StringBuilder bodyBuilder = new StringBuilder("{\"data\":{");
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            bodyBuilder.append("\"").append(entry.getKey()).append("\":");
            if (entry.getValue() instanceof String) {
                bodyBuilder.append("\"").append(entry.getValue()).append("\"");
            } else {
                bodyBuilder.append(entry.getValue());
            }
            bodyBuilder.append(",");
        }
        if (!data.isEmpty()) {
            bodyBuilder.setLength(bodyBuilder.length() - 1); // Remove trailing comma
        }
        bodyBuilder.append("}}");

        HttpResponse<JsonNode> response = httpClient.sendRequest(
                "v1/" + key,
                "POST",
                bodyBuilder.toString());

        if (!RestClient.isSuccessfulResponse(response)) {
            throw new BaoClientException("Failed to write KV2 data: " + response.body());
        }
    }

    public static class BaoClientException extends RuntimeException {
        public BaoClientException(String message) {
            super(message);
        }

        public BaoClientException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
