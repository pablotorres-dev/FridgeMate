package org.example.fridgecalories.service;

import org.example.fridgecalories.model.ParsedReceipt;
import org.example.fridgecalories.model.ProductType;
import org.example.fridgecalories.model.StorageLocation;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class GeminiReceiptParser implements ReceiptParser {

    private final GeminiClient client;
    private final ObjectMapper objectMapper;
    private final Map<String, Object> responseSchema;

    public GeminiReceiptParser(GeminiClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.responseSchema = buildResponseSchema();
    }

    @Override
    public boolean isAvailable() {
        return client.isConfigured();
    }

    @Override
    public ParsedReceipt parse(byte[] image, String mimeType) {
        if (!isAvailable()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Receipt scanning isn't configured on this server");
        }

        String json = client.generateJson(buildRequest(image, mimeType));
        return objectMapper.readValue(json, ParsedReceipt.class);
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
            container deposits whether charged or refunded, and carrier bags.

            For each product, also guess:
            - the product type, from the allowed values. A fruit or vegetable stays
              a fruit or vegetable even when it is mainly used as an ingredient or
              in a drink: a lemon is FRUIT, not a beverage;
            - where it is kept at home. Frozen food goes in FREEZER, fresh food that
              needs chilling goes in FRIDGE, toiletries and household goods go in
              BATHROOM, and everything else goes in PANTRY.

            These two are guesses the user will confirm, so choose the most likely
            value rather than leaving them out.

            If the shop's name is printed on the receipt, give it. If you cannot
            read the receipt at all, return an empty list of items.
            """;
}
