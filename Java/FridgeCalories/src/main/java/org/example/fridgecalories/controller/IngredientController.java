package org.example.fridgecalories.controller;

import jakarta.validation.Valid;
import org.example.fridgecalories.model.Ingredient;
import org.example.fridgecalories.model.StorageLocation;
import org.example.fridgecalories.service.IngredientService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/ingredients")
@CrossOrigin(origins = "http://localhost:4200")
public class IngredientController {

    private final IngredientService service;

    public IngredientController(IngredientService service) {
        this.service = service;
    }

    @GetMapping
    public List<Ingredient> getAll(@RequestParam(required = false) StorageLocation location,
                                    @RequestParam(defaultValue = "asc") String direction) {
        return service.getAll(location, direction);
    }

    @PostMapping
    public Ingredient create(@Valid @RequestBody Ingredient ingredient) {
        return service.save(ingredient);
    }

    @PutMapping("/{id}")
    public Ingredient update(@PathVariable Long id, @Valid @RequestBody Ingredient ingredient) {
        return service.update(id, ingredient);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/expiring")
    public List<Ingredient> getExpiringSoon() {
        return service.getExpiringSoon();
    }
}