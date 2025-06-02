/**
 * Copyright (c) 2025 OpenInfra Foundation Europe and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License, Version 2.0
 * which accompanies this distribution, and is available at
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.nordix.baoclient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;

/**
 * Utility class for PEM encoded certificates.
 */
public class PemUtils {

    public static final String BEGIN_CERT = "-----BEGIN CERTIFICATE-----";
    public static final String END_CERT = "-----END CERTIFICATE-----";

    private PemUtils() {
        // Prevent instantiation.
    }

    /**
     * Decode one or more X509 Certificates from a PEM string (certificate bundle).
     *
     * @param certs
     * @return the list of X509 Certificates
     * @throws PemUtilsException if decoding fails
     */
    public static X509Certificate[] decodeCertificates(String certs) throws PemUtilsException {
        String[] pemBlocks = certs.split(END_CERT);

        List<X509Certificate> x509Certificates = Arrays.stream(pemBlocks)
                .filter(pemBlock -> pemBlock != null && !pemBlock.trim().isEmpty())
                .map(pemBlock -> decodeCertificate(pemBlock + END_CERT))
                .toList();

        return x509Certificates.toArray(new X509Certificate[x509Certificates.size()]);
    }

    /**
     * Decode a single X509 Certificate from a PEM string.
     *
     * @param pemCertificate PEM string of the certificate
     * @return the X509Certificate
     * @throws PemUtilsException if decoding fails
     */
    private static X509Certificate decodeCertificate(String pemCertificate) throws PemUtilsException {
        String sanitizedPem = pemCertificate.replace(BEGIN_CERT, "")
                                            .replace(END_CERT, "")
                                            .replaceAll("\\s+", "");
        byte[] decodedBytes = java.util.Base64.getDecoder().decode(sanitizedPem);
        CertificateFactory certificateFactory;
        try {
            certificateFactory = CertificateFactory.getInstance("X.509");
            return (X509Certificate) certificateFactory.generateCertificate(new ByteArrayInputStream(decodedBytes));
        } catch (CertificateException e) {
            throw new PemUtilsException("Failed to decode certificate", e);
        }
    }

    /**
     * Create a KeyStore truststore from a PEM string containing certificates.
     *
     * @param pemCertificates PEM string containing certificates
     * @return a KeyStore containing the certificates
     * @throws PemUtilsException if creating the truststore fails
     */
    public static KeyStore createTrustStoreFromPem(String pemCertificates) {
        X509Certificate[] certificates = decodeCertificates(pemCertificates);

        try {
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);

            for (int i = 0; i < certificates.length; i++) {
                String alias = "cert-" + i;
                trustStore.setCertificateEntry(alias, certificates[i]);
            }
            return trustStore;
        } catch (KeyStoreException | IOException | CertificateException | NoSuchAlgorithmException e) {
            throw new PemUtilsException("Failed to create truststore from PEM certificates", e);
        }
    }

    public static class PemUtilsException extends RuntimeException {
        public PemUtilsException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
