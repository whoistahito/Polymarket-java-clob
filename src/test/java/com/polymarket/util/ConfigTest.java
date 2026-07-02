package com.polymarket.util;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Test cases for Config.
 * Verifies configuration loading and parsing functionality.
 */
@DisplayName("Config Tests")
class ConfigTest {

    @Test
    @DisplayName("TC-CF-001: Load default config successfully")
    void testLoadDefaultConfig() {
        Config config = Config.load();
        assertNotNull(config, "Config should load successfully");
    }

    @Test
    @DisplayName("TC-CF-002: Default values are sensible")
    void testDefaultValues() {
        Config config = Config.load();

        // API defaults
        assertEquals(
            "https://clob.polymarket.com",
            config.getApiHost(),
            "API host should default to Polymarket CLOB URL"
        );
        assertEquals(
            137,
            config.getChainId(),
            "Chain ID should default to Polygon Mainnet (137)"
        );

        // Core strategy defaults
        assertTrue(
            config.getMaxBudget().compareTo(BigDecimal.ZERO) > 0,
            "Max budget should be positive"
        );
        assertTrue(
            config.getMinProfitThreshold().compareTo(BigDecimal.ZERO) >= 0,
            "Min profit threshold should be non-negative"
        );

        // Tick size default
        assertEquals(
            new BigDecimal("0.01"),
            config.getDefaultTickSize(),
            "Default tick size should be 0.01"
        );

        // Order type default
        assertEquals(
            "FAK",
            config.getDefaultOrderType(),
            "Default order type should be FAK"
        );
    }

    @Test
    @DisplayName("TC-CF-003: BigDecimal values are parsed correctly")
    void testBigDecimalParsing() {
        Config config = Config.load();

        // Test various BigDecimal getters
        BigDecimal maxBudget = config.getMaxBudget();
        assertNotNull(maxBudget, "Max budget should not be null");
        assertTrue(
            maxBudget.compareTo(BigDecimal.ZERO) > 0,
            "Max budget should be positive"
        );

        BigDecimal minProfitThreshold = config.getMinProfitThreshold();
        assertNotNull(minProfitThreshold, "Min profit threshold should not be null");

        BigDecimal maxTotalCost = config.getMaxTotalCost();
        assertNotNull(maxTotalCost, "Max total cost should not be null");
        assertTrue(
            maxTotalCost.compareTo(BigDecimal.ZERO) > 0 &&
            maxTotalCost.compareTo(BigDecimal.ONE) <= 0,
            "Max total cost should be between 0 and 1"
        );

        BigDecimal minTotalCost = config.getMinTotalCost();
        assertNotNull(minTotalCost, "Min total cost should not be null");
        assertTrue(
            minTotalCost.compareTo(BigDecimal.ZERO) > 0,
            "Min total cost should be positive"
        );

        BigDecimal slippageTolerance = config.getSlippageTolerance();
        assertNotNull(slippageTolerance, "Slippage tolerance should not be null");
        assertTrue(
            slippageTolerance.compareTo(BigDecimal.ZERO) >= 0,
            "Slippage tolerance should be non-negative"
        );
    }

    @Test
    @DisplayName("TC-CF-004: Integer values are parsed correctly")
    void testIntegerParsing() {
        Config config = Config.load();

        // Test integer getters
        int chainId = config.getChainId();
        assertTrue(
            chainId == 137 || chainId == 80002,
            "Chain ID should be a valid Polygon network ID"
        );

        int maxConcurrentPositions = config.getMaxConcurrentPositions();
        assertTrue(
            maxConcurrentPositions > 0,
            "Max concurrent positions should be positive"
        );

        int maxOrderRetries = config.getMaxOrderRetries();
        assertTrue(
            maxOrderRetries >= 0,
            "Max order retries should be non-negative"
        );

        int httpConnectionTimeout = config.getHttpConnectionTimeout();
        assertTrue(
            httpConnectionTimeout > 0,
            "HTTP connection timeout should be positive"
        );
    }

