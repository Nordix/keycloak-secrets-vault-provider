/**
 * Copyright (c) 2025 OpenInfra Foundation Europe and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License, Version 2.0
 * which accompanies this distribution, and is available at
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.nordix.junit;

import java.io.File;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * JUnit extension for deploying dependencies using kubectl.
 */
public class KubectlApplyExtension implements BeforeAllCallback {

    private static final String KUBECTL_APPLY = "kubectl apply -f %s";

    private static Logger logger = Logger.getLogger(KubectlApplyExtension.class);

    private final String baseDir = System.getProperty("maven.multiModuleProjectDirectory");
    private final String manifestFileName;
    private final boolean setupEnv;

    public KubectlApplyExtension(String manifestFileName) {
        this(manifestFileName, isEnvSetupEnabled());
    }

    public KubectlApplyExtension(String manifestFileName, boolean setupEnv) {
        this.manifestFileName = manifestFileName;
        this.setupEnv = setupEnv;
    }

    /**
     * Check if environment setup should be enabled.
     */
    public static boolean isEnvSetupEnabled() {
        return System.getProperty("setupEnv") != null;
    }

    /**
     * Deploy dependencies using kubectl before all tests.
     */
    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        if (!setupEnv) {
            logger.info("Skipping kubectl deployment as setupEnv is not set");
            return;
        }

        String command = String.format(KUBECTL_APPLY, manifestFileName);
        run(command, "Failed to deploy dependencies using kubectl.");

        // Wait for the deployment to complete
        waitForDeployment();
    }

    /**
     * Run a command on a subprocess and wait for it to complete.
     */
    private void run(String command, String errorMessage) throws Exception {
        CommandLine cmdLine = CommandLine.parse(command);
        DefaultExecutor executor = DefaultExecutor.builder().setWorkingDirectory(new File(baseDir)).get();

        logger.infov("Running command \"{0}\" in directory \"{1}\"", command, executor.getWorkingDirectory());

        int exitValue = executor.execute(cmdLine);
        if (exitValue != 0) {
            throw new Exception(errorMessage);
        }
    }

    /**
     * Wait for the deployment to complete by polling the status of the resources.
     */
    private void waitForDeployment() throws Exception {
        String checkCommand = "kubectl wait --for=condition=available --timeout=300s deployment --all";
        CommandLine cmdLine = CommandLine.parse(checkCommand);
        DefaultExecutor executor = DefaultExecutor.builder().get();

        logger.infov("Waiting for deployments to become available with command \"{0}\"", checkCommand);

        int exitValue = executor.execute(cmdLine);
        if (exitValue != 0) {
            throw new Exception("Timeout or failure while waiting for deployments to become available.");
        }
    }
}
