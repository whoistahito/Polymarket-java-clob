package com.polymarket.authentication;

import lombok.NonNull;

/**
 * Which Trading Wallet an order names and which Account Signer authorizes it. Valid by
 * construction: an identity cannot exist without both addresses and its official signature type.
 */
public sealed interface SigningIdentity {

    /** Trading Wallet: holds the collateral and position, and is named as the maker of an order. */
    String tradingWallet();

    /** Account Signer: the externally owned account that authorizes the order and every L2 request. */
    String accountSigner();

    /** Official Polymarket signature type: 0 EOA, 1 Proxy, 2 Safe, 3 Deposit Wallet. */
    int signatureType();

    static SigningIdentity eoa(String accountSigner) {
        return new Eoa(accountSigner);
    }

    static SigningIdentity proxyWallet(String tradingWallet, String accountSigner) {
        return new ProxyWallet(tradingWallet, accountSigner);
    }

    static SigningIdentity safeWallet(String tradingWallet, String accountSigner) {
        return new SafeWallet(tradingWallet, accountSigner);
    }

    static SigningIdentity depositWallet(String tradingWallet, String accountSigner) {
        return new DepositWallet(tradingWallet, accountSigner);
    }

    /** Derives the Polymarket Proxy Trading Wallet CREATE2 address; pure local computation, no RPC. */
    static ProxyWallet deriveProxyWallet(String accountSigner) {
        return new ProxyWallet(Addresses.deriveProxyWallet(accountSigner), accountSigner);
    }

    /** Derives the Polymarket Safe Trading Wallet CREATE2 address; pure local computation, no RPC. */
    static SafeWallet deriveSafeWallet(String accountSigner) {
        return new SafeWallet(Addresses.deriveSafeWallet(accountSigner), accountSigner);
    }

    /** The Trading Wallet is the Account Signer itself, so both order address fields match. */
    record Eoa(@NonNull String accountSigner) implements SigningIdentity {
        public Eoa {
            accountSigner = Addresses.require(accountSigner, "accountSigner");
        }

        @Override
        public String tradingWallet() {
            return accountSigner;
        }

        @Override
        public int signatureType() {
            return 0;
        }
    }

    record ProxyWallet(@NonNull String tradingWallet, @NonNull String accountSigner) implements SigningIdentity {
        public ProxyWallet {
            tradingWallet = Addresses.require(tradingWallet, "tradingWallet");
            accountSigner = Addresses.require(accountSigner, "accountSigner");
        }

        @Override
        public int signatureType() {
            return 1;
        }
    }

    record SafeWallet(@NonNull String tradingWallet, @NonNull String accountSigner) implements SigningIdentity {
        public SafeWallet {
            tradingWallet = Addresses.require(tradingWallet, "tradingWallet");
            accountSigner = Addresses.require(accountSigner, "accountSigner");
        }

        @Override
        public int signatureType() {
            return 2;
        }
    }

    /**
     * The Trading Wallet is a smart contract, so it names the order's maker AND the ERC-7739
     * wrapper domain the Account Signer's key signs under.
     */
    record DepositWallet(@NonNull String tradingWallet, @NonNull String accountSigner) implements SigningIdentity {
        // Official sources conflict: combos/market-makers' identity table puts the wallet in
        // signer_address too, while the pinned v2-deposit-wallet vector keeps the Account Signer.
        public DepositWallet {
            tradingWallet = Addresses.require(tradingWallet, "tradingWallet");
            accountSigner = Addresses.require(accountSigner, "accountSigner");
        }

        @Override
        public int signatureType() {
            return 3;
        }
    }
}
