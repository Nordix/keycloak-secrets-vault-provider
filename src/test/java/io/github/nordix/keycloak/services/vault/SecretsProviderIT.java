/**
 * Copyright (c) 2025 OpenInfra Foundation Europe and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License, Version 2.0
 * which accompanies this distribution, and is available at
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.nordix.keycloak.services.vault;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import org.jboss.logging.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.nordix.baoclient.RestClient;
import io.github.nordix.junit.KeycloakRestClientExtension;
import io.github.nordix.junit.LoggingExtension;
import io.github.nordix.junit.Metrics;

@ExtendWith(LoggingExtension.class)
class SecretsProviderIT {

    private static Logger logger = Logger.getLogger(SecretsProviderIT.class);

    private static final String KEYCLOAK_0_BASE_URL = "http://127.0.0.127:8080";
    private static final String KEYCLOAK_1_BASE_URL = "http://127.0.0.127:8081";
    private static final String TEST_REALM = "test-realm";
    private static final String TEST_CLIENT_ID = "test-client";
    private static final String CLIENT_SECRET = "my-secret";
    private static final String OPENBAO_BASE_URL = "http://127.0.0.127:8200";
    private static final String OPENBAO_METRICS_URL = OPENBAO_BASE_URL + "/v1/sys/metrics?format=prometheus";

    // The test environment uses filesystem storage for OpenBao, so the secret ID must be shorter than the Linux filename limit.
    // This value is set below 255 characters to accommodate OpenBao's internal prefixes and suffixes.
    private static final int LONG_SECRET_ID_LENGTH = 230;

    // Keycloak client extension to interact with Keycloak admin API.
    @RegisterExtension
    private static final KeycloakRestClientExtension keycloak0AdminClient = new KeycloakRestClientExtension(
            KEYCLOAK_0_BASE_URL);

    // The Keycloak client for keycloak-1 is not directly used in the tests,
    // but it ensures both Keycloak instances are ready before the tests execute.
    @RegisterExtension
    private static final KeycloakRestClientExtension keycloak1AdminClient = new KeycloakRestClientExtension(
            KEYCLOAK_1_BASE_URL);

    @RegisterExtension
    private final TestRealm testRealm = new TestRealm();

    @Test
    void testSecretReference() {
        testRealm.storeSecret("client.test-client", CLIENT_SECRET);
        testRealm.createClientWithVaultSecret("${vault.client.test-client}");

        int status = performClientCredentialsGrant(KEYCLOAK_0_BASE_URL, TEST_CLIENT_ID, CLIENT_SECRET);
        Assertions.assertEquals(200, status,
                "Expected successful authentication when valid vault secret reference is used");
    }

    @Test
    void testInvalidSecretReference() {
        testRealm.storeSecret("client.test-client", CLIENT_SECRET);
        testRealm.createClientWithVaultSecret("${vault.does-not-exist}");

        int status = performClientCredentialsGrant(KEYCLOAK_0_BASE_URL, TEST_CLIENT_ID, CLIENT_SECRET);
        Assertions.assertEquals(400, status,
                "Expected authentication failure when non-existent vault secret is referenced");
    }

    @Test
    void testWrongClientSecret() {
        testRealm.storeSecret("client.test-client", CLIENT_SECRET);
        testRealm.createClientWithVaultSecret("${vault.client.test-client}");

        int status = performClientCredentialsGrant(KEYCLOAK_0_BASE_URL, TEST_CLIENT_ID, "wrong-secret");
        Assertions.assertEquals(401, status,
                "Expected authentication failure when wrong client secret is provided");
    }

    @Test
    void testInvalidFormat() {
        testRealm.createClientWithVaultSecret("${vault.foo/bar}");

        int status = performClientCredentialsGrant(KEYCLOAK_0_BASE_URL, TEST_CLIENT_ID, CLIENT_SECRET);
        Assertions.assertEquals(400, status,
                "Expected authentication failure when invalid vault reference format is used");
    }

    @Test
    void testFieldReference() {
        testRealm.storeSecret("client.test-client", CLIENT_SECRET);
        testRealm.createClientWithVaultSecret("${vault.client.test-client:secret}");

        int status = performClientCredentialsGrant(KEYCLOAK_0_BASE_URL, TEST_CLIENT_ID, CLIENT_SECRET);
        Assertions.assertEquals(200, status,
                "Expected successful authentication when valid vault secret field reference is used");
    }

    @Test
    void testInvalidFieldReference() {
        testRealm.storeSecret("client.test-client", CLIENT_SECRET);
        testRealm.createClientWithVaultSecret("${vault.client.test-client:invalid}");

        int status = performClientCredentialsGrant(KEYCLOAK_0_BASE_URL, TEST_CLIENT_ID, CLIENT_SECRET);
        Assertions.assertEquals(400, status,
                "Expected authentication failure when non-existent field is referenced");
    }

    @Test
    void testVeryLongSecretId() {
        String longSecretId = "a".repeat(LONG_SECRET_ID_LENGTH);

        testRealm.storeSecret(longSecretId, CLIENT_SECRET);
        testRealm.createClientWithVaultSecret("${vault." + longSecretId + "}");

        int status = performClientCredentialsGrant(KEYCLOAK_0_BASE_URL, TEST_CLIENT_ID, CLIENT_SECRET);
        Assertions.assertEquals(200, status,
                "Expected successful authentication when a secret ID of length " + LONG_SECRET_ID_LENGTH + " is used");
    }

    @Test
    void testSecretCacheDistributedHit() {
        testRealm.storeSecret("client.test-client", CLIENT_SECRET);
        testRealm.createClientWithVaultSecret("${vault.client.test-client}");

        Metrics metrics = new Metrics(OPENBAO_METRICS_URL);

        // Authenticate via Keycloak 0.
        int status0 = performClientCredentialsGrant(KEYCLOAK_0_BASE_URL, TEST_CLIENT_ID, CLIENT_SECRET);
        Assertions.assertEquals(200, status0,
                "Expected successful authentication via Keycloak 0");

        // Authenticate via Keycloak 1.
        int status1 = performClientCredentialsGrant(KEYCLOAK_1_BASE_URL, TEST_CLIENT_ID, CLIENT_SECRET);
        Assertions.assertEquals(200, status1,
                "Expected successful authentication via Keycloak 1");

        // Check that the secret was only fetched once by Keycloak 0 and Keycloak 1 accessed it from the cache.
        metrics.assertCounterIncrementedBy("vault_route_read_secret__count", 1);
    }

    @Test
    void testSecretCacheDistributedInvalidation() {
        testRealm.storeSecret("client.test-client", CLIENT_SECRET);
        testRealm.createClientWithVaultSecret("${vault.client.test-client}");

        Metrics metrics = new Metrics(OPENBAO_METRICS_URL);

        int status0 = performClientCredentialsGrant(KEYCLOAK_0_BASE_URL, TEST_CLIENT_ID, CLIENT_SECRET);
        Assertions.assertEquals(200, status0,
                "Expected successful authentication via Keycloak 0");

        // Update the secret in OpenBao via Keycloak 0. This also evicts the old secret from the distributed cache.
        String newSecret = "my-new-secret";
        testRealm.storeSecret("client.test-client", newSecret);

        // Authenticate via Keycloak 1 with the new secret.
        int status1 = performClientCredentialsGrant(KEYCLOAK_1_BASE_URL, TEST_CLIENT_ID, newSecret);
        Assertions.assertEquals(200, status1,
                "Expected successful authentication with updated secret via Keycloak 1");

        // Check that there were two reads from OpenBao: one from Keycloak 0 and one from Keycloak 1 after the update.
        metrics.assertCounterIncrementedBy("vault_route_read_secret__count", 2);
    }

    /**
     * Test case for verifying secret cache expiry.
     *
     * NOTE:
     * To run this test case, change the expiration manually in custom-cache-ispn.xml to
     *     <expiration lifespan="5000"/>
     * and restart Keycloak.
     */
    @Disabled("Disabled due to manual cache expiration configuration update requirement (see comment).")
    @SuppressWarnings("java:S2925") // Suppress sonarqube warning for Thread.sleep usage.
    @Test
    void testSecretCacheExpiry() throws InterruptedException {
        testRealm.storeSecret("client.test-client", CLIENT_SECRET);
        testRealm.createClientWithVaultSecret("${vault.client.test-client}");

        Metrics metrics = new Metrics(OPENBAO_METRICS_URL);

        // Authenticate (cache miss).
        int status = performClientCredentialsGrant(KEYCLOAK_0_BASE_URL, TEST_CLIENT_ID, CLIENT_SECRET);
        Assertions.assertEquals(200, status, "Expected successful authentication (cache miss)");

        // Authenticate again (cache hit).
        status = performClientCredentialsGrant(KEYCLOAK_0_BASE_URL, TEST_CLIENT_ID, CLIENT_SECRET);
        Assertions.assertEquals(200, status, "Expected successful authentication (cache hit)");

        // Wait for the cache entry to expire.
        Thread.sleep(6000);

        // Authenticate after cache expiry (cache miss).
        status = performClientCredentialsGrant(KEYCLOAK_0_BASE_URL, TEST_CLIENT_ID, CLIENT_SECRET);
        Assertions.assertEquals(200, status, "Expected successful authentication (after cache expiry)");

        // Check that the secret was fetched 2 times (2 cache misses).
        metrics.assertCounterIncrementedBy("vault_route_read_secret__count", 2);
    }

    /**
     * Perform a client_credentials grant against the token endpoint.
     *
     * @return HTTP status code of the token endpoint response
     */
    int performClientCredentialsGrant(String baseUrl, String clientId, String clientSecret) {
        RestClient client = new RestClient(java.net.URI.create(baseUrl));
        client.withHeader("Content-Type", "application/x-www-form-urlencoded");
        String body = "grant_type=client_credentials&client_id=" + clientId + "&client_secret=" + clientSecret;
        HttpResponse<JsonNode> resp = client.sendRequest(
                "/realms/" + TEST_REALM + "/protocol/openid-connect/token", "POST", body);
        logger.debugv("Token endpoint response: status={0} body={1}", resp.statusCode(), resp.body());
        return resp.statusCode();
    }

    class TestRealm implements BeforeEachCallback, AfterEachCallback {

        @Override
        public void beforeEach(ExtensionContext context) throws Exception {
            keycloak0AdminClient.createRealm(TEST_REALM);
        }

        void createClientWithVaultSecret(String vaultReference) {
            logger.infov("Creating client {0} in realm {1} with secret reference: {2}",
                    TEST_CLIENT_ID, TEST_REALM, vaultReference);
            keycloak0AdminClient.createClient(TEST_REALM, TEST_CLIENT_ID, vaultReference,
                    List.of(KEYCLOAK_0_BASE_URL, KEYCLOAK_1_BASE_URL));
        }

        void storeSecret(String secretName, String secretValue) {
            logger.debugv("Storing secret {0} with value {1} in realm {2}", secretName, secretValue, TEST_REALM);
            HttpResponse<JsonNode> response = keycloak0AdminClient.sendRequest(
                    "/admin/realms/" + TEST_REALM + "/secrets-manager/" + secretName, "PUT",
                    Map.of("secret", secretValue));
            Assertions.assertTrue(RestClient.isSuccessfulResponse(response),
                    "Failed to store secret: " + secretName + " " + response.body());
        }

        @Override
        public void afterEach(ExtensionContext context) throws Exception {
            // Clean up secrets before deleting the realm (otherwise they will be left orphaned in OpenBao).
            logger.info("Cleaning up secrets for realm: " + TEST_REALM);
            try {
                HttpResponse<JsonNode> listResp = keycloak0AdminClient
                        .sendRequest("/admin/realms/" + TEST_REALM + "/secrets-manager", "GET");
                if (RestClient.isSuccessfulResponse(listResp)) {
                    JsonNode idsNode = listResp.body().get("secret_ids");
                    if (idsNode != null && idsNode.isArray()) {
                        for (JsonNode idNode : idsNode) {
                            String id = idNode.asText();
                            keycloak0AdminClient.sendRequest(
                                    "/admin/realms/" + TEST_REALM + "/secrets-manager/" + id, "DELETE");
                        }
                    }
                }
            } catch (Exception e) {
                logger.warnv(e, "Exception during secrets cleanup");
            }

            keycloak0AdminClient.deleteRealm(TEST_REALM);
        }
    }
}
