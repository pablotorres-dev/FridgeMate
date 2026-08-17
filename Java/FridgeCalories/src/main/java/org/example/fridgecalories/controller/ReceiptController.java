package org.example.fridgecalories.controller;

import org.example.fridgecalories.model.ParsedReceipt;
import org.example.fridgecalories.service.ReceiptParser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/receipt")
public class ReceiptController {

    /**
     * Comfortably above a downscaled phone photo, and well below the point where
     * reading the image would cost more than it saves. The browser shrinks the
     * picture before sending it, so hitting this means something is wrong.
     */
    private static final long MAX_BYTES = 8L * 1024 * 1024;

    /** The formats the model can decode; anything else is rejected before it is uploaded. */
    private static final Set<String> ALLOWED_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif");

    private final ReceiptParser parser;

    public ReceiptController(ReceiptParser parser) {
        this.parser = parser;
    }

    /** Lets the page hide the feature instead of offering a button that always fails. */
    @GetMapping("/status")
    public Map<String, Boolean> status() {
        return Map.of("available", parser.isAvailable());
    }

    @PostMapping("/scan")
    public ParsedReceipt scan(@RequestParam("image") MultipartFile image) {
        if (image.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No image was uploaded");
        }
        if (image.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "That image is too large. Please take the photo again.");
        }

        String contentType = image.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Only photographs can be scanned");
        }

        try {
            return parser.parse(image.getBytes(), contentType.toLowerCase());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Couldn't read the uploaded image", e);
        }
    }
}
