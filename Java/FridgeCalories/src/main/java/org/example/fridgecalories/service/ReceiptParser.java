package org.example.fridgecalories.service;

import org.example.fridgecalories.model.ParsedReceipt;

/**
 * Reads the products off a photographed till receipt.
 *
 * <p>Kept as an interface for the same reasons as {@link NutritionAnalyzer}:
 * the provider can be swapped without the rest of the app knowing, and tests
 * can stand in for it rather than calling a paid API.
 */
public interface ReceiptParser {

    /**
     * @param image    the raw bytes of the photograph
     * @param mimeType the image's content type, which the provider needs to decode it
     */
    ParsedReceipt parse(byte[] image, String mimeType);

    /** False when the provider isn't configured, so the UI can explain rather than error. */
    boolean isAvailable();
}
