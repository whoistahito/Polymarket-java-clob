package com.polymarket.operations;

import com.polymarket.internal.http.HttpRuntime;

/** Test-only forbidden dependency: proves the boundary rules actually fail. Never shipped. */
public final class InternalTransportLeak {

    public HttpRuntime runtime() {
        return null;
    }
}
