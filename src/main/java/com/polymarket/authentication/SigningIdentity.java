package com.polymarket.authentication;

/**
 * Who funds an order and who signs for it. Valid by construction: an identity cannot
 * exist without both addresses and its official signature type.
 */
public sealed interface SigningIdentity {

    /** Address that holds the funds and owns the position. */
    String maker();

    /** Address whose key produces the signature. */
    String signer();

    /** Official Polymarket signature type: 0 EOA, 1 Proxy, 2 Safe, 3 Deposit Wallet. */
    int signatureType();

    static SigningIdentity eoa(String address) {
        return new Eoa(address);
    }

    static SigningIdentity proxyWallet(String wallet, String signer) {
        return new ProxyWallet(wallet, signer);
    }

    static SigningIdentity safeWallet(String wallet, String signer) {
        return new SafeWallet(wallet, signer);
    }

    static SigningIdentity depositWallet(String wallet, String signer) {
        return new DepositWallet(wallet, signer);
    }

    /** Derives the Polymarket Proxy wallet CREATE2 address for this EOA; pure local computation, no RPC. */
    static ProxyWallet deriveProxyWallet(String eoaSigner) {
        return new ProxyWallet(Addresses.deriveProxyWallet(eoaSigner), eoaSigner);
    }

    /** Derives the Polymarket Safe wallet CREATE2 address for this EOA; pure local computation, no RPC. */
    static SafeWallet deriveSafeWallet(String eoaSigner) {
        return new SafeWallet(Addresses.deriveSafeWallet(eoaSigner), eoaSigner);
    }

    record Eoa(String address) implements SigningIdentity {
        public Eoa {
            address = Addresses.require(address, "address");
        }

        @Override
        public String maker() {
            return address;
        }

        @Override
        public String signer() {
            return address;
        }

        @Override
        public int signatureType() {
            return 0;
        }
    }

    record ProxyWallet(String maker, String signer) implements SigningIdentity {
        public ProxyWallet {
            maker = Addresses.require(maker, "maker");
            signer = Addresses.require(signer, "signer");
        }

        @Override
        public int signatureType() {
            return 1;
        }
    }

    record SafeWallet(String maker, String signer) implements SigningIdentity {
        public SafeWallet {
            maker = Addresses.require(maker, "maker");
            signer = Addresses.require(signer, "signer");
        }

        @Override
        public int signatureType() {
            return 2;
        }
    }

    record DepositWallet(String maker, String signer) implements SigningIdentity {
        public DepositWallet {
            maker = Addresses.require(maker, "maker");
            signer = Addresses.require(signer, "signer");
        }

        @Override
        public int signatureType() {
            return 3;
        }
    }
}
