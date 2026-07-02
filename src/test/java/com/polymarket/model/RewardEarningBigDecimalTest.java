package com.polymarket.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** PMK-015 — reward-earning monetary fields must be {@link BigDecimal}, not double/float. */
class RewardEarningBigDecimalTest {

    private static final Set<String> MONETARY_FIELDS = Set.of("earnings", "assetRate");

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("TC-RE-001 UserEarning deserializes amounts into BigDecimal with no precision loss")
    void userEarningDeserializesWithoutPrecisionLoss() throws Exception {
        String json =
                "{\"date\":\"2026-06-18\",\"condition_id\":\"0xabc\","
                        + "\"asset_address\":\"0xasset\",\"maker_address\":\"0xmaker\","
                        + "\"earnings\":\"12.345678\",\"asset_rate\":1.000001}";

        UserEarning earning = mapper.readValue(json, UserEarning.class);

        assertEquals(0, earning.getEarnings().compareTo(new BigDecimal("12.345678")));
        assertEquals(0, earning.getAssetRate().compareTo(new BigDecimal("1.000001")));
    }

    @Test
    @DisplayName("TC-RE-002 No monetary field on earning models is double or float")
    void noMonetaryFieldIsFloatingPoint() throws Exception {
        assertMonetaryFieldsAreBigDecimal(UserEarning.class);
        assertMonetaryFieldsAreBigDecimal(TotalUserEarning.class);
    }

    private static void assertMonetaryFieldsAreBigDecimal(Class<?> type) throws Exception {
        for (String name : MONETARY_FIELDS) {
            // BigDecimal proves it's not double/float/Double/Float — one assert covers both.
            assertEquals(
                    BigDecimal.class,
                    type.getDeclaredField(name).getType(),
                    type.getSimpleName() + "." + name + " must be BigDecimal, not a floating-point type");
        }
    }
}
