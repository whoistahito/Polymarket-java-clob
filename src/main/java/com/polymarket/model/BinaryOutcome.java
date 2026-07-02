package com.polymarket.model;

import java.util.Locale;

/** Canonical binary outcome labels used by Polymarket markets. */
public enum BinaryOutcome {
  YES("yes"),
  NO("no");

  private final String canonicalLabel;

  BinaryOutcome(String canonicalLabel) {
    this.canonicalLabel = canonicalLabel;
  }

  public boolean matches(String outcomeLabel) {
    if (outcomeLabel == null) {
      return false;
    }
    return canonicalLabel.equals(normalize(outcomeLabel));
  }

  private static String normalize(String label) {
    return label.trim().toLowerCase(Locale.ROOT);
  }
}
