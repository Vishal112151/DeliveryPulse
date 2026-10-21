package com.deliverypulse.insights.llm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.deliverypulse.insights.AiInsightsProperties;

@Configuration
@EnableConfigurationProperties(AiInsightsProperties.class)
public class LlmConfiguration {

    /** The configured provider, wrapped with an optional second provider that is tried when the first fails. */
    @Bean
    LlmClient llmClient(AiInsightsProperties properties,
            @Value("${app.ai.fallback.provider:}") String fallbackProvider,
            @Value("${app.ai.fallback.api-key:}") String fallbackApiKey,
            @Value("${app.ai.fallback.model:}") String fallbackModel) {
        LlmClient primary = create(properties);
        if (fallbackProvider.isBlank()) {
            return primary;
        }
        return new FallbackLlmClient(primary,
                create(properties.forProvider(fallbackProvider, fallbackApiKey, fallbackModel)));
    }

    /** Selects the provider implementation; add new providers here without touching callers. */
    static LlmClient create(AiInsightsProperties properties) {
        if ("anthropic".equalsIgnoreCase(properties.provider())) {
            return new AnthropicLlmClient(properties);
        }
        if ("gemini".equalsIgnoreCase(properties.provider())) {
            return new GeminiLlmClient(properties);
        }
        if ("groq".equalsIgnoreCase(properties.provider())) {
            return new GroqLlmClient(properties);
        }
        return new UnsupportedProviderClient(properties.provider());
    }

    private record UnsupportedProviderClient(String providerName) implements LlmClient {

        @Override
        public boolean isConfigured() {
            return false;
        }

        @Override
        public <T> LlmResult<T> generate(LlmPrompt prompt, Class<T> responseType) {
            throw new LlmException(LlmFailure.NOT_CONFIGURED, "Unsupported AI provider: " + providerName);
        }
    }
}
