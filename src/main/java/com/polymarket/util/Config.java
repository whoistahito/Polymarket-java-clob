package com.polymarket.util;

import com.polymarket.client.ProxyConfig;
import com.polymarket.model.SignatureType;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Configuration loader for the arbitrage bot.
 * Loads settings from config.properties and provides type-safe access.
 */
public class Config {

    private static final String DEFAULT_CONFIG_FILE = "config.properties";
    private final Properties properties;

    private Config(Properties properties) {
        this.properties = properties;
    }

    /**
     * Load configuration from default classpath location
     */
    public static Config load() {
        return load(DEFAULT_CONFIG_FILE);
    }

    /**
     * Load configuration from specified classpath resource
     */
    public static Config load(String resourcePath) {
        Properties props = new Properties();
        try (
            InputStream input =
                Config.class.getClassLoader().getResourceAsStream(resourcePath)
        ) {
            if (input == null) {
                throw new RuntimeException("Unable to find " + resourcePath);
            }
            props.load(input);
        } catch (IOException e) {
            throw new RuntimeException(
                "Failed to load configuration from " + resourcePath,
                e
            );
        }
        return new Config(props);
    }

    /**
     * Load configuration from external file path
     */
    public static Config loadFromFile(String filePath) {
        Properties props = new Properties();
        try (InputStream input = Files.newInputStream(Paths.get(filePath))) {
            props.load(input);
        } catch (IOException e) {
            throw new RuntimeException(
                "Failed to load configuration from " + filePath,
                e
            );
        }
        return new Config(props);
    }

    // ========================================================================
    // CORE STRATEGY PARAMETERS
    // ========================================================================

    public BigDecimal getMaxTotalCost() {
        return getBigDecimal("max.total.cost", "0.95");
    }

    public BigDecimal getMinTotalCost() {
        return getBigDecimal("min.total.cost", "0.90");
    }

    public BigDecimal getMinProfitThreshold() {
        return getBigDecimal("min.profit.threshold", "0.01");
    }

    public BigDecimal getMaxBudget() {
        return getBigDecimal("max.budget", "10.0");
    }

    public boolean useDynamicMinSize() {
        return getBoolean("use.dynamic.min.size", true);
    }

    public BigDecimal getFixedTradeSize() {
        return getBigDecimal("fixed.trade.size", "1.0");
    }

    // ========================================================================
    // MOMENTUM-FIRST STRATEGY
    // ========================================================================

    public boolean enableMomentumFirst() {
        return getBoolean("enable.momentum.first", true);
    }

    public BigDecimal getMomentumThreshold() {
        return getBigDecimal("momentum.threshold", "0.50");
    }

    public BigDecimal getMomentumMinProfitThreshold() {
        return getBigDecimal("momentum.min.profit.threshold", "0.005");
    }

    public boolean placePassiveHedgeOrder() {
        return getBoolean("place.passive.hedge.order", true);
    }

    public boolean hedgePostOnly() {
        return getBoolean("hedge.post.only", true);
    }

    public double getHedgeRepriceInterval() {
        return getDouble("hedge.reprice.interval", 2.0);
    }

    // ========================================================================
    // TIMING
    // ========================================================================

    public double getPollInterval() {
        return getDouble("poll.interval", 0.01);
    }

    public int getMaxLeg2WaitTime() {
        return getInt("max.leg2.wait.time", 30);
    }

    public int getRescanInterval() {
        return getInt("rescan.interval", 5);
    }

    // ========================================================================
    // MARKET SELECTION
    // ========================================================================

