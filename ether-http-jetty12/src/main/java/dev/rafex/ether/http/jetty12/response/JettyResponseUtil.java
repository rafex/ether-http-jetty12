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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

import dev.rafex.ether.json.JsonCodec;

final class JettyResponseUtil {

    private JettyResponseUtil() {
    }

    static void json(final Response response, final Callback callback, final JsonCodec codec, final int status,
            final Object body) {
        try {
            jsonOrThrow(response, callback, codec, status, body);
        } catch (final JettyTransportException e) {
            throw new JettyTransportRuntimeException("Error writing JSON response", e);
        }
    }

    static void jsonOrThrow(final Response response, final Callback callback, final JsonCodec codec, final int status,
            final Object body) throws JettyTransportException {
        response.setStatus(status);
        response.getHeaders().put("content-type", "application/json; charset=utf-8");
        final var jsonBody = toJsonBody(codec, body);
        writeUtf8(response, callback, jsonBody);
    }

    static void text(final Response response, final Callback callback, final int status, final String body) {
        response.setStatus(status);
        response.getHeaders().put("content-type", "text/plain; charset=utf-8");
        writeUtf8(response, callback, body == null ? "" : body);
    }

    static void noContent(final Response response, final Callback callback, final int status) {
        response.setStatus(status);
        callback.succeeded();
    }

    private static void writeUtf8(final Response response, final Callback callback, final String body) {
        final var bytes = body.getBytes(StandardCharsets.UTF_8);
        response.write(true, ByteBuffer.wrap(bytes), callback);
    }

    private static String toJsonBody(final JsonCodec codec, final Object body) throws JettyTransportException {
        if (body instanceof final String s) {
            return s;
        }
        try {
            return Objects.requireNonNull(codec).toJson(body);
        } catch (final RuntimeException e) {
            throw new JettyTransportException("Error serializing JSON payload", e);
        }
    }
}
