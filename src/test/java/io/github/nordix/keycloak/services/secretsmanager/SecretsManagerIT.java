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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.nordix.baoclient.RestClient;
import io.github.nordix.junit.KeycloakRestClientExtension;
import io.github.nordix.junit.LoggingExtension;
import io.github.nordix.junit.Metrics;

@ExtendWith(LoggingExtension.class)
class SecretsManagerIT {

    private static Logger logger = Logger.getLogger(SecretsManagerIT.class);

    private static final String KEYCLOAK_BASE_URL = "http://127.0.0.127:8080";
    private static final String OPENBAO_BASE_URL = "http://127.0.0.127:8200";
    private static final String OPENBAO_METRICS_URL = OPENBAO_BASE_URL + "/v1/sys/metrics?format=prometheus";

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

        Metrics metrics = new Metrics(OPENBAO_METRICS_URL);

        // Create secret with given secret value.
        HttpResponse<JsonNode> createResp = keycloakAdminClient
                .sendRequest(API_PATH + "/" + secretId, "PUT", Map.of("secret", secretValue));

        Assertions.assertTrue(RestClient.isSuccessfulResponse(createResp), "Failed to create secret: " + createResp.body());
        Assertions.assertEquals(secretId, createResp.body().get("id").asText());
        Assertions.assertEquals(secretValue, createResp.body().get("secret").asText());

        // Read secret.
        HttpResponse<JsonNode> getResp = keycloakAdminClient
                .sendRequest(API_PATH + "/" + secretId, "GET");

        Assertions.assertTrue(RestClient.isSuccessfulResponse(getResp), "Failed to read secret: " + getResp.body());
        Assertions.assertEquals(secretId, getResp.body().get("id").asText());
        Assertions.assertEquals(secretValue, getResp.body().get("secret").asText());
        Assertions.assertEquals(vaultId, getResp.body().get("vault_id").asText());

