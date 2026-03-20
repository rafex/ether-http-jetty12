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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.json.JsonCodec;

public final class JettyHttpExchange implements HttpExchange {

    private final Request request;
    private final Response response;
    private final Callback callback;
    private final Map<String, String> pathParams;
    private final Map<String, List<String>> queryParams;
    private final Set<String> allowedMethods;
    private final JettyApiResponses apiResponses;

    public JettyHttpExchange(final Request request, final Response response, final Callback callback,
            final Map<String, String> pathParams, final Map<String, List<String>> queryParams,
            final Set<String> allowedMethods, final JsonCodec jsonCodec) {
        this.request = request;
        this.response = response;
        this.callback = callback;
        this.pathParams = pathParams;
        this.queryParams = queryParams;
        this.allowedMethods = normalizeMethods(allowedMethods);
        this.apiResponses = new JettyApiResponses(jsonCodec);
    }

    public Request request() {
        return request;
    }

    public Response response() {
        return response;
    }

    public Callback callback() {
        return callback;
    }

    @Override
    public String method() {
        return request.getMethod();
    }

    @Override
    public String path() {
        return request.getHttpURI().getPath();
    }

    @Override
    public String pathParam(final String name) {
        return pathParams.get(name);
    }

    @Override
    public String queryFirst(final String name) {
        final var values = queryParams.get(name);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(0);
    }

    @Override
    public List<String> queryAll(final String name) {
        return queryParams.getOrDefault(name, List.of());
    }

    @Override
    public Map<String, String> pathParams() {
        return Collections.unmodifiableMap(pathParams);
    }

    @Override
    public Map<String, List<String>> queryParams() {
        return Collections.unmodifiableMap(queryParams);
    }

    @Override
    public Set<String> allowedMethods() {
        return Collections.unmodifiableSet(allowedMethods);
    }

    @Override
    public void json(final int status, final Object body) {
        apiResponses.json(response, callback, status, body);
    }

    @Override
    public void text(final int status, final String body) {
        apiResponses.text(response, callback, status, body);
    }

    @Override
    public void noContent(final int status) {
        apiResponses.noContent(response, callback, status);
    }

    @Override
    public void methodNotAllowed() {
        response.getHeaders().put("Allow", String.join(", ", allowedMethods));
        HttpExchange.super.methodNotAllowed();
    }

    @Override
    public void options() {
        response.getHeaders().put("Allow", String.join(", ", allowedMethods));
        HttpExchange.super.options();
    }

    private static Set<String> normalizeMethods(final Set<String> methods) {
        final var out = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        if (methods != null) {
            for (final var method : methods) {
                if (method != null && !method.isBlank()) {
                    out.add(method.trim().toUpperCase());
                }
            }
        }
        out.add("OPTIONS");
        return out;
    }
}
