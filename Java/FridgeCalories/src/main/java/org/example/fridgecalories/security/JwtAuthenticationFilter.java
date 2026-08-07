package org.example.fridgecalories.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.fridgecalories.repository.UserRepository;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Restores the logged-in user on every request from the token cookie.
 *
 * <p>Because the token is self-contained and verified by signature, no session
 * state is kept on the server — logins survive restarts and redeploys, which is
 * what keeps users signed in on a platform that sleeps idle services.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = AuthCookies.readToken(request);
            if (token != null) {
                String username = jwtService.extractUsername(token);
                if (username != null) {
                    // Confirm the account still exists — a deleted user's token
                    // must stop working even before it expires.
                    userRepository.findByUsernameIgnoreCase(username).ifPresent(user -> {
                        var authentication = new UsernamePasswordAuthenticationToken(
                                user.getUsername(), null, List.of());
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    });
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
