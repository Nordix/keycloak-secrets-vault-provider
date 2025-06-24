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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;

public class BaoClient {

    private static Logger logger = Logger.getLogger(BaoClient.class);

    private static final String AUTH_URL_KUBERNETES = "/v1/auth/kubernetes/login";

    private RestClient httpClient;

    public BaoClient(URI url) {
        this.httpClient = new RestClient(url);
    }

    /**
     * Sets the CA certificate file to be used by the HTTP client.
     *
     * @param caCertificateFile Path to the CA certificate file.
     * @return This BaoClient instance for method chaining.
     */
    public BaoClient withCaCertificateFile(String caCertificateFile) {
        httpClient.withCaCertificateFile(caCertificateFile);
        return this;
    }

    /**
     * Sets the authentication token to be used by the HTTP client.
     *
     * @param token The authentication token.
     * @return This BaoClient instance for method chaining.
     */
    public BaoClient withToken(String token) {
        httpClient.withHeader("X-Vault-Token", token);
        return this;
    }

    /**
     * Logs in to the Bao service using Kubernetes authentication.
     *
     * @param serviceAccountFile The path to the Kubernetes service account token
     *                           file.
     * @param role               The role to assume for the login.
     * @return This BaoClient instance for method chaining.
     * @throws IOException        if there is an error reading the service account
     *                            file or sending the request.
     * @throws BaoClientException if the login fails.
     */
    public BaoClient loginWithKubernetes(String serviceAccountFile, String role) throws IOException {
        logger.debugv("Attempting to log in using Kubernetes auth method, service account token and role {0}", role);

        String kubernetesSaToken = new String(Files.readAllBytes(Paths.get(serviceAccountFile)));
        if (kubernetesSaToken.isEmpty()) {
            logger.error("Service account token is empty or null.");
            throw new BaoClientException("Service account token is empty or null.");
        }

        logger.debug("Service account token successfully read.");

        Map<String, String> payload = new HashMap<>();
        payload.put("role", role);
        payload.put("jwt", kubernetesSaToken);

        HttpResponse<JsonNode> response = httpClient.sendRequest(
                AUTH_URL_KUBERNETES,
                "POST",
                toJsonString(payload));

        if (!RestClient.isSuccessfulResponse(response)) {
            logger.errorv(
                    "Login failed with response code: {0} body: {1}",
                    response.statusCode(),
                    response.body());
            throw new BaoClientException(
                    "Failed to log in to " + httpClient.getBaseUrl() + ". HTTP response code " + response.statusCode(),
                    response.statusCode());
        }

        logger.debug("Login successful. Token obtained.");
        httpClient.withHeader("X-Vault-Token", response.body().path("auth").path("client_token").asText());
        return this;
    }

    /**
     * Checks if OpenBao is up and running by querying the health endpoint.
     *
     * @return true if the service is ready, false otherwise.
     */
    public boolean isReady() {
        HttpResponse<JsonNode> response = httpClient.sendRequest(
                "v1/sys/health",
                "GET",
                null);
        return RestClient.isSuccessfulResponse(response);
    }

    /**
     * Writes data to the specified path in OpenBao API using a POST request.
     *
     * @param path The path to write data to.
     * @param data The key-value pairs to write.
     * @return This BaoClient instance for method chaining.
     * @throws BaoClientException if the operation fails.
     */
    public BaoClient write(String path, Map<String, String> data) {
        HttpResponse<JsonNode> response = httpClient.sendRequest(
                "v1/" + path,
                "POST",
                toJsonString(data));

        if (!RestClient.isSuccessfulResponse(response)) {
            logger.errorv(
                    "Failed to write data. Response code: {0} body: {1}",
                    response.statusCode(),
                    response.body());
            throw new BaoClientException("Failed to write data to path '" + path + "': '" + response.body() + "'",
                    response.statusCode());
        }
        logger.debug("Successfully wrote data to path: '" + path + "'");
        return this;
    }

