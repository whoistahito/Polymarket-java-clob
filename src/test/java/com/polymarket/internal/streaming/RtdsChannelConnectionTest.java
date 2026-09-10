package com.polymarket.internal.streaming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polymarket.streaming.BinancePriceEvent;
import com.polymarket.streaming.ChainlinkPriceEvent;
import com.polymarket.streaming.CommentCreatedEvent;
import com.polymarket.streaming.CommentRemovedEvent;
import com.polymarket.streaming.ReactionCreatedEvent;
import com.polymarket.streaming.ReactionRemovedEvent;
import com.polymarket.streaming.RtdsEventSink;
import com.polymarket.streaming.RtdsSubscriptions;
import java.io.EOFException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.SSLHandshakeException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class RtdsChannelConnectionTest {

    private ScheduledExecutorService scheduler;
    private RtdsChannelConnection connection;

    @AfterEach
    void tearDown() {
        if (connection != null) {
            connection.close();
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    @Test
    void shouldScheduleOneReconnectWhenInitialSubscriptionSendIsRejected() throws Exception {
        FakeClient client = new FakeClient(true);
        List<String> lifecycle = new CopyOnWriteArrayList<>();
        List<Exception> errors = new CopyOnWriteArrayList<>();
        List<BinancePriceEvent> prices = new CopyOnWriteArrayList<>();
        scheduler = Executors.newSingleThreadScheduledExecutor();
        connection = connection(client, lifecycle, errors, prices);

        client.open(0);
        assertTrue(client.sockets().get(0).canceled());
        assertEquals(List.of("resubscribe:1", "error:1"), lifecycle);
        client.message(0, priceFrame());
        assertTrue(prices.isEmpty());

        awaitSockets(client, 2);
        // OkHttp can report the canceled socket after the replacement is already current. Those
        // stale callbacks must not clear the replacement or queue another reconnect.
        client.fail(0, new EOFException("late terminal callback"));
        client.closed(0, 1006, "late terminal callback");
        Thread.sleep(60);
        assertEquals(2, client.sockets().size());
        assertEquals(1, errors.size());

        client.open(1);
        assertEquals(List.of("resubscribe:1", "error:1", "resubscribe:2", "open:2"), lifecycle);
        assertFalse(client.sockets().get(1).frames().isEmpty());
        client.message(1, priceFrame());
        assertEquals(1, prices.size());
    }

    @Test
    void shouldWarnWhenRecoverableTransportLossFollowsOpen() throws Exception {
        FakeClient client = new FakeClient(false);
        List<String> lifecycle = new CopyOnWriteArrayList<>();
        List<Exception> errors = new CopyOnWriteArrayList<>();
        scheduler = Executors.newSingleThreadScheduledExecutor();
        connection = connection(client, lifecycle, errors);
        client.open(0);

        try (LogCapture logs = new LogCapture()) {
            EOFException failure = new EOFException("peer closed");
            client.fail(0, failure);
            awaitSockets(client, 2);

            Object event = logs.events().stream()
                    .filter(candidate -> logs.formattedMessage(candidate).contains("reconnect scheduled"))
                    .findFirst()
                    .orElseThrow();
            assertEquals("WARN", logs.level(event));
            assertTrue(logs.formattedMessage(event).contains("transport loss after open"));
            assertNull(logs.throwable(event));
            assertSame(failure, errors.get(0));
        }
    }

    @Test
    void shouldRetainErrorStackTraceWhenTransportLossIsUnclassified() throws Exception {
        FakeClient client = new FakeClient(false);
        List<String> lifecycle = new CopyOnWriteArrayList<>();
        List<Exception> errors = new CopyOnWriteArrayList<>();
        scheduler = Executors.newSingleThreadScheduledExecutor();
        connection = connection(client, lifecycle, errors);
        client.open(0);

        try (LogCapture logs = new LogCapture()) {
            SocketException failure = new SocketException("connection refused");
            client.fail(0, failure);
            awaitSockets(client, 2);

            Object event = logs.events().stream()
                    .filter(candidate -> logs.formattedMessage(candidate).contains("RTDS channel failure"))
                    .findFirst()
                    .orElseThrow();
            assertEquals("ERROR", logs.level(event));
            assertNotNull(logs.throwable(event));
            assertSame(failure, errors.get(0));
        }
    }

    @Test
    void shouldWarnWhenConnectExceptionPrecedesOpen() throws Exception {
        assertConnectPhaseWarns(new ConnectException("connection refused"));
    }

    @Test
    void shouldWarnWhenSocketTimeoutExceptionPrecedesOpen() throws Exception {
        assertConnectPhaseWarns(new SocketTimeoutException("connect timed out"));
    }

    @Test
    void shouldWarnWhenUnknownHostExceptionPrecedesOpen() throws Exception {
        assertConnectPhaseWarns(new UnknownHostException("rtds.invalid"));
    }

    @Test
    void shouldRetainErrorStackTraceWhenSslHandshakePrecedesOpen() throws Exception {
        FakeClient client = new FakeClient(false);
        List<String> lifecycle = new CopyOnWriteArrayList<>();
        List<Exception> errors = new CopyOnWriteArrayList<>();
        scheduler = Executors.newSingleThreadScheduledExecutor();
        connection = connection(client, lifecycle, errors);

        try (LogCapture logs = new LogCapture()) {
            SSLHandshakeException failure = new SSLHandshakeException("certificate rejected");
            client.fail(0, failure);
            awaitSockets(client, 2);

            Object event = logs.events().stream()
                    .filter(candidate -> logs.formattedMessage(candidate).contains("RTDS channel failure"))
                    .findFirst()
                    .orElseThrow();
            assertEquals("ERROR", logs.level(event));
            assertNotNull(logs.throwable(event));
            assertSame(failure, errors.get(0));
        }
    }

    @Test
    void shouldDropMessageWhenSocketIsStale() throws Exception {
        FakeClient client = new FakeClient(false);
        List<String> lifecycle = new CopyOnWriteArrayList<>();
        List<Exception> errors = new CopyOnWriteArrayList<>();
        List<BinancePriceEvent> prices = new CopyOnWriteArrayList<>();
        scheduler = Executors.newSingleThreadScheduledExecutor();
        connection = connection(client, lifecycle, errors, prices);
        client.open(0);
        client.fail(0, new EOFException("peer closed"));
        awaitSockets(client, 2);

        client.open(0);
        client.message(0, priceFrame());
        client.closing(0, 1006, "stale");
        assertTrue(client.sockets().get(0).canceled());
        assertTrue(prices.isEmpty());

        client.open(1);
        client.message(0, priceFrame());
        assertTrue(prices.isEmpty());
        client.message(1, priceFrame());
        assertEquals(1, prices.size());
    }

    @Test
    void shouldDropMessageWithoutCancelWhenConnectionWasClosed() throws Exception {
        FakeClient client = new FakeClient(false);
        List<String> lifecycle = new CopyOnWriteArrayList<>();
        List<Exception> errors = new CopyOnWriteArrayList<>();
        List<BinancePriceEvent> prices = new CopyOnWriteArrayList<>();
        scheduler = Executors.newSingleThreadScheduledExecutor();
        connection = connection(client, lifecycle, errors, prices);
        client.open(0);

        connection.close();
        client.message(0, priceFrame());
        Thread.sleep(30);

        assertTrue(prices.isEmpty());
        assertFalse(client.sockets().get(0).canceled());
        assertEquals(1, client.sockets().size());
    }

    private void assertConnectPhaseWarns(Exception failure) throws Exception {
        FakeClient client = new FakeClient(false);
        List<String> lifecycle = new CopyOnWriteArrayList<>();
        List<Exception> errors = new CopyOnWriteArrayList<>();
        scheduler = Executors.newSingleThreadScheduledExecutor();
        connection = connection(client, lifecycle, errors);

        try (LogCapture logs = new LogCapture()) {
            client.fail(0, failure);
            awaitSockets(client, 2);

            Object event = logs.events().stream()
                    .filter(candidate -> logs.formattedMessage(candidate).contains("connect failure"))
                    .findFirst()
                    .orElseThrow();
            assertEquals("WARN", logs.level(event));
            assertNull(logs.throwable(event));
            assertTrue(logs.formattedMessage(event).contains("attempt 1"));
            assertTrue(logs.formattedMessage(event).contains("delay 10 ms"));
            assertSame(failure, errors.get(0));
        }
    }

    private static String priceFrame() {
        return """
                {"topic":"crypto_prices","type":"update","timestamp":1,
                 "payload":{"symbol":"btcusdt","timestamp":1,"value":1}}
                """;
    }

    private RtdsChannelConnection connection(FakeClient client, List<String> lifecycle,
            List<Exception> errors) {
        return connection(client, lifecycle, errors, null);
    }

    private RtdsChannelConnection connection(FakeClient client, List<String> lifecycle,
            List<Exception> errors, List<BinancePriceEvent> prices) {
        return new RtdsChannelConnection(client, scheduler, new ObjectMapper(), "ws://localhost/",
                new RtdsSubscriptions(List.of("btcusdt"), List.of(), List.of()),
                sink(lifecycle, errors, prices), 0, 10, 100, 30_000, 0);
    }

    private static RtdsEventSink sink(List<String> lifecycle, List<Exception> errors) {
        return sink(lifecycle, errors, null);
    }

    private static RtdsEventSink sink(List<String> lifecycle, List<Exception> errors,
            List<BinancePriceEvent> prices) {
        return new RtdsEventSink() {
            @Override public void onBinancePrice(BinancePriceEvent event) {
                if (prices != null) prices.add(event);
            }
            @Override public void onChainlinkPrice(ChainlinkPriceEvent event) {}
            @Override public void onCommentCreated(CommentCreatedEvent event) {}
            @Override public void onCommentRemoved(CommentRemovedEvent event) {}
            @Override public void onReactionCreated(ReactionCreatedEvent event) {}
            @Override public void onReactionRemoved(ReactionRemovedEvent event) {}
            @Override public void onOpen(long generation) { lifecycle.add("open:" + generation); }
            @Override public void onResubscribe(long generation) {
                lifecycle.add("resubscribe:" + generation);
            }
            @Override public void onError(long generation, Exception error) {
                lifecycle.add("error:" + generation);
                errors.add(error);
            }
            @Override public void onClose(long generation, int code, String reason) {
                lifecycle.add("close:" + generation);
            }
        };
    }

    private static void awaitSockets(FakeClient client, int expected) throws InterruptedException {
        for (int i = 0; i < 100 && client.sockets().size() < expected; i++) {
            Thread.sleep(10);
        }
        assertEquals(expected, client.sockets().size());
    }

    /** Captures Logback events without making logback-core a direct test dependency. */
    private static final class LogCapture implements AutoCloseable {
        private final Logger logger = (Logger) LoggerFactory.getLogger(RtdsChannelConnection.class);
        private final List<Object> events = new CopyOnWriteArrayList<>();
        private final Object appender;
        private final Method detachAppender;

        private LogCapture() throws Exception {
            Class<?> appenderType = Class.forName("ch.qos.logback.core.Appender");
            InvocationHandler handler = (proxy, method, arguments) -> handle(proxy, method, arguments);
            appender = Proxy.newProxyInstance(appenderType.getClassLoader(), new Class<?>[] {appenderType}, handler);
            logger.getClass().getMethod("addAppender", appenderType).invoke(logger, appender);
            detachAppender = logger.getClass().getMethod("detachAppender", appenderType);
        }

        private Object handle(Object proxy, Method method, Object[] arguments) {
            return switch (method.getName()) {
                case "doAppend" -> {
                    events.add(arguments[0]);
                    yield null;
                }
                case "isStarted" -> true;
                case "getName" -> "rtds-test-capture";
                case "equals" -> proxy == arguments[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> "rtds-test-capture";
                default -> null;
            };
        }

        private List<Object> events() {
            return events;
        }

        private String level(Object event) {
            return invoke(event, "getLevel").toString();
        }

        private String formattedMessage(Object event) {
            return (String) invoke(event, "getFormattedMessage");
        }

        private Object throwable(Object event) {
            return invoke(event, "getThrowableProxy");
        }

        private static Object invoke(Object target, String method) {
            try {
                return target.getClass().getMethod(method).invoke(target);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }

        @Override
        public void close() throws Exception {
            detachAppender.invoke(logger, appender);
        }
    }

    private static final class FakeClient extends OkHttpClient {
        private final List<FakeWebSocket> sockets = new CopyOnWriteArrayList<>();
        private final List<WebSocketListener> listeners = new CopyOnWriteArrayList<>();
        private final boolean rejectFirst;

        private FakeClient(boolean rejectFirst) {
            this.rejectFirst = rejectFirst;
        }

        @Override
        public WebSocket newWebSocket(Request request, WebSocketListener listener) {
            FakeWebSocket socket = new FakeWebSocket(request, rejectFirst && sockets.isEmpty());
            sockets.add(socket);
            listeners.add(listener);
            return socket;
        }

        List<FakeWebSocket> sockets() {
            return sockets;
        }

        void open(int index) {
            listeners.get(index).onOpen(sockets.get(index), null);
        }

        void fail(int index, Throwable failure) {
            listeners.get(index).onFailure(sockets.get(index), failure, null);
        }

        void message(int index, String text) {
            listeners.get(index).onMessage(sockets.get(index), text);
        }

        void closing(int index, int code, String reason) {
            listeners.get(index).onClosing(sockets.get(index), code, reason);
        }

        void closed(int index, int code, String reason) {
            listeners.get(index).onClosed(sockets.get(index), code, reason);
        }
    }

    private static final class FakeWebSocket implements WebSocket {
        private final Request request;
        private final boolean rejectSend;
        private final List<String> frames = new CopyOnWriteArrayList<>();
        private final AtomicBoolean canceled = new AtomicBoolean();

        private FakeWebSocket(Request request, boolean rejectSend) {
            this.request = request;
            this.rejectSend = rejectSend;
        }

        @Override public Request request() { return request; }
        @Override public long queueSize() { return 0; }
        @Override public boolean send(String text) {
            frames.add(text);
            return !rejectSend;
        }
        @Override public boolean send(ByteString bytes) { return !rejectSend; }
        @Override public boolean close(int code, String reason) { return true; }
        @Override public void cancel() { canceled.set(true); }

        boolean canceled() { return canceled.get(); }
        List<String> frames() { return frames; }
    }
}
