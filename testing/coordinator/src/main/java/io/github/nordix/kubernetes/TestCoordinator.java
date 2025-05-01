/**
 * Copyright (c) 2025 OpenInfra Foundation Europe and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License, Version 2.0
 * which accompanies this distribution, and is available at
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.nordix.kubernetes;

import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.Map;

import fi.protonode.certy.Credential;
import org.jboss.logging.Logger;

public class TestCoordinator {

    private static final Logger logger = Logger.getLogger(TestCoordinator.class);

    public static void main(String[] args) throws Exception {
        logger.info("Starting Test Coordinator...");

        // Create CA certificate.
        Credential caCert = new Credential().subject("CN=ca").ca(true);
        caCert.writeCertificateAsPem(Paths.get("shared/ca.crt"));

        // Create server certificate and keystore.
        Credential serverCert = new Credential().subject("CN=kubernetes").issuer(caCert);
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry("server", serverCert.getPrivateKey(), null, serverCert.getCertificates());

        KubernetesApiServerStub.startServer(keyStore);

        // Enable Kubernetes authentication in OpenBao.
        OpenBaoClient bao = new OpenBaoClient("http://openbao:8200");
        bao.token("my-root-token");

        while (!bao.isReady()) {
            logger.info("Waiting for OpenBao to be ready...");
            Thread.sleep(5000);
        }
        logger.info("OpenBao is ready, proceeding with configuration...");
        bao.enableAuditToStdout();
        bao.enableKubernetesAuth();
        bao.configureKubernetesAuth("https://kubernettes:8443");

        // Write client secret for Keycloak.
        bao.writeKv2("secret/keycloak", Map.of("client-secret", "my-client-secret"));


        logger.info("Kubernetes API server stub initialization complete.");

        // Wait forever.
        while (true) {
            Thread.sleep(Long.MAX_VALUE);
        }
    }
}
