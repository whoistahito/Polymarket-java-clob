package com.polymarket.testsupport;

import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/** Forces test connections direct so ambient proxies cannot bypass the loopback-only DNS guard. */
public final class NoExternalNetworkExtension implements BeforeAllCallback {

    private static final ProxySelector DIRECT = new ProxySelector() {
        @Override
        public List<Proxy> select(URI uri) {
            return List.of(Proxy.NO_PROXY);
        }

        @Override
        public void connectFailed(URI uri, SocketAddress sa, java.io.IOException ioe) {
        }
    };

    private static volatile boolean installed;

    @Override
    public void beforeAll(ExtensionContext context) {
        install();
    }

    public static synchronized void install() {
        if (installed || NoExternalNetworkResolverProvider.liveEnabled()) return;
        ProxySelector.setDefault(DIRECT);
        installed = true;
    }
}
