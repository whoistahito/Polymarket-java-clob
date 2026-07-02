package com.polymarket.client;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for HttpClient retry-on-error behaviour.
 */
@DisplayName("HttpClient Retry Tests")
class HttpClientRetryTest {

    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    @DisplayName("TC-HCR-001: Single retry succeeds after one 500 error")
    void testRetrySucceedsAfterOneFail() throws IOException {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("error"));
        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));

        HttpClient client = new HttpClient.Builder()
                .maxRetries(1)
                .build();

        String url = server.url("/test").toString();
        String result = client.get(url, Collections.emptyMap());

        assertEquals("ok", result);
        assertEquals(2, server.getRequestCount());
    }

    @Test
    @DisplayName("TC-HCR-002: Fails if all attempts exhausted")
    void testRetryExhausted() {
        server.enqueue(new MockResponse().setResponseCode(503).setBody("service unavailable"));
        server.enqueue(new MockResponse().setResponseCode(503).setBody("service unavailable"));

        HttpClient client = new HttpClient.Builder()
                .maxRetries(1)
                .build();

        String url = server.url("/test").toString();
        assertThrows(IOException.class, () -> client.get(url, Collections.emptyMap()));
        assertEquals(2, server.getRequestCount());
    }

    @Test
    @DisplayName("TC-HCR-003: Zero retries does not retry on failure")
    void testZeroRetriesNoRetry() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("error"));

        HttpClient client = new HttpClient.Builder()
                .maxRetries(0)
                .build();

        String url = server.url("/test").toString();
        assertThrows(IOException.class, () -> client.get(url, Collections.emptyMap()));
        assertEquals(1, server.getRequestCount());
    }
}
