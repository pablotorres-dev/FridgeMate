package org.example.fridgecalories.service;

import org.example.fridgecalories.model.Ingredient;
import org.example.fridgecalories.model.NutritionReport;

import java.util.List;

/**
 * Estimates the nutrition available from a set of ingredients.
 *
 * <p>Kept as an interface so the model behind it can be swapped without the
 * rest of the app knowing which provider is in use.
 */
public interface NutritionAnalyzer {

    NutritionReport analyse(List<Ingredient> ingredients);

    /** False when the provider isn't configured, so the UI can explain rather than error. */
    boolean isAvailable();
}
