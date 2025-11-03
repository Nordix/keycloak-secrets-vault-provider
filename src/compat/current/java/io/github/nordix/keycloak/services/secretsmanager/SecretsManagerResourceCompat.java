/**
 * Copyright (c) 2025 OpenInfra Foundation Europe and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License, Version 2.0
 * which accompanies this distribution, and is available at
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.nordix.keycloak.services.secretsmanager;

import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.services.resources.admin.AdminEventBuilder;
import org.keycloak.services.resources.admin.fgap.AdminPermissionEvaluator;

import io.github.nordix.keycloak.common.ProviderConfig;

public class SecretsManagerResourceCompat extends SecretsManagerResource {

    private final AdminPermissionEvaluator auth;

    public SecretsManagerResourceCompat(KeycloakSession session,
            RealmModel realm,
            AdminPermissionEvaluator auth,
            AdminEventBuilder adminEvent,
            ProviderConfig providerConfig) {
        super(session, realm, adminEvent, providerConfig);
        this.auth = auth;
    }

    protected void authorizeRequest() {
        // Check manage-realm permission, throws if unauthorized.
        auth.realm().requireManageRealm();
    }
}
