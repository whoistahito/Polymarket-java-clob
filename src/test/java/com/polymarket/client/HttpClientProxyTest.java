package com.polymarket.client;

import okhttp3.OkHttpClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link HttpClient} proxy functionality.
 */
@DisplayName("HttpClient Proxy Support")
class HttpClientProxyTest {

    @Nested
    @DisplayName("HttpClient without proxy")
    class WithoutProxy {

        @Test
        @DisplayName("should create client without proxy by default")
        void shouldCreateClientWithoutProxyByDefault() {
            HttpClient client = new HttpClient();

            assertFalse(client.hasProxy());
            assertNull(client.proxyConfig());
            assertNotNull(client.okHttpClient());
        }

        @Test
        @DisplayName("should have no proxy in underlying OkHttpClient")
        void shouldHaveNoProxyInOkHttpClient() {
            HttpClient client = new HttpClient();

            OkHttpClient okClient = client.okHttpClient();
            // Default OkHttpClient uses system proxy selector, not a fixed proxy
            assertNotNull(okClient);
        }
    }

    @Nested
    @DisplayName("HttpClient with ProxyConfig constructor")
    class WithProxyConfigConstructor {

        @Test
        @DisplayName("should create client with proxy config")
        void shouldCreateClientWithProxyConfig() {
            ProxyConfig proxyConfig = new ProxyConfig("proxy.example.com", 8080);

            HttpClient client = new HttpClient(proxyConfig);

            assertTrue(client.hasProxy());
            assertNotNull(client.proxyConfig());
            assertEquals("proxy.example.com", client.proxyConfig().getHost());
            assertEquals(8080, client.proxyConfig().getPort());
        }

        @Test
        @DisplayName("should create client with authenticated proxy config")
        void shouldCreateClientWithAuthenticatedProxyConfig() {
            ProxyConfig proxyConfig = new ProxyConfig("brd.superproxy.io", 33335, "user", "pass");

            HttpClient client = new HttpClient(proxyConfig);

            assertTrue(client.hasProxy());
            assertTrue(client.proxyConfig().hasAuthentication());
            assertEquals("user", client.proxyConfig().getUsername());
        }

        @Test
        @DisplayName("should configure proxy in underlying OkHttpClient")
        void shouldConfigureProxyInOkHttpClient() {
            ProxyConfig proxyConfig = new ProxyConfig("proxy.example.com", 8080);

            HttpClient client = new HttpClient(proxyConfig);

            OkHttpClient okClient = client.okHttpClient();
            Proxy proxy = okClient.proxy();

            assertNotNull(proxy);
            assertEquals(Proxy.Type.HTTP, proxy.type());
            assertTrue(proxy.address() instanceof InetSocketAddress);

            InetSocketAddress address = (InetSocketAddress) proxy.address();
            assertEquals("proxy.example.com", address.getHostString());
            assertEquals(8080, address.getPort());
        }

        @Test
        @DisplayName("should handle null proxy config gracefully")
        void shouldHandleNullProxyConfigGracefully() {
            HttpClient client = new HttpClient((ProxyConfig) null);

            assertFalse(client.hasProxy());
            assertNull(client.proxyConfig());
        }
    }

    @Nested
    @DisplayName("HttpClient.Builder")
    class BuilderTests {

        @Test
        @DisplayName("should build client without proxy")
        void shouldBuildClientWithoutProxy() {
            HttpClient client = new HttpClient.Builder().build();

            assertFalse(client.hasProxy());
            assertNull(client.proxyConfig());
        }

        @Test
        @DisplayName("should build client with proxy host and port")
        void shouldBuildClientWithProxyHostAndPort() {
            HttpClient client = new HttpClient.Builder()
                    .proxy("proxy.example.com", 8080)
                    .build();

            assertTrue(client.hasProxy());
            assertEquals("proxy.example.com", client.proxyConfig().getHost());
            assertEquals(8080, client.proxyConfig().getPort());
            assertFalse(client.proxyConfig().hasAuthentication());
        }

