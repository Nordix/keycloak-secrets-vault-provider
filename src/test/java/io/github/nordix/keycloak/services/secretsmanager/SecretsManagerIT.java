/**
 * Copyright (c) 2025 OpenInfra Foundation Europe and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License, Version 2.0
 * which accompanies this distribution, and is available at
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.nordix.keycloak.services.secretsmanager;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import org.jboss.logging.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.nordix.junit.KeycloakRestClientExtension;
import io.github.nordix.junit.LoggingExtension;

@ExtendWith(LoggingExtension.class)
class SecretsManagerIT {

    private static Logger logger = Logger.getLogger(SecretsManagerIT.class);

    private static final String KEYCLOAK_BASE_URL = "http://127.0.0.127:8080";

    // Delete all secrets after each test.
    @RegisterExtension
    private final SecretsCleanupExtension secretsCleanup = new SecretsCleanupExtension();

    @RegisterExtension
    private final KeycloakRestClientExtension keycloakAdminClient = new KeycloakRestClientExtension(
            KEYCLOAK_BASE_URL);

    private static final String REALM = "first";
    private static final String API_PATH = "/admin/realms/" + REALM + "/secrets-manager";

    @Test
    void testCreateWithValueAndReadSecret() {
        String secretId = "test-secret-1";
        String vaultId = "${vault.test-secret-1}";
        String secretValue = "my-secret-value-1";

        // Create secret with given secret value.
        HttpResponse<JsonNode> createResp = keycloakAdminClient
                .sendRequest(API_PATH + "/" + secretId, "PUT", Map.of("secret", secretValue));

        Assertions.assertEquals(200, createResp.statusCode());
        Assertions.assertEquals(secretId, createResp.body().get("id").asText());
        Assertions.assertEquals(secretValue, createResp.body().get("secret").asText());

        // Read secret.
        HttpResponse<JsonNode> getResp = keycloakAdminClient
                .sendRequest(API_PATH + "/" + secretId, "GET");

        Assertions.assertEquals(200, getResp.statusCode());
        Assertions.assertEquals(secretId, getResp.body().get("id").asText());
        Assertions.assertEquals(secretValue, getResp.body().get("secret").asText());
        Assertions.assertEquals(vaultId, getResp.body().get("vault_id").asText());
    }

    @Test
    void testCreateWithRandomValue() {
        // Create secret with random value by submitting empty body.
        String secretId1 = "test-secret-random1";
        String vaultId1 = "${vault.test-secret-random1}";

        HttpResponse<JsonNode> createResp1 = keycloakAdminClient
                .sendRequest(API_PATH + "/" + secretId1, "PUT");

        Assertions.assertEquals(200, createResp1.statusCode());
        Assertions.assertTrue(createResp1.body().has("id"));
        Assertions.assertEquals(vaultId1, createResp1.body().get("vault_id").asText());
        Assertions.assertTrue(createResp1.body().has("secret"));
        Assertions.assertNotNull(createResp1.body().get("secret").asText());
        Assertions.assertEquals(60, createResp1.body().get("secret").asText().length());
        Assertions.assertEquals(secretId1, createResp1.body().get("id").asText());

        // Create secret with random value by submitting empty JSON document.
        String secretId2 = "test-secret-random2";
        String vaultId2 = "${vault.test-secret-random2}";

        HttpResponse<JsonNode> createResp2 = keycloakAdminClient
                .sendRequest(API_PATH + "/" + secretId2, "PUT", "{}");

        Assertions.assertEquals(200, createResp2.statusCode());
        Assertions.assertTrue(createResp2.body().has("id"));
        Assertions.assertEquals(vaultId2, createResp2.body().get("vault_id").asText());
        Assertions.assertTrue(createResp2.body().has("secret"));
        Assertions.assertNotNull(createResp2.body().get("secret").asText());
        Assertions.assertEquals(60, createResp2.body().get("secret").asText().length());
        Assertions.assertEquals(secretId2, createResp2.body().get("id").asText());

    }

    @Test
    void testUpdateSecretWithValue() {
        String secretId = "test-secret-update";
        String initialValue = "init-value";
        String updatedValue = "updated-value";

        // Create secret.
        HttpResponse<JsonNode> createResp = keycloakAdminClient.sendRequest(API_PATH + "/" + secretId, "PUT",
                Map.of("secret", initialValue));

        Assertions.assertEquals(200, createResp.statusCode());

        // Update secret.
        HttpResponse<JsonNode> updateResp = keycloakAdminClient
                .sendRequest(API_PATH + "/" + secretId, "PUT", Map.of("secret", updatedValue));

        Assertions.assertEquals(200, updateResp.statusCode());
        Assertions.assertEquals(secretId, updateResp.body().get("id").asText());
        Assertions.assertEquals(updatedValue, updateResp.body().get("secret").asText());

        // Read secret.
        HttpResponse<JsonNode> getResp = keycloakAdminClient
                .sendRequest(API_PATH + "/" + secretId, "GET");

        Assertions.assertEquals(200, getResp.statusCode());
        Assertions.assertEquals(updatedValue, getResp.body().get("secret").asText());
    }

    @Test
    void testUpdateSecretWithRandomValue() {
        String secretId = "test-secret-random-update";

        // Create secret.
        HttpResponse<JsonNode> createResp = keycloakAdminClient.sendRequest(API_PATH + "/" + secretId, "PUT");

        Assertions.assertEquals(200, createResp.statusCode());

        String initialRandomValue = createResp.body().get("secret").asText();

        // Update secret.
        HttpResponse<JsonNode> updateResp = keycloakAdminClient
                .sendRequest(API_PATH + "/" + secretId, "PUT");

        Assertions.assertEquals(200, updateResp.statusCode());

        String newRandomValue = updateResp.body().get("secret").asText();

        // Verify that the updated value is not the same as the initial value.
        Assertions.assertNotEquals(initialRandomValue, newRandomValue);
    }

    @Test
    void testDeleteSecret() {
        String secretId = "test-secret-deleted";
        String secretValue = "to-be-deleted";

        // Create secret.
        keycloakAdminClient
                .sendRequest(API_PATH + "/" + secretId, "PUT", Map.of("secret", secretValue));

        // Delete secret.
        HttpResponse<JsonNode> deleteResp = keycloakAdminClient
                .sendRequest(API_PATH + "/" + secretId, "DELETE");

        Assertions.assertEquals(204, deleteResp.statusCode());

        // Try to get deleted secret.
        HttpResponse<JsonNode> getResp = keycloakAdminClient
                .sendRequest(API_PATH + "/" + secretId, "GET");

        Assertions.assertEquals(404, getResp.statusCode());
    }

    @Test
    void testListSecrets() {
        String secretId1 = "test-secret-list-1";
        String secretId2 = "test-secret-list-2";

        // Create secrets.
        keycloakAdminClient
                .sendRequest(API_PATH + "/" + secretId1, "PUT", Map.of("secret", "val1"));
        keycloakAdminClient
                .sendRequest(API_PATH + "/" + secretId2, "PUT", Map.of("secret", "val2"));

        // List secrets.
        HttpResponse<JsonNode> listResp = keycloakAdminClient
                .sendRequest(API_PATH, "GET");

        Assertions.assertEquals(200, listResp.statusCode());
        JsonNode idsNode = listResp.body().get("secret_ids");
        Assertions.assertTrue(idsNode.isArray());
        List<String> ids = new java.util.ArrayList<>();
        idsNode.forEach(n -> ids.add(n.asText()));
        Assertions.assertTrue(ids.contains(secretId1));
        Assertions.assertTrue(ids.contains(secretId2));
    }

    @Test
    void testSecretNotFound() {
        String nonExistentId = "non-existent-secret";

        // Try to get non-existent secret.
        HttpResponse<JsonNode> getResp = keycloakAdminClient
                .sendRequest(API_PATH + "/" + nonExistentId, "GET");

        Assertions.assertEquals(404, getResp.statusCode());
    }

    @Test
    void testInvalidSecretId() {
        String invalidSecretId = "not%20a%20valid%20id";

        // Put with invalid ID.
        HttpResponse<JsonNode> putResp = keycloakAdminClient
                .sendRequest(API_PATH + "/" + invalidSecretId, "PUT");
        Assertions.assertEquals(400, putResp.statusCode());

        // Delete with invalid ID.
        HttpResponse<JsonNode> deleteResp = keycloakAdminClient
                .sendRequest(API_PATH + "/" + invalidSecretId, "DELETE");
        Assertions.assertEquals(400, deleteResp.statusCode());

        // Get with invalid ID.
        HttpResponse<JsonNode> getResp = keycloakAdminClient
                .sendRequest(API_PATH + "/" + invalidSecretId, "GET");
        Assertions.assertEquals(400, getResp.statusCode());
    }

    @Test
    void testInvalidMethods() {
        HttpResponse<JsonNode> postResp = keycloakAdminClient
                .sendRequest(API_PATH + "/invalid-create-with-post", "POST", Map.of("secret", "value"));
        Assertions.assertEquals(405, postResp.statusCode(), "POST should not be allowed");

        HttpResponse<JsonNode> patchResp = keycloakAdminClient
                .sendRequest(API_PATH + "/invalid-update-with-patch", "PATCH",
                        Map.of("secret", "value"));
        Assertions.assertEquals(405, patchResp.statusCode(), "PATCH should not be allowed");
    }

    @Test
    void testUnauthorizedAccess() {
        try {
            keycloakAdminClient.createRealm("unauthorized");
            keycloakAdminClient.createUser("unauthorized", "not-admin", "password");

            // Create new client with no admin privileges.
            KeycloakRestClientExtension client = new KeycloakRestClientExtension(KEYCLOAK_BASE_URL, "unauthorized",
                    "not-admin", "password").login();

            // Attempt to create/update secret.
            HttpResponse<JsonNode> createResp = client
                    .sendRequest("/admin/realms/unauthorized/secrets-manager/my-secret", "PUT", Map.of("secret", "value"));
            Assertions.assertEquals(403, createResp.statusCode());

            // Attempt to get secret.
            HttpResponse<JsonNode> getResp = client
                    .sendRequest("/admin/realms/unauthorized/secrets-manager/my-secret", "GET");
            Assertions.assertEquals(403, getResp.statusCode());

            // Attempt to delete secret.
            HttpResponse<JsonNode> deleteResp = client
                    .sendRequest("/admin/realms/unauthorized/secrets-manager/my-secret", "DELETE");
            Assertions.assertEquals(403, deleteResp.statusCode());

            // Attempt to list all secrets.
            HttpResponse<JsonNode> listResp = client
                    .sendRequest("/admin/realms/unauthorized/secrets-manager", "GET");
            Assertions.assertEquals(403, listResp.statusCode());
        } finally {
            keycloakAdminClient.deleteRealm("unauthorized");
        }
    }

    class SecretsCleanupExtension implements AfterEachCallback {
        @Override
        public void afterEach(ExtensionContext context) {
            try {
                HttpResponse<JsonNode> listResp = keycloakAdminClient.sendRequest(API_PATH, "GET");
                if (listResp.statusCode() == 200) {
                    JsonNode idsNode = listResp.body().get("secret_ids");
                    if (idsNode != null && idsNode.isArray()) {
                        for (JsonNode idNode : idsNode) {
                            String id = idNode.asText();
                            HttpResponse<JsonNode> delResp = keycloakAdminClient
                                    .sendRequest(API_PATH + "/" + id, "DELETE");
                            if (delResp.statusCode() != 204
                                    && delResp.statusCode() != 404) {
                                logger.warnv("Failed to delete secret {0}, status: {1}",
                                        id, delResp.statusCode());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.warnv(e, "Exception during secrets cleanup");
            }
        }
    }

}
