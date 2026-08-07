package org.example.fridgecalories.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "ingredients")
public class Ingredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Owner of this item. Set by the service from the logged-in account — never
     * accepted from the request body, so a client can't write into someone
     * else's kitchen. Excluded from JSON and from toString/equals to avoid
     * leaking the account and to keep lazy loading from being triggered.
     */
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotBlank
    private String name;

    @NotNull
    @Positive
    private Double quantity;

    private String unit;

    @NotNull
    @Enumerated(EnumType.STRING)
    private ProductType type;

    private LocalDate expirationDate;

    @NotNull
    @Enumerated(EnumType.STRING)
    private StorageLocation storageLocation;

    @Column(updatable = false)
    private LocalDateTime addedAt;

    @PrePersist
    protected void onCreate() {
        addedAt = LocalDateTime.now();
    }
}