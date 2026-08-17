package com.polymarket.trading;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Which requested order IDs were actually canceled; every other requested ID was not. */
public record CancellationOutcome(List<String> canceled, Map<String, String> notCanceled) {

    public CancellationOutcome {
        Objects.requireNonNull(canceled, "canceled");
        Objects.requireNonNull(notCanceled, "notCanceled");
        canceled = List.copyOf(canceled);
        notCanceled = Map.copyOf(notCanceled);
    }

    public boolean isCanceled(String orderId) {
        return canceled.contains(orderId);
    }
}