    /**
     * Lists secret keys under a given path in KVv1 store.
     *
     * @param kvMountPath      The mount path of the KV store.
     * @param secretPathPrefix The prefix path under which to list keys (e.g.
     *                         "my-app").
     * @return A list of secret keys.
     * @throws BaoClientException if the operation fails.
     */
    public List<String> kv1ListKeys(String kvMountPath, String secretPathPrefix) {
        if (secretPathPrefix == null) {
            secretPathPrefix = "";
        }
        String listPath = "v1/" + kvMountPath + "/" + (secretPathPrefix.isEmpty() ? "" : secretPathPrefix + "/");

        HttpResponse<JsonNode> response = httpClient.sendRequest(
                listPath,
                "LIST",
                null);

        if (response.statusCode() == 404) {
            // If the path does not exist, return an empty list.
            logger.debugv("No keys found at path: {0}. Returning empty list.", listPath);
            return List.of();
        }

        if (!RestClient.isSuccessfulResponse(response)) {
            logger.errorv(
                    "Failed to list keys. Path: {0} Response code: {1} body: {2}",
                    listPath, response.statusCode(), response.body());
            throw new BaoClientException("Failed to list keys from path '" + secretPathPrefix + "': HTTP "
                    + response.statusCode() + " Body: '" + response.body() + "'", response.statusCode());
        }

        JsonNode keysNode = response.body().path("data").path("keys");
        return fromJsonNodeToListString(keysNode);
    }

    /**
     * Retrieves a secret from KVv1 store.
     *
     * @param kvMountPath The mount path of the KV store.
     * @param secretPath  The full path to the secret (e.g. "my-app/secret").
     * @return A map representing the secret data.
     * @throws BaoClientException if the operation fails.
     */
    public Map<String, String> kv1Get(String kvMountPath, String secretPath) {
        HttpResponse<JsonNode> response = httpClient.sendRequest(
                "v1/" + kvMountPath + "/" + secretPath,
                "GET",
                null);

        if (!RestClient.isSuccessfulResponse(response)) {
            logger.errorv(
                    "Failed to read data. Response code: {0} body: {1}",
                    response.statusCode(),
                    response.body());
            throw new BaoClientException(
                    "Failed to read data from path '" + secretPath + "': '" + response.body() + "'",
                    response.statusCode());
        }
        return fromJsonNodeToMapStringString(response.body().path("data"));
    }

    /**
     * Insert or update a secret in KVv1 store.
     *
     * @param kvMountPath The mount path of the KV store.
     * @param secretPath  The full path to the secret to insert or update (e.g.
     *                    "my-app/secret").
     * @param data        The key-value pairs to store in the secret.
     * @return This BaoClient instance for method chaining.
     * @throws BaoClientException if the operation fails.
     */
    public BaoClient kv1Upsert(String kvMountPath, String secretPath, Map<String, String> data) {
        write(kvMountPath + "/" + secretPath, data);
        return this;
    }

    /**
     * Deletes a secret from KVv1 store.
     *
     * @param kvMountPath The mount path of the KV store.
     * @param secretPath  The full path to the secret to delete (e.g.
     *                    "my-app/secret").
     * @throws BaoClientException if the operation fails.
     */
    public void kv1Delete(String kvMountPath, String secretPath) {
        HttpResponse<JsonNode> response = httpClient.sendRequest(
                "v1/" + kvMountPath + "/" + secretPath,
                "DELETE",
                null);

        if (!RestClient.isSuccessfulResponse(response)) {
            logger.errorv(
                    "Failed to delete secret. Response code: {0} body: {1}",
                    response.statusCode(),
                    response.body());
            throw new BaoClientException(
                    "Failed to delete secret at path '" + secretPath + "': '" + response.body() + "'",
                    response.statusCode());
        }
        logger.debug("Successfully deleted secret at path: '" + secretPath + "'");
    }

