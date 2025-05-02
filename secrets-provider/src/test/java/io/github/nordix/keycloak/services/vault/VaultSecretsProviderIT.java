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
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.github.nordix.junit.KindExtension;
import io.github.nordix.junit.KubectlApplyExtension;
import io.github.nordix.junit.LoggingExtension;
import io.github.nordix.baoclient.BaoClient;

@ExtendWith(LoggingExtension.class)
class VaultSecretsProviderIT {

    private static Logger logger = Logger.getLogger(VaultSecretsProviderIT.class);

    @RegisterExtension
    private static final Extension kind = System.getProperty("skipEnvSetup") == null
            ? new KindExtension("testing/configs/kind-cluster-config.yaml", "secrets-provider")
            : new Extension() {
            };

    @RegisterExtension
    private static final Extension deployment = System.getProperty("skipEnvSetup") == null ? new KubectlApplyExtension("testing/manifests")
            : new Extension() {
            };


    @BeforeEach
    void setupKubernetesAuth() {
        logger.info("Setting up Kubernetes authentication...");
        BaoClient bao = new BaoClient(URI.create("http://127.0.0.127:8200"));
        bao.withToken("my-root-token");
        bao.enableKubernetesAuth();
        bao.configureKubernetesAuth("https://kubernetes.default.svc");
        logger.info("Kubernetes authentication setup complete.");


        // Write client secret for Keycloak IdP configuration.
        bao.writeSecretKv2("secret/keycloak", Map.of("client-secret", "my-client-secret"));



    }

    @Test
    void testObtainSecret() {
        logger.info("Running test...");
        Assertions.assertTrue(true);
    }
}
