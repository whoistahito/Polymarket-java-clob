package com.polymarket.strategy.scalp;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ScalpConfigTest {

  @Test
  void rejectsNonPositiveTimeStop() {
    assertThrows(
        IllegalArgumentException.class, () -> new ScalpConfig(0.45, 0.05, 0, 50, 180_000));
    assertThrows(
        IllegalArgumentException.class, () -> new ScalpConfig(0.45, 0.05, -1, 50, 180_000));
  }

  @Test
  void rejectsNegativeEntryLead() {
    assertThrows(
        IllegalArgumentException.class, () -> new ScalpConfig(0.45, 0.05, 60_000, 50, -1));
  }

  @Test
  void acceptsZeroEntryLead() {
    // entryLeadMs=0 is a valid choice: enter as soon as a cheap side appears, no lead window.
    new ScalpConfig(0.45, 0.05, 60_000, 50, 0);
  }
}
