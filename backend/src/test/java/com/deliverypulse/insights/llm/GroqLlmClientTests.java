package com.deliverypulse.insights.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.deliverypulse.insights.AiInsightsProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** Exercises the Groq client against a local fake Chat Completions API. No API key is needed. */
class GroqLlmClientTests {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final LlmPrompt PROMPT = new LlmPrompt("system rules", "<project_data>{}</project_data>");

    record Answer(String summary, List<String> taskIds) {
    }

    interface Responder {
        void respond(HttpExchange exchange) throws IOException;
    }

    record CapturedRequest(String path, String authorization, JsonNode body) {
    }

    private HttpServer server;
    private final AtomicReference<Responder> responder = new AtomicReference<>();
    private final AtomicReference<CapturedRequest> captured = new AtomicReference<>();
    private final AtomicInteger calls = new AtomicInteger();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            byte[] body = exchange.getRequestBody().readAllBytes();
            captured.set(new CapturedRequest(exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    body.length == 0 ? null : JSON.readTree(body)));
            responder.get().respond(exchange);
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void sendsJsonModeRequestWithSchemaAndParsesTheTypedResponse() throws Exception {
        responder.set(exchange -> send(exchange, 200,
                response("stop", "{\"summary\":\"AT-2 is overdue.\",\"taskIds\":[\"AT-2\"]}")));

        LlmResult<Answer> result = client(0).generate(PROMPT, Answer.class);

        assertThat(result.content()).isEqualTo(new Answer("AT-2 is overdue.", List.of("AT-2")));
        assertThat(result.model()).isEqualTo("llama-3.3-70b-versatile");
        assertThat(result.inputTokens()).isEqualTo(120);
        assertThat(result.outputTokens()).isEqualTo(40);
        CapturedRequest request = captured.get();
        assertThat(request.path()).isEqualTo("/v1/chat/completions");
        assertThat(request.authorization()).isEqualTo("Bearer test-only-key");
        assertThat(request.body().path("model").asText()).isEqualTo("llama-3.3-70b-versatile");
        assertThat(request.body().at("/response_format/type").asText()).isEqualTo("json_object");
        assertThat(request.body().path("max_completion_tokens").asLong()).isEqualTo(GroqLlmClient.MAX_COMPLETION_TOKENS);
        assertThat(request.body().at("/messages/0/content").asText()).startsWith("system rules").contains("taskIds");
        assertThat(request.body().at("/messages/1/content").asText()).contains("project_data");
    }

    @Test
    void retriesRateLimitsThenSucceeds() {
        AtomicInteger attempt = new AtomicInteger();
        responder.set(exchange -> {
            if (attempt.getAndIncrement() == 0) {
                send(exchange, 429, error("rate_limit_exceeded"));
            } else {
                send(exchange, 200, response("stop", "{\"summary\":\"ok\",\"taskIds\":[]}"));
            }
        });

        assertThat(client(2).generate(PROMPT, Answer.class).content().summary()).isEqualTo("ok");
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void mapsProviderErrorsAndBadOutputToFailures() {
        responder.set(exchange -> send(exchange, 401, error("invalid_api_key")));
        assertFailure(LlmFailure.AUTHENTICATION_FAILED);

        responder.set(exchange -> send(exchange, 429, error("rate_limit_exceeded")));
        assertFailure(LlmFailure.RATE_LIMITED);

        responder.set(exchange -> send(exchange, 404, error("model_not_found")));
        assertFailure(LlmFailure.UNAVAILABLE);

        responder.set(exchange -> send(exchange, 400, error("json_validate_failed")));
        assertFailure(LlmFailure.INVALID_RESPONSE);

        responder.set(exchange -> send(exchange, 200, response("length", "{\"summary\":\"cut")));
        assertFailure(LlmFailure.INVALID_RESPONSE);

        responder.set(exchange -> send(exchange, 200, response("stop", "this is not json")));
        assertFailure(LlmFailure.INVALID_RESPONSE);
    }

    @Test
    void defaultsToTheGroqModelWhenNoneIsSet() {
        AiInsightsProperties properties = properties("", null);

        assertThat(properties.model()).isEqualTo("llama-3.3-70b-versatile");
        assertThat(new GroqLlmClient(properties).isConfigured()).isFalse();
    }

    private void assertFailure(LlmFailure expected) {
        assertThatThrownBy(() -> client(0).generate(PROMPT, Answer.class))
                .isInstanceOfSatisfying(LlmException.class,
                        exception -> assertThat(exception.getFailure()).isEqualTo(expected));
    }

    private GroqLlmClient client(int maxRetries) {
        AiInsightsProperties properties = new AiInsightsProperties(true, "groq", "test-only-key", null,
                Duration.ofSeconds(5), maxRetries, 16000, true, true, false, false, 200, 10, 5, 500);
        return new GroqLlmClient(properties, "http://127.0.0.1:" + server.getAddress().getPort(), Duration.ofMillis(1));
    }

    private static AiInsightsProperties properties(String apiKey, String model) {
        return new AiInsightsProperties(true, "groq", apiKey, model, Duration.ofSeconds(1), 0, 16000, true,
                true, false, false, 200, 10, 5, 500);
    }

    private static String response(String finishReason, String text) throws IOException {
        var root = JSON.createObjectNode();
        root.put("model", "llama-3.3-70b-versatile");
        var choice = root.putArray("choices").addObject();
        choice.put("finish_reason", finishReason);
        choice.putObject("message").put("role", "assistant").put("content", text);
        root.putObject("usage").put("prompt_tokens", 120).put("completion_tokens", 40);
        return JSON.writeValueAsString(root);
    }

    private static String error(String code) {
        return "{\"error\":{\"message\":\"test\",\"type\":\"invalid_request_error\",\"code\":\"" + code + "\"}}";
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
