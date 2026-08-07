package org.example.fridgecalories.service;

import org.example.fridgecalories.model.Ingredient;
import org.example.fridgecalories.model.ShoppingListEntry;
import org.example.fridgecalories.model.ShoppingListItem;
import org.example.fridgecalories.model.User;
import org.example.fridgecalories.repository.IngredientRepository;
import org.example.fridgecalories.repository.ShoppingListItemRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

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

    public List<ShoppingListEntry> getNeeded() {
        User user = authService.currentUser();
        return repository.findByUser(user).stream()
                .filter(item -> item.getMinQuantity() != null)
                .map(item -> toEntry(user, item))
                .toList();
    }

    private ShoppingListEntry toEntry(User user, ShoppingListItem item) {
        double current = ingredientRepository.findByUserAndNameIgnoreCase(user, item.getName()).stream()
                .mapToDouble(Ingredient::getQuantity)
                .sum();
        double toBuy = Math.max(0, item.getMinQuantity() - current);
        return new ShoppingListEntry(item.getId(), item.getName(), item.getUnit(), item.getMinQuantity(), current, toBuy);
    }

    private ShoppingListItem requireOwned(Long id) {
        return repository.findByIdAndUser(id, authService.currentUser())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shopping list item not found"));
    }
}
