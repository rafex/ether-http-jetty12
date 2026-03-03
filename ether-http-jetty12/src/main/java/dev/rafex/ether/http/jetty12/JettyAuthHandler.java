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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.eclipse.jetty.http.pathmap.PathSpec;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;

import dev.rafex.ether.http.core.AuthPolicy;

public final class JettyAuthHandler extends Handler.Wrapper {

	public static final String REQ_ATTR_AUTH = "auth";

	record Rule(String method, PathSpec pathSpec) {
	}

	private final TokenVerifier tokenVerifier;
	private final JsonCodec jsonCodec;
	private final List<Rule> publicRules = new ArrayList<>();
	private final List<PathSpec> protectedPrefixes = new ArrayList<>();

	public JettyAuthHandler(final Handler delegate, final TokenVerifier tokenVerifier, final JsonCodec jsonCodec) {
		super(delegate);
		this.tokenVerifier = Objects.requireNonNull(tokenVerifier);
		this.jsonCodec = Objects.requireNonNull(jsonCodec);
	}

	public JettyAuthHandler publicPath(final String method, final String pathSpec) {
		publicRules.add(new Rule(method.toUpperCase(), PathSpec.from(pathSpec)));
		return this;
	}

	public JettyAuthHandler protectedPrefix(final String pathSpec) {
		protectedPrefixes.add(PathSpec.from(pathSpec));
		return this;
	}

	public JettyAuthHandler authPolicy(final AuthPolicy policy) {
		if (policy == null) {
			return this;
		}
		if (policy.type() == AuthPolicy.Type.PUBLIC_PATH) {
			return publicPath(policy.method(), policy.pathSpec());
		}
		return protectedPrefix(policy.pathSpec());
	}

	public JettyAuthHandler authPolicies(final List<AuthPolicy> policies) {
		if (policies == null) {
			return this;
		}
		for (final var policy : policies) {
			authPolicy(policy);
		}
		return this;
	}

	@Override
	public boolean handle(final Request request, final Response response, final Callback callback) throws Exception {
		final var method = request.getMethod().toUpperCase();
		final var path = request.getHttpURI() != null ? request.getHttpURI().getPath() : null;
		if (path == null) {
			JettyResponseUtil.json(response, callback, jsonCodec, 400,
					Map.of("error", "bad_request", "message", "missing_path"));
			return true;
		}

		if (isPublic(method, path) || !isProtected(path)) {
			return super.handle(request, response, callback);
		}

		final var authz = request.getHeaders().get("authorization");
		if (authz == null || !authz.startsWith("Bearer ")) {
			JettyResponseUtil.json(response, callback, jsonCodec, 401,
					Map.of("error", "unauthorized", "code", "missing_bearer_token"));
			return true;
		}

		final var token = authz.substring("Bearer ".length()).trim();
		final var verification = tokenVerifier.verify(token, Instant.now().getEpochSecond());
		if (!verification.ok()) {
			final var code = verification.code() == null || verification.code().isBlank() ? "invalid_token"
					: verification.code();
			JettyResponseUtil.json(response, callback, jsonCodec, 401, Map.of("error", "unauthorized", "code", code));
			return true;
		}

		request.setAttribute(REQ_ATTR_AUTH, verification.context());
		return super.handle(request, response, callback);
	}

	private boolean isPublic(final String method, final String path) {
		for (final var rule : publicRules) {
			if (rule.method().equals(method) && rule.pathSpec().matches(path)) {
				return true;
			}
		}
		return false;
	}

	private boolean isProtected(final String path) {
		for (final var p : protectedPrefixes) {
			if (p.matches(path)) {
				return true;
			}
		}
		return false;
	}
}
