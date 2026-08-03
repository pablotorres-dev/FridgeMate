package org.example.fridgecalories.controller;

import jakarta.validation.Valid;
import org.example.fridgecalories.model.ShoppingListEntry;
import org.example.fridgecalories.model.ShoppingListItem;
import org.example.fridgecalories.service.ShoppingListService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/shopping-list")
@CrossOrigin(origins = "http://localhost:4200")
public class ShoppingListController {

    private final ShoppingListService service;

    public ShoppingListController(ShoppingListService service) {
        this.service = service;
    }

    @GetMapping
    public List<ShoppingListItem> getAll() {
        return service.getAll();
    }

    @GetMapping("/needed")
    public List<ShoppingListEntry> getNeeded() {
        return service.getNeeded();
    }

    @PostMapping
    public ShoppingListItem create(@Valid @RequestBody ShoppingListItem item) {
        return service.save(item);
    }

    @PutMapping("/{id}")
    public ShoppingListItem update(@PathVariable Long id, @Valid @RequestBody ShoppingListItem item) {
        return service.update(id, item);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
