/**
 * Copyright (c) 2025 OpenInfra Foundation Europe and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License, Version 2.0
 * which accompanies this distribution, and is available at
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.nordix.keycloak.services.secretsmanager;

import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.github.nordix.junit.KeycloakClientExtension;
// import io.github.nordix.junit.KindExtension;
// import io.github.nordix.junit.KubectlApplyExtension;
import io.github.nordix.junit.LoggingExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Assertions;

import java.net.http.HttpResponse;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.nordix.baoclient.RestClient;

@ExtendWith(LoggingExtension.class)
class SecretsManagerIT {

    private static Logger logger = Logger.getLogger(SecretsManagerIT.class);

    // @RegisterExtension
    // private static final KindExtension kind = new
    // KindExtension("testing/configs/kind-cluster-config.yaml",
    // "secrets-provider");

    // @RegisterExtension
    // private static final KubectlApplyExtension deployment = new
    // KubectlApplyExtension("testing/manifests");

    @RegisterExtension
    static final KeycloakClientExtension keycloak = new KeycloakClientExtension();

    // Delete all secrets after each test.
    @RegisterExtension
    static final SecretsCleanupExtension secretsCleanup = new SecretsCleanupExtension();

    static final String REALM = "first";
    static final String API_PATH = "/admin/realms/" + REALM + "/secrets-manager";

    RestClient restClient;

    @BeforeEach
    void setUp() {
        restClient = keycloak.getClient();
    }

    @Test
    void testCreateAndReadSecret() {
        String secretId = "test-secret-1";
        String vaultId = "${vault.test-secret-1}";
        String secretValue = "my-secret-value-1";

        // Create secret with given secret value.
        String createBody = "{\"secret\":\"" + secretValue + "\"}";
        HttpResponse<JsonNode> createResp = restClient
                .sendRequest(API_PATH + "/" + secretId, "POST", createBody);

        Assertions.assertEquals(201, createResp.statusCode());
        Assertions.assertEquals(secretId, createResp.body().get("id").asText());
        Assertions.assertEquals(secretValue, createResp.body().get("secret").asText());

        // Read secret.
        HttpResponse<JsonNode> getResp = restClient
                .sendRequest(API_PATH + "/" + secretId, "GET", null);

        Assertions.assertEquals(200, getResp.statusCode());
        Assertions.assertEquals(secretId, getResp.body().get("id").asText());
        Assertions.assertEquals(secretValue, getResp.body().get("secret").asText());
        Assertions.assertEquals(vaultId, getResp.body().get("vault_id").asText());
    }

    @Test
    void testCreateWithRandomValue() {
        String secretId = "test-secret-random";
        String vaultId = "${vault.test-secret-random}";

        // Create secret with random value.
        HttpResponse<JsonNode> createResp = restClient
                .sendRequest(API_PATH + "/" + secretId, "POST", null);

        Assertions.assertEquals(201, createResp.statusCode());
        Assertions.assertTrue(createResp.body().has("id"));
        Assertions.assertEquals(vaultId, createResp.body().get("vault_id").asText());
        Assertions.assertTrue(createResp.body().has("secret"));
        Assertions.assertNotNull(createResp.body().get("secret").asText());
        Assertions.assertEquals(60, createResp.body().get("secret").asText().length());
        Assertions.assertEquals(secretId, createResp.body().get("id").asText());
    }

    @Test
    void testUpdateSecret() {
        String secretId = "test-secret-update";
        String initialValue = "init-value";
        String updatedValue = "updated-value";

        // Create secret.
        HttpResponse<JsonNode> createResp = restClient.sendRequest(API_PATH + "/" + secretId, "POST",
                "{\"secret\":\"" + initialValue + "\"}");

        Assertions.assertEquals(201, createResp.statusCode());

        // Update secret.
        HttpResponse<JsonNode> updateResp = restClient
                .sendRequest(API_PATH + "/" + secretId, "PUT", "{\"secret\":\"" + updatedValue + "\"}");

        Assertions.assertEquals(200, updateResp.statusCode());
        Assertions.assertEquals(secretId, updateResp.body().get("id").asText());
        Assertions.assertEquals(updatedValue, updateResp.body().get("secret").asText());

        // Read secret.
        HttpResponse<JsonNode> getResp = restClient
                .sendRequest(API_PATH + "/" + secretId, "GET", null);

        Assertions.assertEquals(200, getResp.statusCode());
        Assertions.assertEquals(updatedValue, getResp.body().get("secret").asText());
    }

    @Test
    void testUpdateSecretWithRandomValue() {
        String secretId = "test-secret-random-update";

        // Create secret.
        HttpResponse<JsonNode> createResp = restClient.sendRequest(API_PATH + "/" + secretId, "POST", null);

        Assertions.assertEquals(201, createResp.statusCode());

        String initialRandomValue = createResp.body().get("secret").asText();

        // Update secret.
        HttpResponse<JsonNode> updateResp = restClient
                .sendRequest(API_PATH + "/" + secretId, "PUT", null);

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
        restClient
                .sendRequest(API_PATH + "/" + secretId, "POST", "{\"secret\":\"" + secretValue + "\"}");

        // Delete secret.
        HttpResponse<JsonNode> deleteResp = restClient
                .sendRequest(API_PATH + "/" + secretId, "DELETE", null);

        Assertions.assertEquals(204, deleteResp.statusCode());

        // Try to get deleted secret.
        HttpResponse<JsonNode> getResp = restClient
                .sendRequest(API_PATH + "/" + secretId, "GET", null);

        Assertions.assertEquals(404, getResp.statusCode());
    }

    @Test
    void testListSecrets() {
        String secretId1 = "test-secret-list-1";
        String secretId2 = "test-secret-list-2";

        // Create secrets.
        restClient
                .sendRequest(API_PATH + "/" + secretId1, "POST", "{\"secret\":\"val1\"}");
        restClient
                .sendRequest(API_PATH + "/" + secretId2, "POST", "{\"secret\":\"val2\"}");

        // List secrets.
        HttpResponse<JsonNode> listResp = restClient
                .sendRequest(API_PATH, "GET", null);

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
        HttpResponse<JsonNode> getResp = restClient
                .sendRequest(API_PATH + "/" + nonExistentId, "GET", null);

        Assertions.assertEquals(404, getResp.statusCode());
    }

    @Test
    void testInvalidSecretId() {
        String invalidSecretId = ".not.valid.id.";

        // Create with invalid ID.
        HttpResponse<JsonNode> createResp = restClient
                .sendRequest(API_PATH + "/" + invalidSecretId, "POST", "{\"secret\":\"new-value\"}");
        Assertions.assertEquals(400, createResp.statusCode());

        // Update with invalid ID.
        HttpResponse<JsonNode> updateResp = restClient
                .sendRequest(API_PATH + "/" + invalidSecretId, "PUT", "{\"secret\":\"new-value\"}");
        Assertions.assertEquals(400, updateResp.statusCode());

        // Delete with invalid ID.
        HttpResponse<JsonNode> deleteResp = restClient
                .sendRequest(API_PATH + "/" + invalidSecretId, "DELETE", null);
        Assertions.assertEquals(400, deleteResp.statusCode());

        // Try to get invalid ID.
        HttpResponse<JsonNode> getResp = restClient
                .sendRequest(API_PATH + "/" + invalidSecretId, "GET", null);
        Assertions.assertEquals(400, getResp.statusCode());
    }

    static class SecretsCleanupExtension implements AfterEachCallback {
        @Override
        public void afterEach(ExtensionContext context) {
            RestClient client = keycloak.getClient();
            try {
                HttpResponse<JsonNode> listResp = client.sendRequest(API_PATH, "GET", null);
                if (listResp.statusCode() == 200) {
                    JsonNode idsNode = listResp.body().get("secret_ids");
                    if (idsNode != null && idsNode.isArray()) {
                        for (JsonNode idNode : idsNode) {
                            String id = idNode.asText();
                            HttpResponse<JsonNode> delResp = client.sendRequest(API_PATH + "/" + id, "DELETE", null);
                            if (delResp.statusCode() != 204 && delResp.statusCode() != 404) {
                                logger.warnv("Failed to delete secret {0}, status: {1}", id, delResp.statusCode());
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