    /**
     * Lists secret keys under a given path in KVv2 store.
     *
     * @param kvMountPath      The mount path of the KV store.
     * @param secretPathPrefix The prefix path under which to list keys (e.g.
     *                         "my-app").
     * @return A list of secret keys.
     * @throws BaoClientException if the operation fails.
     */
    public List<String> kv2ListKeys(String kvMountPath, String secretPathPrefix) {
        String listPath = "v1/" + kvMountPath + "/metadata/"
                + (secretPathPrefix.isEmpty() ? "" : secretPathPrefix + "/");

        HttpResponse<JsonNode> response = httpClient.sendRequest(
                listPath,
                "SCAN",
                null);

        if (!RestClient.isSuccessfulResponse(response)) {
            logger.errorv(
                    "Failed to list keys. Path: {0} Response code: {1} body: {2}",
                    listPath, response.statusCode(), response.body());
            throw new BaoClientException("Failed to list keys from path '" + secretPathPrefix + "': HTTP "
                    + response.statusCode() + " Body: '" + response.body() + "'", response.statusCode());
        }

        JsonNode keysNode = response.body().path("data").path("keys");
        return fromJsonNodeToListString(keysNode);
    }

    /**
     * Retrieves a secret from KVv2 store.
     *
     * @param kvMountPath The mount path of the KV store.
     * @param secretPath  The full path to the secret (e.g. "my-app/secret").
     * @return A map representing the secret data.
     * @throws BaoClientException if the operation fails.
     */
    public Map<String, String> kv2Get(String kvMountPath, String secretPath) {
        HttpResponse<JsonNode> response = httpClient.sendRequest(
                "v1/" + kvMountPath + "/data/" + secretPath,
                "GET",
                null);

        if (!RestClient.isSuccessfulResponse(response)) {
            logger.errorv(
                    "Failed to read data. Response code: {0} body: {1}",
                    response.statusCode(),
                    response.body());
            throw new BaoClientException(
                    "Failed to read data from path '" + secretPath + "': '" + response.body() + "'",
                    response.statusCode());
        }

        JsonNode rootNode = response.body();
        if (!rootNode.has("data") ||
                !rootNode.path("data").has("data")) {

            logger.errorv("Secret not found at path {1}", secretPath);
            throw new BaoClientException("Secret not found at path '" + secretPath + "'", response.statusCode());
        }

        return fromJsonNodeToMapStringString(rootNode.path("data").path("data"));
    }

    /**
     * Insert or update a secret in KVv2 store.
     *
     * @param kvMountPath The mount path of the KV store.
     * @param secretPath  The full path to the secret (e.g. "my-app/secret").
     * @param data        The key-value pairs to store in the secret.
     * @return This BaoClient instance for method chaining.
     * @throws BaoClientException if the operation fails.
     */
    public BaoClient kv2Upsert(String kvMountPath, String secretPath, Map<String, String> data) {
        write(kvMountPath + "/data/" + secretPath, Map.of("data", toJsonString(data)));
        return this;
    }

    // Helper methods for JSON conversion.

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

    private List<String> fromJsonNodeToListString(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return List.of(); // Return empty list if the node is null or not an array.
        }
        return StreamSupport.stream(arrayNode.spliterator(), false)
                .map(JsonNode::asText)
                .toList();
    }

    private Map<String, String> fromJsonNodeToMapStringString(JsonNode objectNode) {
        Map<String, String> map = new HashMap<>();
        if (objectNode == null || !objectNode.isObject()) {
            return map; // Return empty map if the node is null or not an object.
        }
        objectNode.properties().forEach(entry -> {
            String key = entry.getKey();
            String value = entry.getValue().asText();
            map.put(key, value);
        });
        return map;
    }

    /**
     * Exception class for handling client errors.
     */
    public static class BaoClientException extends RuntimeException {
        private final int statusCode;

        public BaoClientException(String message) {
            super(message);
            this.statusCode = -1;
        }

        public BaoClientException(String message, Throwable cause) {
            super(message, cause);
            this.statusCode = -1;
        }

        public BaoClientException(String message, int statusCode) {
            super(message);
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }
}
