package dev.rafex.ether.http.jetty12;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

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

import dev.rafex.ether.json.JsonCodecBuilder;

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

		final var connector = (ServerConnector)server.getConnectors()[0];
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

		final var connector = (ServerConnector)runner.server().getConnectors()[0];
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

	private static JettyServerConfig config(final boolean trustForwardHeaders, final boolean forwardedOnly,
			final int maxConcurrentRequests, final int maxRequestsPerRemoteIp) {
		return new JettyServerConfig(
				"127.0.0.1",
				8081,
				24,
				8,
				10_000,
				"ether-http-test",
				"test",
				256,
				true,
				true,
				15_000L,
				2_000L,
				trustForwardHeaders,
				forwardedOnly,
				8 * 1024,
				32 * 1024,
				12 * 1024,
				16 * 1024,
				256L,
				0L,
				7,
				4,
				2L * 1024L * 1024L,
				4L * 1024L * 1024L,
				maxConcurrentRequests,
				64,
				5_000L,
				maxRequestsPerRemoteIp);
	}

	@SuppressWarnings("unchecked")
	private static <T> T findHandler(final Handler root, final Class<T> type) {
		Handler current = root;
		while (current != null) {
			if (type.isInstance(current)) {
				return (T)current;
			}
			if (current instanceof Handler.Wrapper wrapper) {
				current = wrapper.getHandler();
			} else {
				current = null;
			}
		}
		return null;
	}
}