    @Test
    @DisplayName("TC-CF-005: Boolean values are parsed correctly")
    void testBooleanParsing() {
        Config config = Config.load();

        assertFalse(config.isDryRunMode());
        assertTrue(config.isVerboseLogging());
        assertTrue(config.retryFailedOrders());
        assertTrue(config.useDynamicMinSize());
        assertTrue(config.enableMomentumFirst());
    }

    @Test
    @DisplayName("TC-CF-006: String values are parsed correctly")
    void testStringParsing() {
        Config config = Config.load();

        // Test string getters
        String apiHost = config.getApiHost();
        assertNotNull(apiHost, "API host should not be null");
        assertFalse(apiHost.isEmpty(), "API host should not be empty");
        assertTrue(
            apiHost.startsWith("http"),
            "API host should be a valid URL"
        );

        String defaultOrderType = config.getDefaultOrderType();
        assertNotNull(defaultOrderType, "Default order type should not be null");
        assertTrue(
            defaultOrderType.equals("GTC") ||
            defaultOrderType.equals("GTD") ||
            defaultOrderType.equals("FOK") ||
            defaultOrderType.equals("FAK"),
            "Default order type should be a valid order type"
        );

        String secretKeyFile = config.getSecretKeyFile();
        assertNotNull(secretKeyFile, "Secret key file should not be null");
    }

    @Test
    @DisplayName("TC-CF-007: Double values are parsed correctly")
    void testDoubleParsing() {
        Config config = Config.load();

        double pollInterval = config.getPollInterval();
        assertTrue(pollInterval > 0, "Poll interval should be positive");

        double hedgeRepriceInterval = config.getHedgeRepriceInterval();
        assertTrue(
            hedgeRepriceInterval >= 0,
            "Hedge reprice interval should be non-negative"
        );
    }

    @Test
    @DisplayName("TC-CF-008: List values are parsed correctly")
    void testListParsing() {
        Config config = Config.load();

        var marketKeywords = config.getMarketKeywords();
        assertNotNull(marketKeywords, "Market keywords should not be null");
        // List may be empty or have values depending on config
    }

    @Test
    @DisplayName("TC-CF-009: Raw property access works")
    void testRawPropertyAccess() {
        Config config = Config.load();

        // Test raw property access with default
        String value = config.getProperty("non.existent.key", "default-value");
        assertEquals("default-value", value, "Should return default for missing key");

        // Test that existing properties can be accessed
        String apiHost = config.getProperty("api.host");
        assertNotNull(apiHost, "Should be able to access existing property");
    }

    @Test
    @DisplayName("TC-CF-010: Timing configuration values")
    void testTimingConfiguration() {
        Config config = Config.load();

        int maxLeg2WaitTime = config.getMaxLeg2WaitTime();
        assertTrue(maxLeg2WaitTime > 0, "Max leg2 wait time should be positive");

        int rescanInterval = config.getRescanInterval();
        assertTrue(rescanInterval > 0, "Rescan interval should be positive");
    }

    @Test
    @DisplayName("TC-CF-011: Risk management configuration values")
    void testRiskManagementConfiguration() {
        Config config = Config.load();

        BigDecimal minAccountBalance = config.getMinAccountBalance();
        assertNotNull(minAccountBalance, "Min account balance should not be null");
        assertTrue(
            minAccountBalance.compareTo(BigDecimal.ZERO) >= 0,
            "Min account balance should be non-negative"
        );

        int maxConsecutiveFailures = config.getMaxConsecutiveFailures();
        assertTrue(
            maxConsecutiveFailures > 0,
            "Max consecutive failures should be positive"
        );
    }

