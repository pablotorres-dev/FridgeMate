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
        return new GeminiReceiptParser(key, "gemini-flash-latest", new ObjectMapper());
    }

    @Test
    @DisplayName("with no API key the feature reports itself off rather than failing to start")
    void isUnavailableWithoutAKey() {
        assertThat(parserWithKey("").isAvailable()).isFalse();
        assertThat(parserWithKey(null).isAvailable()).isFalse();
        assertThat(parserWithKey("   ").isAvailable()).isFalse();
    }

    /**
     * Keys pasted into a hosting dashboard commonly pick up a trailing newline,
     * which the API rejects as invalid. Trimming turned a confusing failure into
     * a non-event once already.
     */
    @Test
    @DisplayName("a key surrounded by whitespace still counts as configured")
    void tolerAtesWhitespaceAroundTheKey() {
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
