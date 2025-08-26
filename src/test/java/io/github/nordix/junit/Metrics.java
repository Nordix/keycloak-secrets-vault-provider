/**
 * Copyright (c) 2025 OpenInfra Foundation Europe and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License, Version 2.0
 * which accompanies this distribution, and is available at
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.nordix.junit;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodySubscribers;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Fetch and assert metrics from a Prometheus-compatible metrics endpoint.
 */
public class Metrics {

    private final URI metricsUrl;

    private Map<String, String> baselineMetrics = new HashMap<>();

    public Metrics(String metricsUrl) {
        this.metricsUrl = URI.create(metricsUrl);
        resetMetrics();
    }

    public void resetMetrics() {
        baselineMetrics = fetchMetrics();
    }

    public void assertCounterIncrementedBy(String metricName, int expectedStep) throws AssertionError {
        Map<String, String> currentMetrics = fetchMetrics();

        // If OpenBao was just started, the desired metric might not exist yet.
        // In such cases, default to using "0" as the metric value.
        int baselineValue = Integer.parseInt(baselineMetrics.getOrDefault(metricName, "0"));
        int currentValue = Integer.parseInt(currentMetrics.getOrDefault(metricName, "0"));
        int actualStep = currentValue - baselineValue;
        if (actualStep != expectedStep) {
            throw new AssertionError(String.format("Expected metric '%s' to step by %d, but was %d", metricName,
                    expectedStep, actualStep));
        }
    }

    private Map<String, String> fetchMetrics() {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(metricsUrl)
                .GET()
                .build();

        HttpResponse<Map<String, String>> send;
        try {
            send = client.send(request, prometheusFormatBodyHandler());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to fetch metrics", e);
        }

        if (send.statusCode() / 100 != 2) {
            throw new RuntimeException("Failed to fetch metrics (HTTP status code: " + send.statusCode() + ")");
        }

        return send.body();
    }

    private static BodyHandler<Map<String, String>> prometheusFormatBodyHandler() {
        return respInfo -> BodySubscribers.mapping(
                BodySubscribers.ofString(StandardCharsets.UTF_8),
                body -> {
                    Map<String, String> result = new HashMap<>();
                    for (String line : body.split("\n")) {
                        line = line.trim();

                        // Skip comment lines.
                        if (line.isEmpty() || line.startsWith("#"))
                            continue;

                        // Split line into name and value.
                        int spaceIdx = line.indexOf(' ');
                        String name = spaceIdx > 0 ? line.substring(0, spaceIdx) : line;
                        String value = spaceIdx > 0 ? line.substring(spaceIdx + 1) : "<no value>";
                        result.put(name, value);
                    }
                    return result;
                });
    }

}
