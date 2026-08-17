package org.example.fridgecalories.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Only the behaviour that needs no network. Nothing here calls the model: the
 * point is that an unconfigured server stays usable.
 */
class GeminiReceiptParserTest {

    private GeminiReceiptParser parserWithKey(String key) {
        ObjectMapper mapper = new ObjectMapper();
        return new GeminiReceiptParser(new GeminiClient(key, "gemini-flash-latest", mapper), mapper);
    }

    @Test
    @DisplayName("with no API key the feature reports itself off rather than failing to start")
    void isUnavailableWithoutAKey() {
        assertThat(parserWithKey("").isAvailable()).isFalse();
        assertThat(parserWithKey(null).isAvailable()).isFalse();
        assertThat(parserWithKey("   ").isAvailable()).isFalse();
    }

    @Test
    @DisplayName("a key surrounded by whitespace still counts as configured")
    void toleratesWhitespaceAroundTheKey() {
        assertThat(parserWithKey("  a-key\n").isAvailable()).isTrue();
    }

    @Test
    @DisplayName("scanning without a key is refused clearly, not with a crash")
    void refusesToParseWithoutAKey() {
        assertThatThrownBy(() -> parserWithKey("").parse(new byte[]{1, 2, 3}, "image/jpeg"))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.SERVICE_UNAVAILABLE);
    }
}
