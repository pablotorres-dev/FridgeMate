package org.example.fridgecalories.controller;

import jakarta.servlet.http.Cookie;
import org.example.fridgecalories.model.ParsedReceipt;
import org.example.fridgecalories.model.ProductType;
import org.example.fridgecalories.model.StorageLocation;
import org.example.fridgecalories.model.User;
import org.example.fridgecalories.repository.UserRepository;
import org.example.fridgecalories.security.AuthCookies;
import org.example.fridgecalories.security.JwtAuthenticationFilter;
import org.example.fridgecalories.security.JwtService;
import org.example.fridgecalories.security.SecurityConfig;
import org.example.fridgecalories.service.ReceiptParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The parser is mocked throughout. No test may call the real model: it costs
 * money per run, takes seconds, and would start failing on its own the day the
 * provider changes something.
 *
 * <p>The real security configuration is imported so these prove who can reach
 * the endpoint, rather than passing against a permissive default chain.
 */
@WebMvcTest(ReceiptController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class ReceiptControllerTest {

    private static final String VALID_TOKEN = "a-valid-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReceiptParser parser;

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

    private MockMultipartFile photo(String contentType, byte[] content) {
        return new MockMultipartFile("image", "receipt.jpg", contentType, content);
    }

    @Test
    @DisplayName("a receipt cannot be scanned without signing in")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(multipart("/api/receipt/scan").file(photo("image/jpeg", new byte[]{1, 2, 3})))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(parser);
    }

    @Test
    @DisplayName("a photo comes back as products, with the shop named")
    void returnsTheParsedProducts() throws Exception {
        when(parser.parse(any(), eq("image/jpeg"))).thenReturn(new ParsedReceipt("Lidl", List.of(
                new ParsedReceipt.Item("Cherry tomatoes", 250.0, "g",
                        ProductType.VEGETABLE, StorageLocation.FRIDGE),
                new ParsedReceipt.Item("Toothpaste", 1.0, null,
                        ProductType.BATHROOM, StorageLocation.BATHROOM))));

        mockMvc.perform(multipart("/api/receipt/scan")
                        .file(photo("image/jpeg", new byte[]{1, 2, 3}))
                        .cookie(session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.store").value("Lidl"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].name").value("Cherry tomatoes"))
                .andExpect(jsonPath("$.items[0].storageLocation").value("FRIDGE"))
                .andExpect(jsonPath("$.items[1].unit").doesNotExist());
    }

    @Test
    @DisplayName("a file that isn't a picture never reaches the model")
    void rejectsANonImageUpload() throws Exception {
        mockMvc.perform(multipart("/api/receipt/scan")
                        .file(photo("application/pdf", new byte[]{1, 2, 3}))
                        .cookie(session()))
                .andExpect(status().isUnsupportedMediaType());

        verify(parser, never()).parse(any(), any());
    }

    @Test
    @DisplayName("an empty upload never reaches the model")
    void rejectsAnEmptyUpload() throws Exception {
        mockMvc.perform(multipart("/api/receipt/scan")
                        .file(photo("image/jpeg", new byte[0]))
                        .cookie(session()))
                .andExpect(status().isBadRequest());

        verify(parser, never()).parse(any(), any());
    }

    /**
     * The size check exists to stop a huge upload being forwarded to a paid API,
     * so the assertion that matters is that the parser is never called.
     */
    @Test
    @DisplayName("an oversized photo is refused before it costs anything")
    void rejectsAnOversizedUpload() throws Exception {
        byte[] tooBig = new byte[9 * 1024 * 1024];

        mockMvc.perform(multipart("/api/receipt/scan")
                        .file(photo("image/jpeg", tooBig))
                        .cookie(session()))
                .andExpect(status().isPayloadTooLarge());

        verify(parser, never()).parse(any(), any());
    }

    @Test
    @DisplayName("the page can ask whether scanning is switched on at all")
    void reportsAvailability() throws Exception {
        when(parser.isAvailable()).thenReturn(false);

        mockMvc.perform(get("/api/receipt/status").cookie(session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
    }
}