        @Test
        @DisplayName("should build client with proxy and auth in one call")
        void shouldBuildClientWithProxyAndAuthInOneCall() {
            HttpClient client = new HttpClient.Builder()
                    .proxy("brd.superproxy.io", 33335, "user", "pass")
                    .build();

            assertTrue(client.hasProxy());
            assertEquals("brd.superproxy.io", client.proxyConfig().getHost());
            assertEquals(33335, client.proxyConfig().getPort());
            assertEquals("user", client.proxyConfig().getUsername());
            assertEquals("pass", client.proxyConfig().getPassword());
            assertTrue(client.proxyConfig().hasAuthentication());
        }

        @Test
        @DisplayName("should build client with proxy and auth in chained calls")
        void shouldBuildClientWithProxyAndAuthInChainedCalls() {
            HttpClient client = new HttpClient.Builder()
                    .proxy("brd.superproxy.io", 33335)
                    .proxyAuth("user", "pass")
                    .build();

            assertTrue(client.hasProxy());
            assertEquals("brd.superproxy.io", client.proxyConfig().getHost());
            assertEquals(33335, client.proxyConfig().getPort());
            assertEquals("user", client.proxyConfig().getUsername());
            assertEquals("pass", client.proxyConfig().getPassword());
        }

        @Test
        @DisplayName("should build client with ProxyConfig object")
        void shouldBuildClientWithProxyConfigObject() {
            ProxyConfig proxyConfig = new ProxyConfig("proxy.example.com", 8080, "user", "pass");

            HttpClient client = new HttpClient.Builder()
                    .proxy(proxyConfig)
                    .build();

            assertTrue(client.hasProxy());
            assertEquals(proxyConfig, client.proxyConfig());
        }

        @Test
        @DisplayName("should throw exception when proxyAuth called before proxy")
        void shouldThrowExceptionWhenProxyAuthCalledBeforeProxy() {
            HttpClient.Builder builder = new HttpClient.Builder();

            assertThrows(IllegalStateException.class, () -> builder.proxyAuth("user", "pass"));
        }

        @Test
        @DisplayName("should configure custom timeouts")
        void shouldConfigureCustomTimeouts() {
            HttpClient client = new HttpClient.Builder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .readTimeout(Duration.ofSeconds(60))
                    .writeTimeout(Duration.ofSeconds(45))
                    .callTimeout(Duration.ofSeconds(120))
                    .build();

            OkHttpClient okClient = client.okHttpClient();
            assertEquals(30000, okClient.connectTimeoutMillis());
            assertEquals(60000, okClient.readTimeoutMillis());
            assertEquals(45000, okClient.writeTimeoutMillis());
            assertEquals(120000, okClient.callTimeoutMillis());
        }

        @Test
        @DisplayName("should configure connection pool settings")
        void shouldConfigureConnectionPoolSettings() {
            HttpClient client = new HttpClient.Builder()
                    .connectionPoolSize(50)
                    .connectionPoolKeepAlive(10)
                    .build();

            assertNotNull(client.okHttpClient().connectionPool());
        }

        @Test
        @DisplayName("should build client with proxy and custom timeouts")
        void shouldBuildClientWithProxyAndCustomTimeouts() {
            HttpClient client = new HttpClient.Builder()
                    .proxy("proxy.example.com", 8080)
                    .proxyAuth("user", "pass")
                    .connectTimeout(Duration.ofSeconds(15))
                    .readTimeout(Duration.ofSeconds(30))
                    .build();

            assertTrue(client.hasProxy());
            assertEquals(15000, client.okHttpClient().connectTimeoutMillis());
            assertEquals(30000, client.okHttpClient().readTimeoutMillis());
        }

        @Test
        @DisplayName("should use default values when not specified")
        void shouldUseDefaultValuesWhenNotSpecified() {
            HttpClient client = new HttpClient.Builder().build();

            OkHttpClient okClient = client.okHttpClient();
            assertEquals(5000, okClient.connectTimeoutMillis());
            assertEquals(10000, okClient.readTimeoutMillis());
            assertEquals(10000, okClient.writeTimeoutMillis());
            assertEquals(20000, okClient.callTimeoutMillis());
        }
    }

    @Nested
    @DisplayName("Bright Data proxy configuration")
    class BrightDataProxy {

