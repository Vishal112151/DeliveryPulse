package com.deliverypulse.insights.llm;

import java.io.InterruptedIOException;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicInvalidDataException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.InternalServerException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredTextBlock;
import com.deliverypulse.insights.AiInsightsProperties;

/**
 * Claude implementation of {@link LlmClient} using the official Anthropic Java SDK and
 * structured outputs. The SDK retries 429/5xx/connection errors up to {@code maxRetries} times.
 */
public class AnthropicLlmClient implements LlmClient {

    /** Models that accept the server-side refusal fallback ({@code fallbacks: "default"}). */
    private static final Set<String> FALLBACK_MODELS = Set.of(
            "claude-fable-5-1", "claude-opus-5-5", "claude-opus-5", "claude-sonnet-5-5");
    private static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";

    private final AiInsightsProperties properties;
    private final AnthropicClient client;

    public AnthropicLlmClient(AiInsightsProperties properties) {
        this(properties, null);
    }

    /** {@code baseUrl} is only overridden by tests that point the SDK at a local fake server. */
    AnthropicLlmClient(AiInsightsProperties properties, String baseUrl) {
        this.properties = properties;
        if (!properties.hasApiKey()) {
            this.client = null;
            return;
        }
        AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder()
                .apiKey(properties.apiKey())
                .timeout(properties.timeout())
                .maxRetries(properties.maxRetries());
        if (baseUrl != null) {
            builder.baseUrl(baseUrl);
        }
        this.client = builder.build();
    }

    @Override
    public String providerName() {
        return "anthropic";
    }

    @Override
    public boolean isConfigured() {
        return client != null;
    }

    @Override
    public <T> LlmResult<T> generate(LlmPrompt prompt, Class<T> responseType) {
        if (client == null) {
            throw new LlmException(LlmFailure.NOT_CONFIGURED, "No Anthropic API key is configured.");
        }
        StructuredMessageCreateParams.Builder<T> builder = MessageCreateParams.builder()
                .model(properties.model())
                .maxTokens(properties.maxOutputTokens())
                .system(prompt.system())
                .addUserMessage(prompt.user())
                .outputConfig(responseType);
        if (properties.serverSideFallbacks() && FALLBACK_MODELS.contains(properties.model())) {
            builder.putAdditionalHeader("anthropic-beta", FALLBACK_BETA)
                    .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
        }
        try {
            StructuredMessage<T> message = client.messages().create(builder.build());
            StopReason stopReason = message.stopReason().orElse(null);
            if (StopReason.REFUSAL.equals(stopReason)) {
                throw new LlmException(LlmFailure.REFUSED, "The model declined to answer this request.");
            }
            if (StopReason.MAX_TOKENS.equals(stopReason)) {
                throw new LlmException(LlmFailure.INVALID_RESPONSE, "The model response was truncated.");
            }
            T content = message.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(StructuredTextBlock::text)
                    .findFirst()
                    .orElseThrow(() -> new LlmException(LlmFailure.INVALID_RESPONSE,
                            "The model response contained no structured content."));
            return new LlmResult<>(content, message.model().asString(),
                    message.usage().inputTokens(), message.usage().outputTokens());
        } catch (LlmException exception) {
            throw exception;
        } catch (RateLimitException exception) {
            throw new LlmException(LlmFailure.RATE_LIMITED, "The AI provider rate limit was reached.", exception);
        } catch (UnauthorizedException | PermissionDeniedException exception) {
            throw new LlmException(LlmFailure.AUTHENTICATION_FAILED,
                    "The AI provider rejected the configured credentials.", exception);
        } catch (InternalServerException exception) {
            // Includes 529 overloaded_error.
            throw new LlmException(LlmFailure.UNAVAILABLE, "The AI provider is temporarily unavailable.", exception);
        } catch (AnthropicServiceException exception) {
            if (exception.statusCode() == 402 || isBillingError(exception)) {
                throw new LlmException(LlmFailure.QUOTA_EXCEEDED,
                        "The AI provider account has no remaining quota or credit.", exception);
            }
            if (exception.statusCode() == 408 || exception.statusCode() == 504) {
                throw new LlmException(LlmFailure.TIMEOUT, "The AI provider timed out.", exception);
            }
            throw new LlmException(LlmFailure.UNAVAILABLE,
                    "The AI provider rejected the request (HTTP " + exception.statusCode() + ").", exception);
        } catch (AnthropicInvalidDataException exception) {
            throw new LlmException(LlmFailure.INVALID_RESPONSE,
                    "The model response did not match the expected format.", exception);
        } catch (AnthropicIoException exception) {
            throw connectionFailure(exception);
        } catch (AnthropicException exception) {
            throw connectionFailure(exception);
        }
    }

    private static boolean isBillingError(AnthropicServiceException exception) {
        return exception.errorType().map(Object::toString)
                .filter(type -> type.toLowerCase(java.util.Locale.ROOT).contains("billing"))
                .isPresent();
    }

    private static LlmException connectionFailure(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof InterruptedIOException || cause instanceof TimeoutException) {
                return new LlmException(LlmFailure.TIMEOUT, "The AI provider did not respond in time.", exception);
            }
        }
        return new LlmException(LlmFailure.UNAVAILABLE, "The AI provider could not be reached.", exception);
    }
}
