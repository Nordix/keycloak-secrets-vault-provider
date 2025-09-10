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
import org.junit.jupiter.api.Disabled;
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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.nordix.baoclient.RestClient;
import io.github.nordix.junit.KeycloakRestClientExtension;
import io.github.nordix.junit.LoggingExtension;
import io.github.nordix.junit.Metrics;

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

    private static final String KEYCLOAK_0_BASE_URL = "http://127.0.0.127:8080";
    private static final String KEYCLOAK_1_BASE_URL = "http://127.0.0.127:8081";
    private static final String PROVIDER_REALM = "provider-realm";
    private static final String CONSUMER_REALM = "consumer-realm";
    private static final String PROVIDER_CLIENT_NAME = "federator";
    private static final String PROVIDER_CLIENT_SECRET = "my-secret";
    private static final String USER_LOGIN = "joe";
    private static final String USER_PASSWORD = "password";
    private static final String OPENBAO_BASE_URL = "http://127.0.0.127:8200";
    private static final String OPENBAO_METRICS_URL = OPENBAO_BASE_URL + "/v1/sys/metrics?format=prometheus";

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
    private final IdentityProviderdRealm providerRealm = new IdentityProviderdRealm();

    @RegisterExtension
    private final IdentityConsumerRealm consumerRealm = new IdentityConsumerRealm();

    @Test
    void testSecretReference() {
        consumerRealm.storeSecret("idp.federator", PROVIDER_CLIENT_SECRET);
        consumerRealm.setIdentityBrokeringConfig("${vault.idp.federator}");

        AuthenticationResult result = performBrowserLogin(USER_LOGIN, USER_PASSWORD);
        Assertions.assertEquals(AuthenticationResult.SUCCESS, result,
                "Expected successful login when valid vault secret reference is used");
    }

    @Test
    void testInvalidSecretReference() {
        consumerRealm.storeSecret("idp.federator", PROVIDER_CLIENT_SECRET);
        consumerRealm.setIdentityBrokeringConfig("${vault.idp.invalid}");

        AuthenticationResult result = performBrowserLogin(USER_LOGIN, USER_PASSWORD);
        Assertions.assertEquals(AuthenticationResult.UNEXPECTED_ERROR, result,
                "Expected invalid vault reference error when non-existent secret is referenced");
    }

    @Test
    void testInvalidCredentials() {
        consumerRealm.storeSecret("idp.federator", PROVIDER_CLIENT_SECRET);
        consumerRealm.setIdentityBrokeringConfig("${vault.idp.federator}");

        AuthenticationResult result = performBrowserLogin("invalid-user", "wrong-password");
        Assertions.assertEquals(AuthenticationResult.INVALID_LOGIN, result,
                "Expected invalid credentials error when wrong username/password is used");
    }

    @Test
    void testInvalidFormat() {
        consumerRealm.setIdentityBrokeringConfig("${vault.foo/bar}");
        AuthenticationResult result = performBrowserLogin(USER_LOGIN, USER_PASSWORD);
        // NOTE:
        //
        // The detailed error cannot be asserted here via the browser but the logs should contain:
        //
        //    Invalid secret ID: foo/bar. Must match regex ^[a-zA-Z0-9_.:-]+$
        //
        Assertions.assertEquals(AuthenticationResult.UNEXPECTED_ERROR, result,
                "Expected invalid vault reference error when invalid format is used");
    }

    @Test
    void testFieldReference() {
        consumerRealm.storeSecret("idp.federator", PROVIDER_CLIENT_SECRET);
        consumerRealm.setIdentityBrokeringConfig("${vault.idp.federator:secret}");

        AuthenticationResult result = performBrowserLogin(USER_LOGIN, USER_PASSWORD);
        Assertions.assertEquals(AuthenticationResult.SUCCESS, result,
                "Expected successful login when valid vault secret field reference is used");
    }


    @Test
    void testInvalidFieldReference() {
        consumerRealm.storeSecret("idp.federator", PROVIDER_CLIENT_SECRET);
        consumerRealm.setIdentityBrokeringConfig("${vault.idp.federator:invalid}");

        AuthenticationResult result = performBrowserLogin(USER_LOGIN, USER_PASSWORD);
        Assertions.assertEquals(AuthenticationResult.UNEXPECTED_ERROR, result,
                "Expected invalid vault reference error when non-existent field is referenced");
    }

    @Test
    void testSecretCacheDistributedHit() {
        consumerRealm.storeSecret("idp.federator", PROVIDER_CLIENT_SECRET);
        consumerRealm.setIdentityBrokeringConfig("${vault.idp.federator}");

        Metrics metrics = new Metrics(OPENBAO_METRICS_URL);

        // Login to consumer realm using Keycloak 0.
        AuthenticationResult result = performBrowserLogin(KEYCLOAK_0_BASE_URL, USER_LOGIN, USER_PASSWORD);
        Assertions.assertEquals(AuthenticationResult.SUCCESS, result,
                "Expected successful login when valid vault secret reference is used");

        // Login to consumer realm using Keycloak 1.
        AuthenticationResult result1 = performBrowserLogin(KEYCLOAK_1_BASE_URL, USER_LOGIN, USER_PASSWORD);
        Assertions.assertEquals(AuthenticationResult.SUCCESS, result1,
                "Expected successful login when valid vault secret reference is used");

        // Check that the secret was only fetched once by Keycloak 0 and Keycloak 1 accessed it from the cache.
        metrics.assertCounterIncrementedBy("vault_route_read_secretv1__count", 1);
    }

    @Test
    void testSecretCacheDistributedInvalidation() {
        consumerRealm.storeSecret("idp.federator", PROVIDER_CLIENT_SECRET);
        consumerRealm.setIdentityBrokeringConfig("${vault.idp.federator}");

        Metrics metrics = new Metrics(OPENBAO_METRICS_URL);

        AuthenticationResult result = performBrowserLogin(KEYCLOAK_0_BASE_URL, USER_LOGIN, USER_PASSWORD);
        Assertions.assertEquals(AuthenticationResult.SUCCESS, result,
                "Expected successful login when valid vault secret reference is used");

        // Update the secret in OpenBao using Keycloak 0. This also evicts the old secret from the distributed cache.
        String newSecret = "my-new-secret";
        consumerRealm.storeSecret("idp.federator", newSecret);
        providerRealm.updateClientSecret(newSecret);

        // Login into consumer realm using Keycloak 1.
        result = performBrowserLogin(KEYCLOAK_1_BASE_URL, USER_LOGIN, USER_PASSWORD);
        Assertions.assertEquals(AuthenticationResult.SUCCESS, result,
                "Expected successful login when updated vault secret reference is used");

        // Check that there was two reads from OpenBao: one from Keycloak 0 and one from Keycloak 1 after the update.
        metrics.assertCounterIncrementedBy("vault_route_read_secretv1__count", 2);
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
        consumerRealm.storeSecret("idp.federator", PROVIDER_CLIENT_SECRET);
        consumerRealm.setIdentityBrokeringConfig("${vault.idp.federator}");

        Metrics metrics = new Metrics(OPENBAO_METRICS_URL);

        // Login to consumer realm (cache miss).
        AuthenticationResult result = performBrowserLogin(KEYCLOAK_0_BASE_URL, USER_LOGIN, USER_PASSWORD);
        Assertions.assertEquals(AuthenticationResult.SUCCESS, result,
                "Expected successful login when valid vault secret reference is used");

        // Login to consumer realm again (cache hit).
        result = performBrowserLogin(KEYCLOAK_0_BASE_URL, USER_LOGIN, USER_PASSWORD);
        Assertions.assertEquals(AuthenticationResult.SUCCESS, result,
                "Expected successful login when valid vault secret reference is used");

        // Wait for the cache entry to expire.
        Thread.sleep(6000);

        // Login to consumer realm after cache expiry (cache miss).
        result = performBrowserLogin(KEYCLOAK_0_BASE_URL, USER_LOGIN, USER_PASSWORD);
        Assertions.assertEquals(AuthenticationResult.SUCCESS, result,
                "Expected successful login when valid vault secret reference is used");

        // Check that the secret was fetched 2 times (2 cache misses).
        metrics.assertCounterIncrementedBy("vault_route_read_secretv1__count", 2);
    }

    AuthenticationResult performBrowserLogin(String username, String password) {
        return performBrowserLogin(KEYCLOAK_0_BASE_URL, username, password);
    }

    AuthenticationResult performBrowserLogin(String baseUrl, String username, String password) {
        ChromeOptions options = new ChromeOptions();
        // Comment out headless if want to see browser for debugging.
        options.addArguments("--headless");
        WebDriver driver = new ChromeDriver(options);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        try {
            String consumerLoginUrl = baseUrl + "/realms/" + CONSUMER_REALM
                    + "/protocol/openid-connect/auth"
                    + "?client_id=account&redirect_uri=" + baseUrl + "/realms/" + CONSUMER_REALM
                    + "/account/"
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
            // Comment out the following line if you want to keep the browser open for debugging.
            driver.quit();
        }

        return AuthenticationResult.UNHANDLED_RESULT;
    }

    class IdentityProviderdRealm implements BeforeEachCallback, AfterEachCallback {

        @Override
        public void beforeEach(ExtensionContext context) throws Exception {
            keycloak0AdminClient.createRealm(PROVIDER_REALM);

            // Create client in provider realm for identity brokering tests.
            // This client will have cleartext secret and federated consumer realm will be
            // used to test vault references.
            keycloak0AdminClient.createClient(PROVIDER_REALM, PROVIDER_CLIENT_NAME,
                    PROVIDER_CLIENT_SECRET, List.of(KEYCLOAK_0_BASE_URL, KEYCLOAK_1_BASE_URL));

            // Create user in provider realm for identity brokering tests.
            keycloak0AdminClient.createUser(PROVIDER_REALM, USER_LOGIN, USER_PASSWORD);

        }

        public void updateClientSecret(String newSecret) {
            keycloak0AdminClient.updateClientAttribute(PROVIDER_REALM, PROVIDER_CLIENT_NAME, "secret", newSecret);
        }

        @Override
        public void afterEach(ExtensionContext context) throws Exception {
            keycloak0AdminClient.deleteRealm(PROVIDER_REALM);
        }
    }

    class IdentityConsumerRealm implements BeforeEachCallback, AfterEachCallback {

        @Override
        public void beforeEach(ExtensionContext context) throws Exception {
            keycloak0AdminClient.createRealm(CONSUMER_REALM);
        }

        void setIdentityBrokeringConfig(String secret) {
            logger.info("Configuring identity provider in realm: " + CONSUMER_REALM);
            keycloak0AdminClient.sendRequest("/admin/realms/" + CONSUMER_REALM + "/identity-provider/instances", "POST",
                    Map.of(
                            "alias", "federator",
                            "providerId", "keycloak-oidc",
                            "config", Map.of(
                                    "clientId", PROVIDER_CLIENT_NAME,
                                    "clientSecret", secret,
                                    "authorizationUrl",
                                    KEYCLOAK_0_BASE_URL + "/realms/provider-realm/protocol/openid-connect/auth",
                                    "tokenUrl",
                                    KEYCLOAK_0_BASE_URL + "/realms/provider-realm/protocol/openid-connect/token")));

        }

        void storeSecret(String secretName, String secretValue) {
            logger.debugv("Storing secret {0} with value {1} in realm {2}", secretName, secretValue, CONSUMER_REALM);
            HttpResponse<JsonNode> response = keycloak0AdminClient.sendRequest(
                    "/admin/realms/" + CONSUMER_REALM + "/secrets-manager/" + secretName, "PUT",
                    Map.of("secret", secretValue));
            Assertions.assertTrue(RestClient.isSuccessfulResponse(response),
                    "Failed to store secret: " + secretName + " " + response.body());
        }

        @Override
        public void afterEach(ExtensionContext context) throws Exception {
            // Clean up secrets before deleting the realm (otherwise they will be left orphaned in OpenBao).
            logger.info("Cleaning up secrets for realm: " + CONSUMER_REALM);
            try {
                HttpResponse<JsonNode> listResp = keycloak0AdminClient
                        .sendRequest("/admin/realms/" + CONSUMER_REALM + "/secrets-manager", "GET");
                if (RestClient.isSuccessfulResponse(listResp)) {
                    JsonNode idsNode = listResp.body().get("secret_ids");
                    if (idsNode != null && idsNode.isArray()) {
                        for (JsonNode idNode : idsNode) {
                            String id = idNode.asText();
                            keycloak0AdminClient.sendRequest(
                                    "/admin/realms/" + CONSUMER_REALM + "/secrets-manager/" + id, "DELETE");
                        }
                    }
                }
            } catch (Exception e) {
                // Log the exception
                logger.warnv(e, "Exception during secrets cleanup");
            }

            keycloak0AdminClient.deleteRealm(CONSUMER_REALM);
        }
    }
}
