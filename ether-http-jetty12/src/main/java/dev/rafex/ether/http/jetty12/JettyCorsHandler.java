package dev.rafex.ether.http.jetty12;

import java.util.Map;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

import dev.rafex.ether.http.jetty12.response.JettyApiResponses;
import dev.rafex.ether.http.security.cors.CorsPolicy;

final class JettyCorsHandler extends Handler.Wrapper {

    private final CorsPolicy policy;
    private final JettyApiResponses responses;

    JettyCorsHandler(final Handler next, final CorsPolicy policy, final JettyApiResponses responses) {
        super(next);
        this.policy = policy;
        this.responses = responses;
    }

    @Override
    public boolean handle(final Request request, final Response response, final Callback callback) throws Exception {
        final var origin = request.getHeaders().get("Origin");
        if (isPreflight(request)) {
            if (origin == null || !policy.isOriginAllowed(origin)) {
                responses.text(response, callback, 403, "forbidden");
                return true;
            }
            applyHeaders(response, policy.responseHeaders(origin));
            responses.noContent(response, callback, 204);
            return true;
        }

        final boolean handled = super.handle(request, response, callback);
        if (origin != null && policy.isOriginAllowed(origin)) {
            applyHeaders(response, policy.responseHeaders(origin));
        }
        return handled;
    }

    private static boolean isPreflight(final Request request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                && request.getHeaders().get("Access-Control-Request-Method") != null;
    }

    private static void applyHeaders(final Response response, final Map<String, String> headers) {
        for (final var entry : headers.entrySet()) {
            response.getHeaders().put(entry.getKey(), entry.getValue());
        }
    }
}
