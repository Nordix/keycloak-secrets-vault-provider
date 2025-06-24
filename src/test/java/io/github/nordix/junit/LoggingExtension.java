/**
 * Copyright (c) 2025 OpenInfra Foundation Europe and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License, Version 2.0
 * which accompanies this distribution, and is available at
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.nordix.junit;

import java.util.Optional;

import org.jboss.logging.Logger;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

public class LoggingExtension implements TestWatcher, BeforeTestExecutionCallback {

    private static Logger logger = Logger.getLogger(LoggingExtension.class);

    @Override
    public void testDisabled(ExtensionContext context, Optional<String> reason) {
        logger.warnv("Test {0} is disabled: {11}", context.getDisplayName(), reason.orElse("No reason provided"));
    }

    @Override
    public void testSuccessful(ExtensionContext context) {
        logger.infov("Test {0} succeeded", context.getDisplayName());
    }

    @Override
    public void testAborted(ExtensionContext context, Throwable cause) {
        logger.errorv(cause, "Test {0} aborted", context.getDisplayName());
    }

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        logger.errorv(cause, "Test {0} failed", context.getDisplayName());
    }

    @Override
    public void beforeTestExecution(ExtensionContext context) throws Exception {
        logger.infov("Starting test {0}", context.getDisplayName());
    }

}
