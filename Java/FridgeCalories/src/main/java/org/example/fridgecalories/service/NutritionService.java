package org.example.fridgecalories.service;

import org.example.fridgecalories.model.Ingredient;
import org.example.fridgecalories.model.NutritionReport;
import org.example.fridgecalories.model.User;
import org.example.fridgecalories.repository.IngredientRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class NutritionService {

    private final IngredientRepository ingredientRepository;
    private final AuthService authService;
    private final NutritionAnalyzer analyzer;

    /**
     * Analysing costs a call to an external model, so a result is reused until
     * the kitchen it describes actually changes. Held in memory only: losing it
     * on restart costs one extra call, which isn't worth persisting for.
     */
    private final Map<Long, CachedReport> cache = new ConcurrentHashMap<>();

    private record CachedReport(String fingerprint, NutritionReport report) {
    }

    public NutritionService(IngredientRepository ingredientRepository,
                            AuthService authService,
                            NutritionAnalyzer analyzer) {
        this.ingredientRepository = ingredientRepository;
        this.authService = authService;
        this.analyzer = analyzer;
    }

    public boolean isAvailable() {
        return analyzer.isAvailable();
    }

    public NutritionReport analyse() {
        User user = authService.currentUser();
        List<Ingredient> ingredients = ingredientRepository.findByUser(user, Sort.by("name"));

        if (ingredients.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Add something to your inventory first");
        }

        String fingerprint = fingerprint(ingredients);
        CachedReport cached = cache.get(user.getId());
        if (cached != null && cached.fingerprint().equals(fingerprint)) {
            return cached.report();
        }

        NutritionReport report = analyzer.analyse(ingredients);
        cache.put(user.getId(), new CachedReport(fingerprint, report));
        return report;
    }

    /** Changes only when something that would alter the estimate changes. */
    private String fingerprint(List<Ingredient> ingredients) {
        return ingredients.stream()
                .map(i -> i.getName() + '|' + i.getQuantity() + '|' + i.getUnit())
                .collect(Collectors.joining("\n"));
    }
}
