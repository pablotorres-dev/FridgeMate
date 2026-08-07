package org.example.fridgecalories.config;

import org.example.fridgecalories.model.*;
import org.example.fridgecalories.repository.IngredientRepository;
import org.example.fridgecalories.repository.ShoppingListItemRepository;
import org.example.fridgecalories.repository.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Creates a ready-to-explore "demo" account on first start, so anyone opening
 * the app can click straight into a realistic kitchen instead of an empty one.
 *
 * <p>Expiry dates are stored relative to the current date, so the demo still
 * shows items about to go off however long after deployment it is opened.
 *
 * <p>Runs only when the account is missing, so restarts and redeploys never
 * duplicate the data or overwrite changes made while exploring.
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
    public void run(ApplicationArguments args) {
        if (userRepository.existsByUsernameIgnoreCase(DEMO_USERNAME)) {
            return;
        }

        User demo = new User();
        demo.setUsername(DEMO_USERNAME);
        demo.setPassword(passwordEncoder.encode(DEMO_PASSWORD));
        demo = userRepository.save(demo);

        // A believable weekly shop, with a couple of things about to expire so
        // the "expiring soon" warning has something to show.
        ingredient(demo, "Milk", 2, "liters", ProductType.DAIRY, StorageLocation.FRIDGE, 3);
        ingredient(demo, "Chicken breast", 500, "g", ProductType.MEAT, StorageLocation.FRIDGE, 2);
        ingredient(demo, "Eggs", 6, "units", ProductType.EGG, StorageLocation.FRIDGE, 12);
        ingredient(demo, "Cheddar", 1, "block", ProductType.DAIRY, StorageLocation.FRIDGE, 20);
        ingredient(demo, "Butter", 1, "block", ProductType.DAIRY, StorageLocation.FRIDGE, 30);
        ingredient(demo, "Tomatoes", 6, "units", ProductType.VEGETABLE, StorageLocation.FRIDGE, 5);
        ingredient(demo, "Orange juice", 1, "liter", ProductType.BEVERAGE, StorageLocation.FRIDGE, 8);
        ingredient(demo, "Salmon fillets", 2, "units", ProductType.FISH, StorageLocation.FREEZER, 60);
        ingredient(demo, "Frozen peas", 1, "bag", ProductType.VEGETABLE, StorageLocation.FREEZER, 180);
        ingredient(demo, "Pasta", 500, "g", ProductType.GRAIN, StorageLocation.PANTRY, 240);
        ingredient(demo, "Rice", 1, "kg", ProductType.GRAIN, StorageLocation.PANTRY, 300);
        ingredient(demo, "Olive oil", 1, "bottle", ProductType.SAUCE, StorageLocation.PANTRY, 400);
        ingredient(demo, "Bananas", 4, "units", ProductType.FRUIT, StorageLocation.PANTRY, 4);
        ingredient(demo, "Toothpaste", 1, "tube", ProductType.BATHROOM, StorageLocation.BATHROOM, null);
        ingredient(demo, "Toilet paper", 4, "rolls", ProductType.BATHROOM, StorageLocation.BATHROOM, null);

        // Stock targets: some already met, some short, and one product that
        // isn't in the fridge at all — so "What to buy" shows a useful mix.
        tracked(demo, "Milk", "liters", 3);
        tracked(demo, "Eggs", "units", 12);
        tracked(demo, "Toilet paper", "rolls", 8);
        tracked(demo, "Rice", "kg", 1);
        tracked(demo, "Coffee", "packs", 2);
    }

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
