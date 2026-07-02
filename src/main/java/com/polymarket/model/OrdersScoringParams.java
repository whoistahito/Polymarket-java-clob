package com.polymarket.model;

import java.util.List;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;

/**
 * Parameters for the batch order-scoring check ({@code POST /orders-scoring}).
 *
 * <p>The request body is the JSON array of order IDs, matching the Rust SDK's
 * {@code are_orders_scoring(&[&str])} signature.
 *
 * <pre>{@code
 * OrdersScoringParams params = OrdersScoringParams.builder()
 *     .orderId("abc")
 *     .orderId("def")
 *     .build();
 * Map<String, Boolean> result = client.areOrdersScoring(params);
 * }</pre>
 */
@Value
@Builder
public class OrdersScoringParams {

    /**
     * Order IDs to check for scoring eligibility.
     * Use {@code @Singular} so callers can add IDs one at a time via the builder.
     */
    @NonNull
    @Singular("orderId")
    List<String> orderIds;
}
