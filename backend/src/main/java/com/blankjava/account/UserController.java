package com.blankjava.account;

import com.blankjava.api.UsersApi;
import com.blankjava.api.model.User;
import com.blankjava.security.TokenOwnerNotFoundException;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/users/*} (tag {@code users} of api/openapi.yaml). The user is the access token's
 * {@code sub} and nothing else: no user id is read from the request, so no other account can be
 * reached. The profile is read from the database on every call rather than from the token, so it is
 * never stale.
 */
@RestController
public class UserController implements UsersApi {

    private final UserAccountRepository accounts;

    public UserController(UserAccountRepository accounts) {
        this.accounts = accounts;
    }

    /** The token owner's profile; a valid token whose account no longer exists is a {@code 401}. */
    @Override
    public ResponseEntity<User> getCurrentUser() {
        UserAccount account = tokenOwner().flatMap(accounts::findById).orElseThrow(TokenOwnerNotFoundException::new);
        return ResponseEntity.ok(toApi(account));
    }

    /** The contract's {@code User}: the owner's own view, never the password hash. */
    public static User toApi(UserAccount account) {
        return new User(
                account.getId(),
                account.getEmail(),
                account.getDisplayName(),
                account.getCreatedAt().atOffset(ZoneOffset.UTC));
    }

    private static Optional<UUID> tokenOwner() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(token.getName()));
        } catch (IllegalArgumentException e) {
            // Our tokens always carry a UUID; any other subject has no account
            return Optional.empty();
        }
    }
}
