package com.polymarket.authentication;

/** Thrown before any request when an operation's required authority is absent. */
public final class AuthenticationRequiredException extends RuntimeException {

    public AuthenticationRequiredException(String message) {
        super(message);
    }
}
