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
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteResultHandler;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * JUnit extension for starting and stopping Kind clusters.
 */
public class KindExtension implements BeforeAllCallback, AfterAllCallback {

    private static final String KIND_CREATE_CLUSTER = "kind create cluster --name %s --config %s";
    private static final String KIND_DELETE_CLUSTER = "kind delete cluster --name %s";

    private static Logger logger = Logger.getLogger(KindExtension.class);

    private final String baseDir = System.getProperty("maven.multiModuleProjectDirectory");
    private final String configFileName;
    private final String clusterName;
    private final boolean setupEnv;

    public KindExtension(String configFileName, String clusterName) {
        this(configFileName, clusterName, isEnvSetupEnabled());
    }

    public KindExtension(String configFileName, String clusterName, boolean setupEnv) {
        this.configFileName = configFileName;
        this.clusterName = clusterName;
        this.setupEnv = setupEnv;
    }

    /**
     * Check if environment setup should be enabled.
     */
    public static boolean isEnvSetupEnabled() {
        return System.getProperty("setupEnv") != null;
    }

    /**
     * Start Kind before all tests.
     */
    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        if (!setupEnv) {
            logger.info("Skipping Kind cluster creation as setupEnv is not set");
            return;
        }

        String command = String.format(KIND_CREATE_CLUSTER, clusterName, configFileName);
        run(command, true, "Failed to start Kind cluster.");
    }

    /**
     * Stop Kind after all tests.
     */
    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        if (!setupEnv) {
            logger.info("Skipping Kind cluster deletion as setupEnv is not set");
            return;
        }

        String command = String.format(KIND_DELETE_CLUSTER, clusterName);
        run(command, true, "Failed to stop Kind cluster.");
    }

    /**
     * Run a command on a subprocess.
     *
     * Note: Use apache-commons-exec since ProcessBuilder has problems with output
     * redirection (output stopped in the middle even if the process was still
     * running).
     */
    private void run(String command, boolean waitForCompletion, String errorMessage) throws Exception {
        CommandLine cmdLine = CommandLine.parse(command);
        DefaultExecutor executor = DefaultExecutor.builder().setWorkingDirectory(new File(baseDir)).get();

        logger.infov("Running command \"{0}\" in directory \"{1}\"", command, executor.getWorkingDirectory());

        if (waitForCompletion) {
            int exitValue = executor.execute(cmdLine);
            if (exitValue != 0) {
                throw new Exception(errorMessage);
            }
        } else {
            executor.execute(cmdLine, new ExecuteResultHandler() {
                @Override
                public void onProcessComplete(int exitValue) {
                    if (exitValue != 0) {
                        logger.error(errorMessage);
                    }
                }

                @Override
                public void onProcessFailed(ExecuteException e) {
                    logger.error(errorMessage, e);
                }
            });
        }
    }
}
