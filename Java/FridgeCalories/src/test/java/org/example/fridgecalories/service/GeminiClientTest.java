package org.example.fridgecalories.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the two halves that can be exercised without a network: deciding
 * whether a failure is worth another attempt, and pulling the answer out of the
 * API envelope.
 */
class GeminiClientTest {

    private final GeminiClient client = new GeminiClient("a-key", "gemini-flash-latest", new ObjectMapper());

    @Test
    @DisplayName("with no API key the feature reports itself off rather than failing to start")
    void isUnconfiguredWithoutAKey() {
        ObjectMapper mapper = new ObjectMapper();
        assertThat(new GeminiClient("", "m", mapper).isConfigured()).isFalse();
        assertThat(new GeminiClient(null, "m", mapper).isConfigured()).isFalse();
        assertThat(new GeminiClient("   ", "m", mapper).isConfigured()).isFalse();
        // Keys pasted into a hosting dashboard commonly pick up a trailing newline.
        assertThat(new GeminiClient("  a-key\n", "m", mapper).isConfigured()).isTrue();
    }

    /**
     * The reported failure: the model is shared, and Google answers 503 saying
     * demand spikes are usually temporary. Giving up on the first one turned a
     * blip into a visible error.
     */
    @Test
    @DisplayName("a busy or broken service is worth another attempt")
    void retriesTransientFailures() {
        assertThat(GeminiClient.isRetryable(HttpStatus.SERVICE_UNAVAILABLE)).isTrue();
        assertThat(GeminiClient.isRetryable(HttpStatus.TOO_MANY_REQUESTS)).isTrue();
        assertThat(GeminiClient.isRetryable(HttpStatus.INTERNAL_SERVER_ERROR)).isTrue();
        assertThat(GeminiClient.isRetryable(HttpStatus.GATEWAY_TIMEOUT)).isTrue();
    }

    @Test
    @DisplayName("a rejected key or a bad request is not retried, since it would fail identically")
    void doesNotRetryPermanentFailures() {
        assertThat(GeminiClient.isRetryable(HttpStatus.BAD_REQUEST)).isFalse();
        assertThat(GeminiClient.isRetryable(HttpStatus.UNAUTHORIZED)).isFalse();
        assertThat(GeminiClient.isRetryable(HttpStatus.FORBIDDEN)).isFalse();
        assertThat(GeminiClient.isRetryable(HttpStatus.NOT_FOUND)).isFalse();
    }

    @Test
    @DisplayName("the answer is read out of the envelope")
    void extractsTheAnswer() {
        String response = """
                {"candidates":[{"finishReason":"STOP","content":{"parts":[
                  {"text":"{\\"ok\\":true}"}
                ]}}]}
                """;

        assertThat(client.extractJson(response)).isEqualTo("{\"ok\":true}");
    }

    /**
     * These models reason before answering and can put the reasoning in a part
     * of its own, ahead of the answer. Taking parts[0] blindly would hand back
     * the thought and fail to parse.
     */
    @Test
    @DisplayName("a reasoning part in front of the answer is skipped")
    void skipsAThoughtPart() {
        String response = """
                {"candidates":[{"finishReason":"STOP","content":{"parts":[
                  {"thought":true,"text":"Let me add up the vegetables first."},
                  {"text":"{\\"ok\\":true}"}
                ]}}]}
                """;

        assertThat(client.extractJson(response)).isEqualTo("{\"ok\":true}");
    }

    /**
     * Without this the truncated JSON reaches the parser and surfaces as an
     * unintelligible syntax error, hiding the real cause.
     */
    @Test
    @DisplayName("an answer cut short is reported as such, not as broken JSON")
    void detectsATruncatedAnswer() {
        String response = """
                {"candidates":[{"finishReason":"MAX_TOKENS","content":{"parts":[
                  {"text":"{\\"summary\\":\\"partial"}
                ]}}]}
                """;

        assertThatThrownBy(() -> client.extractJson(response))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("too long");
    }

    @Test
    @DisplayName("a response with nothing usable in it fails clearly")
    void rejectsAnEmptyResponse() {
        assertThatThrownBy(() -> client.extractJson("{\"candidates\":[]}"))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_GATEWAY);
    }
}
