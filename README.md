# ether-http-jetty12

Jetty 12 transport adapter for `ether-http-core`.

## Scope

- `JettyHttpExchange` implementation
- Base handlers for resource routing/dispatch
- Jetty route registry primitives
- Server wiring for Jetty (`JettyServerFactory`, `JettyServerRunner`)
- Auth pipeline (`JettyAuthHandler`, `TokenVerifier`, `JettyAuthPolicyRegistry`)
- Middleware pipeline (`JettyMiddleware`, `JettyMiddlewareRegistry`)

This module may depend on `org.eclipse.jetty:*`.

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
