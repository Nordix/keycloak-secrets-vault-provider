/**
 * Copyright (c) 2025 OpenInfra Foundation Europe and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License, Version 2.0
 * which accompanies this distribution, and is available at
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.nordix.keycloak.services.secretsmanager;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;
import org.keycloak.connections.infinispan.InfinispanConnectionProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.services.ErrorResponse;
import org.keycloak.services.resources.admin.AdminEventBuilder;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.github.nordix.baoclient.BaoClient;
import io.github.nordix.keycloak.common.ProviderConfig;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Tag(name = "Secrets Manager")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
abstract public class SecretsManagerResource {

    private static Logger logger = Logger.getLogger(SecretsManagerResource.class);

    /**
     * Character classes for random secret generation: upper, lower, digit, special.
     */
    static final String SECRET_CHAR_CLASS_UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    static final String SECRET_CHAR_CLASS_LOWER = "abcdefghijklmnopqrstuvwxyz";
    static final String SECRET_CHAR_CLASS_DIGIT = "0123456789";
    static final String SECRET_CHAR_CLASS_SPECIAL = "@#%^-_=+.:";

    /**
     * Characters used to generate random secret values.
     */
    static final String SECRET_CHARS = SECRET_CHAR_CLASS_UPPER + SECRET_CHAR_CLASS_LOWER
            + SECRET_CHAR_CLASS_DIGIT + SECRET_CHAR_CLASS_SPECIAL;

    /**
     * The name of the field in OpenBao / HashiCorp Vault where the secret value is
     * stored.
     */
    private static final String SECRET_FIELD_NAME = "secret";

    /**
     * Regular expression for validating secret IDs.
     */
    private static final String SECRET_ID_REGEX = "^[a-zA-Z0-9_.-]+$";

    private final RealmModel realm;
    private final ProviderConfig providerConfig;
    private BaoClient baoClient;
    private final String resolvedRealmPathPrefix;
    private final KeycloakSession session;

    public SecretsManagerResource(KeycloakSession session,
            RealmModel realm,
            AdminEventBuilder adminEvent,
            ProviderConfig providerConfig) {
        logger.debugv("Creating SecretResource for session: {0}, realm: {1}", session, realm.getName());
        this.realm = realm;
        this.providerConfig = providerConfig;
        this.resolvedRealmPathPrefix = providerConfig.getKvPathPrefix().replace("%realm%", realm.getName());
        this.session = session;
    }

    @GET
    @Path("")
    @Operation(summary = "List all secrets", description = "Returns a list of all secret IDs for the realm.")
    @APIResponse(responseCode = "200", description = "List of secrets", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SecretsListResponse.class)))
    @APIResponse(responseCode = "500", description = "Internal server error")
    public Response listSecrets() {

        authorizeRequest();

        initializeBaoClient();

        logger.debugv("Listing all secrets for realm {0}", realm.getName());

        try {
            List<String> secretKeys = baoClient.kv1ListKeys(providerConfig.getKvMount(), resolvedRealmPathPrefix);
            return Response.ok(new SecretsListResponse(secretKeys)).build();
        } catch (BaoClient.BaoClientException e) {
            logger.errorv(e, "Error listing secrets for realm {0}", realm.getName());
            throw ErrorResponse.error("Error listing secrets", Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @GET
    @Path("{id}")
    @Operation(summary = "Get a secret", description = "Retrieves a secret by its ID.")
    @APIResponse(responseCode = "200", description = "Secret found", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SecretResponse.class)))
    @APIResponse(responseCode = "404", description = "Secret not found")
    @APIResponse(responseCode = "400", description = "Bad request, e.g., invalid ID format")
    @APIResponse(responseCode = "500", description = "Internal server error")
    public Response getSecret(
            @Parameter(description = "The ID of the secret to retrieve. Must match regular expression "
                    + SECRET_ID_REGEX
                    + " and must exist.", required = true) @PathParam("id") String id) {

        authorizeRequest();

        initializeBaoClient();

        logger.debugv("Retrieving secret with ID: {0} in realm {1}", id, realm.getName());

        validateSecretIdFormat(id);

        try {
            Map<String, String> response = baoClient.kv1Get(providerConfig.getKvMount(), fullPathToSecret(id));
            String secret = response.get(SECRET_FIELD_NAME);

            if (secret == null) {
                logger.warnv("Secret with ID: {0} not found in realm {1}", id, realm.getName());
                return Response.status(Response.Status.NOT_FOUND).entity("Secret not found.").build();
            }

            SecretResponse secretResponse = new SecretResponse(id, secret);

            return Response.ok(secretResponse).build();

        } catch (BaoClient.BaoClientException e) {
            if (e.getStatusCode() == 404) {
                throw ErrorResponse.error("Secret not found", Response.Status.NOT_FOUND);
            } else {
                logger.errorv(e, "Error retrieving secret {0} for realm {1}", id, realm.getName());
                throw ErrorResponse.error("Error retrieving secret", Response.Status.INTERNAL_SERVER_ERROR);
            }
        }
    }

    @PUT
    @Path("{id}")
    @Operation(summary = "Create or update a secret", description = "Creates a new secret or updates an existing secret. If a secret value is not provided in the request body, a random secret will be generated.")
    @RequestBody(description = "Optional secret data. If not provided, a random secret will be generated.", required = false, content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SecretRequest.class)))
    @APIResponse(responseCode = "200", description = "Secret created or updated", content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = SecretResponse.class)))
    @APIResponse(responseCode = "400", description = "Bad request, e.g., invalid ID format")
    @APIResponse(responseCode = "500", description = "Internal server error")
    public Response updateSecret(
            @Parameter(description = "The ID of the secret to update. Must match regular expression " + SECRET_ID_REGEX
                    + ".", required = true) @PathParam("id") String id,
            SecretRequest secretRequest,
            @Parameter(description = "Number of characters in the generated random secret. Ignored if secret data is provided.", required = false, example = "32") @Schema(defaultValue = "60", minimum = "1", maximum = "2048") @QueryParam("length") Integer length,
            @Parameter(description = "Character classes to use for random secret generation. Ignored if secret data is provided. Specify as comma-separated values: 'lower' (a-z), 'upper' (A-Z), 'digit' (0-9), 'special' ("
                    + SECRET_CHAR_CLASS_SPECIAL
                    + ")", required = false, example = "lower,upper") @Schema(defaultValue = "lower,upper,digit,special") @QueryParam("charset") String charset) {

        authorizeRequest();

        initializeBaoClient();

        logger.debugv("Creating/updating secret with ID: {0} in realm {1}", id, realm.getName());

        validateSecretIdFormat(id);

        String secretValue;
        if (secretRequest == null || secretRequest.getSecret() == null || secretRequest.getSecret().isEmpty()) {
            logger.debugv(
                    "No secret data provided, generating random secret for ID: {0} with length {1} and charset {2}", id,
                    length, charset);

            secretValue = createRandomSecretValue(length, charset);
        } else {
            secretValue = secretRequest.getSecret();
        }

        try {
            String fullPath = fullPathToSecret(id);
            baoClient.kv1Upsert(providerConfig.getKvMount(), fullPath,
                    Map.of(SECRET_FIELD_NAME, secretValue));
            evictSecretCache(fullPath);
            SecretResponse secretResponse = new SecretResponse(id, secretValue);
            return Response.status(Response.Status.OK).entity(secretResponse).build();
        } catch (BaoClient.BaoClientException e) {
            if (e.getStatusCode() == 404) {
                throw ErrorResponse.error("Secret not found", Response.Status.NOT_FOUND);
            } else {
                logger.errorv(e, "Error creating secret {0} for realm {1}", id, realm.getName());
                throw ErrorResponse.error("Error updating secret", Response.Status.INTERNAL_SERVER_ERROR);
            }
        }
    }

    @DELETE
    @Path("{id}")
    @Operation(summary = "Delete a secret", description = "Deletes a secret by its ID.")
    @APIResponse(responseCode = "204", description = "Secret deleted successfully")
    @APIResponse(responseCode = "400", description = "Bad request, e.g., invalid ID format")
    @APIResponse(responseCode = "500", description = "Internal server error")
    public Response deleteSecret(
            @Parameter(description = "The ID of the secret to delete. Must match the regular expression "
                    + SECRET_ID_REGEX
                    + ".", required = true) @PathParam("id") String id) {

        authorizeRequest();

        initializeBaoClient();

        logger.debugv("Deleting secret with ID: {0} in realm {1}", id, realm.getName());

        validateSecretIdFormat(id);

        try {
            String fullPath = fullPathToSecret(id);
            baoClient.kv1Delete(providerConfig.getKvMount(), fullPath);
            evictSecretCache(fullPath);
            return Response.noContent().build();
        } catch (BaoClient.BaoClientException e) {
            logger.errorv(e, "Error deleting secret {0} for realm {1}", id, realm.getName());
            throw ErrorResponse.error("Error deleting secret", Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Checks that requester is authorized to manage secrets.
     * This method is implemented by concrete subclasses per Keycloak version (pre 26.3.x vs post 26.3.x).
     */
    protected abstract void authorizeRequest();

    /**
     * Login to OpenBao/HashiCorp Vault.
     */
    private void initializeBaoClient() {
        this.baoClient = new BaoClient(providerConfig.getAddress());
        if (providerConfig.getCaCertificateFile() != null && !providerConfig.getCaCertificateFile().isEmpty()) {
            this.baoClient.withCaCertificateFile(providerConfig.getCaCertificateFile());
        }

        try {
            this.baoClient.loginWithKubernetes(providerConfig.getServiceAccountFile(), providerConfig.getRole());
        } catch (IOException e) {
            logger.errorv(e, "Failed to login to OpenBao/Vault using Kubernetes auth for realm {0}",
                    realm.getName());
            throw new RuntimeException("Failed to login to OpenBao/Vault using Kubernetes auth: " + e.getMessage(), e);
        }
    }

    /**
     * Returns the full path to the secret for the given ID.
     *
     * @param id the secret ID
     * @return the full path to the secret
     */
    private String fullPathToSecret(String id) {
        return resolvedRealmPathPrefix + "/" + id;
    }

    /**
     * Validates the format of the secret ID.
     * Throws an error response if the format is invalid.
     *
     * @param id the secret ID
     */
    private void validateSecretIdFormat(String id) {
        if (!id.matches(SECRET_ID_REGEX)) {
            logger.warnv("Invalid secret ID: {0}. Must match regex {1}", id, SECRET_ID_REGEX);
            throw ErrorResponse.error("Invalid secret ID format. Must match regex " + SECRET_ID_REGEX,
                    Response.Status.BAD_REQUEST);
        }
    }

    private void evictSecretCache(String fullPath) {
        if (providerConfig.getCacheName() == null || providerConfig.getCacheName().isEmpty()) {
            return;
        }

        String cacheKey = fullPath + ":" + SECRET_FIELD_NAME;

        logger.debugv("Evicting secret cache (key: {0})", cacheKey);
        session.getProvider(InfinispanConnectionProvider.class)
                .getCache(providerConfig.getCacheName()).remove(cacheKey);
    }

    /**
     * Generate a random secret value of specified length and character set.
     */
    private static String createRandomSecretValue(Integer length, String charset) {
        int secretLen = (length != null && length > 0) ? length : 60;

        // Check minimum length.
        if (secretLen < 1) {
            throw ErrorResponse.error("Secret length must be at least 1 character", Response.Status.BAD_REQUEST);
        }

        // Check maximum length.
        if (secretLen > 2048) {
            throw ErrorResponse.error("Secret length cannot exceed 2048 characters", Response.Status.BAD_REQUEST);
        }

        // Check allowed characters.
        if (charset == null) {
            charset = SECRET_CHAR_CLASS_LOWER + SECRET_CHAR_CLASS_UPPER + SECRET_CHAR_CLASS_DIGIT
                    + SECRET_CHAR_CLASS_SPECIAL;
        } else if (charset.isEmpty()) {
            throw ErrorResponse.error("Charset cannot be empty", Response.Status.BAD_REQUEST);
        } else {
            charset = expandCharsetKeywords(charset);
        }

        // Generate random secret with specified length and character set.
        SecureRandom random = new SecureRandom();

        return random.ints(secretLen, 0, charset.length())
                .mapToObj(charset::charAt)
                .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append)
                .toString();
    }

    /**
     * Expands keyword-based character classes to actual characters.
     */
    private static String expandCharsetKeywords(String charset) {
        String[] parts = charset.split(",");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            String trimmed = part.trim().toLowerCase();
            switch (trimmed) {
                case "upper":
                    sb.append(SECRET_CHAR_CLASS_UPPER);
                    break;
                case "lower":
                    sb.append(SECRET_CHAR_CLASS_LOWER);
                    break;
                case "digit":
                    sb.append(SECRET_CHAR_CLASS_DIGIT);
                    break;
                case "special":
                    sb.append(SECRET_CHAR_CLASS_SPECIAL);
                    break;
                default:
                    throw ErrorResponse.error("Invalid charset specification: " + charset,
                            Response.Status.BAD_REQUEST);
            }
        }

        if (sb.isEmpty()) {
            throw ErrorResponse.error("Invalid charset specification: " + charset,
                    Response.Status.BAD_REQUEST);
        }

        return sb.toString();
    }

    public class SecretsListResponse {
        @JsonProperty("secret_ids")
        @Schema(required = true, description = "List of secret IDs for the realm", examples = {
                "[\"secret-id-1\", \"secret-id-2\"]" })
        private List<String> secretIds;

        public SecretsListResponse() {
        }

        public SecretsListResponse(List<String> secretIds) {
            this.secretIds = secretIds;
        }

        public List<String> getSecretIds() {
            return secretIds;
        }

        public void setSecretIds(List<String> secretIds) {
            this.secretIds = secretIds;
        }
    }

    public static class SecretRequest {
        @Schema(required = false, description = "The secret value to be stored. If omitted or empty, a random secret is generated.", examples = {
                "my-secret-value" })
        private String secret;

        public SecretRequest() {
            // Default constructor.
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }
    }

    public static class SecretResponse {
        @Schema(description = "The ID of the secret.", required = true, examples = { "secret-id-1" })
        private String id;

        @JsonProperty(value = "vault_id")
        @Schema(description = "The Keycloak Vault ID for this secret.", required = true, examples = {
                "${vault.secret-id-1}" })
        private String vaultId;

        @Schema(description = "The secret value.", required = true, examples = { "my-secret-value" })
        private String secret;

        public SecretResponse() {
        }

        public SecretResponse(String id, String secret) {
            this.id = id;
            this.vaultId = "${vault." + id + "}";
            this.secret = secret;
        }

        public String getId() {
            return id;
        }

        public String getVaultId() {
            return vaultId;
        }

        public String getSecret() {
            return secret;
        }

        public void setId(String id) {
            this.id = id;
            this.vaultId = "${vault." + id + "}";
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }
    }

}
