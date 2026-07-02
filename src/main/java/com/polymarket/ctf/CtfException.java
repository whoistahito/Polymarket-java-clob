package com.polymarket.ctf;

/** Exception thrown for CTF (Conditional Token Framework) contract errors. */
public class CtfException extends RuntimeException {

    public CtfException(String message) {
        super(message);
    }

    public CtfException(String message, Throwable cause) {
        super(message, cause);
    }
}
