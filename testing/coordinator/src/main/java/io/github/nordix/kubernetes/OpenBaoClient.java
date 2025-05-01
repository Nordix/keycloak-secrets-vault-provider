/**
 * Copyright (c) 2025 OpenInfra Foundation Europe and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License, Version 2.0
 * which accompanies this distribution, and is available at
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.nordix.kubernetes;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Objects;

import org.jboss.logging.Logger;

public class OpenBaoClient {

    private static final Logger logger = Logger.getLogger(OpenBaoClient.class);
    private final String baseUrl;
    private final HttpClient httpClient;
    private String token;

    public OpenBaoClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newHttpClient();
    }

    private HttpResponse<String> sendRequest(String endpoint, String method, String body) {
        Objects.requireNonNull(token, "Token must be set before sending requests");
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + endpoint))
                    .header("Content-Type", "application/json")
                    .header("X-Vault-Token", token);

            if ("POST".equalsIgnoreCase(method)) {
                requestBuilder.POST(HttpRequest.BodyPublishers.ofString(body));
            } else if ("GET".equalsIgnoreCase(method)) {
                requestBuilder.GET();
            }

            HttpRequest request = requestBuilder.build();
            logger.infov("Sending {0} request to {1}", method, endpoint);
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException | IOException e) {
            logger.errorv("Failed to send HTTP request to {0}", endpoint, e);
            throw new RuntimeException(e);
        }
    }

    private boolean isSuccessfulResponse(HttpResponse<?> response) {
        int statusCode = response.statusCode();
        return statusCode >= 200 && statusCode < 300;
    }

    public boolean isReady() {
        try {
            HttpResponse<String> response = sendRequest("/v1/sys/health", "GET", null);
            return isSuccessfulResponse(response);
        } catch (Exception e) {
            logger.errorv("Failed to check readiness", e);
            return false;
        }
    }

    public void token(String token) {
        this.token = token;
    }

    public void enableKubernetesAuth() {
        HttpResponse<String> response = sendRequest("/v1/sys/auth/kubernetes", "POST", "{\"type\":\"kubernetes\"}");
        if (!isSuccessfulResponse(response)) {
            throw new RuntimeException("Failed to enable Kubernetes auth: " + response.body());
        }
        logger.info("Successfully enabled Kubernetes auth.");
    }

    public void configureKubernetesAuth(String kubernetesHost) {
        String body = String.format("{\"kubernetes_host\":\"%s\"}", kubernetesHost);
        HttpResponse<String> response = sendRequest("/v1/auth/kubernetes/config", "POST", body);
        if (!isSuccessfulResponse(response)) {
            throw new RuntimeException("Failed to configure Kubernetes auth: " + response.body());
        }
        logger.info("Successfully configured Kubernetes auth.");
    }

    public void enableAuditToStdout() {
        HttpResponse<String> response = sendRequest("/v1/sys/audit/stdout", "POST", "{\"type\":\"file\", \"options\":{\"file_path\":\"stdout\"}}");
        if (!isSuccessfulResponse(response)) {
            throw new RuntimeException("Failed to enable audit to stdout: " + response.body());
        }
        logger.info("Successfully enabled audit to stdout.");
    }

    public void writeKv2(String key, Map<String, Object> data) {
        try {
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
            String body = bodyBuilder.toString();

            HttpResponse<String> response = sendRequest("/v1/secret/data/" + key, "POST", body);
            if (!isSuccessfulResponse(response)) {
                throw new RuntimeException("Failed to write secret: " + response.body());
            }
            logger.infov("Successfully wrote secret: {0}", key);
        } catch (Exception e) {
            logger.errorv("Failed to write secret", e);
            throw new RuntimeException(e);
        }
    }
}
