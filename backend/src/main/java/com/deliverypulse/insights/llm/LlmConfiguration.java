package com.deliverypulse.insights.llm;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.deliverypulse.insights.AiInsightsProperties;

@Configuration
@EnableConfigurationProperties(AiInsightsProperties.class)
public class LlmConfiguration {

    /** Selects the provider implementation; add new providers here without touching callers. */
    @Bean
    LlmClient llmClient(AiInsightsProperties properties) {
        if ("anthropic".equalsIgnoreCase(properties.provider())) {
            return new AnthropicLlmClient(properties);
        }
        if ("gemini".equalsIgnoreCase(properties.provider())) {
            return new GeminiLlmClient(properties);
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
