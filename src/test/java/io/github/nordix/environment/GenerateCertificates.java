/**
 * Copyright (c) 2025 OpenInfra Foundation Europe and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License, Version 2.0
 * which accompanies this distribution, and is available at
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.nordix.environment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import fi.protonode.certy.Credential;

public class GenerateCertificates {

	public static void main(String[] args) throws Exception {
		Path certsDir = Paths.get("testing/certs");
		if (!Files.exists(certsDir)) {
			Files.createDirectories(certsDir);
		}

        Credential ca = new Credential().subject("CN=ca");
        ca.writeCertificateAsPem(certsDir.resolve("ca.pem"));
        ca.writePrivateKeyAsPem(certsDir.resolve("ca-key.pem"));

        Credential openbao = new Credential()
            .subject("CN=openbao")
            .subjectAltNames(Arrays.asList("DNS:openbao", "DNS:localhost"))
            .issuer(ca);
        openbao.writeCertificateAsPem(certsDir.resolve("openbao.pem"));
        openbao.writePrivateKeyAsPem(certsDir.resolve("openbao-key.pem"));

        System.out.println("CA certificate and key generated at: " + certsDir.toAbsolutePath());
	}
}
