package com.polymarket.streaming;

import com.polymarket.authentication.ApiCredentials;
import java.util.List;

/**
 * Domain-declared port for the CLOB WebSocket transport. The Authoritative Subscription travels
 * with the connect call, so the initial frame is complete however late the socket opens.
 */
public interface StreamTransport {

    StreamConnection connectMarket(List<String> assetIds, boolean customEventsEnabled, StreamEventSink sink);

    StreamConnection connectUser(ApiCredentials credentials, List<String> markets, StreamEventSink sink);
}
