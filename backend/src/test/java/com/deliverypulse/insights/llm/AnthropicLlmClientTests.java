package com.deliverypulse.insights.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.deliverypulse.insights.AiInsightsProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** Exercises the real SDK client against a local fake Messages API. No paid API access is needed. */
class AnthropicLlmClientTests {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final LlmPrompt PROMPT = new LlmPrompt("system rules", "<project_data>{}</project_data>");

    record Answer(String summary, List<String> taskIds) {
    }

    private HttpServer server;
    private final AtomicReference<Responder> responder = new AtomicReference<>();
    private final AtomicReference<CapturedRequest> captured = new AtomicReference<>();

    interface Responder {
        void respond(HttpExchange exchange) throws IOException;
    }

    record CapturedRequest(String path, String apiKey, String beta, JsonNode body) {
    }

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            captured.set(new CapturedRequest(exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("x-api-key"),
                    exchange.getRequestHeaders().getFirst("anthropic-beta"),
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
    void sendsAStructuredRequestAndParsesTheTypedResponse() throws Exception {
        responder.set(exchange -> send(exchange, 200, message("end_turn",
                "{\"summary\":\"AT-2 is overdue.\",\"taskIds\":[\"AT-2\"]}")));

        LlmResult<Answer> result = client(Duration.ofSeconds(5)).generate(PROMPT, Answer.class);

        assertThat(result.content()).isEqualTo(new Answer("AT-2 is overdue.", List.of("AT-2")));
        assertThat(result.model()).isEqualTo("claude-opus-5-5");
        assertThat(result.inputTokens()).isEqualTo(120);
        CapturedRequest request = captured.get();
        assertThat(request.path()).isEqualTo("/v1/messages");
        assertThat(request.apiKey()).isEqualTo("test-only-key");
        assertThat(request.beta()).contains("server-side-fallback-2026-07-01");
        assertThat(request.body().path("model").asText()).isEqualTo("claude-opus-5-5");
        assertThat(request.body().path("fallbacks").asText()).isEqualTo("default");
        assertThat(request.body().path("output_config").path("format").path("type").asText()).isEqualTo("json_schema");
        assertThat(request.body().toString()).contains("system rules", "project_data");
    }

    @Test
    void mapsRateLimitsToRateLimited() {
        responder.set(exchange -> send(exchange, 429, error("rate_limit_error", "Too many requests")));

        assertFailure(LlmFailure.RATE_LIMITED);
    }

    @Test
    void mapsOverloadedAndServerErrorsToUnavailable() {
        responder.set(exchange -> send(exchange, 529, error("overloaded_error", "Overloaded")));
        assertFailure(LlmFailure.UNAVAILABLE);

        responder.set(exchange -> send(exchange, 500, error("api_error", "Internal error")));
        assertFailure(LlmFailure.UNAVAILABLE);
    }

    @Test
    void mapsBillingErrorsToQuotaExceeded() {
        responder.set(exchange -> send(exchange, 402, error("billing_error", "Credit balance too low")));

        assertFailure(LlmFailure.QUOTA_EXCEEDED);
    }

    @Test
    void mapsRejectedCredentialsToAuthenticationFailed() {
        responder.set(exchange -> send(exchange, 401, error("authentication_error", "invalid x-api-key")));

        assertFailure(LlmFailure.AUTHENTICATION_FAILED);
    }

    @Test
    void mapsRefusalsAndTruncationToFailures() {
        responder.set(exchange -> send(exchange, 200, message("refusal", "")));
        assertFailure(LlmFailure.REFUSED);

        responder.set(exchange -> send(exchange, 200, message("max_tokens", "{\"summary\":\"cut")));
        assertFailure(LlmFailure.INVALID_RESPONSE);
    }

    @Test
    void mapsMalformedModelOutputToInvalidResponse() {
        responder.set(exchange -> send(exchange, 200, message("end_turn", "this is not json")));

        assertFailure(LlmFailure.INVALID_RESPONSE);
    }

    @Test
    void mapsSlowResponsesToTimeout() {
        responder.set(exchange -> {
            try {
                Thread.sleep(2_000);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            send(exchange, 200, message("end_turn", "{\"summary\":\"late\",\"taskIds\":[]}"));
        });

        assertThatThrownBy(() -> client(Duration.ofMillis(300)).generate(PROMPT, Answer.class))
                .isInstanceOfSatisfying(LlmException.class,
                        exception -> assertThat(exception.getFailure()).isEqualTo(LlmFailure.TIMEOUT));
    }

    @Test
    void isNotConfiguredWithoutAnApiKey() {
        AnthropicLlmClient client = new AnthropicLlmClient(properties("", Duration.ofSeconds(1)));

        assertThat(client.isConfigured()).isFalse();
        assertThatThrownBy(() -> client.generate(PROMPT, Answer.class))
                .isInstanceOfSatisfying(LlmException.class,
                        exception -> assertThat(exception.getFailure()).isEqualTo(LlmFailure.NOT_CONFIGURED));
    }

    private void assertFailure(LlmFailure expected) {
        assertThatThrownBy(() -> client(Duration.ofSeconds(5)).generate(PROMPT, Answer.class))
                .isInstanceOfSatisfying(LlmException.class,
                        exception -> assertThat(exception.getFailure()).isEqualTo(expected));
    }

    private AnthropicLlmClient client(Duration timeout) {
        return new AnthropicLlmClient(properties("test-only-key", timeout),
                "http://127.0.0.1:" + server.getAddress().getPort());
    }

    private static AiInsightsProperties properties(String apiKey, Duration timeout) {
        // maxRetries = 0 keeps the tests fast; production defaults to 2 SDK retries.
        return new AiInsightsProperties(true, "anthropic", apiKey, "claude-opus-5-5", timeout, 0, 16000, true,
                true, false, false, 200, 10, 5, 500);
    }

    private static String message(String stopReason, String text) throws IOException {
        return JSON.writeValueAsString(JSON.createObjectNode()
                .put("id", "msg_test").put("type", "message").put("role", "assistant")
                .put("model", "claude-opus-5-5").put("stop_reason", stopReason).putNull("stop_sequence")
                .<com.fasterxml.jackson.databind.node.ObjectNode>set("content", JSON.createArrayNode()
                        .add(JSON.createObjectNode().put("type", "text").put("text", text)))
                .set("usage", JSON.createObjectNode().put("input_tokens", 120).put("output_tokens", 40)));
    }

    private static String error(String type, String message) {
        return "{\"type\":\"error\",\"error\":{\"type\":\"" + type + "\",\"message\":\"" + message + "\"}}";
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("content-type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        } catch (IOException ignored) {
            // The client may have given up already (timeout test).
        }
    }
}
