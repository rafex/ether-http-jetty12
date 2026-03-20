package dev.rafex.ether.http.jetty12;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

import dev.rafex.ether.http.security.ip.IpPolicy;
import dev.rafex.ether.http.security.proxy.TrustedProxyPolicy;

final class JettyIpPolicyHandler extends Handler.Wrapper {

    private final IpPolicy ipPolicy;
    private final TrustedProxyPolicy trustedProxyPolicy;
    private final JettyApiErrorResponses errorResponses;

    JettyIpPolicyHandler(final Handler next, final IpPolicy ipPolicy, final TrustedProxyPolicy trustedProxyPolicy,
            final JettyApiErrorResponses errorResponses) {
        super(next);
        this.ipPolicy = ipPolicy;
        this.trustedProxyPolicy = trustedProxyPolicy;
        this.errorResponses = errorResponses;
    }

    @Override
    public boolean handle(final Request request, final Response response, final Callback callback) throws Exception {
        final var clientIp = JettyRequestIpResolver.resolve(request, trustedProxyPolicy);
        if (!ipPolicy.isAllowed(clientIp)) {
            errorResponses.forbidden(response, callback, "ip_not_allowed");
            return true;
        }
        return super.handle(request, response, callback);
    }
}
