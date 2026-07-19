package com.polymarket.client;

import java.io.IOException;

/** Non-2xx HTTP response; distinct from a network/parse {@link IOException} (Rust {@code ErrorKind::Status}). */
public final class HttpStatusException extends IOException {
    private final int statusCode;
    private final String responseBody;

    public HttpStatusException(String message) {
        this(-1, null, message);
    }

    public HttpStatusException(int statusCode, String responseBody, String message) {
        super(message);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public int statusCode() {
        return statusCode;
    }

    public String responseBody() {
        return responseBody;
    }
}
