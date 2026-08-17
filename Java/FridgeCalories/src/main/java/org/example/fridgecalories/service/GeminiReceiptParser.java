package org.example.fridgecalories.service;

import org.example.fridgecalories.model.ParsedReceipt;
import org.example.fridgecalories.model.ProductType;
import org.example.fridgecalories.model.StorageLocation;
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
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class GeminiReceiptParser implements ReceiptParser {

    private static final Logger log = LoggerFactory.getLogger(GeminiReceiptParser.class);

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models";

    private final String apiKey;
    private final URI endpoint;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final Map<String, Object> responseSchema;

    public GeminiReceiptParser(
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
        this.responseSchema = buildResponseSchema();

        // Reading an image takes longer than a text prompt, and a photo taken on
        // a phone is a large upload on mobile data.
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(90));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public boolean isAvailable() {
        return !apiKey.isBlank();
    }

    @Override
    public ParsedReceipt parse(byte[] image, String mimeType) {
        if (!isAvailable()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Receipt scanning isn't configured on this server");
        }

        try {
            String rawResponse = restClient.post()
                    .uri(endpoint)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildRequest(image, mimeType))
                    .retrieve()
                    .body(String.class);

            return parseReceipt(rawResponse);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (RestClientResponseException e) {
            // When the API itself rejects the call, its own message says why —
            // far more useful than a stack trace, so it goes on the log line.
            log.error("Gemini rejected the receipt: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Couldn't read the receipt. Please try again.", e);
        } catch (Exception e) {
            log.error("Gemini receipt parsing failed", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Couldn't read the receipt. Please try again.", e);
        }
    }

    /**
     * The photo travels in the same message as the instructions, as a second
     * part alongside the text.
     */
    private Map<String, Object> buildRequest(byte[] image, String mimeType) {
        Map<String, Object> imagePart = Map.of("inlineData", Map.of(
                "mimeType", mimeType,
                "data", Base64.getEncoder().encodeToString(image)));

        return Map.of(
                "contents", List.of(Map.of("parts", List.of(
                        Map.of("text", PROMPT),
                        imagePart))),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "responseSchema", responseSchema));
    }

    /**
     * Generated from the enums rather than written out, so adding a product type
     * or a storage location can't leave a stale list here that quietly stops the
     * model from ever choosing the new value.
     */
    private Map<String, Object> buildResponseSchema() {
        Map<String, Object> item = Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "name", Map.of("type", "STRING"),
                        "quantity", Map.of("type", "NUMBER"),
                        "unit", Map.of("type", "STRING"),
                        "type", Map.of("type", "STRING", "enum", names(ProductType.values())),
                        "storageLocation", Map.of("type", "STRING", "enum", names(StorageLocation.values()))),
                // "unit" is left out: loose items are sold by the piece and have none.
                "required", List.of("name", "quantity", "type", "storageLocation"));

        return Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "store", Map.of("type", "STRING"),
                        "items", Map.of("type", "ARRAY", "items", item)),
                "required", List.of("items"));
    }

    private static List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }

    private static final String PROMPT = """
            This photograph shows a supermarket till receipt. List the products
            that were actually purchased.

            Names on receipts are abbreviated and truncated to fit the paper.
            Write each one back out as a person would say it: "SPCLLY SEL CHDDR"
            is "Specially Selected Cheddar", "TOM CHERRY 250G" is "Cherry tomatoes".
            Do not invent detail the line doesn't support.

            Quantities:
            - A line like "2 x 1.50" means a quantity of 2, with no unit.
            - A line priced by weight, like "0.532 kg @ 2.99/kg", means a quantity
              of 0.532 with the unit "kg". Give the weight bought, never the price.
            - If a size is printed in the name, such as "MILK 2L", the quantity is
              still 1 unless the line says otherwise.
            - When no quantity is shown at all, use 1.

            Ignore every line that is not a product being bought: subtotals, totals,
            tax and VAT, payment and change, card and terminal details, store address,
            dates, loyalty points, vouchers, coupons, multibuy and other discounts,
            deposit refunds, and carrier bags.

            For each product, also guess:
            - the product type, from the allowed values;
            - where it is kept at home. Frozen food goes in FREEZER, fresh food that
              needs chilling goes in FRIDGE, toiletries and household goods go in
              BATHROOM, and everything else goes in PANTRY.

            These two are guesses the user will confirm, so choose the most likely
            value rather than leaving them out.

            If the shop's name is printed on the receipt, give it. If you cannot
            read the receipt at all, return an empty list of items.
            """;

    /** The model's answer arrives as a JSON string nested inside the API envelope. */
    private ParsedReceipt parseReceipt(String rawResponse) throws Exception {
        JsonNode root = objectMapper.readTree(rawResponse);
        JsonNode text = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
        if (text.isMissingNode()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "The receipt service returned an unexpected response");
        }
        return objectMapper.readValue(text.asText(), ParsedReceipt.class);
    }
}
