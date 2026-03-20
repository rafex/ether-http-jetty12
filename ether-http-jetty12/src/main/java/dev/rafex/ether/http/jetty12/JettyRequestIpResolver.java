package dev.rafex.ether.http.jetty12;

import java.util.List;

import org.eclipse.jetty.server.Request;

import dev.rafex.ether.http.security.proxy.TrustedProxyPolicy;

final class JettyRequestIpResolver {

    private JettyRequestIpResolver() {
    }

    static String resolve(final Request request, final TrustedProxyPolicy policy) {
        final var remoteAddress = Request.getRemoteAddr(request);
        if (policy == null || !policy.trustForwardedHeader() || !policy.isTrusted(remoteAddress)) {
            return remoteAddress;
        }

        if (policy.forwardedOnly()) {
            final var forwarded = request.getHeaders().get("Forwarded");
            final var forwardedFor = resolveForwarded(forwarded, policy.preferRightMostForwardedFor());
            return forwardedFor == null ? remoteAddress : forwardedFor;
        }

        final var xForwardedFor = request.getHeaders().get("X-Forwarded-For");
        final var forwardedFor = selectFromCsv(xForwardedFor, policy.preferRightMostForwardedFor());
        return forwardedFor == null ? remoteAddress : forwardedFor;
    }

    private static String resolveForwarded(final String forwarded, final boolean preferRightMost) {
        if (forwarded == null || forwarded.isBlank()) {
            return null;
        }
        final List<String> entries = List.of(forwarded.split(","));
        if (entries.isEmpty()) {
            return null;
        }
        final int start = preferRightMost ? entries.size() - 1 : 0;
        final int step = preferRightMost ? -1 : 1;
        for (int i = start; i >= 0 && i < entries.size(); i += step) {
            final var parts = entries.get(i).trim().split(";");
            for (final var part : parts) {
                final var token = part.trim();
                if (token.regionMatches(true, 0, "for=", 0, 4)) {
                    return stripAddressDecorators(token.substring(4).trim());
                }
            }
        }
        return null;
    }

    private static String selectFromCsv(final String raw, final boolean preferRightMost) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        final var parts = raw.split(",");
        if (parts.length == 0) {
            return null;
        }
        final var candidate = preferRightMost ? parts[parts.length - 1] : parts[0];
        return stripAddressDecorators(candidate.trim());
    }

    private static String stripAddressDecorators(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        var sanitized = value;
        if (sanitized.startsWith("\"") && sanitized.endsWith("\"") && sanitized.length() > 1) {
            sanitized = sanitized.substring(1, sanitized.length() - 1);
        }
        return sanitized;
    }
}
