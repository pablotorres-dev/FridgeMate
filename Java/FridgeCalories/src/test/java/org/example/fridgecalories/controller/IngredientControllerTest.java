package org.example.fridgecalories.controller;

import jakarta.servlet.http.Cookie;
import org.example.fridgecalories.model.User;
import org.example.fridgecalories.repository.UserRepository;
import org.example.fridgecalories.security.AuthCookies;
import org.example.fridgecalories.security.JwtAuthenticationFilter;
import org.example.fridgecalories.security.JwtService;
import org.example.fridgecalories.security.SecurityConfig;
import org.example.fridgecalories.service.IngredientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The real security configuration is imported deliberately. Without it the
 * slice loads Spring Boot's permissive default chain, and these tests would
 * pass while proving nothing about who can reach these endpoints.
 *
 * <p>Requests authenticate by sending the token cookie, the same way a browser
 * does, so the filter and the token check are exercised rather than bypassed.
 */
@WebMvcTest(IngredientController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class IngredientControllerTest {

    private static final String VALID_TOKEN = "a-valid-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IngredientService ingredientService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @BeforeEach
    void signedInUserExists() {
        User user = new User();
        user.setId(1L);
        user.setUsername("pablo");
        lenient().when(jwtService.extractUsername(VALID_TOKEN)).thenReturn("pablo");
        lenient().when(userRepository.findByUsernameIgnoreCase("pablo")).thenReturn(Optional.of(user));
    }

    private Cookie session() {
        return new Cookie(AuthCookies.TOKEN_COOKIE, VALID_TOKEN);
    }

    @Test
    @DisplayName("the inventory is not readable without signing in")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/ingredients"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(ingredientService);
    }

    @Test
    @DisplayName("a token that doesn't check out is refused")
    void rejectsAnInvalidToken() throws Exception {
        when(jwtService.extractUsername("tampered")).thenReturn(null);

        mockMvc.perform(get("/api/ingredients").cookie(new Cookie(AuthCookies.TOKEN_COOKIE, "tampered")))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(ingredientService);
    }

    @Test
    @DisplayName("a valid session gets the inventory")
    void returnsTheInventoryWhenSignedIn() throws Exception {
        when(ingredientService.getAll(any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/ingredients").cookie(session()))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    @DisplayName("an ingredient with no name is rejected before it reaches the service")
    void rejectsAnIngredientWithoutAName() throws Exception {
        mockMvc.perform(post("/api/ingredients")
                        .cookie(session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","quantity":1,"type":"DAIRY","storageLocation":"FRIDGE"}
                                """))
                .andExpect(status().isBadRequest());

        verify(ingredientService, never()).save(any());
    }

    @Test
    @DisplayName("a negative quantity is rejected")
    void rejectsANegativeQuantity() throws Exception {
        mockMvc.perform(post("/api/ingredients")
                        .cookie(session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Milk","quantity":-2,"type":"DAIRY","storageLocation":"FRIDGE"}
                                """))
                .andExpect(status().isBadRequest());

        verify(ingredientService, never()).save(any());
    }

    @Test
    @DisplayName("deleting answers with no content")
    void deletesAnIngredient() throws Exception {
        mockMvc.perform(delete("/api/ingredients/7").cookie(session()))
                .andExpect(status().isNoContent());

        verify(ingredientService).delete(7L);
    }
}
