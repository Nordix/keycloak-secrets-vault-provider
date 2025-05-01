/**
 * Copyright (c) 2025 OpenInfra Foundation Europe and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License, Version 2.0
 * which accompanies this distribution, and is available at
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.nordix.kubernetes;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

import org.jboss.logging.Logger;


class KubernetesApiServerStub {

    private static final Logger logger = Logger.getLogger(KubernetesApiServerStub.class);

    public static void startServer(KeyStore keyStore) throws Exception {
            // Create SSLContext for the HTTPS server.
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, null);
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, null);

        // Create HTTPS server.
        HttpsServer server = HttpsServer.create(new InetSocketAddress(8443), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
            @Override
            public void configure(HttpsParameters params) {
                try {
                    // Set up the SSL parameters
                    SSLContext context = getSSLContext();
                    SSLEngine engine = context.createSSLEngine();
                    params.setNeedClientAuth(false);
                    params.setCipherSuites(engine.getEnabledCipherSuites());
                    params.setProtocols(engine.getEnabledProtocols());
                    params.setSSLParameters(context.getDefaultSSLParameters());
                } catch (Exception ex) {
                    logger.error("Failed to configure HTTPS", ex);
                    throw new RuntimeException("Failed to configure HTTPS", ex);
                }
            }
        });

        // Handler for simulated token reviews.
        server.createContext("/apis/authentication.k8s.io/v1/tokenreviews", new TokenReviewHandler());

        logger.info("Kubernetes API server stub is running on :8443");
        server.start();
        Thread.sleep(1000);
    }

    static class TokenReviewHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws java.io.IOException {
            logger.debugv("Received request: {0} {1}", exchange.getRequestMethod(), exchange.getRequestURI());
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())
                    && "/apis/authentication.k8s.io/v1/tokenreviews".equals(exchange.getRequestURI().getPath())) {

                String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                logger.debugv("Request body: {0}", requestBody);

                // Simulate token validation
                String response;
                if (requestBody.contains("\"token\":\"valid-token\"")) {
                    logger.debug("Token is valid, returning authenticated response.");
                    response = """
                            {
                              "apiVersion": "authentication.k8s.io/v1",
                              "kind": "TokenReview",
                              "status": {
                                "authenticated": true,
                                "user": {
                                  "username": "system:serviceaccount:default:keycloak",
                                  "uid": "12345",
                                  "groups": ["system:serviceaccounts", "system:serviceaccounts:default"]
                                }
                              }
                            }
                            """;
                } else {
                    logger.debug("Token is invalid, returning unauthenticated response.");
                    response = """
                            {
                              "apiVersion": "authentication.k8s.io/v1",
                              "kind": "TokenReview",
                              "status": {
                                "authenticated": false
                              }
                            }
                            """;
                }

                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes(StandardCharsets.UTF_8));
                }
                logger.debug("Response sent.");
            } else {
                logger.warnv("Unsupported request: {0} {1}", exchange.getRequestMethod(), exchange.getRequestURI());
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }
}
