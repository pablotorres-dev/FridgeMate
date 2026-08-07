package org.example.fridgecalories.service;

import org.example.fridgecalories.model.Ingredient;
import org.example.fridgecalories.model.StorageLocation;
import org.example.fridgecalories.model.User;
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
    private final AuthService authService;

    public IngredientService(IngredientRepository repository, AuthService authService) {
        this.repository = repository;
        this.authService = authService;
    }

    public List<Ingredient> getAll(StorageLocation location, String direction) {
        User user = authService.currentUser();
        Sort.Direction sortDirection = "desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Sort sort = Sort.by(new Sort.Order(sortDirection, "expirationDate").nullsLast());
        if (location != null) {
            return repository.findByUserAndStorageLocation(user, location, sort);
        }
        return repository.findByUser(user, sort);
    }

    public Ingredient save(Ingredient ingredient) {
        User user = authService.currentUser();
        // Ownership comes from the session, never from the request body.
        ingredient.setUser(user);
        return repository.findByUserAndNameIgnoreCaseAndUnitAndStorageLocationAndExpirationDate(
                        user, ingredient.getName(), ingredient.getUnit(), ingredient.getStorageLocation(),
                        ingredient.getExpirationDate())
                .map(existing -> mergeIntoExisting(existing, ingredient))
                .orElseGet(() -> repository.save(ingredient));
    }

    private Ingredient mergeIntoExisting(Ingredient existing, Ingredient incoming) {
        existing.setQuantity(existing.getQuantity() + incoming.getQuantity());
        return repository.save(existing);
    }

    public Ingredient update(Long id, Ingredient updated) {
        Ingredient existing = requireOwned(id);
        existing.setName(updated.getName());
        existing.setQuantity(updated.getQuantity());
        existing.setUnit(updated.getUnit());
        existing.setType(updated.getType());
        existing.setExpirationDate(updated.getExpirationDate());
        existing.setStorageLocation(updated.getStorageLocation());
        return repository.save(existing);
    }

    public void delete(Long id) {
        repository.delete(requireOwned(id));
    }

    public List<Ingredient> getExpiringSoon() {
        return repository.findByUserAndExpirationDateBefore(authService.currentUser(), LocalDate.now().plusDays(3));
    }

    /**
     * Someone else's ingredient is reported as missing rather than forbidden, so
     * the response can't be used to probe which ids exist.
     */
    private Ingredient requireOwned(Long id) {
        return repository.findByIdAndUser(id, authService.currentUser())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ingredient not found"));
    }
}
