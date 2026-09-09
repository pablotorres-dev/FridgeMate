package org.example.fridgecalories.service;

import org.example.fridgecalories.model.Ingredient;
import org.example.fridgecalories.model.ProductType;
import org.example.fridgecalories.model.StorageLocation;
import org.example.fridgecalories.model.User;
import org.example.fridgecalories.repository.IngredientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IngredientServiceTest {

    @Mock
    private IngredientRepository repository;

    @Mock
    private AuthService authService;

    @InjectMocks
    private IngredientService service;

    private User owner;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(1L);
        owner.setUsername("pablo");
    }

    private Ingredient ingredient(String name, double quantity, String unit, LocalDate expiry) {
        Ingredient ingredient = new Ingredient();
        ingredient.setName(name);
        ingredient.setQuantity(quantity);
        ingredient.setUnit(unit);
        ingredient.setType(ProductType.DAIRY);
        ingredient.setStorageLocation(StorageLocation.FRIDGE);
        ingredient.setExpirationDate(expiry);
        return ingredient;
    }

    @Test
    @DisplayName("listing without a location returns everything the user owns")
    void listsAllOfTheUsersIngredients() {
        when(authService.currentUser()).thenReturn(owner);
        when(repository.findByUser(eq(owner), any(Sort.class))).thenReturn(List.of());

        service.getAll(null, "asc");

        verify(repository).findByUser(eq(owner), any(Sort.class));
        verify(repository, never()).findByUserAndStorageLocation(any(), any(), any());
    }

    @Test
    @DisplayName("listing with a location narrows the query to that location")
    void listsOnlyTheChosenLocation() {
        when(authService.currentUser()).thenReturn(owner);
        when(repository.findByUserAndStorageLocation(eq(owner), eq(StorageLocation.FREEZER), any(Sort.class)))
                .thenReturn(List.of());

        service.getAll(StorageLocation.FREEZER, "asc");

        verify(repository).findByUserAndStorageLocation(eq(owner), eq(StorageLocation.FREEZER), any(Sort.class));
    }

    @Test
    @DisplayName("ownership comes from the session, never from the request body")
    void ignoresAnyOwnerSuppliedByTheClient() {
        User impersonated = new User();
        impersonated.setId(99L);
        impersonated.setUsername("someone-else");

        Ingredient incoming = ingredient("Milk", 1, "liters", null);
        incoming.setUser(impersonated);

        when(authService.currentUser()).thenReturn(owner);
        when(repository.findByUserAndNameIgnoreCaseAndUnitAndStorageLocationAndExpirationDate(
                any(), any(), any(), any(), any())).thenReturn(Optional.empty());
        when(repository.save(any(Ingredient.class))).thenAnswer(call -> call.getArgument(0));

        service.save(incoming);

        ArgumentCaptor<Ingredient> saved = ArgumentCaptor.forClass(Ingredient.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getUser()).isEqualTo(owner);
    }

    @Test
    @DisplayName("adding more of an identical item adds to the existing quantity")
    void mergesIntoTheMatchingIngredient() {
        LocalDate expiry = LocalDate.now().plusDays(5);
        Ingredient existing = ingredient("Milk", 2, "liters", expiry);
        Ingredient incoming = ingredient("Milk", 3, "liters", expiry);

        when(authService.currentUser()).thenReturn(owner);
        when(repository.findByUserAndNameIgnoreCaseAndUnitAndStorageLocationAndExpirationDate(
                owner, "Milk", "liters", StorageLocation.FRIDGE, expiry)).thenReturn(Optional.of(existing));
        when(repository.save(any(Ingredient.class))).thenAnswer(call -> call.getArgument(0));

        Ingredient result = service.save(incoming);

        assertThat(result.getQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("the same product with a different expiry date is kept as a separate batch")
    void doesNotMergeAcrossExpiryDates() {
        LocalDate otherDate = LocalDate.now().plusDays(9);
        Ingredient incoming = ingredient("Milk", 3, "liters", otherDate);

        when(authService.currentUser()).thenReturn(owner);
        // Nothing matches on all four of name, unit, location and expiry.
        when(repository.findByUserAndNameIgnoreCaseAndUnitAndStorageLocationAndExpirationDate(
                owner, "Milk", "liters", StorageLocation.FRIDGE, otherDate)).thenReturn(Optional.empty());
        when(repository.save(any(Ingredient.class))).thenAnswer(call -> call.getArgument(0));

        Ingredient result = service.save(incoming);

        assertThat(result.getQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("updating someone else's ingredient reports it as missing")
    void refusesToUpdateAnotherUsersIngredient() {
        when(authService.currentUser()).thenReturn(owner);
        when(repository.findByIdAndUser(42L, owner)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(42L, ingredient("Milk", 1, "liters", null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("deleting someone else's ingredient reports it as missing")
    void refusesToDeleteAnotherUsersIngredient() {
        when(authService.currentUser()).thenReturn(owner);
        when(repository.findByIdAndUser(42L, owner)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(42L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");

        verify(repository, never()).delete(any());
    }

    @Test
    @DisplayName("the expiry warning looks three days ahead")
    void warnsAboutTheNextThreeDays() {
        when(authService.currentUser()).thenReturn(owner);
        when(repository.findByUserAndExpirationDateBefore(eq(owner), any(LocalDate.class))).thenReturn(List.of());

        service.getExpiringSoon();

        ArgumentCaptor<LocalDate> cutoff = ArgumentCaptor.forClass(LocalDate.class);
        verify(repository).findByUserAndExpirationDateBefore(eq(owner), cutoff.capture());
        assertThat(cutoff.getValue()).isEqualTo(LocalDate.now().plusDays(3));
    }
    // ---- Filing a whole shop at once ----

    /**
     * The saving of a scanned receipt. What matters is the query count: one read
     * of the kitchen however long the receipt is, where the old one-at-a-time
     * path did a lookup per product.
     */
    @Test
    @DisplayName("a batch reads the kitchen once, whatever its size")
    void batchReadsTheKitchenOnce() {
        when(authService.currentUser()).thenReturn(owner);
        when(repository.findByUser(eq(owner), any(Sort.class))).thenReturn(List.of());
        when(repository.saveAll(any())).thenAnswer(call -> call.getArgument(0));

        service.saveAll(List.of(
                ingredient("Milk", 2, "liters", null),
                ingredient("Rice", 1, "kg", null),
                ingredient("Eggs", 6, "units", null)));

        verify(repository, times(1)).findByUser(eq(owner), any(Sort.class));
        verify(repository, times(1)).saveAll(any());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("a batch adds to what is already in the kitchen instead of duplicating it")
    void batchMergesIntoExistingStock() {
        Ingredient stocked = ingredient("Milk", 1, "liters", null);
        when(authService.currentUser()).thenReturn(owner);
        when(repository.findByUser(eq(owner), any(Sort.class))).thenReturn(List.of(stocked));
        when(repository.saveAll(any())).thenAnswer(call -> call.getArgument(0));

        service.saveAll(List.of(ingredient("milk", 2, "liters", null)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Ingredient>> saved = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(saved.capture());
        assertThat(saved.getValue()).hasSize(1);
        assertThat(saved.getValue().get(0).getQuantity()).isEqualTo(3.0);
    }

    /**
     * A receipt can list the same product twice. Saving one at a time only
     * handled this because each request saw the result of the one before.
     */
    @Test
    @DisplayName("two identical lines on one receipt merge into each other")
    void batchMergesLinesWithinItself() {
        when(authService.currentUser()).thenReturn(owner);
        when(repository.findByUser(eq(owner), any(Sort.class))).thenReturn(List.of());
        when(repository.saveAll(any())).thenAnswer(call -> call.getArgument(0));

        service.saveAll(List.of(
                ingredient("Yoghurt", 4, "pots", null),
                ingredient("Yoghurt", 2, "pots", null)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Ingredient>> saved = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(saved.capture());
        assertThat(saved.getValue()).hasSize(1);
        assertThat(saved.getValue().get(0).getQuantity()).isEqualTo(6.0);
    }

    /** The rule that started all of this: two batches with different dates. */
    @Test
    @DisplayName("the same food with different dates stays as separate rows")
    void batchKeepsDifferentExpiryDatesApart() {
        when(authService.currentUser()).thenReturn(owner);
        when(repository.findByUser(eq(owner), any(Sort.class))).thenReturn(List.of());
        when(repository.saveAll(any())).thenAnswer(call -> call.getArgument(0));

        service.saveAll(List.of(
                ingredient("Milk", 2, "liters", LocalDate.of(2026, 9, 20)),
                ingredient("Milk", 2, "liters", LocalDate.of(2026, 9, 27))));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Ingredient>> saved = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(saved.capture());
        assertThat(saved.getValue()).hasSize(2);
    }

}
