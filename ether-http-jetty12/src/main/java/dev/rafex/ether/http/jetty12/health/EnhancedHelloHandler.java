package dev.rafex.ether.http.jetty12.health;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.rafex.ether.http.core.HttpExchange;
import dev.rafex.ether.http.core.Route;
import dev.rafex.ether.http.jetty12.exchange.JettyHttpExchange;
import dev.rafex.ether.http.jetty12.handler.NonBlockingResourceHandler;
import dev.rafex.ether.http.jetty12.response.JettyApiResponses;
import dev.rafex.ether.json.JsonCodec;
import dev.rafex.ether.json.JsonUtils;

public class EnhancedHelloHandler extends NonBlockingResourceHandler {

    private static final JsonCodec JSON_CODEC = JsonUtils.codec();
    private static final JettyApiResponses RESPONSES = new JettyApiResponses(JSON_CODEC);

    private static final Set<String> ALL_METHODS = Set.of("GET", "POST", "PUT", "DELETE", "PATCH");

    // Top 10 most spoken languages by number of speakers
    // Format: "greeting" / "greeting, {name}!"
    private static final Map<String, String[]> GREETINGS = Map.of(
        "en", new String[] { "Hello",     "Hello, %s!"     }, // English
        "zh", new String[] { "你好",       "%s，你好！"      }, // Mandarin Chinese
        "hi", new String[] { "नमस्ते",    "नमस्ते, %s!"    }, // Hindi
        "es", new String[] { "Hola",      "¡Hola, %s!"     }, // Spanish
        "fr", new String[] { "Bonjour",   "Bonjour, %s!"   }, // French
        "ar", new String[] { "مرحبا",     "مرحبا، %s!"     }, // Arabic
        "bn", new String[] { "হ্যালো",    "হ্যালো, %s!"   }, // Bengali
        "ru", new String[] { "Привет",    "Привет, %s!"    }, // Russian
        "pt", new String[] { "Olá",       "Olá, %s!"       }, // Portuguese
        "ur", new String[] { "سلام",      "سلام، %s!"      }  // Urdu
    );

    private static final String DEFAULT_LANG = "en";

    public EnhancedHelloHandler() {
        super(JSON_CODEC);
    }

    @Override
    protected String basePath() {
        return "/hello";
    }

    @Override
    protected List<Route> routes() {
        return List.of(Route.of("/", ALL_METHODS));
    }

    @Override
    public Set<String> supportedMethods() {
        return ALL_METHODS;
    }

    @Override
    public boolean get(final HttpExchange x) {
        return respond(x, 200);
    }

    @Override
    public boolean post(final HttpExchange x) {
        return respond(x, 201);
    }

    @Override
    public boolean put(final HttpExchange x) {
        return respond(x, 200);
    }

    @Override
    public boolean delete(final HttpExchange x) {
        return respond(x, 200);
    }

    @Override
    public boolean patch(final HttpExchange x) {
        return respond(x, 200);
    }

    private static boolean respond(final HttpExchange x, final int status) {
        final var jx = asJetty(x);

        final var lang = resolve(x.queryFirst("lang"), DEFAULT_LANG);
        final var name = x.queryFirst("name");
        final var greeting = buildGreeting(lang, name);

        final var body = new LinkedHashMap<String, Object>();
        body.put("message", greeting);
        body.put("lang", lang);
        body.put("service", "ether");
        body.put("method", x.method());
        body.put("path", x.path());
        body.put("timestamp", Instant.now().toString());

        RESPONSES.json(jx.response(), jx.callback(), status, body);
        return true;
    }

    private static String buildGreeting(final String lang, final String name) {
        final var phrases = GREETINGS.getOrDefault(lang, GREETINGS.get(DEFAULT_LANG));
        if (name != null && !name.isBlank()) {
            return String.format(phrases[1], name.trim());
        }
        return phrases[0];
    }

    private static String resolve(final String value, final String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        final var normalized = value.trim().toLowerCase();
        return GREETINGS.containsKey(normalized) ? normalized : fallback;
    }

    private static JettyHttpExchange asJetty(final HttpExchange x) {
        return (JettyHttpExchange) x;
    }
}