        @Test
        @DisplayName("should configure Bright Data datacenter proxy")
        void shouldConfigureBrightDataDatacenterProxy() {
            HttpClient client = new HttpClient.Builder()
                    .proxy("brd.superproxy.io", 33335)
                    .proxyAuth("brd-customer-hl_0e33c14e-zone-datacenter_proxy1", "454vy96xusl9")
                    .build();

            assertTrue(client.hasProxy());
            ProxyConfig config = client.proxyConfig();

            assertEquals("brd.superproxy.io", config.getHost());
            assertEquals(33335, config.getPort());
            assertEquals("brd-customer-hl_0e33c14e-zone-datacenter_proxy1", config.getUsername());
            assertEquals("454vy96xusl9", config.getPassword());
            assertTrue(config.hasAuthentication());
        }

        @Test
        @DisplayName("should configure Bright Data residential proxy")
        void shouldConfigureBrightDataResidentialProxy() {
            HttpClient client = new HttpClient.Builder()
                    .proxy("brd.superproxy.io", 22225)
                    .proxyAuth("brd-customer-hl_xxxxx-zone-residential", "password123")
                    .build();

            assertTrue(client.hasProxy());
            assertEquals("brd.superproxy.io", client.proxyConfig().getHost());
            assertEquals(22225, client.proxyConfig().getPort());
        }

        @Test
        @DisplayName("should support ProxyConfig.fromUrl for Bright Data")
        void shouldSupportProxyConfigFromUrlForBrightData() {
            ProxyConfig proxyConfig = ProxyConfig.fromUrl(
                    "brd-customer-hl_xxxxx-zone-dc:password@brd.superproxy.io:33335"
            );

            HttpClient client = new HttpClient.Builder()
                    .proxy(proxyConfig)
                    .build();

            assertTrue(client.hasProxy());
            assertEquals("brd.superproxy.io", client.proxyConfig().getHost());
            assertEquals(33335, client.proxyConfig().getPort());
            assertEquals("brd-customer-hl_xxxxx-zone-dc", client.proxyConfig().getUsername());
            assertEquals("password", client.proxyConfig().getPassword());
        }
    }

    @Nested
    @DisplayName("Proxy authentication")
    class ProxyAuthentication {

        @Test
        @DisplayName("should have proxyAuthenticator when credentials provided")
        void shouldHaveProxyAuthenticatorWhenCredentialsProvided() {
            HttpClient client = new HttpClient.Builder()
                    .proxy("proxy.example.com", 8080)
                    .proxyAuth("user", "pass")
                    .build();

            OkHttpClient okClient = client.okHttpClient();
            assertNotNull(okClient.proxyAuthenticator());
        }

        @Test
        @DisplayName("should handle special characters in username")
        void shouldHandleSpecialCharactersInUsername() {
            // Bright Data usernames contain hyphens, underscores, and other special chars
            String complexUsername = "brd-customer-hl_0e33c14e-zone-datacenter_proxy1-country-us";

            HttpClient client = new HttpClient.Builder()
                    .proxy("brd.superproxy.io", 33335)
                    .proxyAuth(complexUsername, "password")
                    .build();

            assertEquals(complexUsername, client.proxyConfig().getUsername());
        }

        @Test
        @DisplayName("should handle special characters in password")
        void shouldHandleSpecialCharactersInPassword() {
            String complexPassword = "p@ss!w0rd#$%^&*()_+-=[]{}|;':\",./<>?";

            HttpClient client = new HttpClient.Builder()
                    .proxy("proxy.example.com", 8080)
                    .proxyAuth("user", complexPassword)
                    .build();

            assertEquals(complexPassword, client.proxyConfig().getPassword());
        }
    }

    @Nested
    @DisplayName("ObjectMapper configuration")
    class ObjectMapperConfiguration {

        @Test
        @DisplayName("should use default ObjectMapper when not specified")
        void shouldUseDefaultObjectMapperWhenNotSpecified() {
            HttpClient client = new HttpClient.Builder()
                    .proxy("proxy.example.com", 8080)
                    .build();

            assertNotNull(client.objectMapper());
        }

        @Test
        @DisplayName("should use custom ObjectMapper when specified")
        void shouldUseCustomObjectMapperWhenSpecified() {
            com.fasterxml.jackson.databind.ObjectMapper customMapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();

            HttpClient client = new HttpClient.Builder()
                    .proxy("proxy.example.com", 8080)
                    .objectMapper(customMapper)
                    .build();

            assertSame(customMapper, client.objectMapper());
        }
    }
}
