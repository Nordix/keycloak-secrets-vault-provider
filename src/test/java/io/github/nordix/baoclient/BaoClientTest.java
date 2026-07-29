/**
 * Copyright (c) 2026 OpenInfra Foundation Europe and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License, Version 2.0
 * which accompanies this distribution, and is available at
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.nordix.baoclient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.nordix.baoclient.RestClient.RestClientException;

class BaoClientTest {

    @TempDir
    Path tempDir;

    private Path writeTokenFile() throws IOException {
        Path tokenFile = tempDir.resolve("token");
        Files.writeString(tokenFile, "dummy-jwt-token");
        return tokenFile;
    }

    /**
     * Connection refused with retry disabled: the request is attempted exactly once
     * (ConnectException) and fails fast.
     */
    @Test
    void testConnectionRefusedNoRetry() throws Exception {
        Path tokenFile = writeTokenFile();

        // Find a free port, then close it so nothing is listening on it.
        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }

        BaoClient client = new BaoClient(URI.create("http://127.0.0.1:" + port))
                .withRetry(0, Duration.ZERO);

        long start = System.currentTimeMillis();
        RestClientException ex = Assertions.assertThrows(
                RestClientException.class,
                () -> client.loginWithKubernetes(tokenFile.toString(), "test-role"));
        long elapsed = System.currentTimeMillis() - start;

        Assertions.assertTrue(ex.getMessage().contains("after 1 attempt(s)"), ex.getMessage());
        Assertions.assertTrue(ex.getMessage().contains("java.net.ConnectException"), ex.getMessage());
        Assertions.assertTrue(elapsed < 1000, "Expected connection refused immediately but took " + elapsed + "ms");
    }

    /**
     * Retry is opt-in: with the default configuration (no withRetry call) the
     * client must not retry. A connection-refused failure is therefore attempted
     * exactly once, preserving the original fail-fast behaviour.
     */
    @Test
    void testDefaultConfigurationDoesNotRetry() throws Exception {
        Path tokenFile = writeTokenFile();

        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }

        // No withRetry(...) call -> rely on the default (retry disabled).
        BaoClient client = new BaoClient(URI.create("http://127.0.0.1:" + port));

        long start = System.currentTimeMillis();
        RestClientException ex = Assertions.assertThrows(
                RestClientException.class,
                () -> client.loginWithKubernetes(tokenFile.toString(), "test-role"));
        long elapsed = System.currentTimeMillis() - start;

        // Default retry-max is 0 -> single attempt, no backoff.
        Assertions.assertTrue(ex.getMessage().contains("after 1 attempt(s)"), ex.getMessage());
        Assertions.assertTrue(ex.getMessage().contains("java.net.ConnectException"), ex.getMessage());
        Assertions.assertTrue(elapsed < 1000, "Expected a single fail-fast attempt but took " + elapsed + "ms");
    }

    /**
     * Connection refused with retry enabled: connect-phase failures are retried
     * up to maxRetries times (so maxRetries + 1 total attempts), applying backoff
     * between attempts, before finally failing.
     */
    @Test
    void testConnectionRefusedExhaustsRetries() throws Exception {
        Path tokenFile = writeTokenFile();

        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }

        BaoClient client = new BaoClient(URI.create("http://127.0.0.1:" + port))
                .withRetry(2, Duration.ofMillis(100));

        long start = System.currentTimeMillis();
        RestClientException ex = Assertions.assertThrows(
                RestClientException.class,
                () -> client.loginWithKubernetes(tokenFile.toString(), "test-role"));
        long elapsed = System.currentTimeMillis() - start;

        // 2 retries -> 3 total attempts.
        Assertions.assertTrue(ex.getMessage().contains("after 3 attempt(s)"), ex.getMessage());
        Assertions.assertTrue(ex.getMessage().contains("java.net.ConnectException"), ex.getMessage());
        // Backoff is scaled per attempt: 100ms + 200ms = 300ms minimum spent sleeping.
        Assertions.assertTrue(elapsed >= 300,
                "Expected at least 300ms of retry backoff but took only " + elapsed + "ms");
    }

    /**
     * Connect-phase failure that recovers: the first attempts are refused because
     * nothing is listening yet, then the server comes up and a subsequent retry
     * succeeds. Verifies that retry actually recovers from a transient outage
     * (the transient backend unavailability scenario, e.g. a restart or failover).
     */
    @Test
    void testRetriesThenSucceedsWhenServerComesUp() throws Exception {
        Path tokenFile = writeTokenFile();

        // Reserve a port, then free it so the initial connect attempts are refused.
        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }

        String responseBody = "{\"auth\":{\"client_token\":\"test-token\"}}";
        String httpResponse = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: application/json\r\n"
                + "Content-Length: " + responseBody.length() + "\r\n"
                + "\r\n"
                + responseBody;

        // Start the server only after a short delay so the first connect attempt(s) fail.
        Thread server = new Thread(() -> {
            try {
                Thread.sleep(300);
                try (ServerSocket ss = new ServerSocket(port);
                        Socket conn = ss.accept()) {
                    conn.getInputStream().readNBytes(1);
                    OutputStream out = conn.getOutputStream();
                    out.write(httpResponse.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }
            } catch (IOException | InterruptedException ignored) {
                // Test will fail via the assertion below if the server never serves.
            }
        });
        server.setDaemon(true);
        server.start();

        BaoClient client = new BaoClient(URI.create("http://127.0.0.1:" + port))
                .withConnectTimeout(Duration.ofMillis(500))
                .withRetry(10, Duration.ofMillis(150));

        // Should recover once the server is up; no exception is thrown.
        Assertions.assertDoesNotThrow(
                () -> client.loginWithKubernetes(tokenFile.toString(), "test-role"));

        server.join(5000);
    }

    /**
     * Connect timeout: uses a non-routable IP address so SYN packets are silently
     * dropped, causing HttpConnectTimeoutException. With retry disabled it fails
     * after a single attempt once the (shortened) connect timeout elapses.
     */
    @Test
    void testConnectTimeoutNoRetry() throws Exception {
        Path tokenFile = writeTokenFile();

        BaoClient client = new BaoClient(URI.create("http://192.0.2.1:8200"))
                .withConnectTimeout(Duration.ofSeconds(2))
                .withRetry(0, Duration.ZERO);

        long start = System.currentTimeMillis();
        RestClientException ex = Assertions.assertThrows(
                RestClientException.class,
                () -> client.loginWithKubernetes(tokenFile.toString(), "test-role"));
        long elapsed = System.currentTimeMillis() - start;

        Assertions.assertTrue(ex.getMessage().contains("after 1 attempt(s)"), ex.getMessage());
        Assertions.assertTrue(ex.getMessage().contains("HttpConnectTimeoutException"), ex.getMessage());
        Assertions.assertTrue(elapsed >= 2000 && elapsed < 5000,
                "Expected connect timeout after ~2s but took " + elapsed + "ms");
    }

    /**
     * Request timeout: a server accepts the TCP connection but never responds. This
     * is a post-connection failure (HttpTimeoutException), which must NOT be retried
     * because the request may already have reached the server.
     */
    @Test
    void testRequestTimeoutIsNotRetried() throws Exception {
        Path tokenFile = writeTokenFile();

        try (ServerSocket server = new ServerSocket(0)) {
            Thread acceptor = new Thread(() -> {
                while (!server.isClosed()) {
                    try {
                        server.accept(); // Accept but never write anything.
                    } catch (IOException ignored) {
                        // Expected once the server socket is closed at the end of the test.
                    }
                }
            });
            acceptor.setDaemon(true);
            acceptor.start();

            BaoClient client = new BaoClient(URI.create("http://127.0.0.1:" + server.getLocalPort()))
                    .withRequestTimeout(Duration.ofSeconds(2))
                    .withRetry(3, Duration.ofMillis(100));

            long start = System.currentTimeMillis();
            RestClientException ex = Assertions.assertThrows(
                    RestClientException.class,
                    () -> client.loginWithKubernetes(tokenFile.toString(), "test-role"));
            long elapsed = System.currentTimeMillis() - start;

            Assertions.assertTrue(ex.getMessage().contains("HttpTimeoutException"), ex.getMessage());
            // Not a connect-phase failure -> no retry -> message must not mention attempts.
            Assertions.assertFalse(ex.getMessage().contains("attempt(s)"), ex.getMessage());
            // A single ~2s request timeout, not multiplied by retries.
            Assertions.assertTrue(elapsed >= 2000 && elapsed < 5000,
                    "Expected a single request timeout after ~2s but took " + elapsed + "ms");
        }
    }

    /**
     * HTTP error status response (500) after a successful connection is a response,
     * not a connect-phase failure, so it must not be retried and surfaces as a
     * BaoClientException carrying the status code.
     */
    @Test
    void testErrorStatusResponse() throws Exception {
        Path tokenFile = writeTokenFile();

        String responseBody = "{\"errors\":[\"permission denied\"]}";
        String httpResponse = "HTTP/1.1 500 Internal Server Error\r\n"
                + "Content-Type: application/json\r\n"
                + "Content-Length: " + responseBody.length() + "\r\n"
                + "\r\n"
                + responseBody;

        try (ServerSocket server = new ServerSocket(0)) {
            Thread responder = new Thread(() -> {
                while (!server.isClosed()) {
                    try (Socket conn = server.accept()) {
                        conn.getInputStream().readNBytes(1);
                        OutputStream out = conn.getOutputStream();
                        out.write(httpResponse.getBytes(StandardCharsets.UTF_8));
                        out.flush();
                    } catch (IOException ignored) {
                        // Expected once the server socket is closed at the end of the test.
                    }
                }
            });
            responder.setDaemon(true);
            responder.start();

            BaoClient client = new BaoClient(URI.create("http://127.0.0.1:" + server.getLocalPort()));

            long start = System.currentTimeMillis();
            BaoClient.BaoClientException ex = Assertions.assertThrows(
                    BaoClient.BaoClientException.class,
                    () -> client.loginWithKubernetes(tokenFile.toString(), "test-role"));
            long elapsed = System.currentTimeMillis() - start;

            Assertions.assertEquals(500, ex.getStatusCode());
            Assertions.assertEquals(
                    "Failed to log in to http://127.0.0.1:" + server.getLocalPort()
                            + ". HTTP response code 500 body: " + responseBody,
                    ex.getMessage());
            Assertions.assertTrue(elapsed < 1000, "Expected error response immediately but took " + elapsed + "ms");
        }
    }
}