    @Test
    @DisplayName("TC-CF-012: Performance tuning configuration values")
    void testPerformanceTuningConfiguration() {
        Config config = Config.load();

        int poolSize = config.getHttpConnectionPoolSize();
        assertTrue(poolSize > 0, "HTTP connection pool size should be positive");

        int readTimeout = config.getHttpReadTimeout();
        assertTrue(readTimeout > 0, "HTTP read timeout should be positive");

        int writeTimeout = config.getHttpWriteTimeout();
        assertTrue(writeTimeout > 0, "HTTP write timeout should be positive");

        int threadPoolSize = config.getThreadPoolSize();
        assertTrue(threadPoolSize > 0, "Thread pool size should be positive");
    }

    @Test
    @DisplayName("TC-CF-013: Fee rate BPS configuration")
    void testFeeRateBpsConfiguration() {
        Config config = Config.load();

        int feeRateBps = config.getDefaultFeeRateBps();
        assertTrue(
            feeRateBps >= 0 && feeRateBps <= 10000,
            "Fee rate BPS should be between 0 and 10000"
        );
    }

    @Test
    @DisplayName("TC-CF-014: Momentum strategy configuration")
    void testMomentumStrategyConfiguration() {
        Config config = Config.load();

        BigDecimal momentumThreshold = config.getMomentumThreshold();
        assertNotNull(momentumThreshold, "Momentum threshold should not be null");

        BigDecimal momentumMinProfitThreshold = config.getMomentumMinProfitThreshold();
        assertNotNull(
            momentumMinProfitThreshold,
            "Momentum min profit threshold should not be null"
        );

        assertDoesNotThrow(() -> config.placePassiveHedgeOrder());
        assertDoesNotThrow(() -> config.hedgePostOnly());
    }

    @Test
    @DisplayName("TC-CF-015: Market selection configuration")
    void testMarketSelectionConfiguration() {
        Config config = Config.load();

        int maxHoursUntilClose = config.getMaxHoursUntilClose();
        assertTrue(
            maxHoursUntilClose >= 0,
            "Max hours until close should be non-negative"
        );

        int maxMarketsToScan = config.getMaxMarketsToScan();
        assertTrue(maxMarketsToScan > 0, "Max markets to scan should be positive");
    }

    // =========================================================================
    // Credential resolution tests (TC-CF-016 … TC-CF-023)
    //
    // These tests are fully isolated from the real secret-key.txt /
    // funder-wallet.txt files (which are .gitignore'd and may not exist).
    // Every test explicitly controls the file paths via @TempDir so that
    // the real project-root files are never consulted.
    // =========================================================================

