package com.polymarket.client;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Objects;

/**
 * Configuration for HTTP proxy support.
 *
 * <p>This class holds proxy settings including host, port, and optional
 * authentication credentials (username/password).
 *
 * <p>Example usage:
 * <pre>{@code
 * // Without authentication
 * ProxyConfig proxy = new ProxyConfig("proxy.example.com", 8080);
 *
 * // With authentication
 * ProxyConfig proxy = new ProxyConfig("brd.superproxy.io", 33335, "username", "password");
 *
 * // Use with HttpClient
 * HttpClient client = new HttpClient(proxy);
 * }</pre>
 */
public final class ProxyConfig {

    private final String host;
    private final int port;
    private final String username;
    private final String password;

    /**
     * Creates a proxy configuration without authentication.
     *
     * @param host the proxy host (e.g., "proxy.example.com")
     * @param port the proxy port (e.g., 8080)
     */
    public ProxyConfig(String host, int port) {
        this(host, port, null, null);
    }

    /**
     * Creates a proxy configuration with authentication.
     *
     * @param host     the proxy host (e.g., "brd.superproxy.io")
     * @param port     the proxy port (e.g., 33335)
     * @param username the proxy username for authentication
     * @param password the proxy password for authentication
     */
    public ProxyConfig(String host, int port, String username, String password) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Proxy host cannot be null or blank");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Proxy port must be between 1 and 65535, got: " + port);
        }
        this.host = host.trim();
        this.port = port;
        this.username = username;
        this.password = password;
    }

    /**
     * Creates a ProxyConfig from environment variables.
     *
     * <p>Reads from:
     * <ul>
     *   <li>PROXY_HOST - the proxy host</li>
     *   <li>PROXY_PORT - the proxy port</li>
     *   <li>PROXY_USERNAME - (optional) username for authentication</li>
     *   <li>PROXY_PASSWORD - (optional) password for authentication</li>
     * </ul>
     *
     * @return the ProxyConfig, or null if PROXY_HOST is not set
     */
    public static ProxyConfig fromEnvironment() {
        String host = System.getenv("PROXY_HOST");
        if (host == null || host.isBlank()) {
            return null;
        }

        String portStr = System.getenv("PROXY_PORT");
        if (portStr == null || portStr.isBlank()) {
            throw new IllegalArgumentException("PROXY_PORT must be set when PROXY_HOST is provided");
        }

        int port;
        try {
            port = Integer.parseInt(portStr.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid PROXY_PORT value: " + portStr);
        }

        String username = System.getenv("PROXY_USERNAME");
        String password = System.getenv("PROXY_PASSWORD");

        return new ProxyConfig(host, port, username, password);
    }

    /**
     * Creates a ProxyConfig from a URL string.
     *
     * <p>Supported formats:
     * <ul>
     *   <li>host:port</li>
     *   <li>username:password@host:port</li>
     * </ul>
     *
     * @param proxyUrl the proxy URL string
     * @return the ProxyConfig
     * @throws IllegalArgumentException if the URL format is invalid
     */
    public static ProxyConfig fromUrl(String proxyUrl) {
        if (proxyUrl == null || proxyUrl.isBlank()) {
            throw new IllegalArgumentException("Proxy URL cannot be null or blank");
        }

        String url = proxyUrl.trim();
        // Remove protocol prefix if present
        if (url.startsWith("http://")) {
            url = url.substring(7);
        } else if (url.startsWith("https://")) {
            url = url.substring(8);
        }

        String username = null;
        String password = null;
        String hostPort;

        // Check for authentication credentials
        int atIndex = url.lastIndexOf('@');
        if (atIndex > 0) {
            String auth = url.substring(0, atIndex);
            hostPort = url.substring(atIndex + 1);

            int colonIndex = auth.indexOf(':');
            if (colonIndex > 0) {
                username = auth.substring(0, colonIndex);
                password = auth.substring(colonIndex + 1);
            } else {
                username = auth;
            }
        } else {
            hostPort = url;
        }

        // Parse host:port
        int lastColonIndex = hostPort.lastIndexOf(':');
        if (lastColonIndex <= 0) {
            throw new IllegalArgumentException("Invalid proxy URL format, expected host:port, got: " + proxyUrl);
        }

        String host = hostPort.substring(0, lastColonIndex);
        String portStr = hostPort.substring(lastColonIndex + 1);

        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid port in proxy URL: " + portStr);
        }

        return new ProxyConfig(host, port, username, password);
    }

    /**
     * @return the proxy host
     */
    public String getHost() {
        return host;
    }

    /**
     * @return the proxy port
     */
    public int getPort() {
        return port;
    }

    /**
     * @return the proxy username, or null if not set
     */
    public String getUsername() {
        return username;
    }

    /**
     * @return the proxy password, or null if not set
     */
    public String getPassword() {
        return password;
    }

    /**
     * @return true if authentication credentials are configured
     */
    public boolean hasAuthentication() {
        return username != null && !username.isBlank() && password != null;
    }

    /**
     * Creates a Java {@link Proxy} object for use with HTTP clients.
     *
     * @return the Proxy object
     */
    public Proxy toProxy() {
        return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProxyConfig that = (ProxyConfig) o;
        return port == that.port &&
               Objects.equals(host, that.host) &&
               Objects.equals(username, that.username) &&
               Objects.equals(password, that.password);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port, username, password);
    }

    @Override
    public String toString() {
        if (hasAuthentication()) {
            // Mask password for security
            return "ProxyConfig{host='" + host + "', port=" + port + ", username='" + username + "', password='***'}";
        }
        return "ProxyConfig{host='" + host + "', port=" + port + "}";
    }
}
