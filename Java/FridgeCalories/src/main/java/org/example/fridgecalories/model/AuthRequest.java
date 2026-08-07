package org.example.fridgecalories.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Credentials sent when registering or logging in. */
public record AuthRequest(
        @NotBlank @Size(min = 3, max = 30) String username,
        @NotBlank @Size(min = 6, max = 100) String password
) {
}
