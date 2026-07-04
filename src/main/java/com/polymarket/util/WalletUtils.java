package com.polymarket.util;

import org.web3j.crypto.Hash;
import org.web3j.crypto.Keys;
import org.web3j.utils.Numeric;

import java.util.Arrays;
import java.util.Optional;

/**
 * Utilities for deriving Polymarket wallet addresses via CREATE2.
 *
 * <p>Polymarket deploys deterministic wallet contracts for users who log in with
 * Magic (email) or browser wallets. Knowing the derived address is required to
 * configure the correct {@link com.polymarket.model.SignatureType} when placing
 * orders on behalf of such wallets.
 *
 * <h2>Algorithm</h2>
 * <pre>
 * CREATE2 address = keccak256(0xff ++ factory ++ salt ++ initCodeHash)[12:]
 * </pre>
 *
 * <h2>Salt derivation</h2>
 * <ul>
 *   <li><b>Proxy</b>: {@code keccak256(eoa_address_bytes)} — 20 raw address bytes, no padding.</li>
 *   <li><b>Safe</b>: {@code keccak256(abi_encode(address))} — 32 bytes: 12 zero prefix + 20-byte address.</li>
 * </ul>
 *
 * <p>Factory addresses and init-code hashes are sourced from the Rust SDK
 * {@code rs-clob-client/src/lib.rs}.
 */
public final class WalletUtils {

    // -------------------------------------------------------------------------
    // Factory addresses (from Rust WALLET_CONFIG static map)
    // -------------------------------------------------------------------------

    /** Proxy wallet factory on Polygon Mainnet (chain 137). */
    private static final String PROXY_FACTORY_POLYGON =
            "0xaB45c5A4B0c941a2F231C04C3f49182e1A254052";

    // Proxy factory NOT supported on Amoy (80002).

    /** Gnosis Safe factory — same address on both Polygon and Amoy. */
    private static final String SAFE_FACTORY =
            "0xaacFeEa03eb1561C4e67d661e40682Bd20E3541b";

    // -------------------------------------------------------------------------
    // Init-code hashes (from Rust PROXY_INIT_CODE_HASH / SAFE_INIT_CODE_HASH)
    // -------------------------------------------------------------------------

    private static final byte[] PROXY_INIT_CODE_HASH = Numeric.hexStringToByteArray(
            "d21df8dc65880a8606f09fe0ce3df9b8869287ab0b058be05aa9e8af6330a00b");

    private static final byte[] SAFE_INIT_CODE_HASH = Numeric.hexStringToByteArray(
            "2bce2127ff07fb632d16c8347c4ebf501f4841168bed00d9e6ef715ddb6fcecf");

    private WalletUtils() {
        // utility class
    }

    /**
     * Derives the Polymarket Proxy wallet address for an EOA using CREATE2.
     *
     * <p>This is the deterministic address of the EIP-1167 minimal-proxy wallet
     * that Polymarket deploys for Magic/email-login users.
     *
     * <p>Currently only supported on Polygon Mainnet (chain 137).
     *
     * @param eoa     the externally-owned account address (checksummed or lower-case hex, with
     *                or without {@code 0x} prefix)
     * @param chainId the EVM chain ID (e.g. 137 for Polygon Mainnet)
     * @return the derived proxy wallet address as a lower-case {@code 0x…} hex string (EIP-55 checksummed), or
     *         {@link Optional#empty()} if proxy wallets are not supported on the given chain
     */
    public static Optional<String> deriveProxyWallet(String eoa, int chainId) {
        if (chainId != 137) {
            // Proxy factory only deployed on Polygon mainnet
            return Optional.empty();
        }
        byte[] eoaBytes = addressToBytes(eoa);
        // Salt = keccak256(address_bytes) — 20 bytes, no ABI padding
        byte[] salt = Hash.sha3(eoaBytes);
        return Optional.of(create2Address(PROXY_FACTORY_POLYGON, salt, PROXY_INIT_CODE_HASH));
    }

    /**
     * Derives the Gnosis Safe wallet address for an EOA using CREATE2.
     *
     * <p>This is the deterministic address of the 1-of-1 Gnosis Safe multisig
     * that Polymarket deploys for browser-wallet users.
     *
     * <p>Supported on both Polygon Mainnet (137) and Polygon Amoy Testnet (80002).
     *
     * @param eoa     the externally-owned account address
     * @param chainId the EVM chain ID
     * @return the derived Safe wallet address as a lower-case {@code 0x…} hex string (EIP-55 checksummed), or
     *         {@link Optional#empty()} if the chain is not supported
     */
    public static Optional<String> deriveSafeWallet(String eoa, int chainId) {
        if (chainId != 137 && chainId != 80002) {
            return Optional.empty();
        }
        byte[] eoaBytes = addressToBytes(eoa);
        // Salt = keccak256(abi_encode(address)) — 32 bytes: 12 zeros + 20-byte address
        byte[] padded = new byte[32];
        System.arraycopy(eoaBytes, 0, padded, 12, 20);
        byte[] salt = Hash.sha3(padded);
        return Optional.of(create2Address(SAFE_FACTORY, salt, SAFE_INIT_CODE_HASH));
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    /**
     * Computes the CREATE2 address:
     * {@code keccak256(0xff ++ factory_20 ++ salt_32 ++ initCodeHash_32)[12:]}
     */
    private static String create2Address(String factory, byte[] salt, byte[] initCodeHash) {
        byte[] factoryBytes = addressToBytes(factory);

        // 1 (0xff) + 20 (factory) + 32 (salt) + 32 (initCodeHash) = 85 bytes
        byte[] input = new byte[85];
        input[0] = (byte) 0xff;
        System.arraycopy(factoryBytes, 0, input, 1, 20);
        System.arraycopy(salt, 0, input, 21, 32);
        System.arraycopy(initCodeHash, 0, input, 53, 32);

        byte[] hash = Hash.sha3(input);
        // Address is the last 20 bytes of the 32-byte keccak output
        byte[] addressBytes = Arrays.copyOfRange(hash, 12, 32);
        // EIP-55 checksummed form, matching the Rust SDK's alloy Address output
        return Keys.toChecksumAddress(Numeric.toHexString(addressBytes));
    }

    /** Converts a hex address string (with or without {@code 0x}) to 20 raw bytes. */
    private static byte[] addressToBytes(String address) {
        String hex = address.startsWith("0x") || address.startsWith("0X")
                ? address.substring(2)
                : address;
        // Ensure 40 hex chars (20 bytes)
        if (hex.length() != 40) {
            throw new IllegalArgumentException(
                    "Invalid Ethereum address length: " + address);
        }
        return Numeric.hexStringToByteArray(hex);
    }
}
