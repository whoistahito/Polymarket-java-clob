package com.polymarket.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.Proxy;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ProxyConfig}.
 */
@DisplayName("ProxyConfig")
class ProxyConfigTest {

    @Nested
    @DisplayName("Constructor without authentication")
    class ConstructorWithoutAuth {

        @Test
        @DisplayName("should create proxy config with host and port")
        void shouldCreateProxyConfigWithHostAndPort() {
            ProxyConfig config = new ProxyConfig("proxy.example.com", 8080);

            assertEquals("proxy.example.com", config.getHost());
            assertEquals(8080, config.getPort());
            assertNull(config.getUsername());
            assertNull(config.getPassword());
            assertFalse(config.hasAuthentication());
        }

        @Test
        @DisplayName("should trim whitespace from host")
        void shouldTrimWhitespaceFromHost() {
            ProxyConfig config = new ProxyConfig("  proxy.example.com  ", 8080);

            assertEquals("proxy.example.com", config.getHost());
        }

        @Test
        @DisplayName("should throw exception for null host")
        void shouldThrowExceptionForNullHost() {
            assertThrows(IllegalArgumentException.class, () -> new ProxyConfig(null, 8080));
        }

        @Test
        @DisplayName("should throw exception for blank host")
        void shouldThrowExceptionForBlankHost() {
            assertThrows(IllegalArgumentException.class, () -> new ProxyConfig("   ", 8080));
        }

        @Test
        @DisplayName("should throw exception for port below 1")
        void shouldThrowExceptionForPortBelowOne() {
            assertThrows(IllegalArgumentException.class, () -> new ProxyConfig("proxy.example.com", 0));
            assertThrows(IllegalArgumentException.class, () -> new ProxyConfig("proxy.example.com", -1));
        }

        @Test
        @DisplayName("should throw exception for port above 65535")
        void shouldThrowExceptionForPortAbove65535() {
            assertThrows(IllegalArgumentException.class, () -> new ProxyConfig("proxy.example.com", 65536));
        }

        @Test
        @DisplayName("should accept valid port boundaries")
        void shouldAcceptValidPortBoundaries() {
            ProxyConfig configMin = new ProxyConfig("proxy.example.com", 1);
            ProxyConfig configMax = new ProxyConfig("proxy.example.com", 65535);

            assertEquals(1, configMin.getPort());
            assertEquals(65535, configMax.getPort());
        }
    }

    @Nested
    @DisplayName("Constructor with authentication")
    class ConstructorWithAuth {

        @Test
        @DisplayName("should create proxy config with authentication")
        void shouldCreateProxyConfigWithAuth() {
            ProxyConfig config = new ProxyConfig("brd.superproxy.io", 33335, "user", "pass");

            assertEquals("brd.superproxy.io", config.getHost());
            assertEquals(33335, config.getPort());
            assertEquals("user", config.getUsername());
            assertEquals("pass", config.getPassword());
            assertTrue(config.hasAuthentication());
        }

        @Test
        @DisplayName("should handle null username")
        void shouldHandleNullUsername() {
            ProxyConfig config = new ProxyConfig("proxy.example.com", 8080, null, "pass");

            assertNull(config.getUsername());
            assertFalse(config.hasAuthentication());
        }

        @Test
        @DisplayName("should handle null password")
        void shouldHandleNullPassword() {
            ProxyConfig config = new ProxyConfig("proxy.example.com", 8080, "user", null);

            assertEquals("user", config.getUsername());
            assertNull(config.getPassword());
            assertFalse(config.hasAuthentication());
        }

        @Test
        @DisplayName("should handle blank username as no auth")
        void shouldHandleBlankUsernameAsNoAuth() {
            ProxyConfig config = new ProxyConfig("proxy.example.com", 8080, "   ", "pass");

            assertFalse(config.hasAuthentication());
        }

