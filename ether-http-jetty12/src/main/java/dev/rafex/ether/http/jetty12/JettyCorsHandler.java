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
