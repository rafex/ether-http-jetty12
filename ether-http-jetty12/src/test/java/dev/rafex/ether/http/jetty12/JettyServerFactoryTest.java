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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.jetty.server.ForwardedRequestCustomizer;
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
import org.junit.jupiter.api.Test;

import dev.rafex.ether.config.EtherConfig;
import dev.rafex.ether.config.sources.MapConfigSource;
import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.security.profile.HttpSecurityProfile;
import dev.rafex.ether.http.security.ratelimit.RateLimitPolicy;
import dev.rafex.ether.http.security.proxy.TrustedProxyPolicy;
import dev.rafex.ether.json.JsonCodecBuilder;
import dev.rafex.ether.observability.core.timing.TimingSample;
import dev.rafex.ether.observability.core.request.UuidRequestIdGenerator;

class JettyServerFactoryTest {

    @Test
    void fromEnvShouldApplyHardeningDefaults() {
        final var config = JettyServerConfig.fromEnv(Map.of("PORT", "9090", "HTTP_HOST", "127.0.0.1"));

        assertEquals("127.0.0.1", config.host());
        assertEquals(9090, config.port());
        assertEquals(128, config.acceptQueueSize());
        assertTrue(config.reuseAddress());
        assertTrue(config.stopAtShutdown());
        assertEquals(30_000L, config.stopTimeoutMs());
        assertEquals(1_000L, config.shutdownIdleTimeoutMs());
        assertFalse(config.trustForwardHeaders());
        assertFalse(config.forwardedOnly());
        assertEquals(8 * 1024, config.requestHeaderSize());
        assertEquals(10L * 1024L * 1024L, config.maxRequestBodyBytes());
        assertEquals(128L, config.minRequestDataRate());
    }

    @Test
    void createShouldApplySafeHttpDefaults() {
        final var config = config(false, false, 0, 0);
        final var runner = JettyServerFactory.create(config, new JettyRouteRegistry(),
                JsonCodecBuilder.strict().build());

        final Server server = runner.server();
        assertEquals(15_000L, server.getStopTimeout());

        final var connector = (ServerConnector) server.getConnectors()[0];
        assertEquals("127.0.0.1", connector.getHost());
        assertEquals(8081, connector.getPort());
        assertEquals(10_000L, connector.getIdleTimeout());
        assertEquals(256, connector.getAcceptQueueSize());
        assertTrue(connector.getReuseAddress());

        final var httpConnectionFactory = connector.getConnectionFactory(HttpConnectionFactory.class);
        assertNotNull(httpConnectionFactory);

        final HttpConfiguration httpConfig = httpConnectionFactory.getHttpConfiguration();
        assertEquals(12 * 1024, httpConfig.getRequestHeaderSize());
        assertEquals(16 * 1024, httpConfig.getResponseHeaderSize());
        assertEquals(256L, httpConfig.getMinRequestDataRate());
        assertFalse(httpConfig.getSendServerVersion());
        assertFalse(httpConfig.isRelativeRedirectAllowed());
        assertTrue(httpConfig.getCustomizers().stream().noneMatch(ForwardedRequestCustomizer.class::isInstance));

        assertNotNull(findHandler(server.getHandler(), GracefulHandler.class));
        assertNotNull(findHandler(server.getHandler(), SizeLimitHandler.class));
        assertNull(findHandler(server.getHandler(), QoSHandler.class));
        assertNull(findHandler(server.getHandler(), ThreadLimitHandler.class));
        assertNotNull(findHandler(server.getHandler(), PathMappingsHandler.class));
    }

    @Test
    void createShouldEnableOptionalForwardingAndConcurrencyGuards() {
        final var config = config(true, true, 32, 6);
        final var runner = JettyServerFactory.create(config, new JettyRouteRegistry(),
                JsonCodecBuilder.strict().build());

        final var connector = (ServerConnector) runner.server().getConnectors()[0];
        final var httpConfig = connector.getConnectionFactory(HttpConnectionFactory.class).getHttpConfiguration();
        assertTrue(httpConfig.getCustomizers().stream().anyMatch(ForwardedRequestCustomizer.class::isInstance));

        final var qos = findHandler(runner.server().getHandler(), QoSHandler.class);
        assertNotNull(qos);
        assertEquals(32, qos.getMaxRequestCount());
        assertEquals(64, qos.getMaxSuspendedRequestCount());

        final var threadLimit = findHandler(runner.server().getHandler(), ThreadLimitHandler.class);
        assertNotNull(threadLimit);
        assertEquals(6, threadLimit.getThreadLimit());
    }

    @Test
    void fromConfigShouldResolveDotNotationSettings() {
        final var config = EtherConfig.of(new MapConfigSource("test",
                Map.of("http.host", "0.0.0.0", "http.port", "8181", "http.max.threads", "40",
                        "http.request.header.size", "16384", "http.max.concurrent.requests", "25",
                        "http.trust.forwarded.headers", "true")));

        final var bound = JettyServerConfig.fromConfig(config);

        assertEquals("0.0.0.0", bound.host());
        assertEquals(8181, bound.port());
        assertEquals(40, bound.maxThreads());
        assertEquals(16 * 1024, bound.requestHeaderSize());
        assertEquals(25, bound.maxConcurrentRequests());
        assertTrue(bound.trustForwardHeaders());
    }

