package com.deliverypulse.insights.llm;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Locale;

import com.deliverypulse.insights.AiInsightsProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Groq implementation of {@link LlmClient} over its OpenAI-compatible Chat Completions API. Uses JSON mode
 * (supported by every Groq chat model) with the response schema in the system message; the caller validates
 * the parsed result. Retries 429/500/502/503 up to {@code maxRetries} times with a short linear backoff.
 */
public class GroqLlmClient implements LlmClient {

    static final String DEFAULT_BASE_URL = "https://api.groq.com/openai";
    // Groq's free tier counts max_tokens against a per-minute token budget, so a large cap is rejected outright.
    static final long MAX_COMPLETION_TOKENS = 4096;
    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final AiInsightsProperties properties;
    private final String baseUrl;
    private final Duration retryDelay;
    private final HttpClient http;

    public GroqLlmClient(AiInsightsProperties properties) {
        this(properties, DEFAULT_BASE_URL, Duration.ofSeconds(2));
    }

    /** {@code baseUrl} and {@code retryDelay} are only overridden by tests that use a local fake server. */
    GroqLlmClient(AiInsightsProperties properties, String baseUrl, Duration retryDelay) {
        this.properties = properties;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.retryDelay = retryDelay;
        this.http = HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
    }

    @Override
    public String providerName() {
        return "groq";
    }

    @Override
    public boolean isConfigured() {
        return properties.hasApiKey();
    }

    @Override
    public <T> LlmResult<T> generate(LlmPrompt prompt, Class<T> responseType) {
        if (!isConfigured()) {
            throw new LlmException(LlmFailure.NOT_CONFIGURED, "No Groq API key is configured.");
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/chat/completions"))
                .timeout(properties.timeout())
                .header("Authorization", "Bearer " + properties.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body(prompt, responseType).toString()))
                .build();
        return parse(send(request).body(), responseType);
    }

    private ObjectNode body(LlmPrompt prompt, Class<?> responseType) {
        ObjectNode body = JSON.createObjectNode();
        body.put("model", properties.model());
        var messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", prompt.system()
                + "\n\nRespond with a single JSON object that matches this JSON Schema exactly:\n"
                + JsonSchemas.of(responseType));
        messages.addObject().put("role", "user").put("content", prompt.user());
        body.putObject("response_format").put("type", "json_object");
        body.put("max_completion_tokens", Math.min(properties.maxOutputTokens(), MAX_COMPLETION_TOKENS));
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
            boolean retryable = status == 429 || status == 500 || status == 502 || status == 503;
            if (!retryable || attempt >= properties.maxRetries()) {
                throw failure(status, response.body());
            }
            sleep(retryDelay.multipliedBy(attempt + 1));
        }
    }

    private <T> LlmResult<T> parse(String body, Class<T> responseType) {
        JsonNode root;
        try {
            root = JSON.readTree(body);
        } catch (IOException exception) {
            throw new LlmException(LlmFailure.INVALID_RESPONSE, "The AI provider returned malformed JSON.", exception);
        }
        JsonNode choice = root.path("choices").path(0);
        if (choice.path("finish_reason").asText("").equals("length")) {
            throw new LlmException(LlmFailure.INVALID_RESPONSE, "The model response was truncated.");
        }
        String text = choice.path("message").path("content").asText("");
        if (text.isBlank()) {
            throw new LlmException(LlmFailure.INVALID_RESPONSE, "The model response contained no structured content.");
        }
        T content;
        try {
            content = JSON.readValue(text, responseType);
        } catch (IOException exception) {
            throw new LlmException(LlmFailure.INVALID_RESPONSE,
                    "The model response did not match the expected format.", exception);
        }
        JsonNode usage = root.path("usage");
        return new LlmResult<>(content, root.path("model").asText(properties.model()),
                usage.path("prompt_tokens").asLong(0), usage.path("completion_tokens").asLong(0));
    }

    private static LlmException failure(int status, String body) {
        JsonNode error = readTreeQuietly(body).path("error");
        String code = error.path("code").asText("").toLowerCase(Locale.ROOT);
        if (status == 401 || status == 403) {
            return new LlmException(LlmFailure.AUTHENTICATION_FAILED, "The AI provider rejected the configured credentials.");
        }
        if (status == 429 || status == 413) {
            return new LlmException(LlmFailure.RATE_LIMITED, "The AI provider rate limit or free-tier quota was reached.");
        }
        if (status == 400 && code.contains("json_validate_failed")) {
            return new LlmException(LlmFailure.INVALID_RESPONSE, "The model response did not match the expected format.");
        }
        if (status == 404 || code.contains("model_not_found") || code.contains("model_decommissioned")) {
            return new LlmException(LlmFailure.UNAVAILABLE,
                    "The AI provider does not offer this model (it may have been retired). Check AI_FALLBACK_MODEL.");
        }
        if (status == 500 || status == 502 || status == 503 || status == 498) {
            return new LlmException(LlmFailure.UNAVAILABLE, "The AI provider is temporarily unavailable.");
        }
        return new LlmException(LlmFailure.UNAVAILABLE, "The AI provider rejected the request (HTTP " + status + ").");
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
