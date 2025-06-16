/**
 * Copyright (c) 2025 OpenInfra Foundation Europe and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License, Version 2.0
 * which accompanies this distribution, and is available at
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.nordix.keycloak.services.secretsmanager;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.services.ErrorResponse;
import org.keycloak.services.resources.admin.AdminEventBuilder;
import org.keycloak.services.resources.admin.permissions.AdminPermissionEvaluator;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.github.nordix.baoclient.BaoClient;
import io.github.nordix.keycloak.common.ProviderConfig;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;


@Tag(name = "Secrets Manager", description = "Operations related to secrets management")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SecretsManagerResource {

    private static Logger logger = Logger.getLogger(SecretsManagerResource.class);
    private static final String SECRET_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789@#%^-_=+.:";

    private final KeycloakSession session;
    private final RealmModel realm;
    private final AdminPermissionEvaluator auth;
    private final AdminEventBuilder adminEvent;
    private final ProviderConfig providerConfig;
    private final BaoClient baoClient;
    private final String resovedRealmPathPrefix;

    public SecretsManagerResource(KeycloakSession session,
            RealmModel realm,
            AdminPermissionEvaluator auth,
            AdminEventBuilder adminEvent,
            ProviderConfig providerConfig) {
        logger.debugv("Creating SecretResource for session: {0}, realm: {1}", session, realm.getName());
        this.session = session;
        this.realm = realm;
        this.auth = auth;
        this.adminEvent = adminEvent;
        this.providerConfig = providerConfig;
        this.resovedRealmPathPrefix = providerConfig.getKvPathPrefix().replace("%realm%", realm.getName());

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

    @GET
    @Path("/")
    @Operation(summary = "List all secrets", description = "Returns a list of all secrets.")
    @APIResponse(responseCode = "200", description = "List of secrets")
    public Response listSecrets() {

        auth.realm().requireManageRealm();

        logger.debugv("Listing all secrets for realm {0}", realm.getName());

        try {
            List<String> secretKeys = baoClient.kv1ListKeys(providerConfig.getKvMount(), resovedRealmPathPrefix);
            return Response.ok(Map.of("secret_ids", secretKeys)).build();
        } catch (BaoClient.BaoClientException e) {
            logger.errorv(e, "Error listing secrets for realm {0}", realm.getName());
            throw ErrorResponse.error("Error listing secrets", Response.Status.INTERNAL_SERVER_ERROR);
        }
    }


    @POST
    @Path("/{id}")
    @Operation(summary = "Create a new secret", description = "Creates a new secret.")
    @APIResponse(responseCode = "201", description = "Secret created")
    @APIResponse(responseCode = "400", description = "Bad request, e.g., missing name or data")
    @APIResponse(responseCode = "409", description = "Secret already exists")
    public Response createSecret(
        @Parameter(description = "ID of the secret", required = true) @PathParam("id") String id,
        @RequestBody(description = "Secret data", required = false) SecretData secret) {

        auth.realm().requireManageRealm();

        logger.debugv("Creating secret with ID: {0} in realm {1}", id, realm.getName());

        if (!id.matches("^[a-zA-Z0-9_-]+$")) {
            logger.warnv("Invalid secret ID: {0}. Must match regex ^[a-zA-Z0-9_-]+$", id);
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("Secret ID must match regex ^[a-zA-Z0-9_-]+$").build();
        }

        if (secret == null || secret.getSecret() == null || secret.getSecret().isEmpty()) {
            secret = createRandomSecret(); // Generate a random secret if not provided
            logger.debugv("No secret data provided, generating random secret for ID: {0}", id);
        }
        secret.setId(id);

        try {
            String vaultSecretPath = resovedRealmPathPrefix + "/" + id;
            baoClient.kv1Upsert(providerConfig.getKvMount(), vaultSecretPath, Map.of("secret", secret.getSecret()));
            return Response.status(Response.Status.CREATED).entity(secret).build();
        } catch (BaoClient.BaoClientException e) {
            logger.errorv(e, "Error creating secret {0} for realm {1}", id, realm.getName());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
        }
    }

    @GET
    @Path("{id}")
    @Operation(summary = "Get a secret", description = "Retrieves a secret by its ID.")
    @APIResponse(responseCode = "200", description = "Secret found")
    @APIResponse(responseCode = "404", description = "Secret not found")
    public Response getSecret(
        @Parameter(description = "ID of the secret", required = true) @PathParam("id") String id) {

        auth.realm().requireManageRealm();

        logger.debugv("Retrieving secret with ID: {0} in realm {1}", id, realm.getName());

        if (!id.matches("^[a-zA-Z0-9_-]+$")) {
            logger.warnv("Invalid secret ID: {0}. Must match regex ^[a-zA-Z0-9_-]+$", id);
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("Secret ID must match regex ^[a-zA-Z0-9_-]+$").build();
        }

        try {
            String vaultSecretPath = resovedRealmPathPrefix + "/" + id;

            Map<String, String> response = baoClient.kv1Get(providerConfig.getKvMount(), vaultSecretPath);
            String secret = response.get("secret");

            if (secret == null) {
                logger.warnv("Secret with ID: {0} not found in realm {1}", id, realm.getName());
                return Response.status(Response.Status.NOT_FOUND).entity("Secret not found.").build();
            }

            SecretData secretData = new SecretData();
            secretData.setId(id);
            secretData.setSecret(secret);

            return Response.ok(secretData).build();

        } catch (BaoClient.BaoClientException e) {
            // Crude check for 404, ideally BaoClientException would carry status
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("failed to read data") && e.getMessage().contains("404")) {
                 logger.warnv("Secret with ID: {0} not found in realm {1}", id, realm.getName());
                 return Response.status(Response.Status.NOT_FOUND).entity("Secret not found.").build();
            }
            logger.errorv(e, "Error retrieving secret {0} for realm {1}", id, realm.getName());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
        }
    }

    @PUT
    @Path("{id}")
    @Operation(summary = "Update a secret", description = "Updates an existing secret.")
    @APIResponse(responseCode = "200", description = "Secret updated")
    @APIResponse(responseCode = "400", description = "Bad request, e.g., missing data")
    @APIResponse(responseCode = "404", description = "Secret not found (if strict update is enforced)")
    public Response updateSecret(
        @Parameter(description = "ID of the secret", required = true) @PathParam("id") String id,
        @RequestBody(description = "Secret data", required = false) SecretData secret) {

        auth.realm().requireManageRealm();

        logger.debugv("Creating secret with ID: {0} in realm {1}", id, realm.getName());

        if (!id.matches("^[a-zA-Z0-9_-]+$")) {
            logger.warnv("Invalid secret ID: {0}. Must match regex ^[a-zA-Z0-9_-]+$", id);
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("Secret ID must match regex ^[a-zA-Z0-9_-]+$").build();
        }

        if (secret == null || secret.getSecret() == null || secret.getSecret().isEmpty()) {
            secret = createRandomSecret(); // Generate a random secret if not provided
            logger.debugv("No secret data provided, generating random secret for ID: {0}", id);
        }
        secret.setId(id);

        try {
            String vaultSecretPath = resovedRealmPathPrefix + "/" + id;
            baoClient.kv1Upsert(providerConfig.getKvMount(), vaultSecretPath, Map.of("secret", secret.getSecret()));
            return Response.status(Response.Status.CREATED).entity(secret).build();
        } catch (BaoClient.BaoClientException e) {
            logger.errorv(e, "Error creating secret {0} for realm {1}", id, realm.getName());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
        }
    }

    @DELETE
    @Path("{id}")
    @Operation(summary = "Delete a secret", description = "Deletes a secret by its ID.")
    @APIResponse(responseCode = "204", description = "Secret deleted")
    @APIResponse(responseCode = "404", description = "Secret not found")
    public Response deleteSecret(
        @Parameter(description = "ID of the secret", required = true) @PathParam("id") String id) {

        auth.realm().requireManageRealm();

        logger.debugv("Deleting secret with ID: {0} in realm {1}", id, realm.getName());

        if (!id.matches("^[a-zA-Z0-9_-]+$")) {
            logger.warnv("Invalid secret ID: {0}. Must match regex ^[a-zA-Z0-9_-]+$", id);
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("Secret ID must match regex ^[a-zA-Z0-9_-]+$").build();
        }

        try {
            String vaultSecretPath = resovedRealmPathPrefix + "/" + id;

            baoClient.kv1Delete(providerConfig.getKvMount(), vaultSecretPath);
            return Response.noContent().build();
        } catch (BaoClient.BaoClientException e) {
            // Crude check for 404, ideally BaoClientException would carry status
             if (e.getMessage() != null && e.getMessage().toLowerCase().contains("failed to delete data") && e.getMessage().contains("404")) {
                 logger.warnv("Secret with ID: {0} not found for deletion in realm {1}", id, realm.getName());
                 return Response.status(Response.Status.NOT_FOUND).entity("Secret not found.").build();
            }
            logger.errorv(e, "Error deleting secret {0} for realm {1}", id, realm.getName());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
        }
    }

    public static class SecretData {
        @Parameter(description = "Secret data", required = true)
        private String secret;

        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        private String id;

        @JsonProperty(value = "vault_id", access = JsonProperty.Access.READ_ONLY)
        private String vaultId;

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

    public static SecretData createRandomSecret() {
        var random = new SecureRandom();
        var secret = random.ints(60, 0, SECRET_CHARS.length())
            .mapToObj(SECRET_CHARS::charAt)
            .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append)
            .toString();
        var secretData = new SecretData();
        secretData.setSecret(secret);
        return secretData;
    }
}
