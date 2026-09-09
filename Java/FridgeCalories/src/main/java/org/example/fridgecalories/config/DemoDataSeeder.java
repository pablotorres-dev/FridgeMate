package org.example.fridgecalories.config;

import org.example.fridgecalories.model.*;
import org.example.fridgecalories.repository.IngredientRepository;
import org.example.fridgecalories.repository.ShoppingListItemRepository;
import org.example.fridgecalories.repository.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Keeps a ready-to-explore "demo" account stocked, so anyone opening the app
 * lands in a realistic kitchen instead of an empty one.
 *
 * <p>The contents are rebuilt on every start, not just the first. Dates are
 * stored relative to the day they are written, so seeding once and leaving it
 * meant the whole kitchen quietly rotted: weeks after deployment every dated
 * item had passed, and a visitor met a fridge full of spoiled food. Rebuilding
 * also undoes whatever the last visitor did, which is what a shared demo wants.
 *
 * <p>Only this account is touched. Real accounts are never read or written here.
 */
@Component
public class DemoDataSeeder implements ApplicationRunner {

    public static final String DEMO_USERNAME = "demo";
    private static final String DEMO_PASSWORD = "demo1234";

    private final UserRepository userRepository;
    private final IngredientRepository ingredientRepository;
    private final ShoppingListItemRepository shoppingListItemRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(UserRepository userRepository,
                          IngredientRepository ingredientRepository,
                          ShoppingListItemRepository shoppingListItemRepository,
                          PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.ingredientRepository = ingredientRepository;
        this.shoppingListItemRepository = shoppingListItemRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        User demo = userRepository.findByUsernameIgnoreCase(DEMO_USERNAME)
                .orElseGet(this::createDemoUser);

        // Cleared first, so restarts refresh the dates instead of stacking a
        // second copy of everything on top of the last one.
        ingredientRepository.deleteByUser(demo);
        shoppingListItemRepository.deleteByUser(demo);

        stockKitchen(demo);
        setStockTargets(demo);
    }

    private User createDemoUser() {
        User demo = new User();
        demo.setUsername(DEMO_USERNAME);
        demo.setPassword(passwordEncoder.encode(DEMO_PASSWORD));
        return userRepository.save(demo);
    }

    /**
     * A believable weekly shop, spread deliberately across every state the app
     * can show: a few things already gone off, a few due within the warning
     * window, the usual middle, and store-cupboard staples years out.
     */
    private void stockKitchen(User demo) {
        // Gone off. Every kitchen has these, and it is what the expired warning
        // is for — the demo should show it working, not hide it.
        ingredient(demo, "Spinach", 1, "bag", ProductType.VEGETABLE, StorageLocation.FRIDGE, -6);
        ingredient(demo, "Milk", 2, "liters", ProductType.DAIRY, StorageLocation.FRIDGE, -3);
        ingredient(demo, "Strawberries", 1, "punnet", ProductType.FRUIT, StorageLocation.FRIDGE, -1);

        // Inside the three-day warning.
        ingredient(demo, "Chicken breast", 500, "g", ProductType.MEAT, StorageLocation.FRIDGE, 1);
        ingredient(demo, "Greek yoghurt", 4, "pots", ProductType.DAIRY, StorageLocation.FRIDGE, 2);
        ingredient(demo, "Bananas", 4, "units", ProductType.FRUIT, StorageLocation.PANTRY, 3);

        // The ordinary middle of a fridge.
        ingredient(demo, "Tomatoes", 6, "units", ProductType.VEGETABLE, StorageLocation.FRIDGE, 6);
        ingredient(demo, "Orange juice", 1, "liter", ProductType.BEVERAGE, StorageLocation.FRIDGE, 9);
        ingredient(demo, "Eggs", 6, "units", ProductType.EGG, StorageLocation.FRIDGE, 14);
        ingredient(demo, "Cheddar", 1, "block", ProductType.DAIRY, StorageLocation.FRIDGE, 22);
        ingredient(demo, "Butter", 1, "block", ProductType.DAIRY, StorageLocation.FRIDGE, 35);
        ingredient(demo, "Wholemeal bread", 1, "loaf", ProductType.GRAIN, StorageLocation.PANTRY, 5);

        // Freezer and store cupboard: months and years away, so sorting by date
        // has a real range to work with rather than everything bunched together.
        ingredient(demo, "Salmon fillets", 2, "units", ProductType.FISH, StorageLocation.FREEZER, 120);
        ingredient(demo, "Frozen peas", 1, "bag", ProductType.VEGETABLE, StorageLocation.FREEZER, 300);
        ingredient(demo, "Pasta", 500, "g", ProductType.GRAIN, StorageLocation.PANTRY, 420);
        ingredient(demo, "Rice", 1, "kg", ProductType.GRAIN, StorageLocation.PANTRY, 600);
        ingredient(demo, "Olive oil", 1, "bottle", ProductType.SAUCE, StorageLocation.PANTRY, 730);
        ingredient(demo, "Tinned tomatoes", 4, "tins", ProductType.VEGETABLE, StorageLocation.PANTRY, 900);
        ingredient(demo, "Honey", 1, "jar", ProductType.SAUCE, StorageLocation.PANTRY, 1095);

        // No date at all, which the app allows on purpose.
        ingredient(demo, "Toothpaste", 1, "tube", ProductType.BATHROOM, StorageLocation.BATHROOM, null);
        ingredient(demo, "Toilet paper", 4, "rolls", ProductType.BATHROOM, StorageLocation.BATHROOM, null);
    }

    /**
     * Some targets already met, some short, and one product not in the kitchen
     * at all — so "What to buy" shows a useful mix rather than one case.
     */
    private void setStockTargets(User demo) {
        tracked(demo, "Milk", "liters", 3);
        tracked(demo, "Eggs", "units", 12);
        tracked(demo, "Toilet paper", "rolls", 8);
        tracked(demo, "Rice", "kg", 1);
        tracked(demo, "Coffee", "packs", 2);
    }

    /** A negative number of days puts the item in the past, on purpose. */
    private void ingredient(User user, String name, double quantity, String unit,
                            ProductType type, StorageLocation location, Integer expiresInDays) {
        Ingredient ingredient = new Ingredient();
        ingredient.setUser(user);
        ingredient.setName(name);
        ingredient.setQuantity(quantity);
        ingredient.setUnit(unit);
        ingredient.setType(type);
        ingredient.setStorageLocation(location);
        ingredient.setExpirationDate(expiresInDays == null ? null : LocalDate.now().plusDays(expiresInDays));
        ingredientRepository.save(ingredient);
    }

    private void tracked(User user, String name, String unit, double minQuantity) {
        ShoppingListItem item = new ShoppingListItem();
        item.setUser(user);
        item.setName(name);
        item.setUnit(unit);
        item.setMinQuantity(minQuantity);
        shoppingListItemRepository.save(item);
    }
}
