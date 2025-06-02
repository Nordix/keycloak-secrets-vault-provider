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

    private RestClient httpClient;

    public BaoClient(URI url) {
        this.httpClient = new RestClient(url);
    }

    public BaoClient withCaCertificateFile(String caCertificateFile) {
        httpClient.withCaCertificateFile(caCertificateFile);
        return this;
    }

    public BaoClient withToken(String token) {
        httpClient.withHeader("X-Vault-Token", token);
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
                toJsonString(Map.of("role", role, "jwt", kubernetesSaToken)));

        if (!RestClient.isSuccessfulResponse(response)) {
            logger.errorv(
                    "Login failed with response code: '{0}' body: '{1}'",
                    response.statusCode(),
                    response.body());
            throw new BaoClientException(
                    "Failed to log in to " + httpClient.getBaseUrl() + ". HTTP response code " + response.statusCode());
        }

        logger.debug("Login successful. Token obtained.");
        httpClient.withHeader("X-Vault-Token", response.body().path("auth").path("client_token").asText());
        return this;
    }

    public boolean isReady() {
        HttpResponse<JsonNode> response = httpClient.sendRequest(
                "v1/sys/health",
                "GET",
                null);
        return RestClient.isSuccessfulResponse(response);
    }

    public void write(String path, Map<String, String> data) {
        HttpResponse<JsonNode> response = httpClient.sendRequest(
                "v1/" + path,
                "POST",
                toJsonString(data));

        if (!RestClient.isSuccessfulResponse(response)) {
            logger.errorv(
                    "Failed to write data. Response code: '{0}' body: '{1}'",
                    response.statusCode(),
                    response.body());
            throw new BaoClientException("Failed to write data to path '" + path + "': '" + response.body() + "'");
        }
        logger.debug("Successfully wrote data to path: '" + path + "'");
    }

    public String kv1Get(String kvMountPath, String secretPath, String secretKey) {
        HttpResponse<JsonNode> response = httpClient.sendRequest(
                "v1/" + kvMountPath + "/" + secretPath,
                "GET",
                null);

        if (!RestClient.isSuccessfulResponse(response)) {
            logger.errorv(
                    "Failed to read data. Response code: '{0}' body: '{1}'",
                    response.statusCode(),
                    response.body());
            throw new BaoClientException("Failed to read data from path '" + secretPath + "': '" + response.body() + "'");
        }
        return response.body().path("data").path(secretKey).asText();
    }

    public String kv2Get(String kvMountPath, String secretPath, String secretKey) {
        HttpResponse<JsonNode> response = httpClient.sendRequest(
                "v1/" + kvMountPath + "/data/" + secretPath,
                "GET",
                null);

        if (!RestClient.isSuccessfulResponse(response)) {
            logger.errorv(
                    "Failed to read data. Response code: '{0}' body: '{1}'",
                    response.statusCode(),
                    response.body());
            throw new BaoClientException("Failed to read data from path '" + secretPath + "': '" + response.body() + "'");
        }

        JsonNode rootNode = response.body();
        if (!rootNode.has("data") ||
            !rootNode.path("data").has("data") ||
            !rootNode.path("data").path("data").has(secretKey)) {

            logger.errorv("Secret key '{0}' not found at path '{1}'", secretKey, secretPath);
            throw new BaoClientException("Secret key '" + secretKey + "' not found at path '" + secretPath + "'");
        }

        return rootNode.path("data").path("data").path(secretKey).asText();
    }

    public void kv2Put(String kvMountPath, String secretPath, Map<String, String> data) {
        HttpResponse<JsonNode> response = httpClient.sendRequest(
                "v1/" + kvMountPath + "/data/" + secretPath,
                "POST",
                "{\"data\":" + toJsonString(data) + "}");

        if (!RestClient.isSuccessfulResponse(response)) {
            logger.errorv(
                    "Failed to write data. Response code: '{0}' body: '{1}'",
                    response.statusCode(),
                    response.body());
            throw new BaoClientException("Failed to write data to path '" + secretPath + "': '" + response.body() + "'");
        }
        logger.debug("Successfully wrote data to path: '" + secretPath + "'");
    }

    private String toJsonString(Map<String, String> data) {
        StringBuilder bodyBuilder = new StringBuilder("{");
        for (Map.Entry<String, String> entry : data.entrySet()) {
            bodyBuilder.append("\"").append(entry.getKey()).append("\":");
            bodyBuilder.append("\"").append(entry.getValue()).append("\"");
            bodyBuilder.append(",");
        }
        if (!data.isEmpty()) {
            bodyBuilder.setLength(bodyBuilder.length() - 1); // Remove trailing comma
        }
        bodyBuilder.append("}");
        return bodyBuilder.toString();
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
