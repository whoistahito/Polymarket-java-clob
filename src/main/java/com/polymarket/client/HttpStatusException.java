package com.polymarket.client;

import java.io.IOException;

/** Non-2xx HTTP response; distinct from a network/parse {@link IOException} (Rust {@code ErrorKind::Status}). */
public final class HttpStatusException extends IOException {
    public HttpStatusException(String message) {
        super(message);
    }
}
