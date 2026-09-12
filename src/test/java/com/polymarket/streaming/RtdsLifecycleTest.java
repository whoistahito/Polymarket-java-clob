package com.polymarket.streaming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.polymarket.internal.streaming.RtdsGateway;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** Covers RTDS lifecycle behavior and its documented five-second heartbeat contract. */
class RtdsLifecycleTest {

    private MockWebServer server;
    private RtdsGateway gateway;
    private Rtds rtds;
    private BareWebSocketServer bareServer;

    @AfterEach
    void tearDown() throws Exception {
        if (rtds != null) rtds.close();
        if (gateway != null) gateway.close();
        if (server != null) {
            Thread.sleep(100);
            try {
                server.shutdown();
            } catch (Exception ignored) {
            }
        }
        if (bareServer != null) bareServer.close();
    }

    private String wsUrl() {
        return "ws://" + server.getHostName() + ":" + server.getPort();
    }

    @Test
    void shouldStartAtGenerationOneWhenRtdsConnects() throws Exception {
        CountDownLatch opened = new CountDownLatch(1);
        server = new MockWebServer();
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {}));
        server.start();

        gateway = RtdsGateway.builder().url(wsUrl()).build();
        rtds = new Rtds(gateway);
        rtds.addLifecycleListener(new RtdsLifecycleListener() {
            @Override public void onOpen(long generation) { opened.countDown(); }
        });
        rtds.subscribeBinancePrices(List.of("btcusdt"));

        assertTrue(opened.await(15, TimeUnit.SECONDS));
        assertEquals(1L, rtds.generation());
    }

    @Test
    void shouldSignalResubscribeBeforeFreshDataWhenRtdsConnects() throws Exception {
        List<String> order = new CopyOnWriteArrayList<>();
        CountDownLatch gotPrice = new CountDownLatch(1);

        server = new MockWebServer();
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onMessage(WebSocket ws, String text) {
                ws.send("""
                    {"topic":"crypto_prices","type":"update","timestamp":1,
                     "payload":{"symbol":"btcusdt","timestamp":1,"value":1}}
                    """);
            }
        }));
        server.start();

        gateway = RtdsGateway.builder().url(wsUrl()).build();
        rtds = new Rtds(gateway);
        rtds.onBinancePrice(List.of(), e -> { order.add("price"); gotPrice.countDown(); });
        rtds.addLifecycleListener(new RtdsLifecycleListener() {
            @Override public void onResubscribe(long generation) { order.add("resubscribe:" + generation); }
        });
        rtds.subscribeBinancePrices(List.of("btcusdt"));

        assertTrue(gotPrice.await(15, TimeUnit.SECONDS));
        assertEquals("resubscribe:1", order.get(0),
                "resubscribe must precede the first event so cached state can be invalidated first");
    }

    @Test
    void shouldSendTextPingsWhenRtdsChannelIsOpen() throws Exception {
        List<String> pings = new CopyOnWriteArrayList<>();
        CountDownLatch twoPings = new CountDownLatch(2);

        server = new MockWebServer();
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onMessage(WebSocket ws, String text) {
                if ("PING".equals(text)) {
                    pings.add(text);
                    twoPings.countDown();
                }
            }
        }));
        server.start();

        gateway = RtdsGateway.builder().url(wsUrl()).pingIntervalMs(100).build();
        rtds = new Rtds(gateway);
        rtds.subscribeBinancePrices(List.of("btcusdt"));

        assertTrue(twoPings.await(15, TimeUnit.SECONDS), "the heartbeat must repeat; saw " + pings);
    }

    @Test
    void shouldReplaceOpenSocketExactlyOnceWhenPeerNeverAnswersProtocolPing() throws Exception {
        bareServer = new BareWebSocketServer();
        List<Exception> errors = new CopyOnWriteArrayList<>();
        List<Long> openedGenerations = new CopyOnWriteArrayList<>();
        CountDownLatch failed = new CountDownLatch(1);
        CountDownLatch reconnected = new CountDownLatch(1);

        gateway = RtdsGateway.builder()
                .url(bareServer.url())
                .controlPingIntervalMs(1_000)
                .pingIntervalMs(0)
                .reconnectDelayMs(50)
                .maxReconnectAttempts(0)
                .build();
        rtds = new Rtds(gateway);
        rtds.addLifecycleListener(new RtdsLifecycleListener() {
            @Override public void onOpen(long generation) {
                openedGenerations.add(generation);
                if (generation == 2) reconnected.countDown();
            }

            @Override public void onError(long generation, Exception error) {
                errors.add(error);
                failed.countDown();
            }
        });
        rtds.subscribeBinancePrices(List.of("btcusdt"));

        assertTrue(bareServer.awaitControlPings(1, 1, 5, TimeUnit.SECONDS),
                "OkHttp must send a protocol control PING, not only RTDS text PING");
        assertTrue(failed.await(10, TimeUnit.SECONDS),
                "a missing protocol PONG must fail the open RTDS socket");
        assertTrue(reconnected.await(10, TimeUnit.SECONDS),
                "the failed RTDS socket must reconnect");
        assertTrue(bareServer.awaitControlPings(2, 2, 10, TimeUnit.SECONDS),
                "the replacement must receive two control PING/PONG cycles");
        assertTrue(bareServer.awaitControlPings(2, 3, 5, TimeUnit.SECONDS),
                "the replacement must remain open through another ping deadline");
        assertTrue(bareServer.connectionOpen(2), "the replacement socket closed unexpectedly");

        assertTrue(bareServer.pingsForConnection(2) >= 2,
                "the replacement did not receive two protocol PINGs");
        assertTrue(bareServer.pongsForConnection(2) >= 2,
                "the replacement did not complete two PING/PONG cycles");
        assertEquals(0, bareServer.pongsForConnection(1),
                "the first peer must not answer its control PING");
        assertEquals(2, bareServer.handshakeCount(), "the open socket was replaced more than once");
        assertEquals(List.of(1L, 2L), openedGenerations);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0) instanceof SocketTimeoutException,
                "OkHttp ping liveness failure was " + errors.get(0));
        assertTrue(errors.get(0).getMessage().contains("didn't receive pong"),
                "unexpected ping liveness failure: " + errors.get(0));
    }

    @Test
    void shouldUseFiveSecondPingIntervalWhenNoOverrideIsConfigured() throws Exception {
        CountDownLatch onePing = new CountDownLatch(1);
        long[] arrivedAtMs = new long[1];
        long startedAtMs = System.currentTimeMillis();

        server = new MockWebServer();
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onMessage(WebSocket ws, String text) {
                if ("PING".equals(text)) {
                    arrivedAtMs[0] = System.currentTimeMillis();
                    onePing.countDown();
                }
            }
        }));
        server.start();

        gateway = RtdsGateway.builder().url(wsUrl()).build();
        rtds = new Rtds(gateway);
        rtds.subscribeBinancePrices(List.of("btcusdt"));

        assertTrue(onePing.await(10, TimeUnit.SECONDS), "the default heartbeat must fire");
        long elapsed = arrivedAtMs[0] - startedAtMs;
        assertTrue(elapsed >= 4_000, "expected roughly a 5s interval, saw " + elapsed + "ms");
    }

    @Test
    void shouldRestartHeartbeatWhenRtdsReconnects() throws Exception {
        CountDownLatch pingOnSecondConnection = new CountDownLatch(1);

        server = new MockWebServer();
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onMessage(WebSocket ws, String text) { ws.close(1000, "bye"); }
        }));
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onMessage(WebSocket ws, String text) {
                if ("PING".equals(text)) pingOnSecondConnection.countDown();
            }
        }));
        server.start();

        gateway = RtdsGateway.builder().url(wsUrl()).pingIntervalMs(100).reconnectDelayMs(50).build();
        rtds = new Rtds(gateway);
        rtds.subscribeBinancePrices(List.of("btcusdt"));

        assertTrue(pingOnSecondConnection.await(20, TimeUnit.SECONDS),
                "the reconnected channel must start its own heartbeat");
    }

    @Test
    void shouldRestoreStateAndBumpGenerationWhenRtdsReconnects() throws Exception {
        CountDownLatch reconnected = new CountDownLatch(1);
        server = new MockWebServer();
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onMessage(WebSocket ws, String text) { ws.close(1000, "bye"); }
        }));
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onOpen(WebSocket ws, Response response) { reconnected.countDown(); }
        }));
        server.start();

        gateway = RtdsGateway.builder().url(wsUrl()).reconnectDelayMs(50).build();
        rtds = new Rtds(gateway);
        rtds.subscribeBinancePrices(List.of("btcusdt"));

        assertTrue(reconnected.await(20, TimeUnit.SECONDS), "channel must reconnect");
        Thread.sleep(200);
        assertEquals(2L, rtds.generation());
    }

    @Test
    void shouldCloseIdempotentlyWhenCloseIsCalledTwice() {
        gateway = RtdsGateway.builder().url("wss://127.0.0.1:1").build();
        rtds = new Rtds(gateway);
        rtds.close();
        Assertions.assertDoesNotThrow(rtds::close);
    }

    @Test
    void shouldReleaseOwnedTransportResourcesWhenRtdsCloses() throws Exception {
        server = new MockWebServer();
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
        }));
        server.start();
        gateway = RtdsGateway.builder().url(wsUrl()).build();
        rtds = new Rtds(gateway);
        rtds.subscribeBinancePrices(List.of("btcusdt"));

        rtds.close();

        assertTrue(gateway.isClosed(),
                "close() must release the scheduler, dispatcher and connection pool it owns");
        assertEquals(0, gateway.connectionPoolSize());
    }

    @Test
    void shouldDeliverNoCallbacksWhenRtdsIsClosed() {
        // A frame may be in flight during close, so the closed-state guard belongs at dispatch.
        CapturingTransport transport = new CapturingTransport();
        Rtds capability = new Rtds(transport);
        List<BinancePriceEvent> seen = new CopyOnWriteArrayList<>();
        capability.onBinancePrice(List.of(), seen::add);
        capability.subscribeBinancePrices(List.of("btcusdt"));

        capability.close();
        transport.sink.onBinancePrice(
                new BinancePriceEvent("btcusdt", 1L, 1L, java.math.BigDecimal.ONE));

        assertEquals(List.of(), seen, "a closed capability must deliver nothing");
    }

    private static final class CapturingTransport implements RtdsTransport {
        private RtdsEventSink sink;

        @Override
        public RtdsConnection connect(RtdsSubscriptions subscriptions, RtdsEventSink sink) {
            this.sink = sink;
            return new RtdsConnection() {
                @Override
                public void subscription(RtdsSubscriptions current) {
                }

                @Override
                public void close() {
                }
            };
        }

        @Override
        public void close() {
        }
    }

    @Test
    void shouldContinueReconnectWhenLifecycleListenerThrows() throws Exception {
        CountDownLatch reconnected = new CountDownLatch(1);
        server = new MockWebServer();
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onMessage(WebSocket ws, String text) { ws.close(1000, "bye"); }
        }));
        server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onOpen(WebSocket ws, Response response) { reconnected.countDown(); }
        }));
        server.start();

        gateway = RtdsGateway.builder().url(wsUrl()).reconnectDelayMs(50).build();
        rtds = new Rtds(gateway);
        rtds.addLifecycleListener(new RtdsLifecycleListener() {
            @Override public void onClose(long generation, int code, String reason) {
                throw new IllegalStateException("boom");
            }
        });
        rtds.subscribeBinancePrices(List.of("btcusdt"));

        assertTrue(reconnected.await(20, TimeUnit.SECONDS),
                "reconnect must be scheduled even though the application callback threw");
    }

    /**
     * A loopback peer that omits the first PONG, then answers replacement PINGs. MockWebServer
     * auto-PONGs control pings, so it cannot exercise the timeout.
     */
    private static final class BareWebSocketServer implements AutoCloseable {
        private static final String WEBSOCKET_MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
        private static final String HANDSHAKE_END = "\r\n\r\n";

        private final ServerSocket server;
        private final AtomicBoolean running = new AtomicBoolean(true);
        private final AtomicInteger handshakes = new AtomicInteger();
        private final Object registrationLock = new Object();
        private final CopyOnWriteArrayList<ConnectionState> connections = new CopyOnWriteArrayList<>();
        private final CopyOnWriteArrayList<Socket> clients = new CopyOnWriteArrayList<>();
        private final Thread acceptor;

        private BareWebSocketServer() throws IOException {
            server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            acceptor = new Thread(this::acceptConnections, "rtds-bare-ws-acceptor");
            acceptor.setDaemon(true);
            acceptor.start();
        }

        private String url() {
            return "ws://127.0.0.1:" + server.getLocalPort();
        }

        private int handshakeCount() {
            return handshakes.get();
        }

        private int pingsForConnection(int connectionNumber) {
            return connections.size() < connectionNumber
                    ? 0 : connections.get(connectionNumber - 1).pings.get();
        }

        private int pongsForConnection(int connectionNumber) {
            return connections.size() < connectionNumber
                    ? 0 : connections.get(connectionNumber - 1).pongs.get();
        }

        private boolean connectionOpen(int connectionNumber) {
            return connections.size() >= connectionNumber
                    && connections.get(connectionNumber - 1).open.get();
        }

        private boolean awaitControlPings(int connectionNumber, int expected, long timeout,
                TimeUnit unit) throws InterruptedException {
            long deadline = System.nanoTime() + unit.toNanos(timeout);
            while (System.nanoTime() < deadline) {
                if (connections.size() >= connectionNumber
                        && connections.get(connectionNumber - 1).pings.get() >= expected) {
                    return true;
                }
                Thread.sleep(10);
            }
            return connections.size() >= connectionNumber
                    && connections.get(connectionNumber - 1).pings.get() >= expected;
        }

        private void acceptConnections() {
            while (running.get()) {
                try {
                    Socket client = server.accept();
                    clients.add(client);
                    Thread worker = new Thread(() -> serve(client), "rtds-bare-ws-client");
                    worker.setDaemon(true);
                    worker.start();
                } catch (IOException ignored) {
                    // Closing the listening socket is the normal teardown path.
                }
            }
        }

        private void serve(Socket client) {
            ConnectionState state = null;
            try (client) {
                InputStream input = client.getInputStream();
                OutputStream output = client.getOutputStream();
                String headers = readHeaders(input);
                String key = header(headers, "Sec-WebSocket-Key");
                if (key == null) return;
                String accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1")
                        .digest((key + WEBSOCKET_MAGIC).getBytes(StandardCharsets.US_ASCII)));
                output.write(("HTTP/1.1 101 Switching Protocols\r\n"
                        + "Upgrade: websocket\r\n"
                        + "Connection: Upgrade\r\n"
                        + "Sec-WebSocket-Accept: " + accept
                        + HANDSHAKE_END).getBytes(StandardCharsets.US_ASCII));
                output.flush();
                int connectionNumber;
                synchronized (registrationLock) {
                    connectionNumber = handshakes.incrementAndGet();
                    state = new ConnectionState();
                    connections.add(state);
                }
                state.open.set(true);
                readFrames(input, output, state, connectionNumber > 1);
            } catch (Exception ignored) {
                // Closing a client socket is the normal teardown path for this bare peer.
            } finally {
                if (state != null) state.open.set(false);
                clients.remove(client);
            }
        }

        private static String readHeaders(InputStream input) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            int matched = 0;
            int value;
            while ((value = input.read()) != -1) {
                bytes.write(value);
                matched = value == HANDSHAKE_END.charAt(matched) ? matched + 1
                        : value == '\r' ? 1 : 0;
                if (matched == HANDSHAKE_END.length()) {
                    return bytes.toString(StandardCharsets.US_ASCII);
                }
                if (bytes.size() > 16 * 1024) throw new IOException("WebSocket headers too large");
            }
            throw new IOException("WebSocket handshake ended before headers");
        }

        private static String header(String headers, String name) {
            for (String line : headers.split("\\r\\n")) {
                int colon = line.indexOf(':');
                if (colon >= 0 && name.equalsIgnoreCase(line.substring(0, colon).trim())) {
                    return line.substring(colon + 1).trim();
                }
            }
            return null;
        }

        private static void readFrames(InputStream input, OutputStream output, ConnectionState state,
                boolean answerPings) throws IOException {
            while (state.open.get()) {
                int first = input.read();
                if (first == -1) return;
                int second = input.read();
                if (second == -1) return;
                long length = second & 0x7f;
                if (length == 126) {
                    length = readUnsigned(input, 2);
                } else if (length == 127) {
                    length = readUnsigned(input, 8);
                }
                if (length > Integer.MAX_VALUE) throw new IOException("WebSocket frame too large");
                boolean masked = (second & 0x80) != 0;
                byte[] mask = masked ? readFully(input, 4) : null;
                byte[] payload = readFully(input, (int) length);
                if (masked) {
                    for (int i = 0; i < payload.length; i++) {
                        payload[i] = (byte) (payload[i] ^ mask[i % 4]);
                    }
                }
                int opcode = first & 0x0f;
                if (opcode == 0x9) {
                    state.pings.incrementAndGet();
                    if (answerPings) {
                        if (payload.length > 125) throw new IOException("WebSocket control frame too large");
                        output.write(0x8a);
                        output.write(payload.length);
                        output.write(payload);
                        output.flush();
                        state.pongs.incrementAndGet();
                    }
                } else if (opcode == 0x8) {
                    return;
                }
            }
        }

        private static long readUnsigned(InputStream input, int bytes) throws IOException {
            long value = 0;
            for (int i = 0; i < bytes; i++) {
                int next = input.read();
                if (next == -1) throw new IOException("WebSocket frame ended");
                value = (value << 8) | next;
            }
            return value;
        }

        private static byte[] readFully(InputStream input, int length) throws IOException {
            byte[] result = input.readNBytes(length);
            if (result.length != length) throw new IOException("WebSocket frame ended");
            return result;
        }

        @Override
        public void close() {
            if (!running.compareAndSet(true, false)) return;
            try {
                server.close();
            } catch (IOException ignored) {
            }
            for (Socket client : clients) {
                try {
                    client.close();
                } catch (IOException ignored) {
                }
            }
            try {
                acceptor.join(1_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        private static final class ConnectionState {
            private final AtomicBoolean open = new AtomicBoolean();
            private final AtomicInteger pings = new AtomicInteger();
            private final AtomicInteger pongs = new AtomicInteger();
        }
    }
}
