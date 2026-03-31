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

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

import dev.rafex.ether.http.jetty12.response.JettyApiErrorResponses;
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
