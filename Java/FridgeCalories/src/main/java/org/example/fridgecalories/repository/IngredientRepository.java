package org.example.fridgecalories.repository;

import org.example.fridgecalories.model.Ingredient;
import org.example.fridgecalories.model.StorageLocation;
import org.example.fridgecalories.model.User;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Every method is scoped by owner. Nothing here can read or reach an ingredient
 * belonging to another account, including lookups by id.
 */
@Repository
public interface IngredientRepository extends JpaRepository<Ingredient, Long> {

    List<Ingredient> findByUser(User user, Sort sort);

    Optional<Ingredient> findByIdAndUser(Long id, User user);

    List<Ingredient> findByUserAndExpirationDateBefore(User user, LocalDate date);

    List<Ingredient> findByUserAndStorageLocation(User user, StorageLocation storageLocation, Sort sort);

    Optional<Ingredient> findByUserAndNameIgnoreCaseAndUnitAndStorageLocationAndExpirationDate(
            User user, String name, String unit, StorageLocation storageLocation, LocalDate expirationDate);

    List<Ingredient> findByUserAndNameIgnoreCase(User user, String name);
}
