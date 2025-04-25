/**
 * Copyright (c) 2025 OpenInfra Foundation Europe and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License, Version 2.0
 * which accompanies this distribution, and is available at
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.nordix.keycloak.services.vault;

import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;

/**
 * Utility class for PEM encoded certificates.
 */
public class PemUtils {

    public static final String END_CERT = "-----END CERTIFICATE-----";

    /**
     * Decode one or more X509 Certificates from a PEM string (certificate bundle).
     *
     * @param certs
     * @return the list of X509 Certificates
     * @throws Exception
     */
    public static X509Certificate[] decodeCertificates(String certs) {
        String[] pemBlocks = certs.split(END_CERT);

        List<X509Certificate> x509Certificates = Arrays.stream(pemBlocks)
                .filter(pemBlock -> pemBlock != null && !pemBlock.trim().isEmpty())
                .map(pemBlock -> org.keycloak.common.util.PemUtils.decodeCertificate(pemBlock + END_CERT))
                .toList();

        return x509Certificates.toArray(new X509Certificate[x509Certificates.size()]);
    }

    /**
     * Create a KeyStore truststore from a PEM string containing certificates.
     *
     * @param pemCertificates PEM string containing certificates
     * @return a KeyStore containing the certificates
     * @throws Exception if an error occurs while creating the KeyStore
     */
    public static KeyStore createTrustStoreFromPem(String pemCertificates) throws Exception {
        X509Certificate[] certificates = decodeCertificates(pemCertificates);

        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);

        for (int i = 0; i < certificates.length; i++) {
            String alias = "cert-" + i;
            trustStore.setCertificateEntry(alias, certificates[i]);
        }

        return trustStore;
    }
}
