package com.blankjava.auth;

import com.blankjava.account.RegistrationService;
import com.blankjava.account.UserAccount;
import com.blankjava.auth.RefreshTokenService.IssuedRefreshToken;
import com.blankjava.security.AccessTokenService;
import com.blankjava.security.AccessTokenService.AccessToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opens sessions (docs/technical_analysis.md §5.2): an access token for the response body and a
 * refresh token for the cookie. Login, refresh and logout come with #30.
 */
@Service
public class SessionService {

    private final RegistrationService registration;
    private final RefreshTokenService refreshTokens;
    private final AccessTokenService accessTokens;

    public SessionService(
            RegistrationService registration, RefreshTokenService refreshTokens, AccessTokenService accessTokens) {
        this.registration = registration;
        this.refreshTokens = refreshTokens;
        this.accessTokens = accessTokens;
    }

    /** An opened session. {@link #toString()} hides both tokens. */
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
        IssuedRefreshToken refreshToken = refreshTokens.issueNewSession(user.getId());
        return new Session(user, accessTokens.issue(user.getId()), refreshToken);
    }
}