        @Test
        @DisplayName("should create Bright Data style proxy config")
        void shouldCreateBrightDataStyleProxyConfig() {
            ProxyConfig config = new ProxyConfig(
                    "brd.superproxy.io",
                    33335,
                    "brd-customer-hl_0e33c14e-zone-datacenter_proxy1",
                    "454vy96xusl9"
            );

            assertEquals("brd.superproxy.io", config.getHost());
            assertEquals(33335, config.getPort());
            assertEquals("brd-customer-hl_0e33c14e-zone-datacenter_proxy1", config.getUsername());
            assertEquals("454vy96xusl9", config.getPassword());
            assertTrue(config.hasAuthentication());
        }
    }

    @Nested
    @DisplayName("fromUrl")
    class FromUrl {

        @Test
        @DisplayName("should parse host:port format")
        void shouldParseHostPortFormat() {
            ProxyConfig config = ProxyConfig.fromUrl("proxy.example.com:8080");

            assertEquals("proxy.example.com", config.getHost());
            assertEquals(8080, config.getPort());
            assertFalse(config.hasAuthentication());
        }

        @Test
        @DisplayName("should parse user:pass@host:port format")
        void shouldParseUserPassHostPortFormat() {
            ProxyConfig config = ProxyConfig.fromUrl("user:pass@proxy.example.com:8080");

            assertEquals("proxy.example.com", config.getHost());
            assertEquals(8080, config.getPort());
            assertEquals("user", config.getUsername());
            assertEquals("pass", config.getPassword());
            assertTrue(config.hasAuthentication());
        }

        @Test
        @DisplayName("should strip http:// prefix")
        void shouldStripHttpPrefix() {
            ProxyConfig config = ProxyConfig.fromUrl("http://proxy.example.com:8080");

            assertEquals("proxy.example.com", config.getHost());
            assertEquals(8080, config.getPort());
        }

        @Test
        @DisplayName("should strip https:// prefix")
        void shouldStripHttpsPrefix() {
            ProxyConfig config = ProxyConfig.fromUrl("https://proxy.example.com:8080");

            assertEquals("proxy.example.com", config.getHost());
            assertEquals(8080, config.getPort());
        }

        @Test
        @DisplayName("should parse URL with authentication and protocol")
        void shouldParseUrlWithAuthAndProtocol() {
            ProxyConfig config = ProxyConfig.fromUrl("http://user:pass@proxy.example.com:8080");

            assertEquals("proxy.example.com", config.getHost());
            assertEquals(8080, config.getPort());
            assertEquals("user", config.getUsername());
            assertEquals("pass", config.getPassword());
        }

        @Test
        @DisplayName("should throw exception for null URL")
        void shouldThrowExceptionForNullUrl() {
            assertThrows(IllegalArgumentException.class, () -> ProxyConfig.fromUrl(null));
        }

        @Test
        @DisplayName("should throw exception for blank URL")
        void shouldThrowExceptionForBlankUrl() {
            assertThrows(IllegalArgumentException.class, () -> ProxyConfig.fromUrl("   "));
        }

        @Test
        @DisplayName("should throw exception for URL without port")
        void shouldThrowExceptionForUrlWithoutPort() {
            assertThrows(IllegalArgumentException.class, () -> ProxyConfig.fromUrl("proxy.example.com"));
        }

        @Test
        @DisplayName("should throw exception for invalid port")
        void shouldThrowExceptionForInvalidPort() {
            assertThrows(IllegalArgumentException.class, () -> ProxyConfig.fromUrl("proxy.example.com:abc"));
        }

        @Test
        @DisplayName("should handle complex username with special characters")
        void shouldHandleComplexUsername() {
            ProxyConfig config = ProxyConfig.fromUrl("brd-customer-hl_xxxxx-zone-dc@proxy.example.com:8080");

            assertEquals("proxy.example.com", config.getHost());
            assertEquals(8080, config.getPort());
            assertEquals("brd-customer-hl_xxxxx-zone-dc", config.getUsername());
            assertNull(config.getPassword());
        }
    }

