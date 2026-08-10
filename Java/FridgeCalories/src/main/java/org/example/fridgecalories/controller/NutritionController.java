package org.example.fridgecalories.controller;

import org.example.fridgecalories.model.NutritionReport;
import org.example.fridgecalories.service.NutritionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/nutrition")
public class NutritionController {

    private final NutritionService service;

    public NutritionController(NutritionService service) {
        this.service = service;
    }

    /** Lets the page say the feature is switched off instead of failing on a click. */
    @GetMapping("/status")
    public Map<String, Boolean> status() {
        return Map.of("available", service.isAvailable());
    }

    @GetMapping
    public NutritionReport analyse() {
        return service.analyse();
    }
}
