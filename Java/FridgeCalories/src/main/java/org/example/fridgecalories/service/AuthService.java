package org.example.fridgecalories.service;

import org.example.fridgecalories.model.AuthRequest;
import org.example.fridgecalories.model.User;
import org.example.fridgecalories.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    public User register(AuthRequest request) {
        if (repository.existsByUsernameIgnoreCase(request.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That username is already taken");
        }
        User user = new User();
        user.setUsername(request.username());
        user.setPassword(passwordEncoder.encode(request.password()));
        return repository.save(user);
    }

    public User login(AuthRequest request) {
        // Deliberately the same error whether the user doesn't exist or the
        // password is wrong, so the response can't be used to discover accounts.
        return repository.findByUsernameIgnoreCase(request.username())
                .filter(user -> passwordEncoder.matches(request.password(), user.getPassword()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Wrong username or password"));
    }

    /** The account behind the current request, as established by the token cookie. */
    public User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        // The filter has already loaded and checked this row for this request.
        // Reading it back out of the context saves an identical query on every
        // call, which is one per request against a database in another region.
        if (authentication.getPrincipal() instanceof User user) {
            return user;
        }

        // Anything authenticated another way still resolves the long way round.
        if (authentication.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return repository.findByUsernameIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
}
