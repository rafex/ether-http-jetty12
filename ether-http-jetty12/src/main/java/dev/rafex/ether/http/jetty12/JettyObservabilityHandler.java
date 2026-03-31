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

import java.time.Instant;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

import dev.rafex.ether.observability.core.request.RequestIdGenerator;
import dev.rafex.ether.observability.core.timing.TimingRecorder;
import dev.rafex.ether.observability.core.timing.TimingSample;

final class JettyObservabilityHandler extends Handler.Wrapper {

    static final String REQUEST_ID_ATTRIBUTE = "ether.request.id";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    private final RequestIdGenerator requestIdGenerator;
    private final TimingRecorder timingRecorder;

    JettyObservabilityHandler(final Handler next, final RequestIdGenerator requestIdGenerator,
            final TimingRecorder timingRecorder) {
        super(next);
        this.requestIdGenerator = requestIdGenerator;
        this.timingRecorder = timingRecorder;
    }

    @Override
    public boolean handle(final Request request, final Response response, final Callback callback) throws Exception {
        final var requestId = resolveRequestId(request);
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        response.getHeaders().put(REQUEST_ID_HEADER, requestId);

        final var startedAt = Instant.now();
        Request.addCompletionListener(request, failure -> timingRecorder.record(new TimingSample(
                request.getMethod() + " " + request.getHttpURI().getPath(), startedAt, Instant.now())));
        return super.handle(request, response, callback);
    }

    private String resolveRequestId(final Request request) {
        final var incoming = request.getHeaders().get(REQUEST_ID_HEADER);
        if (incoming != null && !incoming.isBlank()) {
            return incoming;
        }
        return requestIdGenerator.nextId();
    }
}
