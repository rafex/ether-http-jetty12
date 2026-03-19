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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.eclipse.jetty.server.ForwardedRequestCustomizer;
import org.eclipse.jetty.http.pathmap.PathSpec;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.GracefulHandler;
import org.eclipse.jetty.server.handler.PathMappingsHandler;
import org.eclipse.jetty.server.handler.QoSHandler;
import org.eclipse.jetty.server.handler.SizeLimitHandler;
import org.eclipse.jetty.server.handler.ThreadLimitHandler;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import dev.rafex.ether.http.core.AuthPolicy;
import dev.rafex.ether.json.JsonCodec;

public final class JettyServerFactory {

	private JettyServerFactory() {
	}

	public static JettyServerRunner create(final JettyServerConfig config, final JettyRouteRegistry routeRegistry,
			final JsonCodec jsonCodec) {
		return create(config, routeRegistry, jsonCodec, null, List.of(), List.of());
	}

	public static JettyServerRunner create(final JettyServerConfig config, final JettyRouteRegistry routeRegistry,
			final JsonCodec jsonCodec, final TokenVerifier tokenVerifier, final List<AuthPolicy> authPolicies,
			final List<JettyMiddleware> middlewares) {

		Objects.requireNonNull(config, "config");
		Objects.requireNonNull(routeRegistry, "routeRegistry");
		Objects.requireNonNull(jsonCodec, "jsonCodec");

		final var pool = new QueuedThreadPool();
		pool.setMaxThreads(config.maxThreads());
		pool.setMinThreads(config.minThreads());
		pool.setIdleTimeout(config.idleTimeoutMs());
		pool.setName(config.threadPoolName());

		final var server = new Server(pool);
		server.setStopAtShutdown(config.stopAtShutdown());
		server.setStopTimeout(config.stopTimeoutMs());

		final var httpConfig = buildHttpConfiguration(config);
		final var connector = new ServerConnector(server, new HttpConnectionFactory(httpConfig));
		if (config.host() != null && !config.host().isBlank()) {
			connector.setHost(config.host());
		}
		connector.setPort(config.port());
		connector.setIdleTimeout(config.idleTimeoutMs());
		connector.setAcceptQueueSize(config.acceptQueueSize());
		connector.setReuseAddress(config.reuseAddress());
		server.addConnector(connector);

		final var routesHandler = buildRoutes(routeRegistry.routes());
		final var withAuth = applyAuthPolicies(routesHandler, tokenVerifier, jsonCodec, authPolicies);
		final var withMiddlewares = applyMiddlewares(withAuth, middlewares);
		final var withSizeLimits = applySizeLimits(withMiddlewares, config);
		final var withQoS = applyQoS(withSizeLimits, config);
		final var withThreadLimit = applyThreadLimit(withQoS, config);
		final var appHandler = applyGracefulShutdown(withThreadLimit, config);
		server.setHandler(appHandler);

		return new JettyServerRunner(server);
	}

	public static JettyServerRunner create(final JettyServerConfig config, final JsonCodec jsonCodec,
			final TokenVerifier tokenVerifier, final List<JettyModule> modules) {
		final var routeRegistry = new JettyRouteRegistry();
		final var authPolicyRegistry = new JettyAuthPolicyRegistry();
		final var middlewareRegistry = new JettyMiddlewareRegistry();

		final var context = new JettyModuleContext(config, jsonCodec, tokenVerifier);
		for (final var module : modules == null ? List.<JettyModule>of() : modules) {
			module.registerRoutes(routeRegistry, context);
			module.registerAuthPolicies(authPolicyRegistry, context);
			module.registerMiddlewares(middlewareRegistry, context);
		}

		return create(config, routeRegistry, jsonCodec, tokenVerifier, authPolicyRegistry.policies(),
				middlewareRegistry.middlewares());
	}

	private static PathMappingsHandler buildRoutes(final List<JettyRouteRegistration> routes) {
		final var handler = new PathMappingsHandler();
		for (final var route : routes) {
			handler.addMapping(PathSpec.from(route.pathSpec()), route.handler());
		}
		return handler;
	}

