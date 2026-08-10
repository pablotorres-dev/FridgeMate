package org.example.fridgecalories.service;

import org.example.fridgecalories.model.Ingredient;
import org.example.fridgecalories.model.NutritionReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
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
import java.util.stream.Collectors;

@Service
public class GeminiNutritionAnalyzer implements NutritionAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(GeminiNutritionAnalyzer.class);

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models";

    /**
     * Asking for a fixed shape means the answer arrives as data to render in
     * tables, rather than prose that would have to be parsed out of a paragraph.
     */
    private static final String RESPONSE_SCHEMA = """
            {
              "type": "OBJECT",
              "properties": {
                "summary": { "type": "STRING" },
                "totalCalories": { "type": "NUMBER" },
                "macros": {
                  "type": "ARRAY",
                  "items": {
                    "type": "OBJECT",
                    "properties": {
                      "name": { "type": "STRING" },
                      "amount": { "type": "STRING" }
                    },
                    "required": ["name", "amount"]
                  }
                },
                "micronutrients": {
                  "type": "ARRAY",
                  "items": {
                    "type": "OBJECT",
                    "properties": {
                      "name": { "type": "STRING" },
                      "amount": { "type": "STRING" },
                      "level": { "type": "STRING", "enum": ["LOW", "MODERATE", "GOOD"] },
                      "sources": { "type": "ARRAY", "items": { "type": "STRING" } }
                    },
                    "required": ["name", "amount", "level", "sources"]
                  }
                },
                "gaps": { "type": "ARRAY", "items": { "type": "STRING" } }
              },
              "required": ["summary", "totalCalories", "macros", "micronutrients", "gaps"]
            }
            """;

    private final String apiKey;
    private final URI endpoint;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GeminiNutritionAnalyzer(
            @Value("${app.gemini.api-key:}") String apiKey,
            @Value("${app.gemini.model:gemini-2.0-flash}") String model,
            ObjectMapper objectMapper) {
        // Trimmed because a key pasted into a hosting dashboard often carries a
        // trailing space or newline, which the API rejects as an invalid key.
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.objectMapper = objectMapper;
        // Built once, and as a URI rather than a template string: passing the
        // base URL as a template variable would percent-encode "https://" and
        // leave a relative address that can't be requested.
        this.endpoint = URI.create(BASE_URL + "/" + model + ":generateContent");

        // Generating a full report takes a few seconds, so the read timeout is
        // well above a normal API call's.
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(60));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public NutritionReport analyse(List<Ingredient> ingredients) {
        if (!isAvailable()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Nutrition analysis isn't configured on this server");
        }

        try {
            String rawResponse = restClient.post()
                    .uri(endpoint)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildRequest(ingredients))
                    .retrieve()
                    .body(String.class);

            return parseReport(rawResponse);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (RestClientResponseException e) {
            // When the API itself rejects the call, its own message says why —
            // far more useful than a stack trace, so it goes on the log line.
            log.error("Gemini rejected the request: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Couldn't reach the nutrition service. Please try again.", e);
        } catch (Exception e) {
            log.error("Gemini nutrition analysis failed", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Couldn't reach the nutrition service. Please try again.", e);
        }
    }

    private Map<String, Object> buildRequest(List<Ingredient> ingredients) throws Exception {
        return Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", buildPrompt(ingredients))))),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "responseSchema", objectMapper.readValue(RESPONSE_SCHEMA, Map.class)));
    }

    private String buildPrompt(List<Ingredient> ingredients) {
        String contents = ingredients.stream()
                .map(i -> "- %s: %s%s".formatted(
                        i.getName(),
                        trimNumber(i.getQuantity()),
                        i.getUnit() == null ? "" : " " + i.getUnit()))
                .collect(Collectors.joining("\n"));

        return """
                Estimate the nutrition currently available in this kitchen.

                Contents:
                %s

                Work from typical values for these foods. Give totals for everything
                listed together, not per item.

                Report the total calories, the main macronutrients, and the notable
                vitamins and minerals present. For each vitamin or mineral, name the
                items supplying most of it and rate coverage as LOW, MODERATE or GOOD
                relative to what one adult would need over a few days.

                In "gaps", list nutrients this kitchen covers poorly, and suggest a
                food that would fix each one.

                Ignore anything that isn't food, such as toiletries or cleaning products.

                Keep the summary to two sentences, and make clear the figures are rough
                estimates.
                """.formatted(contents);
    }

    private String trimNumber(Double value) {
        if (value == null) {
            return "";
        }
        return value % 1 == 0 ? String.valueOf(value.longValue()) : String.valueOf(value);
    }

    /** The model's answer arrives as a JSON string nested inside the API envelope. */
    private NutritionReport parseReport(String rawResponse) throws Exception {
        JsonNode root = objectMapper.readTree(rawResponse);
        JsonNode text = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
        if (text.isMissingNode()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "The nutrition service returned an unexpected response");
        }
        return objectMapper.readValue(text.asText(), NutritionReport.class);
    }
}
