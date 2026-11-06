package com.blankjava.auth;

import com.blankjava.api.AuthApi;
import com.blankjava.api.model.LoginRequest;
import com.blankjava.api.model.RegisterRequest;
import com.blankjava.api.model.SessionResponse;
import com.blankjava.api.model.User;
import com.blankjava.auth.SessionService.Session;
import com.blankjava.logging.SecurityAuditLog;
import com.blankjava.logging.SecurityAuditLog.Outcome;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/auth/*} (tag {@code auth} of api/openapi.yaml): open, renew and end sessions. Token
 * responses carry {@code Cache-Control: no-store}; {@code refresh} and {@code logout} read the
 * refresh cookie and first check the {@code Origin} header ({@link OriginCheck}).
 */
@RestController
public class AuthController implements AuthApi {

    static final String REGISTERED = "user.registered";
    static final String LOGIN_SUCCEEDED = "auth.login.succeeded";
    static final String LOGIN_FAILED = "auth.login.failed";
    static final String LOGOUT = "auth.logout";

    private final SessionService sessions;
    private final OriginCheck originCheck;
    private final SecurityAuditLog audit;
    private final HttpServletRequest request;

    public AuthController(
            SessionService sessions, OriginCheck originCheck, SecurityAuditLog audit, HttpServletRequest request) {
        this.sessions = sessions;
        this.originCheck = originCheck;
        this.audit = audit;
        this.request = request;
    }

    @Override
    public ResponseEntity<SessionResponse> register(RegisterRequest registerRequest) {
        Session session = sessions.register(
                registerRequest.getEmail(), registerRequest.getDisplayName(), registerRequest.getPassword());
        audit.record(REGISTERED, Outcome.SUCCESS, session.user().getId().toString(), clientIp());
        return sessionResponse(HttpStatus.CREATED, session);
    }

    @Override
    public ResponseEntity<SessionResponse> login(LoginRequest loginRequest) {
        Session session;
        try {
            session = sessions.login(loginRequest.getEmail(), loginRequest.getPassword());
        } catch (InvalidCredentialsException e) {
            audit.recordForEmail(LOGIN_FAILED, Outcome.FAILURE, loginRequest.getEmail(), clientIp());
            throw e;
        }
        audit.record(LOGIN_SUCCEEDED, Outcome.SUCCESS, session.user().getId().toString(), clientIp());
        return sessionResponse(HttpStatus.OK, session);
    }

    @Override
    public ResponseEntity<SessionResponse> refresh() {
        originCheck.require(request.getHeader(HttpHeaders.ORIGIN));
        return sessionResponse(HttpStatus.OK, sessions.refresh(refreshCookie()));
    }

    @Override
    public ResponseEntity<Void> logout() {
        originCheck.require(request.getHeader(HttpHeaders.ORIGIN));
        Optional<UUID> user = sessions.logout(refreshCookie());
        // Only a session that was really open is audited: a repeated logout is not an event
        user.ifPresent(id -> audit.record(LOGOUT, Outcome.SUCCESS, id.toString(), clientIp()));
        return ResponseEntity.noContent()
                .header(
                        HttpHeaders.SET_COOKIE,
                        RefreshTokenService.clearCookie().toString())
                .build();
    }

    private @Nullable String refreshCookie() {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (RefreshTokenService.COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private String clientIp() {
        return request.getRemoteAddr();
    }

    private static ResponseEntity<SessionResponse> sessionResponse(HttpStatus status, Session session) {
        return ResponseEntity.status(status)
                .header(
                        HttpHeaders.SET_COOKIE,
                        RefreshTokenService.cookie(session.refreshToken()).toString())
                .cacheControl(CacheControl.noStore())
                .body(body(session));
    }

    private static SessionResponse body(Session session) {
        return new SessionResponse(
                session.accessToken().value(),
                session.accessToken().expiresInSeconds(),
                new User(
                        session.user().getId(),
                        session.user().getEmail(),
                        session.user().getDisplayName(),
                        session.user().getCreatedAt().atOffset(ZoneOffset.UTC)));
    }
}
