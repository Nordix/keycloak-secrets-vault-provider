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
import java.time.Duration;
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
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.nordix.junit.KeycloakRestClientExtension;
import io.github.nordix.junit.LoggingExtension;

@ExtendWith(LoggingExtension.class)
class SecretsProviderIT {

    private static Logger logger = Logger.getLogger(SecretsProviderIT.class);

    public enum AuthenticationResult {
        SUCCESS,
        UNEXPECTED_ERROR,
        INVALID_LOGIN,
        WEBDRIVER_EXCEPTION,
        UNHANDLED_RESULT
    }

    private static final String KEYCLOAK_BASE_URL = "http://127.0.0.127:8080";
    private static final String PROVIDER_REALM = "provider-realm";
    private static final String CONSUMER_REALM = "consumer-realm";
    private static final String PROVIDER_CLIENT_NAME = "federator";
    private static final String PROVIDER_CLIENT_SECRET = "my-secret";
    private static final String USER_LOGIN = "joe";
    private static final String USER_PASSWORD = "password";

    // Keycloak client extension to interact with Keycloak admin API.
    @RegisterExtension
    private static final KeycloakRestClientExtension keycloakAdminClient = new KeycloakRestClientExtension(
            KEYCLOAK_BASE_URL);

    @RegisterExtension
    private final SetupFederatedRealms realms = new SetupFederatedRealms();

    @Test
    void testSecretReference() {
        realms.storeSecret("idp.federator", PROVIDER_CLIENT_SECRET);
        realms.setSecretReference("${vault.idp.federator}");
        AuthenticationResult result = performBrowserLogin(USER_LOGIN, USER_PASSWORD);
        Assertions.assertEquals(AuthenticationResult.SUCCESS, result,
            "Expected successful login when valid vault secret reference is used");
    }

    @Test
    void testInvalidSecretReference() {
        realms.storeSecret("idp.federator", PROVIDER_CLIENT_SECRET);
        realms.setSecretReference("${vault.idp.invalid}");
        AuthenticationResult result = performBrowserLogin(USER_LOGIN, USER_PASSWORD);
        Assertions.assertEquals(AuthenticationResult.UNEXPECTED_ERROR, result,
            "Expected invalid vault reference error when non-existent secret is referenced");
    }

    @Test
    void testInvalidCredentials() {
        realms.storeSecret("idp.federator", PROVIDER_CLIENT_SECRET);
        realms.setSecretReference("${vault.idp.federator}");
        AuthenticationResult result = performBrowserLogin("invalid-user", "wrong-password");
        Assertions.assertEquals(AuthenticationResult.INVALID_LOGIN, result,
            "Expected invalid credentials error when wrong username/password is used");
    }

    private AuthenticationResult performBrowserLogin(String username, String password) {
        ChromeOptions options = new ChromeOptions();
        // Comment out headless if want to see browser for debugging.
        options.addArguments("--headless");
        WebDriver driver = new ChromeDriver(options);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        try {
            String consumerLoginUrl = KEYCLOAK_BASE_URL + "/realms/" + CONSUMER_REALM + "/protocol/openid-connect/auth"
                    + "?client_id=account&redirect_uri=" + KEYCLOAK_BASE_URL + "/realms/" + CONSUMER_REALM + "/account/"
                    + "&response_type=code&scope=openid";
            logger.debug("Navigating to consumer login URL: " + consumerLoginUrl);
            driver.get(consumerLoginUrl);

            // Click "federator".
            wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//a[@aria-label='federator']"))).click();

            // Fill in username and password.
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("username"))).sendKeys(username);
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("password"))).sendKeys(password);

            // Click login button.
            wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[@type='submit']"))).click();

            wait.until(d -> d.getCurrentUrl().contains("/realms/" + CONSUMER_REALM + "/account/")
                    || d.getPageSource().contains("Unexpected error when authenticating with identity provider")
                    || d.getPageSource().contains("Invalid username or password"));

            if (driver.getCurrentUrl().contains("/realms/" + CONSUMER_REALM + "/account/")) {
                return AuthenticationResult.SUCCESS;
            } else if (driver.getPageSource().contains("Unexpected error when authenticating with identity provider")) {
                return AuthenticationResult.UNEXPECTED_ERROR;
            } else if (driver.getPageSource().contains("Invalid username or password")) {
                return AuthenticationResult.INVALID_LOGIN;
            }
        } catch (Exception e) {
            logger.error("Failed to open browser for login", e);
            logger.debug(driver.getPageSource());
            return AuthenticationResult.WEBDRIVER_EXCEPTION;
        } finally {
            driver.quit();
        }

        return AuthenticationResult.UNHANDLED_RESULT;
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
            // Clean up secrets in the consumer realm. before delete.
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
