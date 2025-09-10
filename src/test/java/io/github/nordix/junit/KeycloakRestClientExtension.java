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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.logging.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

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
     * Create a Keycloak ADMIN REST client with the specified base URL and default
     * admin credentials.
     *
     * @param baseUrl the base URL of the Keycloak server
     */
    public KeycloakRestClientExtension(String baseUrl) {
        this(baseUrl, "master", DEFAULT_USERNAME, DEFAULT_PASSWORD);
    }

    /**
     * Create a Keycloak ADMIN REST client with given base URL and credentials.
     *
     * @param baseUrl  the base URL of the Keycloak server
     * @param username the username to use for authentication
     * @param password the password to use for authentication
     */
    public KeycloakRestClientExtension(String baseUrl, String realm, String username, String password) {
        super(URI.create(baseUrl));
        this.realm = realm;
        this.username = username;
        this.password = password;
    }

    /**
     * Login to Keycloak and set the Authorization header for subsequent requests.
     *
     * @return the KeycloakRestClientExtension instance
     */
    public KeycloakRestClientExtension login() {
        try {
            String body = "client_id=" + CLIENT_ID + "&username=" + username + "&password=" + password + "&grant_type="
                    + GRANT_TYPE;
            HttpResponse<JsonNode> resp = this
                    .withHeader("Content-Type", "application/x-www-form-urlencoded")
                    .sendRequest("/realms/" + realm + "/protocol/openid-connect/token", "POST", body);
            if (RestClient.isErrorResponse(resp)) {
                throw new RuntimeException("Failed to get token: " + resp.body());
            }
            this.removeAllHeaders();
            String token = resp.body().get("access_token").asText();
            this.withHeader("Authorization", "Bearer " + token);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get token", e);
        }
        return this;
    }

    /**
     * Poll until Keycloak returns 200 OK, or retry count exceeds.
     */
    @SuppressWarnings("java:S2925") // Suppress sonarqube warning for Thread.sleep usage.
    public void waitForReady() {
        int notReadyCount = 0;
        final int maxNotReady = 120;
        final int pollIntervalMillis = 1000;
        logger.infov("Waiting for Keycloak to be ready (max {0} times)", maxNotReady);
        while (true) {
            try {
                HttpResponse<JsonNode> resp = this.sendRequest("/realms/master", "GET");
                if (isSuccessfulResponse(resp)) {
                    break;
                }
                notReadyCount++;
            } catch (Exception e) {
                notReadyCount++;
            }
            if (notReadyCount >= maxNotReady) {
                throw new RuntimeException(
                        "/realms/master not returning 200 OK for " + maxNotReady + " consecutive times");
            }
            try {
                Thread.sleep(pollIntervalMillis);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Polling interrupted", ie);
            }
        }
    }

    /**
     * Create a new realm.
     *
     * @param realmName the name of the realm to create
     */
    public void createRealm(String realmName) {
        logger.infov("Creating realm: {0}", realmName);
        HttpResponse<JsonNode> resp = sendRequest("/admin/realms", "POST", Map.of(
                "realm", realmName,
                "enabled", true));
        Assertions.assertTrue(RestClient.isSuccessfulResponse(resp), "Failed to create realm: " + realmName + " " + resp.body());
    }

    /**
     * Delete a realm.
     *
     * @param realmName the name of the realm to delete
     */
    public void deleteRealm(String realmName) {
        logger.infov("Deleting realm: {0}", realmName);
        HttpResponse<JsonNode> resp = sendRequest("/admin/realms/" + realmName, "DELETE");
        Assertions.assertTrue(RestClient.isSuccessfulResponse(resp), "Failed to delete realm: " + realmName + " " + resp.body());
    }

    /**
     * Create a new client.
     *
     * @param realmName    the name of the realm to create the client in
     * @param clientName   the name of the client to create
     * @param clientSecret the secret of the client to create
     */
    public void createClient(String realmName, String clientName, String clientSecret, List<String> baseUrls) {
        logger.infov("Creating client: {0} in realm: {1}", clientName, realmName);
        HttpResponse<JsonNode> resp = sendRequest("/admin/realms/" + realmName + "/clients", "POST", Map.of(
                "clientId", clientName,
                "enabled", true,
                "protocol", "openid-connect",
                "publicClient", false,
                "serviceAccountsEnabled", true,
                "redirectUris", baseUrls.stream().map(url -> url + "/*").toList(),
                "webOrigins", baseUrls.stream().map(url -> url + "/*").toList(),
                "secret", clientSecret));
        Assertions.assertTrue(RestClient.isSuccessfulResponse(resp), "Failed to create client: " + clientName + " " + resp.body());
    }

    /**
     * Create a new user.
     *
     * @param realmName    the name of the realm to create the user in
     * @param userName     the name of the user to create
     * @param userPassword the password of the user to create
     * @param clientRoles  a map of client roles to assign to the user
     *
     * clientRoles looks like this:
     * {
     *   "client-id-1": ["role-1", "role-2"],
     *   "client-id-2": ["role-3"]
     * }
     */
    public void createUser(String realmName, String userName, String userPassword) {
        logger.infov("Creating user: {0} in realm: {1}", userName, realmName);

        HttpResponse<JsonNode> resp = sendRequest("/admin/realms/" + realmName + "/users", "POST", Map.of(
                "username", userName,
                "enabled", true,
                "email", "email@example.com",
                "firstName", "First",
                "lastName", "Last",
                "credentials", List.of(Map.of(
                        "type", "password",
                        "value", userPassword,
                        "temporary", false))));
        Assertions.assertTrue(RestClient.isSuccessfulResponse(resp),
                "Failed to create user: " + userName + " " + resp.body());
    }

    /**
     * Update a client attribute.
     *
     * @param realmName      the name of the realm
     * @param clientName     the name of the client
     * @param parameterName  the name of the attribute to update
     * @param parameterValue the new value of the attribute
     */
    public void updateClientAttribute(String realmName, String clientName, String parameterName,
            String parameterValue) {
        logger.infov("Updating client parameter: {0} for client: {1} in realm: {2}", parameterName, clientName,
                realmName);

        // Get the configuration of existing client.
        HttpResponse<JsonNode> getResp = sendRequest(
                "/admin/realms/" + realmName + "/clients?clientId=" + clientName, "GET");
        Assertions.assertTrue(RestClient.isSuccessfulResponse(getResp), "Failed to fetch client by clientId");
        JsonNode clients = getResp.body();
        Assertions.assertTrue(clients.isArray() && clients.size() > 0, "Client not found");
        JsonNode client = clients.get(0);
        String clientId = client.get("id").asText();

        // Update the client with the new parameter value.
        ObjectNode updatedClient = (ObjectNode) client;
        updatedClient.put(parameterName, parameterValue);

        HttpResponse<JsonNode> updateResp = sendRequest(
                "/admin/realms/" + realmName + "/clients/" + clientId, "PUT", updatedClient.toString());

        Assertions.assertTrue(RestClient.isSuccessfulResponse(updateResp),
                "Failed to update client: " + clientName + " " + updateResp.body());
    }

    /**
     * Perform a partial import to a realm.
     * This can be used to add users and clients easier than creating them one by one, which also requires fetching IDs.
     *
     * @param realmName the name of the realm to import to
     * @param users     a list of user representations to import
     * @param clients   a list of client representations to import
     */

    public void partialImport(String realmName, List<Map<String, Object>> users, List<Map<String, Object>> clients) {
        logger.infov("Performing partial import to realm: {0}", realmName);
        Map<String, Object> params = new HashMap<>();
        params.put("ifResourceExists", "OVERWRITE");
        params.put("realm", realmName);
        if (users != null && !users.isEmpty()) {
            params.put("users", users);
        }
        if (clients != null && !clients.isEmpty()) {
            params.put("clients", clients);
        }
        HttpResponse<JsonNode> resp = sendRequest("/admin/realms/" + realmName + "/partialImport", "POST", params);
        Assertions.assertTrue(RestClient.isSuccessfulResponse(resp), "Failed to perform partial import to realm: " + realmName + " " + resp.body());
    }

    public void beforeEach(ExtensionContext context) throws Exception {
        waitForReady();
        login();
    }

}
