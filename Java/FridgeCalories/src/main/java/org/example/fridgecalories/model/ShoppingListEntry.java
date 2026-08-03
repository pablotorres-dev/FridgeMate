package org.example.fridgecalories.model;

public record ShoppingListEntry(
        Long id,
        String name,
        String unit,
        double minQuantity,
        double currentQuantity,
        double quantityToBuy
) {
}