        metrics.assertCounterIncrementedBy("vault_route_create_secret__count", 1);
        metrics.assertCounterIncrementedBy("vault_route_read_secret__count", 1);
    }

    @Test
    void testCreateWithRandomValue() {
        // Create secret with random value by submitting empty body.
        String secretId1 = "test-secret-random1";
        String vaultId1 = "${vault.test-secret-random1}";

        Metrics metrics = new Metrics(OPENBAO_METRICS_URL);

        HttpResponse<JsonNode> createResp1 = keycloakAdminClient
                .sendRequest(API_PATH + "/" + secretId1, "PUT");

        Assertions.assertTrue(RestClient.isSuccessfulResponse(createResp1), "Failed to create secret: " + createResp1.body());
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

        Assertions.assertTrue(RestClient.isSuccessfulResponse(createResp2), "Failed to create secret: " + createResp2.body());
        Assertions.assertTrue(createResp2.body().has("id"));
        Assertions.assertEquals(vaultId2, createResp2.body().get("vault_id").asText());
        Assertions.assertTrue(createResp2.body().has("secret"));
        Assertions.assertNotNull(createResp2.body().get("secret").asText());
        Assertions.assertEquals(60, createResp2.body().get("secret").asText().length());
        Assertions.assertEquals(secretId2, createResp2.body().get("id").asText());

        metrics.assertCounterIncrementedBy("vault_route_create_secret__count", 2);
    }

    @Test
    void testUpdateSecretWithValue() {
        String secretId = "test-secret-update";
        String initialValue = "init-value";
        String updatedValue = "updated-value";

        Metrics metrics = new Metrics(OPENBAO_METRICS_URL);

        // Create secret.
        HttpResponse<JsonNode> createResp = keycloakAdminClient.sendRequest(API_PATH + "/" + secretId, "PUT",
                Map.of("secret", initialValue));

        Assertions.assertTrue(RestClient.isSuccessfulResponse(createResp), "Failed to create secret: " + createResp.body());

        // Update secret.
        HttpResponse<JsonNode> updateResp = keycloakAdminClient
                .sendRequest(API_PATH + "/" + secretId, "PUT", Map.of("secret", updatedValue));

        Assertions.assertTrue(RestClient.isSuccessfulResponse(updateResp), "Failed to update secret: " + updateResp.body());
        Assertions.assertEquals(secretId, updateResp.body().get("id").asText());
        Assertions.assertEquals(updatedValue, updateResp.body().get("secret").asText());

        // Read secret.
        HttpResponse<JsonNode> getResp = keycloakAdminClient
                .sendRequest(API_PATH + "/" + secretId, "GET");

        Assertions.assertTrue(RestClient.isSuccessfulResponse(getResp), "Failed to read secret: " + getResp.body());
        Assertions.assertEquals(updatedValue, getResp.body().get("secret").asText());

        metrics.assertCounterIncrementedBy("vault_route_create_secret__count", 1);
        metrics.assertCounterIncrementedBy("vault_route_update_secret__count", 1);
    }

    @Test
    void testUpdateSecretWithRandomValue() {
        String secretId = "test-secret-random-update";

        Metrics metrics = new Metrics(OPENBAO_METRICS_URL);

        // Create secret.
        HttpResponse<JsonNode> createResp = keycloakAdminClient.sendRequest(API_PATH + "/" + secretId, "PUT");

        Assertions.assertEquals(200, createResp.statusCode());

        String initialRandomValue = createResp.body().get("secret").asText();

        // Update secret.
        HttpResponse<JsonNode> updateResp = keycloakAdminClient
                .sendRequest(API_PATH + "/" + secretId, "PUT");

        Assertions.assertTrue(RestClient.isSuccessfulResponse(updateResp), "Failed to update secret: " + updateResp.body());

        String newRandomValue = updateResp.body().get("secret").asText();

        // Verify that the updated value is not the same as the initial value.
        Assertions.assertNotEquals(initialRandomValue, newRandomValue);

        metrics.assertCounterIncrementedBy("vault_route_create_secret__count", 1);
        metrics.assertCounterIncrementedBy("vault_route_update_secret__count", 1);
    }

    @Test
    void testDeleteSecret() {
        String secretId = "test-secret-deleted";
        String secretValue = "to-be-deleted";

        Metrics metrics = new Metrics(OPENBAO_METRICS_URL);

        // Create secret.
        keycloakAdminClient
                .sendRequest(API_PATH + "/" + secretId, "PUT", Map.of("secret", secretValue));

        // Delete secret.
        HttpResponse<JsonNode> deleteResp = keycloakAdminClient
                .sendRequest(API_PATH + "/" + secretId, "DELETE");

        Assertions.assertTrue(RestClient.isSuccessfulResponse(deleteResp), "Failed to delete secret: " + deleteResp.body());

        // Try to get deleted secret.
        HttpResponse<JsonNode> getResp = keycloakAdminClient
                .sendRequest(API_PATH + "/" + secretId, "GET");

        Assertions.assertEquals(404, getResp.statusCode());

        metrics.assertCounterIncrementedBy("vault_route_delete_secret__count", 1);

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

        Metrics metrics = new Metrics(OPENBAO_METRICS_URL);

        // List secrets.
        HttpResponse<JsonNode> listResp = keycloakAdminClient
                .sendRequest(API_PATH, "GET");

        Assertions.assertTrue(RestClient.isSuccessfulResponse(listResp), "Failed to list secrets: " + listResp.body());
        JsonNode idsNode = listResp.body().get("secret_ids");
        Assertions.assertTrue(idsNode.isArray());
        List<String> ids = new java.util.ArrayList<>();
        idsNode.forEach(n -> ids.add(n.asText()));
        Assertions.assertTrue(ids.contains(secretId1));
        Assertions.assertTrue(ids.contains(secretId2));

        metrics.assertCounterIncrementedBy("vault_route_list_secret__count", 1);
    }

    @Test
    void testSecretNotFound() {
        String nonExistentId = "non-existent-secret";

        // Try to get non-existent secret.
        HttpResponse<JsonNode> getResp = keycloakAdminClient
                .sendRequest(API_PATH + "/" + nonExistentId, "GET");

        Assertions.assertEquals(404, getResp.statusCode());

        // Try to delete non-existent secret.
        // Since DELETE is idempotent, deleting a non-existent secret should still return 204 No Content.
        HttpResponse<JsonNode> deleteResp = keycloakAdminClient
                .sendRequest(API_PATH + "/" + nonExistentId, "DELETE");
        Assertions.assertEquals(204, deleteResp.statusCode());
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

            // Create new REST client, authenticate it with user with no admin privileges at all.
            KeycloakRestClientExtension client = new KeycloakRestClientExtension(KEYCLOAK_BASE_URL, "unauthorized",
                    "not-admin", "password").login();

            Metrics metrics = new Metrics(OPENBAO_METRICS_URL);

            // Attempt to create/update secret.
            HttpResponse<JsonNode> createResp = client
                    .sendRequest("/admin/realms/unauthorized/secrets-manager/my-secret", "PUT",
                            Map.of("secret", "value"));
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

            // Check that no requests were made to OpenBao.
            metrics.assertCounterIncrementedBy("vault_route_create_secret__count", 0);
            metrics.assertCounterIncrementedBy("vault_route_update_secret__count", 0);
            metrics.assertCounterIncrementedBy("vault_route_read_secret__count", 0);
            metrics.assertCounterIncrementedBy("vault_route_list_secret__count", 0);
            metrics.assertCounterIncrementedBy("vault_route_delete_secret__count", 0);

        } finally {
            keycloakAdminClient.deleteRealm("unauthorized");
        }
    }

    @Test
    void testUnauthorizedRealmAdmin() {
        String secretValue1 = "secret value from realm 1";
        String secretValue2 = "secret value from realm 2";

        try {
            // Create two realms.
            keycloakAdminClient.createRealm("realm1");
            keycloakAdminClient.createRealm("realm2");

            // Create realm admin user in realm1 with manage-realm role.
            Map<String, Object> user = Map.of(
                    "username", "realm1-admin",
                    "email", "admin@example.com",
                    "firstName", "realm",
                    "lastName", "admin",
                    "enabled", true,
                    "credentials", List.of(
                            Map.of(
                                    "type", "password",
                                    "value", "password",
                                    "temporary", false)),
                    "clientRoles", Map.of(
                            "realm-management", List.of("manage-realm")));

            keycloakAdminClient.partialImport("realm1", List.of(user), null);

            // Create secrets.
            keycloakAdminClient.sendRequest("/admin/realms/realm1/secrets-manager/my-secret", "PUT",
                    Map.of("secret", secretValue1));
            keycloakAdminClient.sendRequest("/admin/realms/realm2/secrets-manager/my-secret", "PUT",
                    Map.of("secret", secretValue2));

            // Create new REST client, authenticate it with realm1-admin.
            KeycloakRestClientExtension realm1Admin = new KeycloakRestClientExtension(KEYCLOAK_BASE_URL, "realm1",
                    "realm1-admin", "password").login();

            // Access realm1 secret with realm1-admin: should succeed.
            HttpResponse<JsonNode> getResp1 = realm1Admin.
                    sendRequest("/admin/realms/realm1/secrets-manager/my-secret", "GET");
            Assertions.assertEquals(200, getResp1.statusCode());
            Assertions.assertEquals(secretValue1, getResp1.body().get("secret").asText());

            // Attempt to access realm2 secret with realm1-admin: should fail with 403.
            HttpResponse<JsonNode> getResp2 = realm1Admin
                    .sendRequest(
                            "/admin/realms/realm2/secrets-manager/my-secret",
                            "GET");
            logger.infov("body: {0}", getResp2.body());
            Assertions.assertEquals(403, getResp2.statusCode());

        } finally {
            cleanUpSecrets("realm1");
            cleanUpSecrets("realm2");
            keycloakAdminClient.deleteRealm("realm1");
            keycloakAdminClient.deleteRealm("realm2");
        }
    }

    @AfterEach
    void cleanUpSecrets() {
        cleanUpSecrets(REALM);
    }

    private void cleanUpSecrets(String realm) {
        try {
            HttpResponse<JsonNode> listResp = keycloakAdminClient
                    .sendRequest("/admin/realms/" + realm + "/secrets-manager", "GET");
            if (RestClient.isSuccessfulResponse(listResp)) {
                JsonNode idsNode = listResp.body().get("secret_ids");
                if (idsNode != null && idsNode.isArray()) {
                    logger.debugv("Cleaning up secrets for realm {0}: {1}", realm, idsNode);
                    for (JsonNode idNode : idsNode) {
                        String id = idNode.asText();
                        keycloakAdminClient.sendRequest("/admin/realms/" + realm + "/secrets-manager/" + id, "DELETE");
                    }
                }
            }
        } catch (Exception e) {
            logger.warnv(e, "Exception during secrets cleanup for realm {0}", realm);
        }
    }
}
