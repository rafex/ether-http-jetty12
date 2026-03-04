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
import java.util.LinkedHashMap;
import java.util.Objects;

import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

import dev.rafex.ether.json.JsonCodec;
import dev.rafex.ether.http.core.HttpError;

public final class JettyApiErrorResponses {

	private final JettyApiResponses responses;
	private final Clock clock;

	public JettyApiErrorResponses(final JsonCodec jsonCodec) {
		this(jsonCodec, Clock.systemUTC());
	}

	public JettyApiErrorResponses(final JsonCodec jsonCodec, final Clock clock) {
		this.responses = new JettyApiResponses(Objects.requireNonNull(jsonCodec));
		this.clock = Objects.requireNonNull(clock);
	}

	public void notFound(final Response response, final Callback callback) {
		error(response, callback, 404, "not_found", null, "resource not found", null);
	}

	public void notFound(final Response response, final Callback callback, final String path) {
		error(response, callback, 404, "not_found", null, "resource not found", path);
	}

	public void badRequest(final Response response, final Callback callback, final String message) {
		error(response, callback, 400, "bad_request", "bad_request", message, null);
	}

	public void unauthorized(final Response response, final Callback callback, final String code) {
		error(response, callback, 401, "unauthorized", code, null, null);
	}

	public void forbidden(final Response response, final Callback callback, final String code) {
		error(response, callback, 403, "forbidden", code, null, null);
	}

	public void internalServerError(final Response response, final Callback callback, final String message) {
		error(response, callback, 500, "internal_server_error", "internal_error", message, null);
	}

	public void error(final Response response, final Callback callback, final HttpError mapped) {
		error(response, callback, mapped, null);
	}

	public void error(final Response response, final Callback callback, final HttpError mapped, final String path) {
		if (mapped == null) {
			internalServerError(response, callback, "internal_error");
			return;
		}
		error(response, callback, mapped.status(), mapped.code(), null, mapped.message(), path);
	}

	public void error(final Response response, final Callback callback, final int status, final String error,
			final String code) {
		error(response, callback, status, error, code, null, null);
	}

	public void error(final Response response, final Callback callback, final int status, final String error,
			final String code, final String message) {
		error(response, callback, status, error, code, message, null);
	}

	public void error(final Response response, final Callback callback, final int status, final String error,
			final String code, final String message, final String path) {
		final var payload = new LinkedHashMap<String, Object>();
		payload.put("error", normalizeError(error));
		if (code != null && !code.isBlank()) {
			payload.put("code", code);
		}
		if (message != null && !message.isBlank()) {
			payload.put("message", message);
		}
		if (path != null && !path.isBlank()) {
			payload.put("path", path);
		}
		payload.put("timestamp", clock.instant().toString());
		responses.json(response, callback, status, payload);
	}

	private static String normalizeError(final String error) {
		if (error == null || error.isBlank()) {
			return "error";
		}
		return error;
	}
}
