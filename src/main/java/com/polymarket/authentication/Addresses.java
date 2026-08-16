package com.polymarket.authentication;

import java.util.Locale;
import org.web3j.crypto.Credentials;

/** Address parsing and derivation shared by the identity types. */
final class Addresses {

    private Addresses() {
    }

    static String require(String address, String field) {
        if (address == null || !address.matches("(?i)0x[0-9a-f]{40}")) {
            throw new IllegalArgumentException(
                    field + " must be a 20-byte hex address, got: " + address);
        }
        return address.toLowerCase(Locale.ROOT);
    }

    static String fromPrivateKey(String privateKeyHex) {
        return Credentials.create(privateKeyHex).getAddress().toLowerCase(Locale.ROOT);
    }
}
