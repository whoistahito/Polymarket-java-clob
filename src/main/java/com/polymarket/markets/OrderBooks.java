package com.polymarket.markets;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;

/** Live CLOB order books. Every call is a credential-free read of the exchange itself. */
public final class OrderBooks {

    private final OrderBookSource source;

    public OrderBooks(@NonNull OrderBookSource source) {
        this.source = source;
    }

    /** Empty when the exchange keeps no book for that token. */
    public Optional<OrderBookSnapshot> book(@NonNull TokenId token) throws IOException {
        return source.book(token);
    }

    /** One read for many tokens. A token with no book is simply missing from the result. */
    public List<OrderBookSnapshot> books(@NonNull List<TokenId> tokens) throws IOException {
        List<TokenId> requested = List.copyOf(tokens);
        return requested.isEmpty() ? List.of() : source.books(requested);
    }
}
