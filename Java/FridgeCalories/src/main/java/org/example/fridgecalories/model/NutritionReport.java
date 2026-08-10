package org.example.fridgecalories.model;

import java.util.List;

/**
 * An estimate of the nutrition sitting in a kitchen right now.
 *
 * <p>Figures are approximations derived from product names, not measurements,
 * and are meant to give a rough sense of what a shop is covering well or
 * missing — not to inform dietary decisions.
 */
public record NutritionReport(
        String summary,
        Double totalCalories,
        List<NutrientAmount> macros,
        List<Micronutrient> micronutrients,
        List<String> gaps
) {

    /** A macronutrient total, e.g. "Protein" / "180 g". */
    public record NutrientAmount(String name, String amount) {
    }

    /**
     * A vitamin or mineral found in the kitchen.
     *
     * @param level   how well covered it is: {@code LOW}, {@code MODERATE} or {@code GOOD}
     * @param sources the items contributing most of it
     */
    public record Micronutrient(String name, String amount, String level, List<String> sources) {
    }
}
