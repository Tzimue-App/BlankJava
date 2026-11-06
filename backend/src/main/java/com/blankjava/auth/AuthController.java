package com.blankjava.auth;

import com.blankjava.api.AuthApi;
import com.blankjava.api.model.LoginRequest;
import com.blankjava.api.model.RegisterRequest;
import com.blankjava.api.model.SessionResponse;
import com.blankjava.api.model.User;
import com.blankjava.auth.SessionService.Session;
import com.blankjava.logging.SecurityAuditLog;
import jakarta.servlet.http.HttpServletRequest;
import java.time.ZoneOffset;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@code /api/auth/*} (tag {@code auth} of api/openapi.yaml). Only {@code register} is implemented
 * so far; {@code login}, {@code refresh} and {@code logout} answer {@code 501} until #30.
 */
@RestController
public class AuthController implements AuthApi {

    static final String REGISTERED = "user.registered";

    private final SessionService sessions;
    private final SecurityAuditLog audit;
    private final HttpServletRequest request;

    public AuthController(SessionService sessions, SecurityAuditLog audit, HttpServletRequest request) {
        this.sessions = sessions;
        this.audit = audit;
        this.request = request;
    }

    @Override
    public ResponseEntity<SessionResponse> register(RegisterRequest registerRequest) {
        Session session = sessions.register(
                registerRequest.getEmail(), registerRequest.getDisplayName(), registerRequest.getPassword());
        audit.record(
                REGISTERED,
                SecurityAuditLog.Outcome.SUCCESS,
                session.user().getId().toString(),
                request.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(
                        HttpHeaders.SET_COOKIE,
                        RefreshTokenService.cookie(session.refreshToken()).toString())
                .cacheControl(CacheControl.noStore())
                .body(body(session));
    }

    @Override
    public ResponseEntity<SessionResponse> login(LoginRequest loginRequest) {
        throw notYet();
    }

    @Override
    public ResponseEntity<SessionResponse> refresh() {
        throw notYet();
    }

    @Override
    public ResponseEntity<Void> logout() {
        throw notYet();
    }

    private static ResponseStatusException notYet() {
        return new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Not implemented yet (#30).");
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
