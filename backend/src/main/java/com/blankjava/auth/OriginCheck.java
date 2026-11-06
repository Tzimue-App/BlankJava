package com.blankjava.auth;

import com.blankjava.security.SecurityProperties;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * The {@code Origin} rule of the cookie endpoints ({@code refresh}, {@code logout}): the header
 * must be one of {@code blankjava.security.allowed-origins}. Together with {@code SameSite=Strict}
 * this replaces CSRF tokens (backend/README.md, <i>Why CSRF protection is disabled</i>). A missing
 * {@code Origin} is refused like a foreign one: browsers send it on every cross-origin and every
 * same-origin {@code POST}, so only a non-browser client or a stripped request lacks it.
 */
@Component
public class OriginCheck {

    private final Set<String> allowed;

    public OriginCheck(SecurityProperties properties) {
        this.allowed = properties.allowedOrigins().stream()
                .map(OriginCheck::normalize)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Passes when {@code origin} is allowed.
     *
     * @throws OriginNotAllowedException when it is missing, {@code null} (opaque origin) or not listed
     */
    public void require(@Nullable String origin) {
        if (origin == null || !allowed.contains(normalize(origin))) {
            throw new OriginNotAllowedException();
        }
    }

    /** {@code scheme://host[:port]} compared without case and without a trailing slash. */
    private static String normalize(String origin) {
        String trimmed = origin.strip().toLowerCase(Locale.ROOT);
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
