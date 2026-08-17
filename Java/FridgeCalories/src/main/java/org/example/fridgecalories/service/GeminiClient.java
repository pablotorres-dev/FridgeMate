package org.example.fridgecalories.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The one place that talks to Gemini.
 *
 * <p>Both features that use the model — the nutrition estimate and reading a
 * till receipt — send a prompt and a response schema, and get JSON back. Only
 * the prompt and the schema differ, so everything else lives here: the key, the
 * endpoint, the timeouts, retrying, and digging the answer out of the envelope.
 */
@Service
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models";

    /**
     * Statuses worth trying again. A shared model runs out of capacity from time
     * to time and says so — the API's own words are "spikes in demand are
     * usually temporary". A rejected key or a malformed request, by contrast,
     * will be rejected just as firmly on the second attempt.
     */
    private static final Set<Integer> RETRYABLE = Set.of(429, 500, 502, 503, 504);

    private static final int MAX_ATTEMPTS = 3;

    /** Waited before the 2nd and 3rd attempts. Short enough that someone stays. */
    private static final List<Duration> BACKOFF = List.of(Duration.ofSeconds(1), Duration.ofSeconds(3));

    private final String apiKey;
    private final URI endpoint;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GeminiClient(
            @Value("${app.gemini.api-key:}") String apiKey,
            @Value("${app.gemini.model:gemini-flash-latest}") String model,
            ObjectMapper objectMapper) {
        // Trimmed because a key pasted into a hosting dashboard often carries a
        // trailing space or newline, which the API rejects as an invalid key.
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.objectMapper = objectMapper;
        // Built once, and as a URI rather than a template string: passing the
        // base URL as a template variable would percent-encode "https://" and
        // leave a relative address that can't be requested.
        this.endpoint = URI.create(BASE_URL + "/" + model + ":generateContent");

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(90));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    /** False when no key is set, so a feature can report itself off instead of failing. */
    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    /**
     * Sends a request and returns the JSON the model produced, retrying while
     * the failure looks temporary.
     */
    public String generateJson(Map<String, Object> requestBody) {
        RuntimeException lastFailure = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                String rawResponse = restClient.post()
                        .uri(endpoint)
                        .header("x-goog-api-key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .retrieve()
                        .body(String.class);

                return extractJson(rawResponse);
            } catch (RestClientResponseException e) {
                if (!isRetryable(e.getStatusCode()) || attempt == MAX_ATTEMPTS) {
                    // The API's own message says why, which beats a stack trace.
                    log.error("Gemini rejected the request on attempt {}: {} {}",
                            attempt, e.getStatusCode(), e.getResponseBodyAsString());
                    throw asFailure(e.getStatusCode());
                }
                log.warn("Gemini returned {} on attempt {} of {}; retrying",
                        e.getStatusCode(), attempt, MAX_ATTEMPTS);
                lastFailure = e;
            } catch (ResponseStatusException e) {
                throw e;
            } catch (Exception e) {
                if (attempt == MAX_ATTEMPTS) {
                    log.error("Gemini call failed on attempt {}", attempt, e);
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                            "Couldn't reach the AI service. Please try again.", e);
                }
                log.warn("Gemini call failed on attempt {} of {}; retrying", attempt, MAX_ATTEMPTS);
                lastFailure = new IllegalStateException(e);
            }

            pauseBefore(attempt);
        }

        // Unreachable: the final attempt always either returns or throws above.
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Couldn't reach the AI service. Please try again.", lastFailure);
    }

    static boolean isRetryable(HttpStatusCode status) {
        return RETRYABLE.contains(status.value());
    }

    private ResponseStatusException asFailure(HttpStatusCode status) {
        if (isRetryable(status)) {
            return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "The AI service is busy right now. Please try again in a moment.");
        }
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "The AI service rejected the request.");
    }

    private void pauseBefore(int completedAttempt) {
        if (completedAttempt > BACKOFF.size()) {
            return;
        }
        try {
            Thread.sleep(BACKOFF.get(completedAttempt - 1).toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The request was interrupted", e);
        }
    }

    /**
     * Digs the model's JSON out of the API envelope.
     *
     * <p>Deliberately not just {@code parts[0]}: these models reason before
     * answering and may put a thought ahead of the answer, so the first part
     * isn't reliably the one wanted. A truncated answer is caught here too,
     * because otherwise it surfaces as an unintelligible JSON parse error.
     */
    String extractJson(String rawResponse) {
        JsonNode candidate = objectMapper.readTree(rawResponse).path("candidates").path(0);
        JsonNode finishReason = candidate.path("finishReason");
        boolean ranOutOfRoom = !finishReason.isMissingNode() && "MAX_TOKENS".equals(finishReason.asText());

        for (JsonNode part : candidate.path("content").path("parts")) {
            JsonNode thought = part.path("thought");
            if (thought.isBoolean() && thought.asBoolean()) {
                continue;
            }
            JsonNode text = part.path("text");
            if (!text.isMissingNode() && !text.asText().isBlank()) {
                if (ranOutOfRoom) {
                    log.error("Gemini answer was cut short (MAX_TOKENS); the JSON is incomplete");
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                            "The answer was too long to finish. Please try again.");
                }
                return text.asText();
            }
        }

        log.error("Gemini returned no usable content. finishReason={} body={}",
                finishReason.asText(), abbreviate(rawResponse));
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "The AI service returned an unexpected response");
    }

    /** Keeps a diagnostic log line from dumping an entire response. */
    private static String abbreviate(String value) {
        if (value == null) {
            return "null";
        }
        return value.length() <= 500 ? value : value.substring(0, 500) + "...";
    }
}
