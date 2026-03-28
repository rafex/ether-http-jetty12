package dev.rafex.ether.http.jetty12.response;

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

import java.util.Objects;

import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

import dev.rafex.ether.json.JsonCodec;

public final class JettyApiResponses {

    private final JsonCodec jsonCodec;

    public JettyApiResponses(final JsonCodec jsonCodec) {
        this.jsonCodec = Objects.requireNonNull(jsonCodec);
    }

    public void json(final Response response, final Callback callback, final int status, final Object body) {
        JettyResponseUtil.json(response, callback, jsonCodec, status, body);
    }

    public void jsonOrThrow(final Response response, final Callback callback, final int status, final Object body)
            throws JettyTransportException {
        JettyResponseUtil.jsonOrThrow(response, callback, jsonCodec, status, body);
    }

    public void text(final Response response, final Callback callback, final int status, final String body) {
        JettyResponseUtil.text(response, callback, status, body);
    }

    public void noContent(final Response response, final Callback callback, final int status) {
        JettyResponseUtil.noContent(response, callback, status);
    }

    public void ok(final Response response, final Callback callback, final Object body) {
        json(response, callback, 200, body);
    }

    public void created(final Response response, final Callback callback, final Object body) {
        json(response, callback, 201, body);
    }

    public void okNoContent(final Response response, final Callback callback) {
        noContent(response, callback, 204);
    }
}
