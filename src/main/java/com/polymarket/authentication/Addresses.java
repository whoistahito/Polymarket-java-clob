package com.polymarket.authentication;

import java.util.Arrays;
import java.util.Locale;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Keys;
import org.web3j.utils.Numeric;

/** Address parsing and derivation shared by the identity types. */
final class Addresses {

    // CREATE2 factory + init-code hash, Polygon mainnet only (2.0 dropped Amoy).
    // Ground truth: rs-clob-client/src/lib.rs WALLET_CONFIG.
    private static final String PROXY_FACTORY = "0xaB45c5A4B0c941a2F231C04C3f49182e1A254052";
    private static final String SAFE_FACTORY = "0xaacFeEa03eb1561C4e67d661e40682Bd20E3541b";
    private static final byte[] PROXY_INIT_CODE_HASH = Numeric.hexStringToByteArray(
            "d21df8dc65880a8606f09fe0ce3df9b8869287ab0b058be05aa9e8af6330a00b");
    private static final byte[] SAFE_INIT_CODE_HASH = Numeric.hexStringToByteArray(
            "2bce2127ff07fb632d16c8347c4ebf501f4841168bed00d9e6ef715ddb6fcecf");

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

    /** Deterministic Polymarket Proxy wallet address for an EOA. Salt is {@code keccak256(address)}. */
    static String deriveProxyWallet(String eoa) {
        byte[] salt = Hash.sha3(addressBytes(eoa));
        return create2Address(PROXY_FACTORY, salt, PROXY_INIT_CODE_HASH);
    }

    /** Deterministic Polymarket Safe wallet address for an EOA. Salt is {@code keccak256(abiEncode(address))}. */
    static String deriveSafeWallet(String eoa) {
        byte[] padded = new byte[32];
        System.arraycopy(addressBytes(eoa), 0, padded, 12, 20);
        byte[] salt = Hash.sha3(padded);
        return create2Address(SAFE_FACTORY, salt, SAFE_INIT_CODE_HASH);
    }

    /** {@code keccak256(0xff ++ factory ++ salt ++ initCodeHash)[12:]}, EIP-55 checksummed. */
    private static String create2Address(String factory, byte[] salt, byte[] initCodeHash) {
        byte[] input = new byte[85];
        input[0] = (byte) 0xff;
        System.arraycopy(addressBytes(factory), 0, input, 1, 20);
        System.arraycopy(salt, 0, input, 21, 32);
        System.arraycopy(initCodeHash, 0, input, 53, 32);
        byte[] address = Arrays.copyOfRange(Hash.sha3(input), 12, 32);
        return Keys.toChecksumAddress(Numeric.toHexString(address)).toLowerCase(Locale.ROOT);
    }

    private static byte[] addressBytes(String address) {
        return Numeric.hexStringToByteArray(require(address, "address"));
    }
}
