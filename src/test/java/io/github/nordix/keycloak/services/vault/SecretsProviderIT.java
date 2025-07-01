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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.nordix.junit.KeycloakRestClientExtension;
import io.github.nordix.junit.KindExtension;
import io.github.nordix.junit.KubectlApplyExtension;
import io.github.nordix.junit.LoggingExtension;

@ExtendWith(LoggingExtension.class)
class SecretsProviderIT {

    private static Logger logger = Logger.getLogger(SecretsProviderIT.class);

    private static final String KEYCLOAK_BASE_URL = "http://127.0.0.127:8080";
    private static final String PROVIDER_REALM = "provider-realm";
    private static final String CONSUMER_REALM = "consumer-realm";
    private static final String PROVIDER_CLIENT_NAME = "federator";
    private static final String PROVIDER_CLIENT_SECRET = "my-secret";
    private static final String USER_LOGIN = "joe";
    private static final String USER_PASSWORD = "password";

    // Create a Kind cluster.
    @RegisterExtension
    private static final KindExtension kind = new KindExtension("testing/configs/kind-cluster-config.yaml",
            "secrets-provider");

    // Deploy Keycloak and OpenBao.
    @RegisterExtension
    private static final KubectlApplyExtension deployment = new KubectlApplyExtension("testing/manifests");

    // Keycloak client extension to interact with Keycloak admin API.
    @RegisterExtension
    private static final KeycloakRestClientExtension keycloakAdminClient = new KeycloakRestClientExtension(
            KEYCLOAK_BASE_URL);

    @RegisterExtension
    private final SetupFederatedRealms realms = new SetupFederatedRealms();

    @Test
    void testSecretReference() {
        // Create a vault secret reference in the consumer realm.
        realms.storeSecret("idp.federator", PROVIDER_CLIENT_SECRET);
        realms.setSecretReference("${vault.idp.federator}");

        // TODO:
        // Cannot use password grant for identity brokering / idp user so testing would require browser login via selenium
        // Consider bringing in OpenLDAP into test env and LDAP federation instead.

    }

    class SetupFederatedRealms implements BeforeEachCallback, AfterEachCallback {

        @Override
        public void beforeEach(ExtensionContext context) throws Exception {
            logger.info("Creating realm: " + PROVIDER_REALM);
            HttpResponse<JsonNode> resp = keycloakAdminClient.sendRequest("/admin/realms", "POST", Map.of(
                    "realm", PROVIDER_REALM,
                    "enabled", true));
            Assertions.assertEquals(201, resp.statusCode(), "Failed to create provider realm: + " + resp.body());

            logger.info("Creating realm: " + CONSUMER_REALM);
            resp = keycloakAdminClient.sendRequest("/admin/realms", "POST", Map.of(
                    "realm", CONSUMER_REALM,
                    "enabled", true));
            Assertions.assertEquals(201, resp.statusCode(), "Failed to create consumer realm: + " + resp.body());

            // Create client in provider realm.
            // This client will have cleartext secret.
            // Consumer realm will be used to test vault reference.
            logger.info("Creating client: " + PROVIDER_CLIENT_NAME + " in realm: " + PROVIDER_REALM);
            keycloakAdminClient.sendRequest("/admin/realms/" + PROVIDER_REALM + "/clients", "POST", Map.of(
                    "clientId", PROVIDER_CLIENT_NAME,
                    "enabled", true,
                    "protocol", "openid-connect",
                    "publicClient", false,
                    "serviceAccountsEnabled", true,
                    "redirectUris", List.of(KEYCLOAK_BASE_URL + "/*"),
                    "webOrigins", List.of(KEYCLOAK_BASE_URL + "/*"),
                    "secret", PROVIDER_CLIENT_SECRET));

            // Create user in provider realm.
            logger.info("Creating user: " + USER_LOGIN + " in realm: " + PROVIDER_REALM);
            keycloakAdminClient.sendRequest("/admin/realms/" + PROVIDER_REALM + "/users", "POST", Map.of(
                    "username", USER_LOGIN,
                    "enabled", true,
                    "email", "email@example.com",
                    "firstName", "First",
                    "lastName", "Last",
                    "credentials", List.of(Map.of(
                            "type", "password",
                            "value", USER_PASSWORD,
                            "temporary", false))));

            // Create identity provider in consumer realm.
            // Test case will use this to check that vault secret is fetched correctly.
            logger.info("Creating identity provider in realm: " + CONSUMER_REALM);
            keycloakAdminClient.sendRequest("/admin/realms/" + CONSUMER_REALM + "/identity-provider/instances", "POST",
                    Map.of(
                            "alias", "federator",
                            "providerId", "keycloak-oidc",
                            "config", Map.of(
                                    "clientId", PROVIDER_CLIENT_NAME,
                                    "clientSecret", "<placeholder>",
                                    "authorizationUrl",
                                    KEYCLOAK_BASE_URL + "/realms/provider-realm/protocol/openid-connect/auth",
                                    "tokenUrl",
                                    KEYCLOAK_BASE_URL + "/realms/provider-realm/protocol/openid-connect/token")));
        }

        @Override
        public void afterEach(ExtensionContext context) throws Exception {
            // First, clean up secrets in the consumer realm.
            try {
                HttpResponse<JsonNode> listResp = keycloakAdminClient
                        .sendRequest("/admin/realms/" + CONSUMER_REALM + "/secrets-manager", "GET");
                if (listResp.statusCode() == 200) {
                    JsonNode idsNode = listResp.body().get("secret_ids");
                    if (idsNode != null && idsNode.isArray()) {
                        for (JsonNode idNode : idsNode) {
                            String id = idNode.asText();
                            HttpResponse<JsonNode> delResp = keycloakAdminClient.sendRequest(
                                    "/admin/realms/" + CONSUMER_REALM + "/secrets-manager/" + id, "DELETE");
                            if (delResp.statusCode() != 204 && delResp.statusCode() != 404) {
                                logger.warnv("Failed to delete secret {0}, status: {1}", id, delResp.statusCode());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.warnv(e, "Exception during secrets cleanup");
            }

            // Delete realms.
            keycloakAdminClient.sendRequest("/admin/realms/" + PROVIDER_REALM, "DELETE");
            keycloakAdminClient.sendRequest("/admin/realms/" + CONSUMER_REALM, "DELETE");
        }

        void storeSecret(String secretName, String secretValue) {
            logger.debugv("Storing secret {0} with value {1} in realm {2}", secretName, secretValue, CONSUMER_REALM);
            HttpResponse<JsonNode> response = keycloakAdminClient.sendRequest(
                    "/admin/realms/" + CONSUMER_REALM + "/secrets-manager/" + secretName, "PUT",
                    Map.of("secret", secretValue));
            Assertions.assertEquals(200, response.statusCode(), "Failed to create secret");
        }

        void setSecretReference(String secret) {
            HttpResponse<JsonNode> response = keycloakAdminClient.sendRequest(
                    "/admin/realms/" + CONSUMER_REALM + "/identity-provider/instances/federator",
                    "PUT",
                    Map.of(
                            "alias", "federator",
                            "providerId", "keycloak-oidc",
                            "config", Map.of(
                                    "clientId", PROVIDER_CLIENT_NAME,
                                    "clientSecret", secret,
                                    "authorizationUrl",
                                    KEYCLOAK_BASE_URL + "/realms/provider-realm/protocol/openid-connect/auth",
                                    "tokenUrl",
                                    KEYCLOAK_BASE_URL + "/realms/provider-realm/protocol/openid-connect/token")));
            Assertions.assertEquals(204, response.statusCode(), "Failed to update identity provider config");
        }

    }

}
