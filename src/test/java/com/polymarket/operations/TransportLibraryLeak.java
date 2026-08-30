package com.polymarket.operations;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import org.web3j.crypto.Credentials;

/** Test-only forbidden dependency: proves the boundary rules actually fail. Never shipped. */
public final class TransportLibraryLeak {

    private ObjectMapper json;
    private OkHttpClient http;
    private Credentials key;

    @Override
    public String toString() {
        return json + " " + http + " " + key;
    }
}
