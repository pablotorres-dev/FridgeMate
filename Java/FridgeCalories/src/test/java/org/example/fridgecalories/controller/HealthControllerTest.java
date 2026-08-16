package org.example.fridgecalories.controller;

import org.example.fridgecalories.repository.UserRepository;
import org.example.fridgecalories.security.JwtAuthenticationFilter;
import org.example.fridgecalories.security.JwtService;
import org.example.fridgecalories.security.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The real security configuration is imported so this proves something: the
 * whole point of the endpoint is that it answers a caller holding no account.
 */
@WebMvcTest(HealthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    @DisplayName("answers without a session, since the uptime monitor has no account")
    void isReachableAnonymously() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    /**
     * Guards the reason this endpoint exists rather than reusing an existing one:
     * it runs every few minutes forever, so it must stay free to answer. A later
     * change that starts checking the database here would keep the managed
     * database awake around the clock and quietly burn its usage allowance.
     */
    @Test
    @DisplayName("costs nothing: reaches neither the database nor the token service")
    void touchesNoDependencies() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());

        verifyNoInteractions(userRepository, jwtService);
    }
}
