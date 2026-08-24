package com.polymarket.rfq;

import java.io.IOException;
import lombok.NonNull;

/**
 * The Builder Gateway refused a write before any RFQ existed, so there is no durable RFQ ID to
 * recover with. Distinct from {@link RfqOutcome.Failed}, which is a business result at HTTP 200.
 */
public final class RfqGatewayException extends IOException {

    private static final long serialVersionUID = 1L;

    private final int httpStatus;

    public RfqGatewayException(int httpStatus, @NonNull String message) {
        super("Builder Gateway refused the RFQ: HTTP " + httpStatus + " " + message);
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
