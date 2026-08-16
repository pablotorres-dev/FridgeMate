package org.example.fridgecalories.service;

import org.example.fridgecalories.model.AuthRequest;
import org.example.fridgecalories.model.User;
import org.example.fridgecalories.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository repository;

    // A real encoder rather than a mock: the point of these tests is that
    // passwords are genuinely hashed, which a mock would happily fake.
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private AuthService service() {
        return new AuthService(repository, passwordEncoder);
    }

    private User existingUser(String username, String rawPassword) {
        User user = new User();
        user.setId(1L);
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        return user;
    }

    @Test
    @DisplayName("the password is stored hashed, never in the clear")
    void storesOnlyAHashOfThePassword() {
        when(repository.existsByUsernameIgnoreCase("pablo")).thenReturn(false);
        when(repository.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

        service().register(new AuthRequest("pablo", "supersecret"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getPassword())
                .isNotEqualTo("supersecret")
                .startsWith("$2");
        assertThat(passwordEncoder.matches("supersecret", saved.getValue().getPassword())).isTrue();
    }

    @Test
    @DisplayName("a username already taken is rejected as a conflict")
    void rejectsADuplicateUsername() {
        when(repository.existsByUsernameIgnoreCase("pablo")).thenReturn(true);

        assertThatThrownBy(() -> service().register(new AuthRequest("pablo", "supersecret")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("signing in with the right password returns the account")
    void signsInWithCorrectCredentials() {
        when(repository.findByUsernameIgnoreCase("pablo"))
                .thenReturn(Optional.of(existingUser("pablo", "supersecret")));

        assertThat(service().login(new AuthRequest("pablo", "supersecret")).getUsername()).isEqualTo("pablo");
    }

    @Test
    @DisplayName("a wrong password is refused")
    void refusesAWrongPassword() {
        when(repository.findByUsernameIgnoreCase("pablo"))
                .thenReturn(Optional.of(existingUser("pablo", "supersecret")));

        assertThatThrownBy(() -> service().login(new AuthRequest("pablo", "not-the-password")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401");
    }

    @Test
    @DisplayName("an unknown username fails the same way as a wrong password, revealing nothing")
    void doesNotRevealWhetherAnAccountExists() {
        when(repository.findByUsernameIgnoreCase("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().login(new AuthRequest("ghost", "supersecret")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401");
    }
}