	private static Handler applyAuthPolicies(final Handler delegate, final TokenVerifier tokenVerifier,
			final JsonCodec jsonCodec, final List<AuthPolicy> policies) {
		if (tokenVerifier == null || policies == null || policies.isEmpty()) {
			return delegate;
		}
		return new JettyAuthHandler(delegate, tokenVerifier, jsonCodec).authPolicies(policies);
	}

	private static Handler applyMiddlewares(final Handler delegate, final List<JettyMiddleware> middlewares) {
		var current = delegate;
		final var stack = middlewares == null ? List.<JettyMiddleware>of() : new ArrayList<>(middlewares);
		for (int i = stack.size() - 1; i >= 0; i--) {
			current = stack.get(i).wrap(current);
		}
		return current;
	}

	private static HttpConfiguration buildHttpConfiguration(final JettyServerConfig config) {
		final var httpConfig = new HttpConfiguration();
		httpConfig.setInputBufferSize(config.inputBufferSize());
		httpConfig.setOutputBufferSize(config.outputBufferSize());
		httpConfig.setRequestHeaderSize(config.requestHeaderSize());
		httpConfig.setResponseHeaderSize(config.responseHeaderSize());
		httpConfig.setMinRequestDataRate(config.minRequestDataRate());
		httpConfig.setMinResponseDataRate(config.minResponseDataRate());
		httpConfig.setMaxErrorDispatches(config.maxErrorDispatches());
		httpConfig.setMaxUnconsumedRequestContentReads(config.maxUnconsumedRequestContentReads());
		httpConfig.setSendServerVersion(false);
		httpConfig.setSendDateHeader(true);
		httpConfig.setRelativeRedirectAllowed(false);
		httpConfig.setNotifyForbiddenComplianceViolations(true);
		httpConfig.setPersistentConnectionsEnabled(true);
		httpConfig.setSecureScheme("https");
		httpConfig.setSecurePort(443);

		if (config.trustForwardHeaders()) {
			final var forwarded = new ForwardedRequestCustomizer();
			forwarded.setForwardedOnly(config.forwardedOnly());
			httpConfig.addCustomizer(forwarded);
		}

		return httpConfig;
	}

	private static Handler applySizeLimits(final Handler delegate, final JettyServerConfig config) {
		final var requestLimit = normalizeLimit(config.maxRequestBodyBytes());
		final var responseLimit = normalizeLimit(config.maxResponseBodyBytes());
		if (requestLimit < 0 && responseLimit < 0) {
			return delegate;
		}

		final var handler = new SizeLimitHandler(requestLimit, responseLimit);
		handler.setHandler(delegate);
		return handler;
	}

	private static Handler applyQoS(final Handler delegate, final JettyServerConfig config) {
		if (config.maxConcurrentRequests() <= 0) {
			return delegate;
		}

		final var handler = new QoSHandler();
		handler.setMaxRequestCount(config.maxConcurrentRequests());
		handler.setMaxSuspendedRequestCount(config.maxSuspendedRequests());
		handler.setMaxSuspend(Duration.ofMillis(Math.max(0L, config.maxSuspendMs())));
		handler.setHandler(delegate);
		return handler;
	}

	private static Handler applyThreadLimit(final Handler delegate, final JettyServerConfig config) {
		if (config.maxRequestsPerRemoteIp() <= 0) {
			return delegate;
		}

		final String forwardedHeader;
		final boolean rfc7239;
		if (config.trustForwardHeaders()) {
			forwardedHeader = config.forwardedOnly() ? "Forwarded" : "X-Forwarded-For";
			rfc7239 = config.forwardedOnly();
		} else {
			forwardedHeader = null;
			rfc7239 = true;
		}

		final var handler = new ThreadLimitHandler(forwardedHeader, rfc7239);
		handler.setThreadLimit(config.maxRequestsPerRemoteIp());
		handler.setHandler(delegate);
		return handler;
	}

	private static Handler applyGracefulShutdown(final Handler delegate, final JettyServerConfig config) {
		final var handler = new GracefulHandler();
		handler.setShutdownIdleTimeout(Math.max(0L, config.shutdownIdleTimeoutMs()));
		handler.setHandler(delegate);
		return handler;
	}

	private static long normalizeLimit(final long value) {
		return value <= 0 ? -1L : value;
	}
}
