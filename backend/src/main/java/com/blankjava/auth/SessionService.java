package com.blankjava.auth;

import com.blankjava.account.LoginService;
import com.blankjava.account.RegistrationService;
import com.blankjava.account.UserAccount;
import com.blankjava.account.UserAccountRepository;
import com.blankjava.auth.RefreshTokenService.IssuedRefreshToken;
import com.blankjava.auth.RefreshTokenService.Rotation;
import com.blankjava.security.AccessTokenService;
import com.blankjava.security.AccessTokenService.AccessToken;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Session lifecycle (docs/technical_analysis.md §5.2): an access token for the response body and a
 * refresh token for the cookie, opened at registration or login, renewed by {@code refresh}, ended
 * by {@code logout}.
 */
@Service
public class SessionService {

    private final RegistrationService registration;
    private final LoginService login;
    private final UserAccountRepository accounts;
    private final RefreshTokenService refreshTokens;
    private final AccessTokenService accessTokens;

    public SessionService(
            RegistrationService registration,
            LoginService login,
            UserAccountRepository accounts,
            RefreshTokenService refreshTokens,
            AccessTokenService accessTokens) {
        this.registration = registration;
        this.login = login;
        this.accounts = accounts;
        this.refreshTokens = refreshTokens;
        this.accessTokens = accessTokens;
    }

    /** An opened or renewed session. {@link #toString()} hides both tokens. */
    public record Session(UserAccount user, AccessToken accessToken, IssuedRefreshToken refreshToken) {

        @Override
        public String toString() {
            return "Session[user=" + user + "]";
        }
    }

    /**
     * Creates the account and its first session in one transaction: the account is never left
     * without the refresh token the response announces.
     */
    @Transactional
    public Session register(String email, String displayName, String password) {
        UserAccount user = registration.register(email, displayName, password);
        return open(user, refreshTokens.issueNewSession(user.getId()));
    }

    /**
     * Opens a new session (a new token family) for matching credentials.
     *
     * @throws InvalidCredentialsException for any mismatch, always with the same body
     */
    @Transactional
    public Session login(String email, String password) {
        UserAccount user = login.authenticate(email, password).orElseThrow(InvalidCredentialsException::new);
        return open(user, refreshTokens.issueNewSession(user.getId()));
    }

    /**
     * Renews the session of a refresh cookie: the token is rotated and a new access token issued.
     *
     * @throws RefreshRejectedException no cookie, or a token unknown, expired, already used or
     *     revoked, or whose account no longer exists
     */
    @Transactional(noRollbackFor = RefreshRejectedException.class)
    public Session refresh(@Nullable String refreshToken) {
        if (refreshToken == null || refreshToken.isEmpty()) {
            throw new RefreshRejectedException();
        }
        Rotation rotation = refreshTokens.rotate(refreshToken).orElseThrow(RefreshRejectedException::new);
        UserAccount user = accounts.findById(rotation.userId()).orElseThrow(RefreshRejectedException::new);
        return open(user, rotation.token());
    }

    /**
     * Ends the session of a refresh cookie. Returns the user whose token was revoked; empty when
     * there was no active token, which still ends the session as far as the browser is concerned.
     */
    @Transactional
    public Optional<UUID> logout(@Nullable String refreshToken) {
        if (refreshToken == null || refreshToken.isEmpty()) {
            return Optional.empty();
        }
        return refreshTokens.revoke(refreshToken);
    }

    private Session open(UserAccount user, IssuedRefreshToken refreshToken) {
        return new Session(user, accessTokens.issue(user.getId()), refreshToken);
    }
}