    public List<String> getMarketKeywords() {
        String keywords = getString("market.keywords", "XRP");
        return Arrays.stream(keywords.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
    }

    public int getMaxHoursUntilClose() {
        return getInt("max.hours.until.close", 1);
    }

    public int getMaxMarketsToScan() {
        return getInt("max.markets.to.scan", 100);
    }

    // ========================================================================
    // RISK MANAGEMENT
    // ========================================================================

    public boolean isDryRunMode() {
        return getBoolean("dry.run.mode", false);
    }

    public int getMaxConcurrentPositions() {
        return getInt("max.concurrent.positions", 5);
    }

    public int getMaxConsecutiveFailures() {
        return getInt("max.consecutive.failures", 10);
    }

    public BigDecimal getMinAccountBalance() {
        return getBigDecimal("min.account.balance", "10.0");
    }

    // ========================================================================
    // LOGGING
    // ========================================================================

    public boolean isVerboseLogging() {
        return getBoolean("verbose.logging", true);
    }

    public boolean logAllPriceUpdates() {
        return getBoolean("log.all.price.updates", false);
    }

    public boolean saveTradeHistory() {
        return getBoolean("save.trade.history", true);
    }

    public String getTradeHistoryFile() {
        return getString("trade.history.file", "trade_history.json");
    }

    // ========================================================================
    // API CONFIGURATION
    // ========================================================================

    public String getApiHost() {
        return getString("api.host", "https://clob.polymarket.com");
    }

    public int getChainId() {
        return getInt("chain.id", 137);
    }

    public String getSecretKeyFile() {
        return getString("secret.key.file", "secret-key.txt");
    }

    public String getFunderWalletFile() {
        return getString("funder.wallet.file", "funder-wallet.txt");
    }

    // ========================================================================
    // PROXY CONFIGURATION
    // ========================================================================

    /**
     * Whether proxy is enabled.
     * @return true if proxy.enabled is set to true
     */
    public boolean isProxyEnabled() {
        return getBoolean("proxy.enabled", false);
    }

    /**
     * Get the proxy host (e.g., "brd.superproxy.io").
     * @return the proxy host, or null if not configured
     */
    public String getProxyHost() {
        String host = getString("proxy.host", null);
        return (host == null || host.isBlank()) ? null : host;
    }

    /**
     * Get the proxy url.
     * @return the proxy url, or null if not configured
     */
    public String getProxyUrl() {
        String host = getString("proxy.url", null);
        return (host == null || host.isBlank()) ? null : host;
    }

    /**
     * Get the proxy port (e.g., 33335).
     * @return the proxy port, or 0 if not configured
     */
    public int getProxyPort() {
        return getInt("proxy.port", 0);
    }

    /**
     * Get the proxy username for authentication.
     * For Bright Data, this is typically like "brd-customer-hl_xxxxx-zone-datacenter_proxy1".
     * @return the proxy username, or null if not configured
     */
    public String getProxyUsername() {
        String username = getString("proxy.username", null);
        return (username == null || username.isBlank()) ? null : username;
    }

    /**
     * Get the proxy password for authentication.
     * @return the proxy password, or null if not configured
     */
    public String getProxyPassword() {
        String password = getString("proxy.password", null);
        return (password == null || password.isBlank()) ? null : password;
    }

    /**
     * Check if proxy authentication credentials are configured.
     * @return true if both username and password are set
     */
    public boolean hasProxyAuthentication() {
        return getProxyUsername() != null && getProxyPassword() != null;
    }

    /**
     * Creates a ProxyConfig from the configuration properties.
     *
     * <p>This is a convenience method that reads proxy settings from the config
     * and creates a ProxyConfig object ready to use with HttpClient.
     *
     * <p>Example usage:
     * <pre>{@code
     * Config config = Config.load();
     * ProxyConfig proxyConfig = config.getProxyConfig();
     * if (proxyConfig != null) {
     *     HttpClient client = new HttpClient.Builder()
     *         .proxy(proxyConfig)
     *         .build();
     * }
     * }</pre>
     *
     * @return a ProxyConfig if proxy is enabled and configured, null otherwise
     */
    public ProxyConfig getProxyConfig() {
        if (!isProxyEnabled()) {
            return null;
        }

        String host = getProxyHost();
        int port = getProxyPort();

        if (host == null || port <= 0) {
            return null;
        }

        String username = getProxyUsername();
        String password = getProxyPassword();

        if (username != null && password != null) {
            return new ProxyConfig(host, port, username, password);
        } else {
            return new ProxyConfig(host, port);
        }
    }

    // ========================================================================
    // PERFORMANCE TUNING
    // ========================================================================

    public int getHttpConnectionPoolSize() {
        return getInt("http.connection.pool.size", 50);
    }

    public int getHttpConnectionTimeout() {
        return getInt("http.connection.timeout", 5000);
    }

    public int getHttpReadTimeout() {
        return getInt("http.read.timeout", 10000);
    }

    public int getHttpWriteTimeout() {
        return getInt("http.write.timeout", 10000);
    }

    public int getHttpKeepAliveDuration() {
        return getInt("http.keep.alive.duration", 300);
    }

    public int getThreadPoolSize() {
        return getInt("thread.pool.size", 10);
    }

    public int getScheduledPoolSize() {
        return getInt("scheduled.pool.size", 2);
    }

    // ========================================================================
    // ADVANCED SETTINGS
    // ========================================================================

    public BigDecimal getSlippageTolerance() {
        return getBigDecimal("slippage.tolerance", "0.01");
    }

    public boolean retryFailedOrders() {
        return getBoolean("retry.failed.orders", true);
    }

    public int getMaxOrderRetries() {
        return getInt("max.order.retries", 2);
    }

    public boolean useMarketOrders() {
        return getBoolean("use.market.orders", false);
    }

    public BigDecimal getMinLiquidity() {
        return getBigDecimal("min.liquidity", "10.0");
    }

    public BigDecimal getDefaultTickSize() {
        return getBigDecimal("default.tick.size", "0.01");
    }

    public String getDefaultOrderType() {
        return getString("default.order.type", "FAK");
    }

    public String getHedgeOrderType() {
        return getString("hedge.order.type", "GTC");
    }

    public int getDefaultFeeRateBps() {
        return getInt("default.fee.rate.bps", 100);
    }

    public String getPrivateKey() {
        String direct = getString("credentials.private-key", null);
        if (direct != null && !direct.isBlank()) {
            return direct.trim();
        }
        return readFirstLineFromFile(getSecretKeyFile());
    }

    public String getFunderWallet() {
        String direct = getString("credentials.funder-wallet", null);
        if (direct != null && !direct.isBlank()) {
            return direct.trim();
        }
        return readFirstLineFromFile(getFunderWalletFile());
    }

  public SignatureType getSignatureType() {
    String raw = getString("credentials.signature-type", "0");
    if (raw == null || raw.isBlank()) {
      return SignatureType.EOA;
    }

    String normalized = raw.trim();
    if (normalized.equalsIgnoreCase("EOA")) {
      return SignatureType.EOA;
    }
    if (normalized.equalsIgnoreCase("POLY_PROXY")) {
      return SignatureType.POLY_PROXY;
    }
    if (normalized.equalsIgnoreCase("POLY_GNOSIS_SAFE")
        || normalized.equalsIgnoreCase("GNOSIS_SAFE")) {
      return SignatureType.POLY_GNOSIS_SAFE;
    }

    try {
      int value = Integer.parseInt(normalized);
      if (value == 1) {
        return SignatureType.POLY_PROXY;
      }
      if (value == 2) {
        return SignatureType.POLY_GNOSIS_SAFE;
      }
      return SignatureType.EOA;
    } catch (NumberFormatException e) {
      System.err.println(
          "Invalid credentials.signature-type: " + normalized + ", defaulting to EOA (0)");
      return SignatureType.EOA;
    }
  }

    /**
     * Reads the first non-blank line from the given file path.
     * The path is resolved relative to the working directory.
     * Returns null if the file does not exist or is empty.
     */
    private static String readFirstLineFromFile(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return null;
        }
        java.nio.file.Path path = java.nio.file.Paths.get(filePath);
        if (!java.nio.file.Files.exists(path)) {
            return null;
        }
        try {
            return java.nio.file.Files.lines(path)
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .findFirst()
                .orElse(null);
        } catch (IOException e) {
            System.err.println("Could not read credential file '" + filePath + "': " + e.getMessage());
            return null;
        }
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    private String getString(String key, String defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        return value;
    }

    private int getInt(String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            System.err.println(
                "Invalid integer value for " +
                    key +
                    ": " +
                    value +
                    ", using default: " +
                    defaultValue
            );
            return defaultValue;
        }
    }

    private double getDouble(String key, double defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            System.err.println(
                "Invalid double value for " +
                    key +
                    ": " +
                    value +
                    ", using default: " +
                    defaultValue
            );
            return defaultValue;
        }
    }

    private boolean getBoolean(String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.trim());
    }

    private BigDecimal getBigDecimal(String key, String defaultValue) {
        String value = properties.getProperty(key, defaultValue);
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            System.err.println(
                "Invalid decimal value for " +
                    key +
                    ": " +
                    value +
                    ", using default: " +
                    defaultValue
            );
            return new BigDecimal(defaultValue);
        }
    }

    /**
     * Get raw property value
     */
    public String getProperty(String key) {
        return properties.getProperty(key);
    }

    /**
     * Get raw property value with default
     */
    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    /**
     * Print all configuration values (useful for debugging)
     */
    public void printConfig() {
        System.out.println("=".repeat(70));
        System.out.println("CONFIGURATION");
        System.out.println("=".repeat(70));
        properties
            .stringPropertyNames()
            .stream()
            .sorted()
            .forEach(key ->
                System.out.println(key + " = " + properties.getProperty(key))
            );
        System.out.println("=".repeat(70));
    }
}