    /**
     * Build a Config from an explicit Properties map without touching the classpath.
     * The caller is responsible for setting secret.key.file / funder.wallet.file
     * to paths inside @TempDir so the project-root credential files are never read.
     */
    private static Config configFromProps(Properties props) throws Exception {
        Path tmp = Files.createTempFile("test-config-", ".properties");
        try (var out = Files.newBufferedWriter(tmp)) {
            props.store(out, null);
        }
        try {
            return Config.loadFromFile(tmp.toString());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    @DisplayName("TC-CF-016: getPrivateKey returns inline value when credentials.private-key is set")
    void testGetPrivateKey_inlineProperty(@TempDir Path dir) throws Exception {
        Properties props = new Properties();
        props.setProperty("credentials.private-key", "0xINLINEKEY");
        // Redirect the file path into the empty temp dir so the project-root
        // secret-key.txt (if present) is never consulted.
        props.setProperty("secret.key.file", dir.resolve("absent.txt").toString());

        Config config = configFromProps(props);

        assertEquals("0xINLINEKEY", config.getPrivateKey(),
                "Inline credentials.private-key should be returned directly");
    }

    @Test
    @DisplayName("TC-CF-017: getPrivateKey reads from file when credentials.private-key is absent")
    void testGetPrivateKey_readsFromFile(@TempDir Path dir) throws Exception {
        Path keyFile = dir.resolve("secret-key.txt");
        Files.writeString(keyFile, "0xFILEKEY\n");

        Properties props = new Properties();
        props.setProperty("secret.key.file", keyFile.toString());
        props.setProperty("funder.wallet.file", dir.resolve("absent.txt").toString());

        Config config = configFromProps(props);

        assertEquals("0xFILEKEY", config.getPrivateKey(),
                "Private key should be read from the file when no inline property is set");
    }

    @Test
    @DisplayName("TC-CF-018: getPrivateKey — inline property takes precedence over file")
    void testGetPrivateKey_inlinePrecedenceOverFile(@TempDir Path dir) throws Exception {
        Path keyFile = dir.resolve("secret-key.txt");
        Files.writeString(keyFile, "0xFILEKEY\n");

        Properties props = new Properties();
        props.setProperty("credentials.private-key", "0xINLINEKEY");
        props.setProperty("secret.key.file", keyFile.toString());

        Config config = configFromProps(props);

        assertEquals("0xINLINEKEY", config.getPrivateKey(),
                "Inline property must take precedence over the file");
    }

    @Test
    @DisplayName("TC-CF-019: getPrivateKey returns null when no inline value and no file exists")
    void testGetPrivateKey_returnsNullWhenNeitherSet(@TempDir Path dir) throws Exception {
        Properties props = new Properties();
        props.setProperty("secret.key.file", dir.resolve("no-such-key.txt").toString());
        props.setProperty("funder.wallet.file", dir.resolve("no-such-wallet.txt").toString());

        Config config = configFromProps(props);

        assertNull(config.getPrivateKey(),
                "Should return null when file does not exist and no inline property is set");
    }

    @Test
    @DisplayName("TC-CF-020: getPrivateKey returns null when referenced file does not exist")
    void testGetPrivateKey_missingFileReturnsNull(@TempDir Path dir) throws Exception {
        Properties props = new Properties();
        // File path is set but the file was never created
        props.setProperty("secret.key.file", dir.resolve("nonexistent.txt").toString());

        Config config = configFromProps(props);

        assertNull(config.getPrivateKey(),
                "Should return null when the credential file does not exist");
    }

    @Test
    @DisplayName("TC-CF-021: getPrivateKey skips blank lines and comment lines in file")
    void testGetPrivateKey_skipsBlankAndCommentLines(@TempDir Path dir) throws Exception {
        Path keyFile = dir.resolve("secret-key.txt");
        Files.writeString(keyFile, "\n# this is a comment\n\n0xACTUALKEY\n0xSECONDLINE\n");

        Properties props = new Properties();
        props.setProperty("secret.key.file", keyFile.toString());

        Config config = configFromProps(props);

        assertEquals("0xACTUALKEY", config.getPrivateKey(),
                "Should return first non-blank, non-comment line from the file");
    }

    @Test
    @DisplayName("TC-CF-022: getFunderWallet reads from file when credentials.funder-wallet is absent")
    void testGetFunderWallet_readsFromFile(@TempDir Path dir) throws Exception {
        Path walletFile = dir.resolve("funder-wallet.txt");
        Files.writeString(walletFile, "0xFUNDERWALLET\n");

        Properties props = new Properties();
        props.setProperty("secret.key.file", dir.resolve("absent.txt").toString());
        props.setProperty("funder.wallet.file", walletFile.toString());

        Config config = configFromProps(props);

        assertEquals("0xFUNDERWALLET", config.getFunderWallet(),
                "Funder wallet should be read from the file when no inline property is set");
    }

    @Test
    @DisplayName("TC-CF-023: getFunderWallet — inline property takes precedence over file")
    void testGetFunderWallet_inlinePrecedenceOverFile(@TempDir Path dir) throws Exception {
        Path walletFile = dir.resolve("funder-wallet.txt");
        Files.writeString(walletFile, "0xFILEWALLET\n");

        Properties props = new Properties();
        props.setProperty("credentials.funder-wallet", "0xINLINEWALLET");
        props.setProperty("funder.wallet.file", walletFile.toString());

        Config config = configFromProps(props);

        assertEquals("0xINLINEWALLET", config.getFunderWallet(),
                "Inline property must take precedence over the file");
    }
}
