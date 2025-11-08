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
import java.net.http.HttpClient;
import java.net.http.HttpClient.Builder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodySubscribers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class RestClient {

    private static Logger logger = Logger.getLogger(RestClient.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final String CONTENT_TYPE_JSON = "application/json";

    private final URI baseUrl;
    private String caCertificateFile;
    private Map<String, String> headers = new java.util.HashMap<>();

    public RestClient(URI baseUrl) {
        this.baseUrl = baseUrl;
    }

    public HttpResponse<JsonNode> sendRequest(String endpoint, String method, String body) {
        Objects.requireNonNull(endpoint, "Endpoint must not be null");
        Objects.requireNonNull(method, "HTTP method must not be null");

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(baseUrl.resolve(endpoint))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", CONTENT_TYPE_JSON);

        headers.forEach(requestBuilder::header);

        HttpRequest.BodyPublisher publisher;

        if ("GET".equalsIgnoreCase(method)) {
            publisher = HttpRequest.BodyPublishers.noBody();
        } else {
            publisher = HttpRequest.BodyPublishers.ofString(body != null ? body : "");
        }

        requestBuilder.method(method, publisher);

        HttpRequest request = requestBuilder.build();
        logger.debugv("Sending {0} request to {1}", method, request.uri());

        try {
            return getHttpClient().send(request, jsonBodyHandler());
        } catch (IOException e) {
            throw new RestClientException(String.format("Failed to send %s to %s: %s",
                    request.method(), request.uri(), e.getCause()), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RestClientException(String.format("Request to %s was interrupted: %s",
                    request.uri(), e.getMessage()), e);
        }
    }

    public HttpResponse<JsonNode> sendRequest(String endpoint, String method, Map<String, Object> body) {
        String bodyString = null;
        if (body != null) {
            try {
                bodyString = OBJECT_MAPPER.writeValueAsString(body);
            } catch (IOException e) {
                throw new RestClientException("Failed to serialize body to JSON", e);
            }
        }
        return sendRequest(endpoint, method, bodyString);
    }

    public HttpResponse<JsonNode> sendRequest(String endpoint, String method, List<Map<String, Object>> body) {
        String bodyString = null;
        if (body != null) {
            try {
                bodyString = OBJECT_MAPPER.writeValueAsString(body);
            } catch (IOException e) {
                throw new RestClientException("Failed to serialize body to JSON", e);
            }
        }
        return sendRequest(endpoint, method, bodyString);
    }

    public HttpResponse<JsonNode> sendRequest(String endpoint, String method) {
        return sendRequest(endpoint, method, (String) null);
    }

    public RestClient withHeader(String key, String value) {
        Objects.requireNonNull(key, "Header key must not be null");
        Objects.requireNonNull(value, "Header value must not be null");
        headers.put(key, value);
        return this;
    }

    public RestClient removeAllHeaders() {
        headers.clear();
        return this;
    }

    public RestClient withCaCertificateFile(String caCertificateFile) {
        Objects.requireNonNull(caCertificateFile, "CA certificate file must not be null");
        if (!Files.exists(Paths.get(caCertificateFile))) {
            throw new IllegalArgumentException("CA certificate file does not exist: " + caCertificateFile);
        }
        this.caCertificateFile = caCertificateFile;
        return this;
    }

    public URI getBaseUrl() {
        return baseUrl;
    }

    public static boolean isSuccessfulResponse(HttpResponse<?> response) {
        return response.statusCode() / 100 == 2;
    }

    public static boolean isErrorResponse(HttpResponse<?> response) {
        return response.statusCode() / 100 == 4 || response.statusCode() / 100 == 5;
    }

    private HttpClient getHttpClient() {
        Builder clientBuilder = HttpClient.newBuilder();

        clientBuilder.connectTimeout(CONNECTION_TIMEOUT);
        clientBuilder.followRedirects(HttpClient.Redirect.NORMAL);

        if (caCertificateFile != null) {
            try {
                String caPem = new String(Files.readAllBytes(Paths.get(caCertificateFile)));
                KeyStore trustStore = PemUtils.createTrustStoreFromPem(caPem);

                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(trustStore);

                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, tmf.getTrustManagers(), null);

                clientBuilder.sslContext(sslContext);
            } catch (IOException | GeneralSecurityException e) {
                throw new RestClientException(String.format("Failed to load CA certificate from '%s': %s",
                        caCertificateFile, e.getMessage()), e);
            }
        }

        return clientBuilder.build();
    }

    private static BodyHandler<JsonNode> jsonBodyHandler() {
        return responseInfo -> {
            int statusCode = responseInfo.statusCode();
            String contentType = responseInfo.headers().firstValue("Content-Type").orElse("<none>").toLowerCase();

            return BodySubscribers.mapping(
                    BodySubscribers.ofString(StandardCharsets.UTF_8),
                    body -> {
                        if (contentType.contains(CONTENT_TYPE_JSON)) {
                            try {
                                return OBJECT_MAPPER.readTree(body);
                            } catch (IOException e) {
                                throw new RestClientException(
                                        String.format(
                                                "Failed to parse JSON response: HTTP %d, Content-Type: %s, Body: '%s', Error: %s",
                                                statusCode, contentType, truncateBody(body), e.getMessage()));
                            }
                        }

                        // For successful 2xx responses without JSON content-type, return empty object.
                        if (statusCode / 100 == 2) {
                            return OBJECT_MAPPER.createObjectNode();
                        }

                        // For other responses, check if body is empty and return empty object.
                        if (body == null || body.trim().isEmpty()) {
                            return OBJECT_MAPPER.createObjectNode();
                        }

                        // For non-2xx responses with non-JSON content-type and non-empty body, throw exception.
                        throw new RestClientException(
                                String.format(
                                        "Unexpected response: HTTP %d, Content-Type: %s (expected %s), Body: '%s'",
                                        statusCode, contentType, CONTENT_TYPE_JSON, truncateBody(body)));
                    });
        };
    }

    private static String truncateBody(String body) {
        int maxLength = 200;
        if (body == null) {
            return "<null>";
        }
        if (body.isEmpty()) {
            return "<empty>";
        }
        if (body.length() <= maxLength) {
            return body;
        }
        return body.substring(0, maxLength) + "... (truncated, total length: " + body.length() + ")";
    }

    public static class RestClientException extends RuntimeException {
        public RestClientException(String message) {
            super(message);
        }

        public RestClientException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
