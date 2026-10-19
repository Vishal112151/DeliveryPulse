package com.deliverypulse.insights.llm;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;

import com.deliverypulse.insights.AiInsightsProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Google Gemini implementation of {@link LlmClient} over the Generative Language REST API
 * ({@code models/{model}:generateContent}) with a JSON response schema. Retries 429/500/503 up to
 * {@code maxRetries} times with a short linear backoff.
 */
public class GeminiLlmClient implements LlmClient {

    static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com";
    private static final Set<String> REFUSAL_REASONS = Set.of(
            "SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII", "RECITATION", "IMAGE_SAFETY");
    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final AiInsightsProperties properties;
    private final String baseUrl;
    private final Duration retryDelay;
    private final HttpClient http;

    public GeminiLlmClient(AiInsightsProperties properties) {
        this(properties, DEFAULT_BASE_URL, Duration.ofSeconds(2));
    }

    /** {@code baseUrl} and {@code retryDelay} are only overridden by tests that use a local fake server. */
    GeminiLlmClient(AiInsightsProperties properties, String baseUrl, Duration retryDelay) {
        this.properties = properties;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.retryDelay = retryDelay;
        this.http = HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
    }

    @Override
    public String providerName() {
        return "gemini";
    }

    @Override
    public boolean isConfigured() {
        return properties.hasApiKey();
    }

    @Override
    public <T> LlmResult<T> generate(LlmPrompt prompt, Class<T> responseType) {
        if (!isConfigured()) {
            throw new LlmException(LlmFailure.NOT_CONFIGURED, "No Gemini API key is configured.");
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/v1beta/models/"
                        + URLEncoder.encode(properties.model(), StandardCharsets.UTF_8) + ":generateContent"))
                .timeout(properties.timeout())
                .header("x-goog-api-key", properties.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body(prompt, responseType).toString()))
                .build();
        HttpResponse<String> response = send(request);
        return parse(response.body(), responseType);
    }

    private ObjectNode body(LlmPrompt prompt, Class<?> responseType) {
        ObjectNode body = JSON.createObjectNode();
        body.putObject("systemInstruction").putArray("parts").addObject().put("text", prompt.system());
        ObjectNode user = body.putArray("contents").addObject();
        user.put("role", "user");
        user.putArray("parts").addObject().put("text", prompt.user());
        ObjectNode config = body.putObject("generationConfig");
        config.put("responseMimeType", "application/json");
        config.set("responseJsonSchema", JsonSchemas.of(responseType));
        config.put("maxOutputTokens", properties.maxOutputTokens());
        return body;
    }

    private HttpResponse<String> send(HttpRequest request) {
        for (int attempt = 0; ; attempt++) {
            HttpResponse<String> response;
            try {
                response = http.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (HttpTimeoutException exception) {
                throw new LlmException(LlmFailure.TIMEOUT, "The AI provider did not respond in time.", exception);
            } catch (IOException exception) {
                throw new LlmException(LlmFailure.UNAVAILABLE, "The AI provider could not be reached.", exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new LlmException(LlmFailure.UNAVAILABLE, "The AI request was interrupted.", exception);
            }
            int status = response.statusCode();
            if (status / 100 == 2) {
                return response;
            }
            boolean retryable = status == 429 || status == 500 || status == 503;
            if (!retryable || attempt >= properties.maxRetries()) {
                throw failure(status, response.body());
            }
            sleep(retryDelay.multipliedBy(attempt + 1));
        }
    }

    private <T> LlmResult<T> parse(String body, Class<T> responseType) {
        JsonNode root = readTree(body);
        String blocked = root.path("promptFeedback").path("blockReason").asText("");
        if (!blocked.isBlank()) {
            throw new LlmException(LlmFailure.REFUSED, "The model declined to answer this request.");
        }
        JsonNode candidate = root.path("candidates").path(0);
        String finishReason = candidate.path("finishReason").asText("");
        if (REFUSAL_REASONS.contains(finishReason)) {
            throw new LlmException(LlmFailure.REFUSED, "The model declined to answer this request.");
        }
        if (finishReason.equals("MAX_TOKENS")) {
            throw new LlmException(LlmFailure.INVALID_RESPONSE, "The model response was truncated.");
        }
        StringBuilder text = new StringBuilder();
        candidate.path("content").path("parts").forEach(part -> {
            if (!part.path("thought").asBoolean(false)) {
                text.append(part.path("text").asText(""));
            }
        });
        if (text.isEmpty()) {
            throw new LlmException(LlmFailure.INVALID_RESPONSE, "The model response contained no structured content.");
        }
        T content;
        try {
            content = JSON.readValue(text.toString(), responseType);
        } catch (IOException exception) {
            throw new LlmException(LlmFailure.INVALID_RESPONSE,
                    "The model response did not match the expected format.", exception);
        }
        JsonNode usage = root.path("usageMetadata");
        return new LlmResult<>(content, root.path("modelVersion").asText(properties.model()),
                usage.path("promptTokenCount").asLong(0), usage.path("candidatesTokenCount").asLong(0));
    }

    private static LlmException failure(int status, String body) {
        JsonNode error = readTreeQuietly(body).path("error");
        String message = error.path("message").asText("").toLowerCase(Locale.ROOT);
        if (status == 401 || status == 403 || message.contains("api key")) {
            return new LlmException(LlmFailure.AUTHENTICATION_FAILED, "The AI provider rejected the configured credentials.");
        }
        if (status == 429) {
            return message.contains("billing")
                    ? new LlmException(LlmFailure.QUOTA_EXCEEDED, "The AI provider account has no remaining quota or credit.")
                    : new LlmException(LlmFailure.RATE_LIMITED, "The AI provider rate limit or free-tier quota was reached.");
        }
        if (status == 504) {
            return new LlmException(LlmFailure.TIMEOUT, "The AI provider timed out.");
        }
        if (status == 500 || status == 503) {
            return new LlmException(LlmFailure.UNAVAILABLE, "The AI provider is temporarily unavailable.");
        }
        if (status == 404) {
            return new LlmException(LlmFailure.UNAVAILABLE,
                    "The AI provider does not know this model. Check AI_MODEL (for example gemini-2.5-flash).");
        }
        return new LlmException(LlmFailure.UNAVAILABLE, "The AI provider rejected the request (HTTP " + status + ").");
    }

    private static JsonNode readTree(String body) {
        try {
            return JSON.readTree(body);
        } catch (IOException exception) {
            throw new LlmException(LlmFailure.INVALID_RESPONSE, "The AI provider returned malformed JSON.", exception);
        }
    }

    private static JsonNode readTreeQuietly(String body) {
        try {
            return body == null || body.isBlank() ? JSON.createObjectNode() : JSON.readTree(body);
        } catch (IOException exception) {
            return JSON.createObjectNode();
        }
    }

    private static void sleep(Duration delay) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new LlmException(LlmFailure.UNAVAILABLE, "The AI request was interrupted.", exception);
        }
    }
}
