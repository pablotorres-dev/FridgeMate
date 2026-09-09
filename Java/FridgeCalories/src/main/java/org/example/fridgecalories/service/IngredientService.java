package org.example.fridgecalories.service;

import org.example.fridgecalories.model.Ingredient;
import org.example.fridgecalories.model.StorageLocation;
import org.example.fridgecalories.model.User;
import org.example.fridgecalories.repository.IngredientRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.Map;

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

    /**
     * Files a whole shop at once, with the same merging rules as saving one.
     *
     * <p>Putting a scanned receipt away used to be one request per product:
     * twenty-six trips across the network for one shop, each looking up its own
     * duplicate. This reads the kitchen once and matches in memory, so the cost
     * is one query plus the writes however long the receipt is.
     *
     * <p>Two identical lines on the same receipt merge into each other too,
     * which the one-at-a-time version only managed because each request saw the
     * result of the last.
     */
    @Transactional
    public List<Ingredient> saveAll(List<Ingredient> incoming) {
        User user = authService.currentUser();

        Map<MergeKey, Ingredient> byKey = new LinkedHashMap<>();
        for (Ingredient existing : repository.findByUser(user, Sort.unsorted())) {
            byKey.putIfAbsent(MergeKey.of(existing), existing);
        }

        List<Ingredient> toSave = new ArrayList<>();
        Set<MergeKey> queued = new HashSet<>();

        for (Ingredient candidate : incoming) {
            // Ownership comes from the session, never from the request body.
            candidate.setUser(user);
            MergeKey key = MergeKey.of(candidate);
            Ingredient match = byKey.get(key);

            if (match == null) {
                byKey.put(key, candidate);
                queued.add(key);
                toSave.add(candidate);
            } else {
                match.setQuantity(match.getQuantity() + candidate.getQuantity());
                // Queued once however many lines of the receipt land on it.
                if (queued.add(key)) {
                    toSave.add(match);
                }
            }
        }

        return repository.saveAll(toSave);
    }

    /**
     * What makes two rows the same product. Deliberately the same four fields
     * the single-item query matches on, so a batch and a sequence of individual
     * saves cannot disagree — two batches of the same food with different dates
     * stay apart.
     */
    private record MergeKey(String name, String unit, StorageLocation location, LocalDate expiry) {
        static MergeKey of(Ingredient ingredient) {
            String name = ingredient.getName() == null
                    ? null
                    : ingredient.getName().toLowerCase(Locale.ROOT);
            return new MergeKey(name, ingredient.getUnit(),
                    ingredient.getStorageLocation(), ingredient.getExpirationDate());
        }
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
