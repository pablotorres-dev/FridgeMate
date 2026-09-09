package org.example.fridgecalories.service;

import org.example.fridgecalories.model.Ingredient;
import org.example.fridgecalories.model.ShoppingListEntry;
import org.example.fridgecalories.model.ShoppingListItem;
import org.example.fridgecalories.model.User;
import org.example.fridgecalories.repository.IngredientRepository;
import org.example.fridgecalories.repository.ShoppingListItemRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ShoppingListService {

    private final ShoppingListItemRepository repository;
    private final IngredientRepository ingredientRepository;
    private final AuthService authService;

    public ShoppingListService(ShoppingListItemRepository repository,
                               IngredientRepository ingredientRepository,
                               AuthService authService) {
        this.repository = repository;
        this.ingredientRepository = ingredientRepository;
        this.authService = authService;
    }

    public List<ShoppingListItem> getAll() {
        return repository.findByUser(authService.currentUser());
    }

    public ShoppingListItem save(ShoppingListItem item) {
        // Ownership comes from the session, never from the request body.
        item.setUser(authService.currentUser());
        return repository.save(item);
    }

    public ShoppingListItem update(Long id, ShoppingListItem updated) {
        ShoppingListItem existing = requireOwned(id);
        existing.setName(updated.getName());
        existing.setUnit(updated.getUnit());
        existing.setMinQuantity(updated.getMinQuantity());
        return repository.save(existing);
    }

    public void delete(Long id) {
        repository.delete(requireOwned(id));
    }

    /**
     * Two queries regardless of how many products are tracked.
     *
     * <p>This used to ask the database for the stock of each tracked product in
     * turn, which is a query per row: five tracked products cost six round trips
     * to a database in another region, and the endpoint measured more than twice
     * the plain list it is built from. A kitchen fits in memory, so it is
     * fetched once and the names are matched here.
     */
    public List<ShoppingListEntry> getNeeded() {
        User user = authService.currentUser();

        Map<String, Double> stockByName = ingredientRepository.findByUser(user, Sort.unsorted()).stream()
                .filter(ingredient -> ingredient.getQuantity() != null)
                .collect(Collectors.groupingBy(
                        ingredient -> ingredient.getName().toLowerCase(Locale.ROOT),
                        Collectors.summingDouble(Ingredient::getQuantity)));

        return repository.findByUser(user).stream()
                .filter(item -> item.getMinQuantity() != null)
                .map(item -> toEntry(item, stockByName))
                .toList();
    }

    private ShoppingListEntry toEntry(ShoppingListItem item, Map<String, Double> stockByName) {
        // Matched case-insensitively, as the query it replaces was: "Milk" in
        // the shopping list should find "milk" in the fridge.
        double current = stockByName.getOrDefault(item.getName().toLowerCase(Locale.ROOT), 0.0);
        double toBuy = Math.max(0, item.getMinQuantity() - current);
        return new ShoppingListEntry(item.getId(), item.getName(), item.getUnit(), item.getMinQuantity(), current, toBuy);
    }

    private ShoppingListItem requireOwned(Long id) {
        return repository.findByIdAndUser(id, authService.currentUser())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shopping list item not found"));
    }
}
