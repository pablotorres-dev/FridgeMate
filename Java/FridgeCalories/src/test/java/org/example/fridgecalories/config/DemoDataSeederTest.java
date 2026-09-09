package org.example.fridgecalories.config;

import org.example.fridgecalories.model.Ingredient;
import org.example.fridgecalories.model.User;
import org.example.fridgecalories.repository.IngredientRepository;
import org.example.fridgecalories.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The seeder has already run by the time these execute, so they assert the
 * state a visitor actually lands in.
 */
@SpringBootTest
class DemoDataSeederTest {

    @Autowired
    private DemoDataSeeder seeder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private IngredientRepository ingredientRepository;

    private List<Ingredient> demoKitchen() {
        User demo = userRepository.findByUsernameIgnoreCase(DemoDataSeeder.DEMO_USERNAME).orElseThrow();
        return ingredientRepository.findByUser(demo, Sort.by("name"));
    }

    @Test
    @DisplayName("a visitor lands in a stocked kitchen, not an empty one")
    void stocksTheDemoKitchen() {
        assertThat(demoKitchen()).hasSizeGreaterThan(10);
    }

    /**
     * The bug this guards: dates are written relative to the day they are
     * seeded, and the kitchen was only ever seeded once. Weeks after a
     * deployment every dated item had quietly passed, so the demo showed a
     * fridge full of spoiled food. Rebuilding on each start keeps the spread
     * meaningful however long the deployment has been up.
     */
    @Test
    @DisplayName("the dates span from already past to years away")
    void coversEveryExpiryState() {
        List<LocalDate> dates = demoKitchen().stream()
                .map(Ingredient::getExpirationDate)
                .filter(java.util.Objects::nonNull)
                .toList();

        LocalDate today = LocalDate.now();

        assertThat(dates).as("something already gone off, so the expired warning has something to show")
                .anyMatch(date -> date.isBefore(today));
        assertThat(dates).as("something inside the three-day window")
                .anyMatch(date -> !date.isBefore(today) && date.isBefore(today.plusDays(4)));
        assertThat(dates).as("a store-cupboard staple over a year out, so sorting has a real range")
                .anyMatch(date -> date.isAfter(today.plusDays(365)));
    }

    @Test
    @DisplayName("items with no date at all are represented, since the app allows them")
    void includesItemsWithoutADate() {
        assertThat(demoKitchen()).anyMatch(ingredient -> ingredient.getExpirationDate() == null);
    }

    @Test
    @DisplayName("restarting refreshes the kitchen rather than stacking a second copy on it")
    void doesNotDuplicateOnRerun() {
        int before = demoKitchen().size();

        seeder.run(null);

        assertThat(demoKitchen()).hasSize(before);
    }
}
