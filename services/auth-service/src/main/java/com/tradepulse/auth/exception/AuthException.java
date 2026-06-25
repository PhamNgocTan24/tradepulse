package com.tradepulse.auth.exception;

import org.springframework.http.HttpStatus;

/**
 * Base domain exception for auth failures.
 * Always carry an HTTP status to simplify the @RestControllerAdvice handler.
 */
public class AuthException extends RuntimeException {

    private final HttpStatus status;

    public AuthException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    // -- Convenience factory methods --

    public static AuthException invalidCredentials() {
        return new AuthException("Invalid email or password", HttpStatus.UNAUTHORIZED);
    }

    public static AuthException emailAlreadyExists(String email) {
        return new AuthException("Email already registered: " + email, HttpStatus.CONFLICT);
    }

    public static AuthException tokenExpiredOrInvalid() {
        return new AuthException("Token is expired or invalid", HttpStatus.UNAUTHORIZED);
    }

    public static AuthException totpRequired() {
        return new AuthException("TOTP code required for this account", HttpStatus.UNAUTHORIZED);
    }

    public static AuthException totpInvalid() {
        return new AuthException("Invalid TOTP code", HttpStatus.UNAUTHORIZED);
    }

    public static AuthException userDisabled() {
        return new AuthException("Account is disabled", HttpStatus.FORBIDDEN);
    }
}
