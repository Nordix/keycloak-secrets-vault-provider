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
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Builder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodySubscribers;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.GeneralSecurityException;
import java.util.Objects;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class RestClient {

    private static Logger logger = Logger.getLogger(RestClient.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final URI baseUrl;
    private String token;
    private String caCertificateFile;

    public RestClient(URI baseUrl) {
        this.baseUrl = baseUrl;
    }

    public HttpResponse<JsonNode> sendRequest(String endpoint, String method, String body) {
        Objects.requireNonNull(token, "Token must be set before sending requests");
        Objects.requireNonNull(endpoint, "Endpoint must not be null");
        Objects.requireNonNull(method, "HTTP method must not be null");

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(baseUrl.resolve(endpoint))
                .header("Content-Type", "application/json")
                .header("X-Vault-Token", token);

        if ("POST".equalsIgnoreCase(method)) {
            Objects.requireNonNull(body, "Body must not be null for POST requests");
            requestBuilder.POST(HttpRequest.BodyPublishers.ofString(body));
        } else if ("GET".equalsIgnoreCase(method)) {
            requestBuilder.GET();
        }

        HttpRequest request = requestBuilder.build();
        logger.debugv("Sending {0} request to {1}", method, request.uri());

        try {
            return getHttpClient().send(request, jsonBodyHandler());
        } catch (IOException e) {
            logger.errorv("Failed to send HTTP request to {0}", endpoint, e);
            throw new RestClientException("Failed to send request", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.errorv("Request to {0} was interrupted", endpoint, e);
            throw new RestClientException("Request was interrupted", e);
        }
    }

    public RestClient withToken(String token) {
        this.token = token;
        return this;
    }

    public RestClient withCaCertificateFile(String caCertificateFile) {
        Objects.requireNonNull(caCertificateFile, "CA certificate file must not be null");
        if (!Files.exists(Paths.get(caCertificateFile))) {
            String errorMessage = "CA certificate file does not exist: " + caCertificateFile;
            logger.error(errorMessage);
            throw new IllegalArgumentException(errorMessage);
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

    private HttpClient getHttpClient() {
        Builder clientBuilder = HttpClient.newBuilder();

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
                String errorMessage = "Error loading CA certificate: " + e.getMessage();
                logger.error(errorMessage, e);
                throw new RestClientException(errorMessage, e);
            }
        }

        return clientBuilder.build();
    }

    private static BodyHandler<JsonNode> jsonBodyHandler() {
        return responseInfo -> BodySubscribers.mapping(
                BodySubscribers.ofString(java.nio.charset.StandardCharsets.UTF_8),
                body -> {
                    try {
                        return OBJECT_MAPPER.readTree(body);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
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
