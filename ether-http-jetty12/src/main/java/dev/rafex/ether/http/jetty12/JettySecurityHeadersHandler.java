package dev.rafex.ether.http.jetty12;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

import dev.rafex.ether.http.security.headers.SecurityHeadersPolicy;

final class JettySecurityHeadersHandler extends Handler.Wrapper {

    private final SecurityHeadersPolicy policy;

    JettySecurityHeadersHandler(final Handler next, final SecurityHeadersPolicy policy) {
        super(next);
        this.policy = policy;
    }

    @Override
    public boolean handle(final Request request, final Response response, final Callback callback) throws Exception {
        final boolean handled = super.handle(request, response, callback);
        for (final var entry : policy.headers().entrySet()) {
            if (response.getHeaders().get(entry.getKey()) == null) {
                response.getHeaders().put(entry.getKey(), entry.getValue());
            }
        }
        return handled;
    }
}
