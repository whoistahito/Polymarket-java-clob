package com.polymarket.testsupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.polymarket.client.HttpClient;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Deterministic suite makes no external network calls")
class NoExternalNetworkTest {

    @Test
    @DisplayName("TC-NN-001: resolving a real Polymarket host fails in the offline suite")
    void blocksExternalHostResolution() {
        assertThrows(UnknownHostException.class, () -> InetAddress.getByName("clob.polymarket.com"));
    }

    @Test
    @DisplayName("TC-NN-002: the SDK HTTP client cannot reach a real endpoint")
    void blocksSdkCallsToRealEndpoints() {
        assertThrows(IOException.class,
                () -> new HttpClient().get("https://clob.polymarket.com/time", null));
    }

    @Test
    @DisplayName("TC-NN-003: loopback still resolves so MockWebServer keeps working")
    void allowsLoopbackForMockWebServer() throws Exception {
        assertTrue(InetAddress.getByName("localhost").isLoopbackAddress());
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().setBody("{\"ok\":true}"));
            String body = new HttpClient().get(server.url("/ping").toString(), null);
            assertEquals("{\"ok\":true}", body);
        }
    }
}
