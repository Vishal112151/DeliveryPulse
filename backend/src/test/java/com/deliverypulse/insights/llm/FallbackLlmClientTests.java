package com.deliverypulse.insights.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class FallbackLlmClientTests {

    private static final LlmPrompt PROMPT = new LlmPrompt("system", "user");

    /** Answers with its own name, or throws {@code failure} when one is set. */
    static final class FakeClient implements LlmClient {
        private final String name;
        private final boolean configured;
        private final LlmFailure failure;
        final AtomicInteger calls = new AtomicInteger();

        FakeClient(String name, boolean configured, LlmFailure failure) {
            this.name = name;
            this.configured = configured;
            this.failure = failure;
        }

        @Override
        public String providerName() {
            return name;
        }

        @Override
        public boolean isConfigured() {
            return configured;
        }

        @Override
        public <T> LlmResult<T> generate(LlmPrompt prompt, Class<T> responseType) {
            calls.incrementAndGet();
            if (failure != null) {
                throw new LlmException(failure, name + " failed");
            }
            return new LlmResult<>(responseType.cast(name), name + "-model", 1, 1);
        }
    }

    @Test
    void usesThePrimaryWhenItSucceeds() {
        FakeClient primary = new FakeClient("gemini", true, null);
        FakeClient secondary = new FakeClient("groq", true, null);

        assertThat(new FallbackLlmClient(primary, secondary).generate(PROMPT, String.class).model())
                .isEqualTo("gemini-model");
        assertThat(secondary.calls.get()).isZero();
    }

    @Test
    void triesTheSecondaryWhenThePrimaryFails() {
        FakeClient secondary = new FakeClient("groq", true, null);
        FallbackLlmClient client = new FallbackLlmClient(new FakeClient("gemini", true, LlmFailure.RATE_LIMITED),
                secondary);

        assertThat(client.generate(PROMPT, String.class).model()).isEqualTo("groq-model");
        assertThat(client.providerName()).isEqualTo("gemini, with groq as fallback");
    }

    @Test
    void skipsAnUnconfiguredPrimary() {
        FakeClient primary = new FakeClient("gemini", false, null);
        FallbackLlmClient client = new FallbackLlmClient(primary, new FakeClient("groq", true, null));

        assertThat(client.isConfigured()).isTrue();
        assertThat(client.generate(PROMPT, String.class).model()).isEqualTo("groq-model");
        assertThat(primary.calls.get()).isZero();
    }

    @Test
    void reportsThePrimaryFailureWhenTheSecondaryIsNotConfigured() {
        FallbackLlmClient client = new FallbackLlmClient(new FakeClient("gemini", true, LlmFailure.QUOTA_EXCEEDED),
                new FakeClient("groq", false, null));

        assertThatThrownBy(() -> client.generate(PROMPT, String.class))
                .isInstanceOfSatisfying(LlmException.class,
                        exception -> assertThat(exception.getFailure()).isEqualTo(LlmFailure.QUOTA_EXCEEDED));
    }

    @Test
    void reportsTheSecondaryFailureWhenBothFail() {
        FallbackLlmClient client = new FallbackLlmClient(new FakeClient("gemini", true, LlmFailure.RATE_LIMITED),
                new FakeClient("groq", true, LlmFailure.TIMEOUT));

        assertThatThrownBy(() -> client.generate(PROMPT, String.class))
                .isInstanceOfSatisfying(LlmException.class,
                        exception -> assertThat(exception.getFailure()).isEqualTo(LlmFailure.TIMEOUT));
    }
}
