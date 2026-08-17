package org.example.fridgecalories.model;

import java.util.List;

/**
 * What a photographed till receipt turned out to contain.
 *
 * <p>Everything here is a reading of a printed slip, so it is a starting point
 * for the user to correct rather than a record to trust. Receipts abbreviate
 * names heavily ({@code SPCLLY SEL CHDDR}), and they carry no expiry dates at
 * all, so the storage details still have to be filled in afterwards.
 *
 * @param store the shop name printed on the receipt, or null if it wasn't legible
 * @param items the purchased products, with non-product lines already dropped
 */
public record ParsedReceipt(String store, List<Item> items) {

    /**
     * One purchased product.
     *
     * @param name             the name expanded back into readable words
     * @param quantity         units bought, or the weight when sold by weight
     * @param unit             the unit that quantity is counted in, or null for loose items
     * @param type             a guess at the product type, for the user to confirm
     * @param storageLocation  a guess at where it belongs, for the user to confirm
     */
    public record Item(
            String name,
            Double quantity,
            String unit,
            ProductType type,
            StorageLocation storageLocation
    ) {
    }
}
