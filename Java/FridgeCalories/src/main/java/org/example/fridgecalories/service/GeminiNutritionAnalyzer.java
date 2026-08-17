package org.example.fridgecalories.service;

import org.example.fridgecalories.model.Ingredient;
import org.example.fridgecalories.model.NutritionReport;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GeminiNutritionAnalyzer implements NutritionAnalyzer {

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

    private final GeminiClient client;
    private final ObjectMapper objectMapper;

    public GeminiNutritionAnalyzer(GeminiClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean isAvailable() {
        return client.isConfigured();
    }

    @Override
    public NutritionReport analyse(List<Ingredient> ingredients) {
        if (!isAvailable()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Nutrition analysis isn't configured on this server");
        }

        String json = client.generateJson(buildRequest(ingredients));
        return objectMapper.readValue(json, NutritionReport.class);
    }

    private Map<String, Object> buildRequest(List<Ingredient> ingredients) {
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
                vitamins and minerals present. Cover the twelve most significant
                vitamins and minerals at most, and name at most four sources for
                each: a well stocked kitchen otherwise produces an answer too long
                to finish, and the reader wants the picture rather than the
                inventory back.

                For each vitamin or mineral, name the items supplying most of it and
                rate coverage as LOW, MODERATE or GOOD relative to what one adult
                would need over a few days.

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
}
