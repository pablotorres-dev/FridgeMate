package org.example.fridgecalories.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * An account. Every ingredient and shopping-list item belongs to one,
 * so each user only ever sees their own kitchen.
 *
 * <p>Named {@code users} because {@code user} is a reserved word in PostgreSQL.
 */
@Entity
@Data
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    /** BCrypt hash — the plain-text password is never stored, and never serialized. */
    @JsonIgnore
    @Column(nullable = false)
    private String password;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
