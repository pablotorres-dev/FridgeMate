package org.example.fridgecalories.service;

import org.example.fridgecalories.model.Ingredient;
import org.example.fridgecalories.model.StorageLocation;
import org.example.fridgecalories.repository.IngredientRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.List;

@Service
public class IngredientService {

    private final IngredientRepository repository;

    public IngredientService(IngredientRepository repository) {
        this.repository = repository;
    }

    public List<Ingredient> getAll(StorageLocation location, String direction) {
        Sort.Direction sortDirection = "desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Sort sort = Sort.by(new Sort.Order(sortDirection, "expirationDate").nullsLast());
        if (location != null) {
            return repository.findByStorageLocation(location, sort);
        }
        return repository.findAll(sort);
    }

    public Ingredient save(Ingredient ingredient) {
        return repository.findByNameIgnoreCaseAndUnitAndStorageLocationAndExpirationDate(
                        ingredient.getName(), ingredient.getUnit(), ingredient.getStorageLocation(),
                        ingredient.getExpirationDate())
                .map(existing -> mergeIntoExisting(existing, ingredient))
                .orElseGet(() -> repository.save(ingredient));
    }

    private Ingredient mergeIntoExisting(Ingredient existing, Ingredient incoming) {
        existing.setQuantity(existing.getQuantity() + incoming.getQuantity());
        return repository.save(existing);
    }

    public Ingredient update(Long id, Ingredient updated) {
        Ingredient existing = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ingredient not found"));
        existing.setName(updated.getName());
        existing.setQuantity(updated.getQuantity());
        existing.setUnit(updated.getUnit());
        existing.setType(updated.getType());
        existing.setExpirationDate(updated.getExpirationDate());
        existing.setStorageLocation(updated.getStorageLocation());
        return repository.save(existing);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    public List<Ingredient> getExpiringSoon() {
        return repository.findByExpirationDateBefore(LocalDate.now().plusDays(3));
    }
}
