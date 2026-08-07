package org.example.fridgecalories.model;

/** The account details safe to send to the browser — never the password hash. */
public record UserResponse(Long id, String username) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getUsername());
    }
}
