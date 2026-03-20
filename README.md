# ether-http-jetty12

Jetty 12 transport adapter for `ether-http-core`.

## Scope

- `JettyHttpExchange` implementation
- JSON powered by `ether-json` (`dev.rafex.ether.json.JsonCodec`)
- Response helpers (`JettyApiResponses`, `JettyApiErrorResponses`)
- Base handlers for resource routing/dispatch
- Jetty route registry primitives
- Server wiring for Jetty (`JettyServerFactory`, `JettyServerRunner`)
- Auth pipeline (`JettyAuthHandler`, `TokenVerifier`, `JettyAuthPolicyRegistry`)
- Middleware pipeline (`JettyMiddleware`, `JettyMiddlewareRegistry`)
- Transport-level errors (`JettyTransportException`, `JettyTransportRuntimeException`)

This module may depend on `org.eclipse.jetty:*`.

## Current integration

- `JettyServerConfig.fromEnv()` for env-based bootstrap
- `JettyServerConfig.fromConfig(EtherConfig)` for typed config via `ether-config`
- Built-in `GET /hello` and `GET /health` routes backed by `ether-http-core`
- `HttpSecurityProfile` mapped to Jetty handlers for:
  - CORS
  - security headers
  - IP allow/deny
  - trusted proxy resolution
  - rate limiting
- `Problem Details` rendering for framework-generated errors and `ProblemException`
- `RequestIdGenerator` and `TimingRecorder` from `ether-observability-core`

## Hardening and limits

- Request/response body limits, header limits, QoS and graceful shutdown are configured in `JettyServerFactory`.
- Rate limiting is intentionally local and in-memory for now.
- Trusted proxy support is best effort within Jetty's available APIs. The adapter resolves client IPs using `TrustedProxyPolicy`, and also enables forwarded-header customization when configured.

## Kiwi compatibility

This module includes a server structure intentionally close to Kiwi transport:

- Config from env: `JettyServerConfig.fromEnv()`
- Route registry: `JettyRouteRegistry`
- Auth policies: `JettyAuthPolicyRegistry`
- Middlewares: `JettyMiddlewareRegistry`
- Factory + runner: `JettyServerFactory`, `JettyServerRunner`
- Module contract: `JettyModule`, `JettyModuleContext`

That lets Kiwi migrate incrementally by replacing its Jetty transport wiring first,
then moving handlers/resources progressively.
