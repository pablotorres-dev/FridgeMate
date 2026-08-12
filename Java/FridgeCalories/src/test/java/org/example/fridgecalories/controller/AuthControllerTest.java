package org.example.fridgecalories.controller;

import org.example.fridgecalories.model.AuthRequest;
import org.example.fridgecalories.model.User;
import org.example.fridgecalories.repository.UserRepository;
import org.example.fridgecalories.security.AuthCookies;
import org.example.fridgecalories.security.JwtAuthenticationFilter;
import org.example.fridgecalories.security.JwtService;
import org.example.fridgecalories.security.SecurityConfig;
import org.example.fridgecalories.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    private User account() {
        User user = new User();
        user.setId(1L);
        user.setUsername("pablo");
        user.setPassword("$2a$10$hashed");
        return user;
    }

    @Test
    @DisplayName("signing in hands back a token cookie the browser will keep")
    void signingInSetsALastingCookie() throws Exception {
        when(authService.login(any(AuthRequest.class))).thenReturn(account());
        when(jwtService.generateToken("pablo")).thenReturn("issued-token");
        when(jwtService.getLifetime()).thenReturn(Duration.ofDays(30));

        String setCookie = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"pablo","password":"supersecret"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("pablo"))
                .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);

        assertThat(setCookie)
                .contains(AuthCookies.TOKEN_COOKIE + "=issued-token")
                // Unreadable from JavaScript, so an XSS bug can't steal the session.
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                // Long-lived, which is what keeps you signed in between visits.
                .contains("Max-Age=2592000");
    }

    @Test
    @DisplayName("the password never comes back in the response")
    void neverReturnsThePasswordHash() throws Exception {
        when(authService.login(any(AuthRequest.class))).thenReturn(account());
        when(jwtService.generateToken(any())).thenReturn("issued-token");
        when(jwtService.getLifetime()).thenReturn(Duration.ofDays(30));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"pablo","password":"supersecret"}
                                """))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("too short a password is rejected before any account is created")
    void rejectsAWeakPassword() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"pablo","password":"123"}
                                """))
                .andExpect(status().isBadRequest());

        verify(authService, never()).register(any());
    }

    @Test
    @DisplayName("signing out expires the cookie")
    void signingOutClearsTheCookie() throws Exception {
        String setCookie = mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent())
                .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);

        assertThat(setCookie).contains("Max-Age=0");
    }

    @Test
    @DisplayName("asking who is signed in reports no session when nobody is")
    void reportsNoSessionWhenSignedOut() throws Exception {
        // This endpoint is deliberately reachable without a session — it is how
        // the app checks a stored cookie on startup — so the refusal comes from
        // the service, exactly as it does in production.
        when(authService.currentUser()).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }
}
