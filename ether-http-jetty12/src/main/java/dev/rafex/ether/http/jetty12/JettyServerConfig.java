package dev.rafex.ether.http.jetty12;

import java.util.Locale;
import java.util.Map;

/*-
 * #%L
 * ether-http-jetty12
 * %%
 * Copyright (C) 2025 - 2026 Raúl Eduardo González Argote
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */

import java.util.Objects;

import dev.rafex.ether.config.EtherConfig;

public record JettyServerConfig(String host, int port, int maxThreads, int minThreads, int idleTimeoutMs,
        String threadPoolName, String environment, int acceptQueueSize, boolean reuseAddress, boolean stopAtShutdown,
        long stopTimeoutMs, long shutdownIdleTimeoutMs, boolean trustForwardHeaders, boolean forwardedOnly,
        int inputBufferSize, int outputBufferSize, int requestHeaderSize, int responseHeaderSize,
        long minRequestDataRate, long minResponseDataRate, int maxErrorDispatches, int maxUnconsumedRequestContentReads,
        long maxRequestBodyBytes, long maxResponseBodyBytes, int maxConcurrentRequests, int maxSuspendedRequests,
        long maxSuspendMs, int maxRequestsPerRemoteIp) {

    public static JettyServerConfig fromEnv() {
        return fromEnv(System.getenv());
    }

    public static JettyServerConfig fromEnv(final Map<String, String> env) {
        return fromLookup(new Lookup() {
            @Override
            public String get(final String key) {
                final var direct = env.get(key);
                if (direct != null) {
                    return direct;
                }
                return env.get(key.replace('.', '_').toUpperCase(Locale.ROOT));
            }
        });
    }

    public static JettyServerConfig fromConfig(final EtherConfig config) {
        Objects.requireNonNull(config, "config");
        return fromLookup(new Lookup() {
            @Override
            public String get(final String key) {
                return config.get(key).orElse(null);
            }
        });
    }

    private static JettyServerConfig fromLookup(final Lookup lookup) {
        final var cpus = Runtime.getRuntime().availableProcessors();
        return new JettyServerConfig(blankToNull(firstNonBlank(lookup.get("http.host"), lookup.get("HTTP_HOST"))),
                parseInt(firstNonBlank(lookup.get("port"), lookup.get("http.port"), lookup.get("HTTP_PORT")), 8080),
                parseInt(firstNonBlank(lookup.get("http.max.threads"), lookup.get("HTTP_MAX_THREADS")),
                        Math.max(cpus * 2, 16)),
                parseInt(firstNonBlank(lookup.get("http.min.threads"), lookup.get("HTTP_MIN_THREADS")), 4),
                parseInt(firstNonBlank(lookup.get("http.idle.timeout.ms"), lookup.get("HTTP_IDLE_TIMEOUT_MS")), 30_000),
                defaultString(firstNonBlank(lookup.get("http.pool.name"), lookup.get("HTTP_POOL_NAME")), "ether-http"),
                defaultString(firstNonBlank(lookup.get("environment"), lookup.get("ENVIRONMENT")), "unknown"),
                parseInt(firstNonBlank(lookup.get("http.accept.queue.size"), lookup.get("HTTP_ACCEPT_QUEUE_SIZE")),
                        128),
                parseBoolean(firstNonBlank(lookup.get("http.reuse.address"), lookup.get("HTTP_REUSE_ADDRESS")), true),
                parseBoolean(firstNonBlank(lookup.get("http.stop.at.shutdown"), lookup.get("HTTP_STOP_AT_SHUTDOWN")),
                        true),
                parseLong(firstNonBlank(lookup.get("http.stop.timeout.ms"), lookup.get("HTTP_STOP_TIMEOUT_MS")),
                        30_000L),
                parseLong(firstNonBlank(lookup.get("http.shutdown.idle.timeout.ms"),
                        lookup.get("HTTP_SHUTDOWN_IDLE_TIMEOUT_MS")), 1_000L),
                parseBoolean(firstNonBlank(lookup.get("http.trust.forwarded.headers"),
                        lookup.get("HTTP_TRUST_FORWARDED_HEADERS")), false),
                parseBoolean(firstNonBlank(lookup.get("http.forwarded.only"), lookup.get("HTTP_FORWARDED_ONLY")),
                        false),
                parseInt(firstNonBlank(lookup.get("http.input.buffer.size"), lookup.get("HTTP_INPUT_BUFFER_SIZE")),
                        8 * 1024),
                parseInt(firstNonBlank(lookup.get("http.output.buffer.size"), lookup.get("HTTP_OUTPUT_BUFFER_SIZE")),
                        32 * 1024),
                parseInt(firstNonBlank(lookup.get("http.request.header.size"), lookup.get("HTTP_REQUEST_HEADER_SIZE")),
                        8 * 1024),
                parseInt(
                        firstNonBlank(lookup.get("http.response.header.size"), lookup.get("HTTP_RESPONSE_HEADER_SIZE")),
                        8 * 1024),
                parseLong(firstNonBlank(lookup.get("http.min.request.data.rate"),
                        lookup.get("HTTP_MIN_REQUEST_DATA_RATE")), 128L),
                parseLong(firstNonBlank(lookup.get("http.min.response.data.rate"),
                        lookup.get("HTTP_MIN_RESPONSE_DATA_RATE")), 0L),
                parseInt(
                        firstNonBlank(lookup.get("http.max.error.dispatches"), lookup.get("HTTP_MAX_ERROR_DISPATCHES")),
                        10),
                parseInt(firstNonBlank(lookup.get("http.max.unconsumed.request.content.reads"),
                        lookup.get("HTTP_MAX_UNCONSUMED_REQUEST_CONTENT_READS")), 8),
                parseLong(firstNonBlank(lookup.get("http.max.request.body.bytes"),
                        lookup.get("HTTP_MAX_REQUEST_BODY_BYTES")), 10L * 1024L * 1024L),
                parseLong(firstNonBlank(lookup.get("http.max.response.body.bytes"),
                        lookup.get("HTTP_MAX_RESPONSE_BODY_BYTES")), -1L),
                parseInt(firstNonBlank(lookup.get("http.max.concurrent.requests"),
                        lookup.get("HTTP_MAX_CONCURRENT_REQUESTS")), 0),
                parseInt(firstNonBlank(lookup.get("http.max.suspended.requests"),
                        lookup.get("HTTP_MAX_SUSPENDED_REQUESTS")), 1_024),
                parseLong(firstNonBlank(lookup.get("http.max.suspend.ms"), lookup.get("HTTP_MAX_SUSPEND_MS")), 30_000L),
                parseInt(firstNonBlank(lookup.get("http.max.requests.per.remote.ip"),
                        lookup.get("HTTP_MAX_REQUESTS_PER_REMOTE_IP")), 0));
    }

    private static String firstNonBlank(final String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (final var candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }

    private static String blankToNull(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static boolean parseBoolean(final String raw, final boolean fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return Boolean.parseBoolean(raw.trim());
    }

    private static int parseInt(final String raw, final int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (final NumberFormatException e) {
            return fallback;
        }
    }

    private static long parseLong(final String raw, final long fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (final NumberFormatException e) {
            return fallback;
        }
    }

    private static String defaultString(final String raw, final String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return raw;
    }

    private interface Lookup {
        String get(String key);
    }
}
