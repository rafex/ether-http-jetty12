package dev.rafex.ether.http.jetty12.handler;

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

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.MultiMap;
import org.eclipse.jetty.util.UrlEncoded;

import dev.rafex.ether.http.core.DefaultErrorMapper;
import dev.rafex.ether.http.core.ErrorMapper;
import dev.rafex.ether.http.core.HttpResource;
import dev.rafex.ether.http.core.Route;
import dev.rafex.ether.http.core.RouteMatcher;
import dev.rafex.ether.http.jetty12.exchange.JettyHttpExchange;
import dev.rafex.ether.http.jetty12.response.JettyApiErrorResponses;
import dev.rafex.ether.http.problem.exception.ProblemException;
import dev.rafex.ether.json.JsonCodec;

public abstract class ResourceHandler extends Handler.Abstract implements HttpResource {

    private final JsonCodec jsonCodec;
    private final ErrorMapper errorMapper;
    private final JettyApiErrorResponses errorResponses;

    protected ResourceHandler(final JsonCodec jsonCodec) {
        this(jsonCodec, new DefaultErrorMapper());
    }

    protected ResourceHandler(final JsonCodec jsonCodec, final ErrorMapper errorMapper) {
        this.jsonCodec = jsonCodec;
        this.errorMapper = errorMapper;
        this.errorResponses = new JettyApiErrorResponses(jsonCodec);
    }

    protected abstract String basePath();

    protected List<Route> routes() {
        return List.of(Route.of("/", supportedMethods()));
    }

    @Override
    public boolean handle(final Request request, final Response response, final Callback callback) {
        final var path = request.getHttpURI().getPath();
        if (!matchesBasePath(path)) {
            return false;
        }

        final var relPath = normalizeRelPath(path);
        final var match = RouteMatcher.match(relPath, routes());
        if (match.isEmpty()) {
            errorResponses.notFound(response, callback, path);
            return true;
        }

        final var routeMatch = match.get();
        final var x = new JettyHttpExchange(request, response, callback, routeMatch.pathParams(),
                parseQueryMap(request), routeMatch.route().allowedMethods(), jsonCodec);

        final var method = request.getMethod().toUpperCase();
        if (!routeMatch.route().allows(method) && !"OPTIONS".equals(method)) {
            x.methodNotAllowed();
            return true;
        }

        try {
            return dispatch(method, x);
        } catch (final ProblemException e) {
            errorResponses.problem(response, callback, e.problem());
            return true;
        } catch (final Exception e) {
            final var mapped = errorMapper.map(e);
            errorResponses.error(response, callback, mapped, path);
            return true;
        }
    }

    private boolean dispatch(final String method, final JettyHttpExchange x) throws Exception {
        return switch (method) {
        case "GET" -> get(x);
        case "POST" -> post(x);
        case "PUT" -> put(x);
        case "DELETE" -> delete(x);
        case "PATCH" -> patch(x);
        case "OPTIONS" -> options(x);
        default -> {
            x.methodNotAllowed();
            yield true;
        }
        };
    }

    private String normalizeRelPath(final String absolutePath) {
        final var base = basePath();
        if (absolutePath.length() == base.length()) {
            return "/";
        }
        final var rel = absolutePath.substring(base.length());
        return rel.isEmpty() ? "/" : rel;
    }

    private boolean matchesBasePath(final String path) {
        final var base = basePath();
        if ("/".equals(base)) {
            return path != null && path.startsWith("/");
        }
        if (base.equals(path)) {
            return true;
        }
        return path.startsWith(base + "/");
    }

    private static Map<String, List<String>> parseQueryMap(final Request request) {
        final MultiMap<String> params = new MultiMap<>();
        final var rawQuery = request.getHttpURI().getQuery();
        if (rawQuery != null && !rawQuery.isEmpty()) {
            UrlEncoded.decodeTo(rawQuery, params, StandardCharsets.UTF_8);
        }

        final var out = new LinkedHashMap<String, List<String>>();
        for (final var key : params.keySet()) {
            final var values = params.getValues(key);
            out.put(key, values == null ? List.of() : List.copyOf(values));
        }
        return out;
    }

    protected static String queryParam(final JettyHttpExchange x, final String key) {
        final var value = x.queryFirst(key);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
