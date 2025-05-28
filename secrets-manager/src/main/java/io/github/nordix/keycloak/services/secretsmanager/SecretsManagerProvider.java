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
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.services.resources.admin.AdminEventBuilder;
import org.keycloak.services.resources.admin.ext.AdminRealmResourceProvider;
import org.keycloak.services.resources.admin.permissions.AdminPermissionEvaluator;

public class SecretsManagerProvider implements AdminRealmResourceProvider {

    private static Logger logger = Logger.getLogger(SecretsManagerProvider.class);

    public SecretsManagerProvider() {
        logger.debugf("Creating SecretManagerProvider instance");
    }

    @Override
    public void close() {
        // Intentionally left empty.
    }

    @Override
    public Object getResource(KeycloakSession session, RealmModel realm, AdminPermissionEvaluator auth,
            AdminEventBuilder adminEvent) {
        logger.debugv("Creating SecretManagerProvider for session: {0}, realm: {1}", session, realm.getName());
        return new SecretsManagerResource(session, realm, auth, adminEvent);
    }
}
