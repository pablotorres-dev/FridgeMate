package org.example.fridgecalories.controller;

import jakarta.validation.Valid;
import org.example.fridgecalories.model.AuthRequest;
import org.example.fridgecalories.model.User;
import org.example.fridgecalories.model.UserResponse;
import org.example.fridgecalories.security.AuthCookies;
import org.example.fridgecalories.security.JwtService;
import org.example.fridgecalories.service.AuthService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    public AuthController(AuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody AuthRequest request) {
        return signedInResponse(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(@Valid @RequestBody AuthRequest request) {
        return signedInResponse(authService.login(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, AuthCookies.clear().toString())
                .build();
    }

    /** Lets the frontend find out on startup whether a stored cookie is still valid. */
    @GetMapping("/me")
    public UserResponse me() {
        return UserResponse.from(authService.currentUser());
    }

    /** Registering and logging in both end the same way: hand back a fresh token cookie. */
    private ResponseEntity<UserResponse> signedInResponse(User user) {
        String token = jwtService.generateToken(user.getUsername());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        AuthCookies.issue(token, jwtService.getLifetime()).toString())
                .body(UserResponse.from(user));
    }
}
