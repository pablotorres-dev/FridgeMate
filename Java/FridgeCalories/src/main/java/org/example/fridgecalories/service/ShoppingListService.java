package org.example.fridgecalories.service;

import org.example.fridgecalories.model.Ingredient;
import org.example.fridgecalories.model.ShoppingListEntry;
import org.example.fridgecalories.model.ShoppingListItem;
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

    public ShoppingListService(ShoppingListItemRepository repository, IngredientRepository ingredientRepository) {
        this.repository = repository;
        this.ingredientRepository = ingredientRepository;
    }

    public List<ShoppingListItem> getAll() {
        return repository.findAll();
    }

    public ShoppingListItem save(ShoppingListItem item) {
        return repository.save(item);
    }

    public ShoppingListItem update(Long id, ShoppingListItem updated) {
        ShoppingListItem existing = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shopping list item not found"));
        existing.setName(updated.getName());
        existing.setUnit(updated.getUnit());
        existing.setMinQuantity(updated.getMinQuantity());
        return repository.save(existing);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    public List<ShoppingListEntry> getNeeded() {
        return repository.findAll().stream()
                .filter(item -> item.getMinQuantity() != null)
                .map(this::toEntry)
                .toList();
    }

    private ShoppingListEntry toEntry(ShoppingListItem item) {
        double current = ingredientRepository.findByNameIgnoreCase(item.getName()).stream()
                .mapToDouble(Ingredient::getQuantity)
                .sum();
        double toBuy = Math.max(0, item.getMinQuantity() - current);
        return new ShoppingListEntry(item.getId(), item.getName(), item.getUnit(), item.getMinQuantity(), current, toBuy);
    }
}
