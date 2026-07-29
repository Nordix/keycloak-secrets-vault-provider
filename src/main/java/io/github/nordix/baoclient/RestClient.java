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
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Builder;
import java.net.http.HttpConnectTimeoutException;
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

    // Default connection settings. The connect (3s) and request (10s) timeouts are
    // fast-fail defaults matching the previous hard-coded behaviour; on their own they do
    // not ride out a transient backend outage. Resilience against a transient connection
    // failure (for example while the backend is briefly unavailable during a restart or
    // failover) is provided by the optional retry, which is OFF by default (maxRetries = 0)
    // so the client's behaviour is unchanged unless the caller opts in via withRetry(...).
    // All values can be overridden per-instance via the with* setters below.
    private static final Duration DEFAULT_CONNECTION_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final int DEFAULT_MAX_RETRIES = 0;
    private static final Duration DEFAULT_RETRY_BACKOFF = Duration.ofSeconds(2);

    private static final String CONTENT_TYPE_JSON = "application/json";

    private final URI baseUrl;
    private String caCertificateFile;
    private Map<String, String> headers = new java.util.HashMap<>();

    private Duration connectionTimeout = DEFAULT_CONNECTION_TIMEOUT;
    private Duration requestTimeout = DEFAULT_REQUEST_TIMEOUT;
    private int maxRetries = DEFAULT_MAX_RETRIES;
    private Duration retryBackoff = DEFAULT_RETRY_BACKOFF;

    public RestClient(URI baseUrl) {
        this.baseUrl = baseUrl;
    }

    public HttpResponse<JsonNode> sendRequest(String endpoint, String method, String body) {
        Objects.requireNonNull(endpoint, "Endpoint must not be null");
        Objects.requireNonNull(method, "HTTP method must not be null");

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(baseUrl.resolve(endpoint))
                .timeout(requestTimeout)
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

        // Build the client once and reuse it across retry attempts.
        HttpClient client = getHttpClient();

        int attempt = 0;
        while (true) {
            try {
                return client.send(request, jsonBodyHandler());
            } catch (HttpConnectTimeoutException | ConnectException e) {
                // Connection-phase failures are caught separately from all other
                // IOExceptions on purpose, to draw a clear safety boundary for retries:
                //
                //  * A failure to establish the connection (connection refused or connect
                //    timeout) guarantees the request never reached the server. Re-sending
                //    it therefore cannot cause duplicate side effects, so it is safe to
                //    retry regardless of HTTP method - including non-idempotent writes.
                //
                //  * Any failure AFTER the connection is established (request timeout,
                //    other IOException, or an error status code) may mean the server
                //    already received and partially processed the request. Retrying those
                //    could double-apply a write, so they are intentionally NOT retried
                //    here and fall through to the generic handler below.
                //
                // This connection-phase failure is the mode seen when the backend is
                // briefly unavailable, e.g. during a restart or failover. Retry is opt-in
                // (maxRetries defaults to 0); when it is disabled this block simply rethrows
                // on the first failure, preserving the original fail-fast behaviour.
                if (attempt >= maxRetries) {
                    throw new RestClientException(String.format(
                            "Failed to send %s to %s after %d attempt(s): %s",
                            request.method(), request.uri(), attempt + 1,
                            e.getCause() != null ? e.getCause() : e), e);
                }
                attempt++;
                long backoffMs = retryBackoff.toMillis() * attempt;
                logger.warnv(
                        "Connection to {0} failed (attempt {1}/{2}): {3}. Retrying in {4} ms.",
                        request.uri(), attempt, maxRetries, e.toString(), backoffMs);
                sleepBeforeRetry(backoffMs, request.uri().toString());
            } catch (IOException e) {
                throw new RestClientException(String.format("Failed to send %s to %s: %s",
                        request.method(), request.uri(), e.getCause() != null ? e.getCause() : e), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RestClientException(String.format("Request to %s was interrupted: %s",
                        request.uri(), e.getMessage()), e);
            }
        }
    }

    private void sleepBeforeRetry(long backoffMs, String uri) {
        try {
            Thread.sleep(backoffMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RestClientException("Retry backoff for " + uri + " was interrupted", ie);
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

    public RestClient withConnectTimeout(Duration connectionTimeout) {
        Objects.requireNonNull(connectionTimeout, "Connection timeout must not be null");
        this.connectionTimeout = connectionTimeout;
        return this;
    }

    public RestClient withRequestTimeout(Duration requestTimeout) {
        Objects.requireNonNull(requestTimeout, "Request timeout must not be null");
        this.requestTimeout = requestTimeout;
        return this;
    }

    public RestClient withRetry(int maxRetries, Duration retryBackoff) {
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must not be negative");
        }
        Objects.requireNonNull(retryBackoff, "Retry backoff must not be null");
        this.maxRetries = maxRetries;
        this.retryBackoff = retryBackoff;
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

        clientBuilder.connectTimeout(connectionTimeout);
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
