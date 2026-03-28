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

import java.util.Objects;
import java.util.Set;

import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.HttpResource;
import dev.rafex.ether.http.jetty12.handler.ResourceHandler;
import dev.rafex.ether.json.JsonCodec;

final class DelegatingResourceHandler extends ResourceHandler {

    private final String basePath;
    private final HttpResource delegate;

    DelegatingResourceHandler(final String basePath, final HttpResource delegate, final JsonCodec jsonCodec) {
        super(jsonCodec);
        this.basePath = Objects.requireNonNull(basePath, "basePath");
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    protected String basePath() {
        return basePath;
    }

    @Override
    public boolean get(final HttpExchange x) throws Exception {
        return delegate.get(x);
    }

    @Override
    public boolean post(final HttpExchange x) throws Exception {
        return delegate.post(x);
    }

    @Override
    public boolean put(final HttpExchange x) throws Exception {
        return delegate.put(x);
    }

    @Override
    public boolean delete(final HttpExchange x) throws Exception {
        return delegate.delete(x);
    }

    @Override
    public boolean patch(final HttpExchange x) throws Exception {
        return delegate.patch(x);
    }

    @Override
    public boolean options(final HttpExchange x) throws Exception {
        return delegate.options(x);
    }

    @Override
    public Set<String> supportedMethods() {
        return delegate.supportedMethods();
    }
}
