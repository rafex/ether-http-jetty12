package dev.rafex.ether.http.jetty12;

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

import java.util.Map;

public record JettyServerConfig(
		String host,
		int port,
		int maxThreads,
		int minThreads,
		int idleTimeoutMs,
		String threadPoolName,
		String environment,
		int acceptQueueSize,
		boolean reuseAddress,
		boolean stopAtShutdown,
		long stopTimeoutMs,
		long shutdownIdleTimeoutMs,
		boolean trustForwardHeaders,
		boolean forwardedOnly,
		int inputBufferSize,
		int outputBufferSize,
		int requestHeaderSize,
		int responseHeaderSize,
		long minRequestDataRate,
		long minResponseDataRate,
		int maxErrorDispatches,
		int maxUnconsumedRequestContentReads,
		long maxRequestBodyBytes,
		long maxResponseBodyBytes,
		int maxConcurrentRequests,
		int maxSuspendedRequests,
		long maxSuspendMs,
		int maxRequestsPerRemoteIp) {

	public static JettyServerConfig fromEnv() {
		return fromEnv(System.getenv());
	}

	public static JettyServerConfig fromEnv(final Map<String, String> env) {
		final var cpus = Runtime.getRuntime().availableProcessors();
		return new JettyServerConfig(
				blankToNull(env.get("HTTP_HOST")),
				parseInt(firstNonBlank(env.get("PORT"), env.get("HTTP_PORT")), 8080),
				parseInt(env.get("HTTP_MAX_THREADS"), Math.max(cpus * 2, 16)),
				parseInt(env.get("HTTP_MIN_THREADS"), 4),
				parseInt(env.get("HTTP_IDLE_TIMEOUT_MS"), 30_000),
				env.getOrDefault("HTTP_POOL_NAME", "ether-http"),
				env.getOrDefault("ENVIRONMENT", "unknown"),
				parseInt(env.get("HTTP_ACCEPT_QUEUE_SIZE"), 128),
				parseBoolean(env.get("HTTP_REUSE_ADDRESS"), true),
				parseBoolean(env.get("HTTP_STOP_AT_SHUTDOWN"), true),
				parseLong(env.get("HTTP_STOP_TIMEOUT_MS"), 30_000L),
				parseLong(env.get("HTTP_SHUTDOWN_IDLE_TIMEOUT_MS"), 1_000L),
				parseBoolean(env.get("HTTP_TRUST_FORWARDED_HEADERS"), false),
				parseBoolean(env.get("HTTP_FORWARDED_ONLY"), false),
				parseInt(env.get("HTTP_INPUT_BUFFER_SIZE"), 8 * 1024),
				parseInt(env.get("HTTP_OUTPUT_BUFFER_SIZE"), 32 * 1024),
				parseInt(env.get("HTTP_REQUEST_HEADER_SIZE"), 8 * 1024),
				parseInt(env.get("HTTP_RESPONSE_HEADER_SIZE"), 8 * 1024),
				parseLong(env.get("HTTP_MIN_REQUEST_DATA_RATE"), 128L),
				parseLong(env.get("HTTP_MIN_RESPONSE_DATA_RATE"), 0L),
				parseInt(env.get("HTTP_MAX_ERROR_DISPATCHES"), 10),
				parseInt(env.get("HTTP_MAX_UNCONSUMED_REQUEST_CONTENT_READS"), 8),
				parseLong(env.get("HTTP_MAX_REQUEST_BODY_BYTES"), 10L * 1024L * 1024L),
				parseLong(env.get("HTTP_MAX_RESPONSE_BODY_BYTES"), -1L),
				parseInt(env.get("HTTP_MAX_CONCURRENT_REQUESTS"), 0),
				parseInt(env.get("HTTP_MAX_SUSPENDED_REQUESTS"), 1_024),
				parseLong(env.get("HTTP_MAX_SUSPEND_MS"), 30_000L),
				parseInt(env.get("HTTP_MAX_REQUESTS_PER_REMOTE_IP"), 0));
	}

	private static String firstNonBlank(final String primary, final String fallback) {
		return primary != null && !primary.isBlank() ? primary : fallback;
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
}
