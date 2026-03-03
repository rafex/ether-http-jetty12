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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.eclipse.jetty.http.pathmap.PathSpec;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.PathMappingsHandler;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import dev.rafex.ether.http.core.AuthPolicy;

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
		final var connector = new ServerConnector(server);
		connector.setPort(config.port());
		server.addConnector(connector);

		final var routesHandler = buildRoutes(routeRegistry.routes());
		final var withAuth = applyAuthPolicies(routesHandler, tokenVerifier, jsonCodec, authPolicies);
		final var appHandler = applyMiddlewares(withAuth, middlewares);
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
}
