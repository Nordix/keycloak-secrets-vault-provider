/**
 * Copyright (c) 2025 OpenInfra Foundation Europe and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License, Version 2.0
 * which accompanies this distribution, and is available at
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.nordix.junit;

import java.net.URI;
import java.net.http.HttpResponse;

import org.jboss.logging.Logger;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.nordix.baoclient.RestClient;

public class KeycloakRestClientExtension extends RestClient implements BeforeEachCallback {

    private static Logger logger = Logger.getLogger(KeycloakRestClientExtension.class);

    private static final String CLIENT_ID = "admin-cli";
    private static final String DEFAULT_USERNAME = "admin";
    private static final String DEFAULT_PASSWORD = "admin";
    private static final String GRANT_TYPE = "password";
    private final String realm;
    private final String username;
    private final String password;

    /**
     * Creates a KeycloakRestClientExtension with the specified base URL and default admin credentials.
     *
     * @param baseUrl the base URL of the Keycloak server
     */
    public KeycloakRestClientExtension(String baseUrl) {
        this(baseUrl, "master", DEFAULT_USERNAME, DEFAULT_PASSWORD);
    }

    /**
     * Creates a KeycloakRestClientExtension with the specified base URL and credentials.
     *
     * @param baseUrl the base URL of the Keycloak server
     * @param username the username to use for authentication
     * @param password the password to use for authentication
     */
    public KeycloakRestClientExtension(String baseUrl, String realm, String username, String password) {
        super(URI.create(baseUrl));
        this.realm = realm;
        this.username = username;
        this.password = password;
    }

    public void login() {
        try {
            String body = "client_id=" + CLIENT_ID + "&username=" + username + "&password=" + password + "&grant_type="
                    + GRANT_TYPE;
            HttpResponse<JsonNode> resp = this
                    .withHeader("Content-Type", "application/x-www-form-urlencoded")
                    .sendRequest("/realms/" + realm + "/protocol/openid-connect/token", "POST", body);
            if (resp.statusCode() != 200) {
                throw new RuntimeException("Failed to get token: " + resp.body());
            }
            this.removeAllHeaders();
            String token = resp.body().get("access_token").asText();
            this.withHeader("Authorization", "Bearer " + token);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get token", e);
        }
    }

    /**
     * Polls until Keycloak returns 200 OK, or retry count exceeds.
     */
    @SuppressWarnings("java:S2925")  // Suppress sonarqube warning for Thread.sleep usage
    public void waitForReady() {
        int notReadyCount = 0;
        final int maxNotReady = 120;
        final int pollIntervalMillis = 1000;
        logger.infov("Waiting for Keycloak to be ready (max {0} times)", maxNotReady);
        while (true) {
            try {
                var resp = this.sendRequest("/realms/master", "GET");
                if (isSuccessfulResponse(resp)) {
                    break;
                }
                notReadyCount++;
            } catch (Exception e) {
                notReadyCount++;
            }
            if (notReadyCount >= maxNotReady) {
                throw new RuntimeException("/realms/master not returning 200 OK for " + maxNotReady + " consecutive times");
            }
            try {
                Thread.sleep(pollIntervalMillis);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Polling interrupted", ie);
            }
        }
    }

    public void beforeEach(ExtensionContext context) throws Exception {
        waitForReady();
        login();
    }

}
