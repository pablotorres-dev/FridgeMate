package org.example.fridgecalories.service;

import org.example.fridgecalories.model.Ingredient;
import org.example.fridgecalories.model.NutritionReport;
import org.example.fridgecalories.model.User;
import org.example.fridgecalories.repository.IngredientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * The analyzer is mocked throughout, so these tests never call a real AI
 * provider — they cost nothing to run and can't be broken by a model changing.
 */
@ExtendWith(MockitoExtension.class)
class NutritionServiceTest {

    @Mock
    private IngredientRepository ingredientRepository;

    @Mock
    private AuthService authService;

    @Mock
    private NutritionAnalyzer analyzer;

    @InjectMocks
    private NutritionService service;

    private User owner;
    private NutritionReport report;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(1L);
        owner.setUsername("pablo");
        report = new NutritionReport("A summary", 1200.0, List.of(), List.of(), List.of());
    }

    private Ingredient ingredient(String name, double quantity) {
        Ingredient ingredient = new Ingredient();
        ingredient.setName(name);
        ingredient.setQuantity(quantity);
        ingredient.setUnit("units");
        return ingredient;
    }

    @Test
    @DisplayName("an empty kitchen is rejected before any analysis is requested")
    void refusesToAnalyseAnEmptyInventory() {
        when(authService.currentUser()).thenReturn(owner);
        when(ingredientRepository.findByUser(eq(owner), any(Sort.class))).thenReturn(List.of());

        assertThatThrownBy(() -> service.analyse())
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");

        verifyNoInteractions(analyzer);
    }

    @Test
    @DisplayName("the analyzer's report is what gets returned")
    void returnsTheAnalysis() {
        when(authService.currentUser()).thenReturn(owner);
        when(ingredientRepository.findByUser(eq(owner), any(Sort.class)))
                .thenReturn(List.of(ingredient("Eggs", 6)));
        when(analyzer.analyse(any())).thenReturn(report);

        assertThat(service.analyse()).isEqualTo(report);
    }

    @Test
    @DisplayName("asking twice for an unchanged kitchen only costs one analysis")
    void reusesTheReportWhileTheInventoryIsUnchanged() {
        when(authService.currentUser()).thenReturn(owner);
        when(ingredientRepository.findByUser(eq(owner), any(Sort.class)))
                .thenReturn(List.of(ingredient("Eggs", 6)));
        when(analyzer.analyse(any())).thenReturn(report);

        service.analyse();
        service.analyse();

        verify(analyzer, times(1)).analyse(any());
    }

    @Test
    @DisplayName("changing the inventory makes it analyse again")
    void analysesAgainOnceTheInventoryChanges() {
        when(authService.currentUser()).thenReturn(owner);
        when(analyzer.analyse(any())).thenReturn(report);
        when(ingredientRepository.findByUser(eq(owner), any(Sort.class)))
                .thenReturn(List.of(ingredient("Eggs", 6)))
                .thenReturn(List.of(ingredient("Eggs", 12)));

        service.analyse();
        service.analyse();

        verify(analyzer, times(2)).analyse(any());
    }

    @Test
    @DisplayName("availability follows whichever provider is configured")
    void reportsWhetherAnalysisIsConfigured() {
        when(analyzer.isAvailable()).thenReturn(false);
        assertThat(service.isAvailable()).isFalse();
    }
}
