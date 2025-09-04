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

import io.github.nordix.baoclient.RestClient;

/**
 * Fetch and assert metrics from a Prometheus-compatible metrics endpoint.
 */
public class Metrics {

    private final URI metricsUrl;

    private Map<String, String> baselineMetrics = new HashMap<>();

    /**
     * Create a new Metrics instance.
     *
     * @param metricsUrl the URL of the Prometheus compatible metrics endpoint
     */
    public Metrics(String metricsUrl) {
        this.metricsUrl = URI.create(metricsUrl);
        resetMetrics();
    }

    /**
     * Reset the baseline metrics to the current metrics.
     */
    public void resetMetrics() {
        baselineMetrics = fetchMetrics();
    }

    /**
     * Assert that a counter metric has been incremented by a specific count.
     */
    public void assertCounterIncrementedBy(String metricName, int expectedStep) throws AssertionError {
        Map<String, String> currentMetrics = fetchMetrics();

        // If OpenBao was just started, the desired metric might not exist yet.
        // In such cases, defaults "0" as the metric value.
        int baselineValue = Integer.parseInt(baselineMetrics.getOrDefault(metricName, "0"));
        int currentValue = Integer.parseInt(currentMetrics.getOrDefault(metricName, "0"));
        int actualStep = currentValue - baselineValue;
        if (actualStep != expectedStep) {
            throw new AssertionError(
                    "Expected metric '" + metricName + "' to step by: <" + expectedStep + ">, but was: <" + actualStep + ">");
        }
    }

    /**
     * Fetch the current metrics from the Prometheus endpoint.
     */
    private Map<String, String> fetchMetrics() {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(metricsUrl)
                .GET()
                .build();

        HttpResponse<Map<String, String>> resp;
        try {
            resp = client.send(request, prometheusFormatBodyHandler());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to fetch metrics", e);
        }

        if (RestClient.isErrorResponse(resp)) {
            throw new RuntimeException("Failed to fetch metrics (HTTP status code: " + resp.statusCode() + ")");
        }

        return resp.body();
    }

    /**
     * Prometheus text format body handler.
     */
    private static BodyHandler<Map<String, String>> prometheusFormatBodyHandler() {
        // Example for Prometheus text format:
        //
        // # HELP vault_route_list_secretv1_ vault_route_list_secretv1_
        // # TYPE vault_route_list_secretv1_ summary
        // vault_route_list_secretv1_{quantile="0.5"} NaN
        // vault_route_list_secretv1_{quantile="0.9"} NaN
        // vault_route_list_secretv1_{quantile="0.99"} NaN
        // vault_route_list_secretv1__sum 0.16811500489711761
        // vault_route_list_secretv1__count 1
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