    @Nested
    @DisplayName("toProxy")
    class ToProxy {

        @Test
        @DisplayName("should create HTTP proxy")
        void shouldCreateHttpProxy() {
            ProxyConfig config = new ProxyConfig("proxy.example.com", 8080);

            Proxy proxy = config.toProxy();

            assertEquals(Proxy.Type.HTTP, proxy.type());
            assertNotNull(proxy.address());
        }

        @Test
        @DisplayName("should create proxy with correct address")
        void shouldCreateProxyWithCorrectAddress() {
            ProxyConfig config = new ProxyConfig("brd.superproxy.io", 33335);

            Proxy proxy = config.toProxy();

            assertTrue(proxy.address().toString().contains("brd.superproxy.io"));
            assertTrue(proxy.address().toString().contains("33335"));
        }
    }

    @Nested
    @DisplayName("equals and hashCode")
    class EqualsAndHashCode {

        @Test
        @DisplayName("should be equal for same values")
        void shouldBeEqualForSameValues() {
            ProxyConfig config1 = new ProxyConfig("proxy.example.com", 8080, "user", "pass");
            ProxyConfig config2 = new ProxyConfig("proxy.example.com", 8080, "user", "pass");

            assertEquals(config1, config2);
            assertEquals(config1.hashCode(), config2.hashCode());
        }

        @Test
        @DisplayName("should not be equal for different host")
        void shouldNotBeEqualForDifferentHost() {
            ProxyConfig config1 = new ProxyConfig("proxy1.example.com", 8080);
            ProxyConfig config2 = new ProxyConfig("proxy2.example.com", 8080);

            assertNotEquals(config1, config2);
        }

        @Test
        @DisplayName("should not be equal for different port")
        void shouldNotBeEqualForDifferentPort() {
            ProxyConfig config1 = new ProxyConfig("proxy.example.com", 8080);
            ProxyConfig config2 = new ProxyConfig("proxy.example.com", 8081);

            assertNotEquals(config1, config2);
        }

        @Test
        @DisplayName("should not be equal for different credentials")
        void shouldNotBeEqualForDifferentCredentials() {
            ProxyConfig config1 = new ProxyConfig("proxy.example.com", 8080, "user1", "pass");
            ProxyConfig config2 = new ProxyConfig("proxy.example.com", 8080, "user2", "pass");

            assertNotEquals(config1, config2);
        }

        @Test
        @DisplayName("should not be equal to null")
        void shouldNotBeEqualToNull() {
            ProxyConfig config = new ProxyConfig("proxy.example.com", 8080);

            assertNotEquals(null, config);
        }

        @Test
        @DisplayName("should be equal to itself")
        void shouldBeEqualToItself() {
            ProxyConfig config = new ProxyConfig("proxy.example.com", 8080);

            assertEquals(config, config);
        }
    }

    @Nested
    @DisplayName("toString")
    class ToString {

        @Test
        @DisplayName("should not expose password in toString")
        void shouldNotExposePasswordInToString() {
            ProxyConfig config = new ProxyConfig("proxy.example.com", 8080, "user", "secretPassword123");

            String str = config.toString();

            assertTrue(str.contains("proxy.example.com"));
            assertTrue(str.contains("8080"));
            assertTrue(str.contains("user"));
            assertFalse(str.contains("secretPassword123"));
            assertTrue(str.contains("***"));
        }

        @Test
        @DisplayName("should show host and port without auth")
        void shouldShowHostAndPortWithoutAuth() {
            ProxyConfig config = new ProxyConfig("proxy.example.com", 8080);

            String str = config.toString();

            assertTrue(str.contains("proxy.example.com"));
            assertTrue(str.contains("8080"));
            assertFalse(str.contains("***"));
        }
    }
}
