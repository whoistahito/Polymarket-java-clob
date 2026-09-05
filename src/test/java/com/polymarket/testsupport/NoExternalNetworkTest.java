package com.polymarket.testsupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.polymarket.ReadRetryPolicy;
import com.polymarket.internal.http.HttpOutcome;
import com.polymarket.internal.http.HttpRuntime;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

class NoExternalNetworkTest {

    private static HttpRuntime runtime() {
        return new HttpRuntime(Duration.ofSeconds(2), Duration.ofSeconds(2), ReadRetryPolicy.none());
    }

    @Test
    void shouldThrowUnknownHostExceptionWhenResolvingExternalHost() {
        assertThrows(UnknownHostException.class, () -> InetAddress.getByName("clob.polymarket.com"));
    }

    @Test
    void shouldThrowIOExceptionWhenSdkCallsExternalEndpoint() throws Exception {
        try (HttpRuntime runtime = runtime()) {
            assertThrows(IOException.class,
                    () -> runtime.get(URI.create("https://clob.polymarket.com"), "/time", Map.of()));
        }
    }

    @Test
    void shouldAllowLoopbackWhenUsingMockWebServer() throws Exception {
        assertTrue(InetAddress.getByName("localhost").isLoopbackAddress());
        try (MockWebServer server = new MockWebServer(); HttpRuntime runtime = runtime()) {
            server.start();
            server.enqueue(new MockResponse().setBody("{\"ok\":true}"));

            HttpOutcome outcome = runtime.get(server.url("/").uri(), "/ping", Map.of());

            assertEquals(200, outcome.status());
            assertEquals("{\"ok\":true}", outcome.body());
        }
    }
}
