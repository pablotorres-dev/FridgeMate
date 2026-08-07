package org.example.fridgecalories.repository;

import org.example.fridgecalories.model.ShoppingListItem;
import org.example.fridgecalories.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Scoped by owner, like {@link IngredientRepository}. */
@Repository
public interface ShoppingListItemRepository extends JpaRepository<ShoppingListItem, Long> {

    List<ShoppingListItem> findByUser(User user);

    Optional<ShoppingListItem> findByIdAndUser(Long id, User user);
}
