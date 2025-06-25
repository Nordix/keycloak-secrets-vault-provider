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

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.nordix.baoclient.RestClient;

public class KeycloakClientExtension implements BeforeEachCallback {

    private static final String BASE_URL = "http://127.0.0.127:8080";
    private static final String CLIENT_ID = "admin-cli";
    private static final String USERNAME = "admin";
    private static final String PASSWORD = "admin";
    private static final String GRANT_TYPE = "password";

    private RestClient client;

    public KeycloakClientExtension() {
        client = new RestClient(URI.create(BASE_URL));
    }

    public void beforeEach(ExtensionContext context) throws Exception {

        try {
            String body = "client_id=" + CLIENT_ID + "&username=" + USERNAME + "&password=" + PASSWORD + "&grant_type="
                    + GRANT_TYPE;
            HttpResponse<JsonNode> resp = client
                    .withHeader("Content-Type", "application/x-www-form-urlencoded")
                    .sendRequest("/realms/master/protocol/openid-connect/token", "POST", body);
            if (resp.statusCode() != 200) {
                throw new RuntimeException("Failed to get admin token: " + resp.body());
            }
            client.removeAllHeaders();
            String token = resp.body().get("access_token").asText();
            client.withHeader("Authorization", "Bearer " + token);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get admin token", e);
        }
    }

    public RestClient getClient() {
        return client;
    }

}
