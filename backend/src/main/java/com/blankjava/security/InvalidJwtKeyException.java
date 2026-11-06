package com.blankjava.security;

/**
 * A JWT key file is missing, unreadable or not acceptable. The message names the file and the
 * reason, never the key material; {@link InvalidJwtKeyFailureAnalyzer} turns it into a short
 * startup error.
 */
public class InvalidJwtKeyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidJwtKeyException(String message) {
        super(message);
    }

    public InvalidJwtKeyException(String message, Throwable cause) {
        super(message, cause);
    }
}
