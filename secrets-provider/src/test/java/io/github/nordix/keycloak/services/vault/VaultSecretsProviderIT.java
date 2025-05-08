/**
 * Copyright (c) 2025 OpenInfra Foundation Europe and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License, Version 2.0
 * which accompanies this distribution, and is available at
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.nordix.keycloak.services.vault;

import java.net.URI;
import java.util.Map;

import org.jboss.logging.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.github.nordix.junit.KindExtension;
import io.github.nordix.junit.KubectlApplyExtension;
import io.github.nordix.junit.LoggingExtension;
import io.github.nordix.baoclient.BaoClient;

@ExtendWith(LoggingExtension.class)
class VaultSecretsProviderIT {

    private static Logger logger = Logger.getLogger(VaultSecretsProviderIT.class);

    @RegisterExtension
    private static final KindExtension kind = new KindExtension("testing/configs/kind-cluster-config.yaml", "secrets-provider");

    @RegisterExtension
    private static final KubectlApplyExtension deployment = new KubectlApplyExtension("testing/manifests");

    @BeforeEach
    void setupKubernetesAuth() {
        logger.info("Setting up Kubernetes authentication...");
        BaoClient bao = new BaoClient(URI.create("http://127.0.0.127:8200")).withToken("my-root-token");

        // Enable Kubernetes authentication method.
//        bao.write("sys/auth/kubernetes", Map.of("type", "kubernetes"));
        bao.write("auth/kubernetes/config", Map.of("kubernetes_host", "https://kubernetes.default.svc"));

        // Configure policy and role.
        bao.write("sys/policy/my-policy", Map.of(
            "policy", "path \\\"secret/data/*\\\" { capabilities = [\\\"read\\\"] }"));

        bao.write("auth/kubernetes/role/my-role", Map.of(
                "bound_service_account_names", "*",
                "bound_service_account_namespaces", "default",
                "policies", "my-policy"
        ));

        logger.info("Kubernetes authentication setup complete.");

        // Write client secret for Keycloak IdP configuration.
        bao.kv2Put("secret", "keycloak/first", Map.of("client-secret", "my-client-secret"));
    }

    @Test
    void testObtainSecret() {
        logger.info("Running test...");
        Assertions.assertTrue(true);
    }
}
