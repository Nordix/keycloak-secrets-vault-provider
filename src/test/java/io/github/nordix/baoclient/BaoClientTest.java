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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.nordix.baoclient.RestClient.RestClientException;

class BaoClientTest {

    @TempDir
    Path tempDir;

    /**
     * Test connection refused.
     * Uses a free port that is not listening, causing ConnectException.
     */
    @Test
    void testConnectionRefused() throws Exception {
        Path tokenFile = tempDir.resolve("token");
        Files.writeString(tokenFile, "dummy-jwt-token");

        // Find a free port, then close it so nothing is listening on it.
        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }

        BaoClient client = new BaoClient(URI.create("http://127.0.0.1:" + port));

        long start = System.currentTimeMillis();
        RestClientException ex = Assertions.assertThrows(
                RestClientException.class,
                () -> client.loginWithKubernetes(tokenFile.toString(), "test-role"));
        long elapsed = System.currentTimeMillis() - start;

        Assertions.assertEquals(
                "Failed to send POST to http://127.0.0.1:" + port + "/v1/auth/kubernetes/login: java.net.ConnectException",
                ex.getMessage());
        Assertions.assertTrue(elapsed < 1000, "Expected connection refused immediately but took " + elapsed + "ms");
    }

    /**
     * Test connect timeout.
     * Uses a non-routable IP address so that SYN packets are silently dropped,
     * causing HttpConnectTimeoutException after CONNECTION_TIMEOUT.
     */
    @Test
    void testConnectTimeout() throws Exception {
        Path tokenFile = tempDir.resolve("token");
        Files.writeString(tokenFile, "dummy-jwt-token");

        BaoClient client = new BaoClient(URI.create("http://192.0.2.1:8200"));

        long start = System.currentTimeMillis();
        RestClientException ex = Assertions.assertThrows(
                RestClientException.class,
                () -> client.loginWithKubernetes(tokenFile.toString(), "test-role"));
        long elapsed = System.currentTimeMillis() - start;

        Assertions.assertEquals(
                "Failed to send POST to http://192.0.2.1:8200/v1/auth/kubernetes/login: java.net.http.HttpConnectTimeoutException: HTTP connect timed out",
                ex.getMessage());
        Assertions.assertTrue(elapsed >= 3000 && elapsed < 6000,
                "Expected connect timeout after ~3s but took " + elapsed + "ms");
    }

    /**
     * Test request timeout.
     * Uses a server that accepts TCP connections but never responds,
     * causing HttpTimeoutException after REQUEST_TIMEOUT.
     */
    @Test
    void testRequestTimeout() throws Exception {
        Path tokenFile = tempDir.resolve("token");
        Files.writeString(tokenFile, "dummy-jwt-token");

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

            BaoClient client = new BaoClient(
                    URI.create("http://127.0.0.1:" + server.getLocalPort()));

            long start = System.currentTimeMillis();
            RestClientException ex = Assertions.assertThrows(
                    RestClientException.class,
                    () -> client.loginWithKubernetes(tokenFile.toString(), "test-role"));
            long elapsed = System.currentTimeMillis() - start;

            Assertions.assertEquals(
                    "Failed to send POST to http://127.0.0.1:" + server.getLocalPort() + "/v1/auth/kubernetes/login: java.net.http.HttpTimeoutException: request timed out",
                    ex.getMessage());
            Assertions.assertTrue(elapsed >= 10000 && elapsed < 13000,
                    "Expected request timeout after ~10s but took " + elapsed + "ms");
        }
    }

    /**
     * Test HTTP error status response (500).
     */
    @Test
    void testErrorStatusResponse() throws Exception {
        Path tokenFile = tempDir.resolve("token");
        Files.writeString(tokenFile, "dummy-jwt-token");

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
                    "Failed to log in to http://127.0.0.1:" + server.getLocalPort() + ". HTTP response code 500 body: " + responseBody,
                    ex.getMessage());
            Assertions.assertTrue(elapsed < 1000, "Expected error response immediately but took " + elapsed + "ms");
        }
    }
}
