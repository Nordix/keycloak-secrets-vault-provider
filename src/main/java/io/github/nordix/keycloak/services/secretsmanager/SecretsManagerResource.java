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
import org.keycloak.services.resources.admin.AdminEventBuilder;
import org.keycloak.services.resources.admin.permissions.AdminPermissionEvaluator;

import io.github.nordix.keycloak.common.ProviderConfig;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

// /admin/realms/{realm}/secrets-manager/secrets

@Tag(name = "Secrets Manager", description = "Operations related to secrets management")
@Produces(MediaType.APPLICATION_JSON)
public class SecretsManagerResource {

    private static Logger logger = Logger.getLogger(SecretsManagerResource.class);

    private final KeycloakSession session;
    private final RealmModel realm;
    private final AdminPermissionEvaluator auth;
    private final AdminEventBuilder adminEvent;
    private final ProviderConfig providerConfig;

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
    }

    @GET
    @Path("/")
    @Operation(summary = "List all secrets", description = "Returns a list of all secrets.")
    @APIResponse(responseCode = "200", description = "List of secrets")
    public Response listSecrets() {
        logger.debug("Listing all secrets");
        return Response.ok("[]", MediaType.APPLICATION_JSON).build();
    }

    @POST
    @Path("/")
    @Operation(summary = "Create a new secret", description = "Creates a new secret.")
    @APIResponse(responseCode = "201", description = "Secret created")
    public Response createSecret(
        @RequestBody(description = "Secret to create") SecretRepresentation secret) {
        logger.debug("Creating a new secret");
        return Response.status(Response.Status.NOT_IMPLEMENTED).build();
    }


    @GET
    @Path("{id}")
    @Operation(summary = "Get a secret", description = "Retrieves a secret by its ID.")
    @APIResponse(responseCode = "200", description = "Secret found")
    @APIResponse(responseCode = "404", description = "Secret not found")
    public Response getSecret(
        @Parameter(description = "ID of the secret", required = true) @PathParam("id") String id) {
        logger.debugv("Retrieving secret with ID: {0}", id);
        return Response.status(Response.Status.NOT_IMPLEMENTED).build();
    }

    @PUT
    @Path("{id}")
    @Operation(summary = "Update a secret", description = "Updates an existing secret.")
    @APIResponse(responseCode = "200", description = "Secret updated")
    @APIResponse(responseCode = "404", description = "Secret not found")
    public Response updateSecret(
        @Parameter(description = "ID of the secret", required = true) @PathParam("id") String id,
        @RequestBody(description = "Updated secret") SecretRepresentation secret) {
        logger.debugv("Updating secret with ID: {0}", id);
        return Response.status(Response.Status.NOT_IMPLEMENTED).build();
    }

    @DELETE
    @Path("{id}")
    @Operation(summary = "Delete a secret", description = "Deletes a secret by its ID.")
    @APIResponse(responseCode = "204", description = "Secret deleted")
    @APIResponse(responseCode = "404", description = "Secret not found")
    public Response deleteSecret(
        @Parameter(description = "ID of the secret", required = true) @PathParam("id") String id) {
        logger.debugv("Deleting secret with ID: {0}", id);
        return Response.status(Response.Status.NOT_IMPLEMENTED).build();
    }

}
