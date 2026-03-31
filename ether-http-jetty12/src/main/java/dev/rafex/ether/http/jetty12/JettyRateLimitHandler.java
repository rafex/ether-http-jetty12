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

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

import dev.rafex.ether.http.jetty12.response.JettyApiErrorResponses;
import dev.rafex.ether.http.security.proxy.TrustedProxyPolicy;
import dev.rafex.ether.http.security.ratelimit.RateLimitPolicy;

final class JettyRateLimitHandler extends Handler.Wrapper {

    private final RateLimitPolicy policy;
    private final TrustedProxyPolicy trustedProxyPolicy;
    private final JettyApiErrorResponses errorResponses;
    private final Clock clock;
    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

    JettyRateLimitHandler(final Handler next, final RateLimitPolicy policy, final TrustedProxyPolicy trustedProxyPolicy,
            final JettyApiErrorResponses errorResponses) {
        this(next, policy, trustedProxyPolicy, errorResponses, Clock.systemUTC());
    }

    JettyRateLimitHandler(final Handler next, final RateLimitPolicy policy, final TrustedProxyPolicy trustedProxyPolicy,
            final JettyApiErrorResponses errorResponses, final Clock clock) {
        super(next);
        this.policy = policy;
        this.trustedProxyPolicy = trustedProxyPolicy;
        this.errorResponses = errorResponses;
        this.clock = clock;
    }

    @Override
    public boolean handle(final Request request, final Response response, final Callback callback) throws Exception {
        final var decision = register(request);
        response.getHeaders().put("RateLimit-Limit", Integer.toString(decision.limit()));
        response.getHeaders().put("RateLimit-Remaining", Integer.toString(decision.remaining()));
        response.getHeaders().put("RateLimit-Reset", Long.toString(decision.resetAtEpochSecond()));

        if (!decision.allowed()) {
            errorResponses.error(response, callback, 429, "too_many_requests", "rate_limit_exceeded",
                    "request rate limit exceeded", request.getHttpURI().getPath());
            return true;
        }

        return super.handle(request, response, callback);
    }

    private Decision register(final Request request) {
        final long nowEpochSecond = clock.instant().getEpochSecond();
        final long windowSeconds = Math.max(1, policy.windowSeconds());
        final long windowStart = (nowEpochSecond / windowSeconds) * windowSeconds;
        final long resetAt = windowStart + windowSeconds;
        final int limit = Math.max(0, policy.maxRequests()) + Math.max(0, policy.burst());
        final String key = keyFor(request);

        final var counter = counters.compute(key, (ignored, existing) -> {
            if (existing == null || existing.windowStartEpochSecond != windowStart) {
                return new WindowCounter(windowStart, 1);
            }
            existing.count++;
            return existing;
        });

        final boolean allowed = counter.count <= limit;
        final int remaining = Math.max(0, limit - counter.count);
        return new Decision(allowed, limit, remaining, resetAt);
    }

    private String keyFor(final Request request) {
        if (policy.scope() == RateLimitPolicy.Scope.GLOBAL) {
            return "global";
        }
        final var clientIp = JettyRequestIpResolver.resolve(request, trustedProxyPolicy);
        return clientIp == null || clientIp.isBlank() ? "unknown" : clientIp;
    }

    private record Decision(boolean allowed, int limit, int remaining, long resetAtEpochSecond) {
    }

    private static final class WindowCounter {
        private final long windowStartEpochSecond;
        private int count;

        private WindowCounter(final long windowStartEpochSecond, final int count) {
            this.windowStartEpochSecond = windowStartEpochSecond;
            this.count = count;
        }
    }
}
