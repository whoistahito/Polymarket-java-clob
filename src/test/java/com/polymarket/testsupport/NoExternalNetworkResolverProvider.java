package com.polymarket.testsupport;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.spi.InetAddressResolver;
import java.net.spi.InetAddressResolverProvider;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/** Test-scope DNS guard: deterministic tests resolve loopback only; -Plive permits real hosts. */
public final class NoExternalNetworkResolverProvider extends InetAddressResolverProvider {

    public static final String LIVE_PROPERTY = "polymarket.live";

    private static final Set<String> LOOPBACK_NAMES =
            Set.of("localhost", "localhost.localdomain", "ip6-localhost", "ip6-loopback");

    @Override
    public InetAddressResolver get(Configuration configuration) {
        InetAddressResolver builtin = configuration.builtinResolver();
        return new InetAddressResolver() {
            @Override
            public Stream<InetAddress> lookupByName(String host, LookupPolicy lookupPolicy)
                    throws UnknownHostException {
                if (!liveEnabled() && !isLoopbackName(host)) {
                    throw new UnknownHostException(
                            "blocked by " + name() + ": the deterministic test suite may not resolve '"
                                    + host + "'. Run live checks with -Plive.");
                }
                return builtin.lookupByName(host, lookupPolicy);
            }

            @Override
            public String lookupByAddress(byte[] addr) throws UnknownHostException {
                return builtin.lookupByAddress(addr);
            }
        };
    }

    @Override
    public String name() {
        return "polymarket-no-external-network";
    }

    public static boolean liveEnabled() {
        return Boolean.parseBoolean(System.getProperty(LIVE_PROPERTY, "false"));
    }

    private static boolean isLoopbackName(String host) {
        if (host == null || host.isBlank()) return false;
        String h = host.toLowerCase(Locale.ROOT);
        if (h.startsWith("[") && h.endsWith("]")) h = h.substring(1, h.length() - 1);
        return LOOPBACK_NAMES.contains(h)
                || h.equals("::1")
                || h.equals("0:0:0:0:0:0:0:1")
                || h.matches("127\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}");
    }
}
