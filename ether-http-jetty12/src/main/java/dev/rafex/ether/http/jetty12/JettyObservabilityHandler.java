package dev.rafex.ether.http.jetty12;

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