    @Test
    void createShouldInstallSecurityAndObservabilityAdapters() {
        final var config = config(false, false, 0, 0);
        final var timings = new CopyOnWriteArrayList<TimingSample>();
        final var securityProfile = new HttpSecurityProfile(HttpSecurityProfile.defaults().cors(),
                HttpSecurityProfile.defaults().headers(), HttpSecurityProfile.defaults().trustedProxies(),
                HttpSecurityProfile.defaults().ipPolicy(),
                new RateLimitPolicy(RateLimitPolicy.Scope.GLOBAL, 10, 60, 2, 11));

        final var runner = JettyServerFactory.create(config, new JettyRouteRegistry(),
                JsonCodecBuilder.strict().build(), null, List.of(), List.of(), securityProfile,
                new UuidRequestIdGenerator(), timings::add);

        assertNotNull(findHandler(runner.server().getHandler(), JettyObservabilityHandler.class));
        assertNotNull(findHandler(runner.server().getHandler(), JettyRateLimitHandler.class));
        assertNotNull(findHandler(runner.server().getHandler(), JettyIpPolicyHandler.class));
        assertNotNull(findHandler(runner.server().getHandler(), JettyCorsHandler.class));
        assertNotNull(findHandler(runner.server().getHandler(), JettySecurityHeadersHandler.class));

        final var qos = findHandler(runner.server().getHandler(), QoSHandler.class);
        assertNotNull(qos);
        assertEquals(11, qos.getMaxRequestCount());
        assertTrue(timings.isEmpty());
    }

    @Test
    void createShouldEnableForwardedCustomizerFromSecurityProfile() {
        final var config = config(false, false, 0, 0);
        final var securityProfile = new HttpSecurityProfile(HttpSecurityProfile.defaults().cors(),
                HttpSecurityProfile.defaults().headers(), new TrustedProxyPolicy(List.of("10.0."), true, true, true),
                HttpSecurityProfile.defaults().ipPolicy(), HttpSecurityProfile.defaults().rateLimit());

        final var runner = JettyServerFactory.create(config, new JettyRouteRegistry(),
                JsonCodecBuilder.strict().build(), null, List.of(), List.of(), securityProfile,
                new UuidRequestIdGenerator(), sample -> {
                });

        final var connector = (ServerConnector) runner.server().getConnectors()[0];
        final var httpConfig = connector.getConnectionFactory(HttpConnectionFactory.class).getHttpConfiguration();
        assertTrue(httpConfig.getCustomizers().stream().anyMatch(ForwardedRequestCustomizer.class::isInstance));
    }

    @Test
    void createShouldExposeBuiltinHelloAndHealthRoutes() throws Exception {
        final var config = configWithPort(0, false, false, 0, 0);
        final var runner = JettyServerFactory.create(config, new JettyRouteRegistry(),
                JsonCodecBuilder.strict().build());

        runner.start();
        try {
            final var client = HttpClient.newHttpClient();

            final var helloResponse = send(client, runner, "/hello");
            assertEquals(200, helloResponse.statusCode());
            assertTrue(helloResponse.body().contains("\"message\":\"hello\""));

            final var healthResponse = send(client, runner, "/health");
            assertEquals(200, healthResponse.statusCode());
            assertTrue(healthResponse.body().contains("\"status\":\"UP\""));
        } finally {
            runner.stop();
        }
    }

    @Test
    void createShouldNotOverrideCustomBuiltinRoutePath() throws Exception {
        final var config = configWithPort(0, false, false, 0, 0);
        final var routeRegistry = new JettyRouteRegistry();
        routeRegistry.add("/health", new DelegatingResourceHandler("/health", new CustomHealthResource(),
                JsonCodecBuilder.strict().build()));

        final var runner = JettyServerFactory.create(config, routeRegistry, JsonCodecBuilder.strict().build());

        runner.start();
        try {
            final var response = send(HttpClient.newHttpClient(), runner, "/health");
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"status\":\"CUSTOM\""));
        } finally {
            runner.stop();
        }
    }

    private static JettyServerConfig config(final boolean trustForwardHeaders, final boolean forwardedOnly,
            final int maxConcurrentRequests, final int maxRequestsPerRemoteIp) {
        return configWithPort(8081, trustForwardHeaders, forwardedOnly, maxConcurrentRequests, maxRequestsPerRemoteIp);
    }

    private static JettyServerConfig configWithPort(final int port, final boolean trustForwardHeaders,
            final boolean forwardedOnly, final int maxConcurrentRequests, final int maxRequestsPerRemoteIp) {
        return new JettyServerConfig("127.0.0.1", port, 24, 8, 10_000, "ether-http-test", "test", 256, true, true,
                15_000L, 2_000L, trustForwardHeaders, forwardedOnly, 8 * 1024, 32 * 1024, 12 * 1024, 16 * 1024, 256L,
                0L, 7, 4, 2L * 1024L * 1024L, 4L * 1024L * 1024L, maxConcurrentRequests, 64, 5_000L,
                maxRequestsPerRemoteIp);
    }

    @SuppressWarnings("unchecked")
    private static <T> T findHandler(final Handler root, final Class<T> type) {
        Handler current = root;
        while (current != null) {
            if (type.isInstance(current)) {
                return (T) current;
            }
            if (current instanceof Handler.Wrapper wrapper) {
                current = wrapper.getHandler();
            } else {
                current = null;
            }
        }
        return null;
    }

    private static HttpResponse<String> send(final HttpClient client, final JettyServerRunner runner, final String path)
            throws Exception {
        final var connector = (ServerConnector) runner.server().getConnectors()[0];
        final var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + connector.getLocalPort() + path))
                .GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static final class CustomHealthResource implements dev.rafex.ether.http.core.HttpResource {

        @Override
        public boolean get(final HttpExchange x) {
            x.json(200, Map.of("status", "CUSTOM"));
            return true;
        }

        @Override
        public java.util.Set<String> supportedMethods() {
            return java.util.Set.of("GET");
        }
    }
}
