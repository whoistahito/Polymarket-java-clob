package com.polymarket.integration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.polymarket.client.ApiKeyCreds;
import com.polymarket.client.PolymarketClient;
import com.polymarket.model.AssetType;
import com.polymarket.model.BalanceAllowanceParams;
import com.polymarket.model.BalanceAllowanceResponse;
import com.polymarket.model.SignatureType;
import com.polymarket.util.Config;
import com.polymarket.util.WalletUtils;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Live troubleshooting test for wallet collateral balance. Disabled by default.
 *
 * <p>Enable with: -Dlive.balance.test=true
 */
@DisplayName("Live wallet balance troubleshooting test")
class LiveWalletBalanceDebugTest {

  private record ProbeCase(String label, SignatureType signatureType, String funder) {}

  @Test
  @EnabledIfSystemProperty(named = "live.balance.test", matches = "true")
  @DisplayName("TC-IT-LIVE-001: compare collateral balance by wallet mode")
  void compareCollateralBalanceByWalletMode() throws Exception {
    Config config = Config.load();
    String privateKey = config.getPrivateKey();
    String funder = config.getFunderWallet();
    SignatureType signatureType = config.getSignatureType();

    assertNotNull(privateKey, "credentials.private-key must be set in config.properties");
    assertTrue(!privateKey.isBlank(), "credentials.private-key must not be blank");

    PolymarketClient initClient =
        new PolymarketClient.Builder()
            .privateKey(privateKey)
            .chainId(config.getChainId())
            .useServerTime(true)
            .build();

    ApiKeyCreds apiCreds = initClient.createOrDeriveApiKey();

    PolymarketClient configuredClient =
        new PolymarketClient.Builder()
            .privateKey(privateKey)
            .chainId(config.getChainId())
            .useServerTime(true)
            .apiCreds(apiCreds)
            .signatureType(signatureType)
            .funderAddress(funder)
            .build();

    String signer = initClient.getAddress();
    String derivedProxy = WalletUtils.deriveProxyWallet(signer, config.getChainId()).orElse(null);
    String derivedSafe = WalletUtils.deriveSafeWallet(signer, config.getChainId()).orElse(null);

    List<ProbeCase> probes = new ArrayList<>();
    probes.add(new ProbeCase("configured-default", signatureType, funder));
    probes.add(new ProbeCase("eoa-signer", SignatureType.EOA, null));
    probes.add(new ProbeCase("eoa-funder", SignatureType.EOA, funder));
    probes.add(new ProbeCase("proxy-funder", SignatureType.POLY_PROXY, funder));
    probes.add(new ProbeCase("safe-funder", SignatureType.POLY_GNOSIS_SAFE, funder));
    if (derivedProxy != null) {
      probes.add(new ProbeCase("proxy-derived", SignatureType.POLY_PROXY, derivedProxy));
    }
    if (derivedSafe != null) {
      probes.add(new ProbeCase("safe-derived", SignatureType.POLY_GNOSIS_SAFE, derivedSafe));
    }

    System.out.println("========== LIVE BALANCE DEBUG ==========");
    System.out.println("Signer: " + signer);
    System.out.println("Funder: " + (funder == null || funder.isBlank() ? "(not set)" : funder));
    System.out.println("SignatureType: " + signatureType + " (" + signatureType.getValue() + ")");
    System.out.println("Derived proxy: " + (derivedProxy == null ? "(n/a)" : derivedProxy));
    System.out.println("Derived safe:  " + (derivedSafe == null ? "(n/a)" : derivedSafe));
    System.out.println();

    BigDecimal best = BigDecimal.ZERO;
    String bestCase = "none";
    for (ProbeCase probe : probes) {
      BalanceAllowanceResponse response =
          configuredClient.getBalanceAllowance(
              BalanceAllowanceParams.builder()
                  .assetType(AssetType.COLLATERAL)
                  .signatureType(probe.signatureType())
                  .funderAddress(probe.funder())
                  .build());
      BigDecimal usdc = toUsdc(response.getBalance());
      if (usdc.compareTo(best) > 0) {
        best = usdc;
        bestCase = probe.label();
      }

      System.out.printf(
          "%-16s sig=%d funder=%s raw=%s usdc=%s%n",
          probe.label(),
          probe.signatureType().getValue(),
          probe.funder() == null || probe.funder().isBlank() ? "(none)" : probe.funder(),
          response.getBalance(),
          usdc.toPlainString());
    }

    System.out.println();
    System.out.println("Best case: " + bestCase + " => " + best.toPlainString() + " USDC");
    System.out.println("========================================");

    assertTrue(best.compareTo(BigDecimal.ZERO) >= 0, "Balance must be non-negative");

    if (Boolean.getBoolean("live.expectPositiveBalance")) {
      assertTrue(
          best.compareTo(BigDecimal.ZERO) > 0,
          "Expected positive balance in at least one wallet mode; set -Dlive.expectPositiveBalance=false to inspect only");
    }
  }

  private static BigDecimal toUsdc(String rawAmount) {
    if (rawAmount == null || rawAmount.isBlank()) {
      return BigDecimal.ZERO;
    }
    try {
      return new BigDecimal(rawAmount).scaleByPowerOfTen(-6);
    } catch (NumberFormatException e) {
      return BigDecimal.ZERO;
    }
  }
}
