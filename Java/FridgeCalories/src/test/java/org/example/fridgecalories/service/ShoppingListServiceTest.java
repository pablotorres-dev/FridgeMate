package org.example.fridgecalories.service;

import org.example.fridgecalories.model.*;
import org.example.fridgecalories.repository.IngredientRepository;
import org.example.fridgecalories.repository.ShoppingListItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShoppingListServiceTest {

    @Mock
    private ShoppingListItemRepository repository;

    @Mock
    private IngredientRepository ingredientRepository;

    @Mock
    private AuthService authService;

    @InjectMocks
    private ShoppingListService service;

    private User owner;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(1L);
        owner.setUsername("pablo");
    }

    private ShoppingListItem tracked(String name, Double minQuantity) {
        ShoppingListItem item = new ShoppingListItem();
        item.setId(1L);
        item.setName(name);
        item.setUnit("units");
        item.setMinQuantity(minQuantity);
        return item;
    }

    private Ingredient inStock(String name, double quantity) {
        Ingredient ingredient = new Ingredient();
        ingredient.setName(name);
        ingredient.setQuantity(quantity);
        return ingredient;
    }

    @Test
    @DisplayName("shortfall is the target minus what is already in stock")
    void worksOutHowMuchIsMissing() {
        when(authService.currentUser()).thenReturn(owner);
        when(repository.findByUser(owner)).thenReturn(List.of(tracked("Eggs", 12.0)));
        when(ingredientRepository.findByUserAndNameIgnoreCase(owner, "Eggs"))
                .thenReturn(List.of(inStock("Eggs", 4)));

        List<ShoppingListEntry> needed = service.getNeeded();

        assertThat(needed).singleElement().satisfies(entry -> {
            assertThat(entry.currentQuantity()).isEqualTo(4);
            assertThat(entry.quantityToBuy()).isEqualTo(8);
        });
    }

    @Test
    @DisplayName("stock spread across several batches is counted together")
    void addsUpEveryBatchOfTheSameProduct() {
        when(authService.currentUser()).thenReturn(owner);
        when(repository.findByUser(owner)).thenReturn(List.of(tracked("Milk", 5.0)));
        when(ingredientRepository.findByUserAndNameIgnoreCase(owner, "Milk"))
                .thenReturn(List.of(inStock("Milk", 2), inStock("Milk", 1)));

        List<ShoppingListEntry> needed = service.getNeeded();

        assertThat(needed.getFirst().currentQuantity()).isEqualTo(3);
        assertThat(needed.getFirst().quantityToBuy()).isEqualTo(2);
    }

    @Test
    @DisplayName("having more than the target never asks you to buy a negative amount")
    void neverAsksToBuyWhenAlreadyStocked() {
        when(authService.currentUser()).thenReturn(owner);
        when(repository.findByUser(owner)).thenReturn(List.of(tracked("Rice", 1.0)));
        when(ingredientRepository.findByUserAndNameIgnoreCase(owner, "Rice"))
                .thenReturn(List.of(inStock("Rice", 4)));

        assertThat(service.getNeeded().getFirst().quantityToBuy()).isZero();
    }

    @Test
    @DisplayName("products added without a target are left out until one is set")
    void skipsProductsWithNoTarget() {
        when(authService.currentUser()).thenReturn(owner);
        when(repository.findByUser(owner)).thenReturn(List.of(tracked("Coffee", null)));

        assertThat(service.getNeeded()).isEmpty();
        verify(ingredientRepository, never()).findByUserAndNameIgnoreCase(any(), any());
    }

    @Test
    @DisplayName("ownership comes from the session, never from the request body")
    void ignoresAnyOwnerSuppliedByTheClient() {
        User impersonated = new User();
        impersonated.setId(99L);

        ShoppingListItem incoming = tracked("Eggs", 6.0);
        incoming.setUser(impersonated);

        when(authService.currentUser()).thenReturn(owner);
        when(repository.save(any(ShoppingListItem.class))).thenAnswer(call -> call.getArgument(0));

        service.save(incoming);

        ArgumentCaptor<ShoppingListItem> saved = ArgumentCaptor.forClass(ShoppingListItem.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getUser()).isEqualTo(owner);
    }

    @Test
    @DisplayName("deleting someone else's tracked product reports it as missing")
    void refusesToDeleteAnotherUsersItem() {
        when(authService.currentUser()).thenReturn(owner);
        when(repository.findByIdAndUser(42L, owner)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(42L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");

        verify(repository, never()).delete(any());
    }
}
