package org.example.fridgecalories.repository;

import org.example.fridgecalories.model.Ingredient;
import org.example.fridgecalories.model.StorageLocation;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface IngredientRepository extends JpaRepository<Ingredient, Long> {

    List<Ingredient> findByExpirationDateBefore(LocalDate date);

    List<Ingredient> findByStorageLocation(StorageLocation storageLocation, Sort sort);

    Optional<Ingredient> findByNameIgnoreCaseAndUnitAndStorageLocationAndExpirationDate(
            String name, String unit, StorageLocation storageLocation, LocalDate expirationDate);

    List<Ingredient> findByNameIgnoreCase(String name);
}
