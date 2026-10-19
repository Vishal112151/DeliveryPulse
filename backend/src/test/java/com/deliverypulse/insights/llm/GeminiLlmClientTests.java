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

import com.deliverypulse.insights.AiInsightDraft;
import com.deliverypulse.insights.AiInsightsProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** Exercises the Gemini client against a local fake generateContent API. No API key is needed. */
class GeminiLlmClientTests {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final LlmPrompt PROMPT = new LlmPrompt("system rules", "<project_data>{}</project_data>");

    record Answer(String summary, List<String> taskIds) {
    }

    interface Responder {
        void respond(HttpExchange exchange) throws IOException;
    }

    record CapturedRequest(String path, String apiKey, JsonNode body) {
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
                    exchange.getRequestHeaders().getFirst("x-goog-api-key"),
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
    void sendsSchemaConstrainedRequestAndParsesTheTypedResponse() throws Exception {
        responder.set(exchange -> send(exchange, 200,
                response("STOP", "{\"summary\":\"AT-2 is overdue.\",\"taskIds\":[\"AT-2\"]}")));

        LlmResult<Answer> result = client(Duration.ofSeconds(5), 0).generate(PROMPT, Answer.class);

        assertThat(result.content()).isEqualTo(new Answer("AT-2 is overdue.", List.of("AT-2")));
        assertThat(result.model()).isEqualTo("gemini-2.5-flash-001");
        assertThat(result.inputTokens()).isEqualTo(120);
        assertThat(result.outputTokens()).isEqualTo(40);
        CapturedRequest request = captured.get();
        assertThat(request.path()).isEqualTo("/v1beta/models/gemini-2.5-flash:generateContent");
        assertThat(request.apiKey()).isEqualTo("test-only-key");
        assertThat(request.body().at("/systemInstruction/parts/0/text").asText()).isEqualTo("system rules");
        assertThat(request.body().at("/contents/0/parts/0/text").asText()).contains("project_data");
        JsonNode config = request.body().path("generationConfig");
        assertThat(config.path("responseMimeType").asText()).isEqualTo("application/json");
        assertThat(config.at("/responseJsonSchema/properties/taskIds/type").asText()).isEqualTo("array");
        assertThat(config.at("/responseJsonSchema/required").toString()).contains("summary", "taskIds");
    }

    @Test
    void schemaForTheInsightDraftCarriesEnumsAndDescriptions() {
        JsonNode schema = JsonSchemas.of(AiInsightDraft.class);

        assertThat(schema.at("/properties/executiveSummary/items/properties/basis/enum").toString())
                .contains("FACT", "ASSUMPTION");
        assertThat(schema.at("/properties/recommendedActions/description").asText()).contains("next actions");
    }

    @Test
    void retriesTransientErrorsThenSucceeds() {
        AtomicInteger attempt = new AtomicInteger();
        responder.set(exchange -> {
            if (attempt.getAndIncrement() == 0) {
                send(exchange, 503, error(503, "UNAVAILABLE", "The model is overloaded."));
            } else {
                send(exchange, 200, response("STOP", "{\"summary\":\"ok\",\"taskIds\":[]}"));
            }
        });

        assertThat(client(Duration.ofSeconds(5), 2).generate(PROMPT, Answer.class).content().summary()).isEqualTo("ok");
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void mapsProviderErrorsToFailures() {
        responder.set(exchange -> send(exchange, 400, error(400, "INVALID_ARGUMENT",
                "API key not valid. Please pass a valid API key.")));
        assertFailure(LlmFailure.AUTHENTICATION_FAILED);

        responder.set(exchange -> send(exchange, 429, error(429, "RESOURCE_EXHAUSTED", "You exceeded your current quota.")));
        assertFailure(LlmFailure.RATE_LIMITED);

        responder.set(exchange -> send(exchange, 500, error(500, "INTERNAL", "Internal error")));
        assertFailure(LlmFailure.UNAVAILABLE);

        responder.set(exchange -> send(exchange, 404, error(404, "NOT_FOUND", "models/x is not found")));
        assertFailure(LlmFailure.UNAVAILABLE);
    }

    @Test
    void mapsSafetyBlocksTruncationAndBadJsonToFailures() {
        responder.set(exchange -> send(exchange, 200, response("SAFETY", "")));
        assertFailure(LlmFailure.REFUSED);

        responder.set(exchange -> send(exchange, 200, "{\"promptFeedback\":{\"blockReason\":\"OTHER\"}}"));
        assertFailure(LlmFailure.REFUSED);

        responder.set(exchange -> send(exchange, 200, response("MAX_TOKENS", "{\"summary\":\"cut")));
        assertFailure(LlmFailure.INVALID_RESPONSE);

        responder.set(exchange -> send(exchange, 200, response("STOP", "this is not json")));
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
            send(exchange, 200, response("STOP", "{\"summary\":\"late\",\"taskIds\":[]}"));
        });

        assertThatThrownBy(() -> client(Duration.ofMillis(300), 0).generate(PROMPT, Answer.class))
                .isInstanceOfSatisfying(LlmException.class,
                        exception -> assertThat(exception.getFailure()).isEqualTo(LlmFailure.TIMEOUT));
    }

    @Test
    void isNotConfiguredWithoutAnApiKeyAndDefaultsToAGeminiModel() {
        AiInsightsProperties properties = properties("", Duration.ofSeconds(1), 0, null);
        GeminiLlmClient client = new GeminiLlmClient(properties);

        assertThat(properties.model()).isEqualTo("gemini-2.5-flash");
        assertThat(client.isConfigured()).isFalse();
        assertThatThrownBy(() -> client.generate(PROMPT, Answer.class))
                .isInstanceOfSatisfying(LlmException.class,
                        exception -> assertThat(exception.getFailure()).isEqualTo(LlmFailure.NOT_CONFIGURED));
    }

    private void assertFailure(LlmFailure expected) {
        assertThatThrownBy(() -> client(Duration.ofSeconds(5), 0).generate(PROMPT, Answer.class))
                .isInstanceOfSatisfying(LlmException.class,
                        exception -> assertThat(exception.getFailure()).isEqualTo(expected));
    }

    private GeminiLlmClient client(Duration timeout, int maxRetries) {
        return new GeminiLlmClient(properties("test-only-key", timeout, maxRetries, "gemini-2.5-flash"),
                "http://127.0.0.1:" + server.getAddress().getPort(), Duration.ofMillis(10));
    }

    private static AiInsightsProperties properties(String apiKey, Duration timeout, int maxRetries, String model) {
        return new AiInsightsProperties(true, "gemini", apiKey, model, timeout, maxRetries, 16000, true,
                true, false, false, 200, 10, 5, 500);
    }

    private static String response(String finishReason, String text) throws IOException {
        return JSON.writeValueAsString(JSON.createObjectNode()
                .put("modelVersion", "gemini-2.5-flash-001")
                .<com.fasterxml.jackson.databind.node.ObjectNode>set("candidates", JSON.createArrayNode().add(
                        JSON.createObjectNode().put("finishReason", finishReason)
                                .set("content", JSON.createObjectNode().put("role", "model")
                                        .set("parts", JSON.createArrayNode()
                                                .add(JSON.createObjectNode().put("text", "thinking...").put("thought", true))
                                                .add(JSON.createObjectNode().put("text", text))))))
                .set("usageMetadata", JSON.createObjectNode().put("promptTokenCount", 120).put("candidatesTokenCount", 40)));
    }

    private static String error(int code, String status, String message) {
        return "{\"error\":{\"code\":" + code + ",\"status\":\"" + status + "\",\"message\":\"" + message + "\"}}";
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
