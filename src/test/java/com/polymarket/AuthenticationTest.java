package com.polymarket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.polymarket.authentication.ApiCredentials;
import com.polymarket.authentication.ApiKey;
import com.polymarket.authentication.ApiKeyDeletion;
import com.polymarket.authentication.ApiKeyValidation;
import com.polymarket.authentication.AuthenticationRequiredException;
import com.polymarket.authentication.PrivateKeySigner;
import com.polymarket.authentication.SigningAuthority;
import com.polymarket.authentication.SigningIdentity;
import com.polymarket.internal.http.HttpRuntime;
import com.polymarket.markets.MarketRules;
import com.polymarket.markets.Price;
import com.polymarket.markets.PusdAmount;
import com.polymarket.markets.ShareQuantity;
import com.polymarket.markets.TickSize;
import com.polymarket.markets.TokenId;
import com.polymarket.trading.Side;
import com.polymarket.trading.SignedOrder;
import com.polymarket.trading.SigningContext;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.Credentials;

class AuthenticationTest {

    private static final String TEST_KEY =
            "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";
    private static final Clock FIXED =
            Clock.fixed(Instant.ofEpochSecond(1773890758L), ZoneOffset.UTC);
    /** The Account Signer whose key the caller does NOT hold; only its address is known. */
    private static final String ACCOUNT_SIGNER = "0x8f3cf7ad23cd3cadbd9735aff958023239c6a063";
    /** Trading Wallet and token from the pinned protocol vectors, never the Account Signer. */
    private static final String TRADING_WALLET = "0x70997970c51812dc3a010c7d01b50e0d17dc79c8";
    private static final String TOKEN_ID =
            "71321045679252212594626385532706912750332728571942532289631379312455583992563";

    private MockWebServer server;

    @BeforeEach
    void startServer() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void stopServer() throws Exception {
        server.close();
    }

    private Polymarket sdk(SigningAuthority authority) {
        URI host = server.url("/").uri();
        PolymarketConfig config = PolymarketConfig.defaults()
                .clobHost(host).gammaHost(host).dataHost(host).geoblockHost(host);
        return Polymarket.with(config,
                new HttpRuntime(Duration.ofSeconds(2), Duration.ofSeconds(5),
                        ReadRetryPolicy.none(), d -> {
                        }),
                authority, FIXED);
    }

    private static PrivateKeySigner signer() {
        return PrivateKeySigner.of(TEST_KEY);
    }

    private static SigningAuthority localAuthority() {
        PrivateKeySigner signer = signer();
        return SigningAuthority.signing(signer, SigningIdentity.eoa(signer.address()));
    }

    private static ApiCredentials creds() {
        return new ApiCredentials("f4f247b7-4ac7-ff29-a152-04fda0a8755a",
                "c2VjcmV0LXNlY3JldC1zZWNyZXQtc2VjcmV0LXNlY3JldA==", "hex-passphrase");
    }

    @Nested
    class Identities {

        @Test
        void shouldThrowExceptionWhenBuildingIdentityFromInvalidAddress() {
            assertThrows(IllegalArgumentException.class, () -> SigningIdentity.eoa("nope"));
            assertThrows(IllegalArgumentException.class,
                    () -> SigningIdentity.depositWallet("0x1234", "0x" + "a".repeat(40)));
            assertThrows(NullPointerException.class,
                    () -> SigningIdentity.safeWallet("0x" + "a".repeat(40), null));
        }

        @Test
        void shouldExposeOfficialSignatureTypesWhenBuildingIdentities() throws Exception {
            String wallet = "0x" + "a".repeat(40);
            String eoa = "0x" + "b".repeat(40);

            assertEquals(officialSignatureType("EOA"), SigningIdentity.eoa(eoa).signatureType());
            assertEquals(officialSignatureType("POLY_PROXY"),
                    SigningIdentity.proxyWallet(wallet, eoa).signatureType());
            assertEquals(officialSignatureType("POLY_GNOSIS_SAFE"),
                    SigningIdentity.safeWallet(wallet, eoa).signatureType());
            assertEquals(officialSignatureType("DEPOSIT_WALLET"),
                    SigningIdentity.depositWallet(wallet, eoa).signatureType());
        }

        @Test
        void shouldUseOwnAddressWhenBuildingEoaIdentity() {
            SigningIdentity identity = SigningIdentity.eoa("0x" + "b".repeat(40));
            assertEquals(identity.tradingWallet(), identity.accountSigner());
        }

        @Test
        void shouldThrowIllegalArgumentExceptionWhenAuthorityIdentityDoesNotMatchSigner() {
            assertThrows(IllegalArgumentException.class, () -> SigningAuthority.signing(
                    signer(), SigningIdentity.eoa("0x" + "c".repeat(40))));
        }

        /** Reads the value pinned from official documentation by issue #3. */
        private int officialSignatureType(String name) throws Exception {
            try (InputStream in = getClass().getResourceAsStream("/protocol/constraints.json")) {
                return new ObjectMapper().readTree(in)
                        .path("signatureTypes").path(name).asInt(-1);
            }
        }
    }

    @Nested
    class Redaction {

        @Test
        void shouldRedactSecretsWhenRenderingSecretBearingValues() {
            String rendered = SigningAuthority.signing(signer(), SigningIdentity.eoa(signer().address()))
                    .withApiCredentials(creds()).toString();

            assertFalse(rendered.contains(TEST_KEY), "private key leaked");
            assertFalse(rendered.contains(creds().secret()), "api secret leaked");
            assertFalse(rendered.contains(creds().key()), "api key leaked");
            assertFalse(rendered.contains(creds().passphrase()), "passphrase leaked");
            assertTrue(rendered.contains(signer().address()), "the public address may be shown");
        }

        @Test
        void shouldRedactPairedCredentialsWhenRenderingAuthority() {
            String rendered =
                    SigningAuthority.apiCredentials(creds(), ACCOUNT_SIGNER).toString();

            assertFalse(rendered.contains(creds().key()), "api key leaked");
            assertFalse(rendered.contains(creds().secret()), "api secret leaked");
            assertFalse(rendered.contains(creds().passphrase()), "passphrase leaked");
            assertFalse(creds().toString().contains(creds().secret()), "api secret leaked");
            assertTrue(rendered.contains(ACCOUNT_SIGNER), "the Account Signer address may be shown");
        }

        @Test
        void shouldDeriveAddressOfflineWhenCreatingSigner() {
            PrivateKeySigner signer = signer();
            assertEquals(Credentials.create(TEST_KEY).getAddress().toLowerCase(), signer.address());
            assertFalse(signer.toString().contains(TEST_KEY));
        }
    }

    @Nested
    class MissingAuthority {

        @Test
        void shouldThrowAuthenticationRequiredExceptionWhenL1OperationHasNoLocalKey() {
            try (Polymarket sdk = sdk(SigningAuthority.none())) {
                assertThrows(AuthenticationRequiredException.class,
                        () -> sdk.authentication().createApiKey());
                assertThrows(AuthenticationRequiredException.class,
                        () -> sdk.authentication().deriveApiKey());
                assertThrows(AuthenticationRequiredException.class,
                        () -> sdk.authentication().apiKeys());
            }
            assertEquals(0, server.getRequestCount(), "nothing may reach the wire");
        }

        @Test
        void shouldThrowAuthenticationRequiredExceptionWhenL2OperationHasNoApiCredentials() {
            try (Polymarket sdk = sdk(localAuthority())) {
                assertThrows(AuthenticationRequiredException.class,
                        () -> sdk.authentication().validate());
                assertThrows(AuthenticationRequiredException.class,
                        () -> sdk.authentication().deleteApiKey());
            }
            assertEquals(0, server.getRequestCount(), "nothing may reach the wire");
        }

        @Test
        void shouldMakeNoNetworkCallWhenBuildingWithCredentials() {
            try (Polymarket sdk = sdk(localAuthority().withApiCredentials(creds()))) {
                assertTrue(sdk.authentication() != null);
            }
            assertEquals(0, server.getRequestCount());
        }
    }

    @Nested
    class CredentialsWithoutALocalKey {

        @Test
        void shouldPairCredentialsWithAccountSignerWhenNoLocalKeyIsHeld() throws Exception {
            server.enqueue(new MockResponse().setBody("{\"closed_only\":false}"));

            try (Polymarket sdk = sdk(
                    SigningAuthority.apiCredentials(creds(), ACCOUNT_SIGNER))) {
                assertTrue(sdk.authentication().validate().valid());
            }

            RecordedRequest request = server.takeRequest();
            assertEquals(ACCOUNT_SIGNER, request.getHeader("POLY_ADDRESS"));
            assertEquals(creds().key(), request.getHeader("POLY_API_KEY"));
            assertEquals(creds().passphrase(), request.getHeader("POLY_PASSPHRASE"));
        }

        @Test
        void shouldThrowAuthenticationRequiredExceptionWhenL1OperationHasOnlyApiCredentials() {
            try (Polymarket sdk = sdk(
                    SigningAuthority.apiCredentials(creds(), ACCOUNT_SIGNER))) {
                assertThrows(AuthenticationRequiredException.class,
                        () -> sdk.authentication().createApiKey());
            }
            assertEquals(0, server.getRequestCount(), "nothing may reach the wire");
        }
    }

    @Nested
    class SeparateAuthority {

        @Test
        void shouldUseAccountSignerForL2AndTradingWalletForOrdersWhenUsingProxyWallet()
                throws Exception {
            SigningAuthority authority = SigningAuthority
                    .signing(signer(), SigningIdentity.proxyWallet(TRADING_WALLET, signer().address()))
                    .withApiCredentials(creds());
            server.enqueue(new MockResponse().setBody("{\"closed_only\":false}"));

            SignedOrder order;
            try (Polymarket sdk = sdk(authority)) {
                assertTrue(sdk.authentication().validate().valid());
                order = sdk.trading().sign(new TokenId(TOKEN_ID), Side.BUY,
                        Price.of(new BigDecimal("0.52")),
                        ShareQuantity.of(new BigDecimal("10")),
                        new MarketRules(TickSize.of("0.01"), ShareQuantity.of("0.01"), false),
                        signingContext(authority));
            }

            assertEquals(signer().address(), server.takeRequest().getHeader("POLY_ADDRESS"),
                    "L2 headers carry the Account Signer, never the Trading Wallet");
            assertEquals(TRADING_WALLET, order.maker(),
                    "the order names the Trading Wallet as maker");
            assertEquals(signer().address(), order.signer(),
                    "the Account Signer still authorizes the order");
        }

        @Test
        void shouldSeparateTradingWalletAndAccountSignerWhenUsingDepositWallet() throws Exception {
            SigningAuthority authority = SigningAuthority.signing(
                    signer(), SigningIdentity.depositWallet(TRADING_WALLET, signer().address()));

            SignedOrder order;
            try (Polymarket sdk = sdk(authority)) {
                order = sdk.trading().sign(new TokenId(TOKEN_ID), Side.BUY,
                        Price.of(new BigDecimal("0.52")),
                        ShareQuantity.of(new BigDecimal("10")),
                        new MarketRules(TickSize.of("0.01"), ShareQuantity.of("0.01"), false),
                        signingContext(authority));
            }

            assertEquals(TRADING_WALLET, order.maker());
            // Signature type 3 is verified through the wallet's own ERC-1271 check, so the wallet
            // is the signer the exchange resolves; the Account Signer keeps POLY_ADDRESS.
            assertEquals(TRADING_WALLET, order.signer());
            assertEquals(signer().address(), order.accountSigner());
            assertEquals(3, order.signatureType());
            // The pinned v2-deposit-wallet signature only reproduces if the ERC-7739 wrapper
            // domain is the Trading Wallet, so this proves both wallet fields are the wallet's.
            assertEquals(depositWalletVectorSignature(), order.signature());
            assertEquals(0, server.getRequestCount(), "signing reaches no network");
        }

        private SigningContext signingContext(SigningAuthority authority) {
            return SigningContext.of(authority.requireSigningIdentity("sign"),
                    authority.accountSignerKey().orElseThrow(),
                    479249096354L, Instant.ofEpochMilli(1773890758000L));
        }

        /** Reads the vector pinned from an independent ethers implementation by issue #3. */
        private String depositWalletVectorSignature() throws Exception {
            try (InputStream in = getClass().getResourceAsStream("/protocol/signing-vectors.json")) {
                for (JsonNode vector : new ObjectMapper().readTree(in).path("vectors")) {
                    if ("v2-deposit-wallet".equals(vector.path("id").asText())) {
                        // The exchange verifies the whole ERC-7739 envelope, not its inner 65 bytes.
                        return vector.path("wrappedSignature").asText();
                    }
                }
            }
            throw new IllegalStateException("no v2-deposit-wallet vector");
        }
    }

    @Nested
    class Lifecycle {

        @Test
        void shouldReturnTypedCredentialsWhenCreatingApiKey() throws Exception {
            server.enqueue(new MockResponse().setBody(
                    "{\"apiKey\":\"key-1\",\"secret\":\"c2VjcmV0\",\"passphrase\":\"pass-1\"}"));

            try (Polymarket sdk = sdk(localAuthority())) {
                ApiCredentials credentials = sdk.authentication().createApiKey();
                assertEquals("key-1", credentials.key());
                assertEquals("pass-1", credentials.passphrase());
            }

            RecordedRequest request = server.takeRequest();
            assertEquals("POST", request.getMethod());
            assertEquals("/auth/api-key", request.getPath());
            assertEquals(signer().address(), request.getHeader("POLY_ADDRESS"));
            assertEquals("1773890758", request.getHeader("POLY_TIMESTAMP"));
            assertEquals("0", request.getHeader("POLY_NONCE"));
            assertTrue(request.getHeader("POLY_SIGNATURE").startsWith("0x"));
        }

        @Test
        void shouldUseDocumentedEndpointWhenDerivingApiKey() throws Exception {
            server.enqueue(new MockResponse().setBody(
                    "{\"apiKey\":\"key-1\",\"secret\":\"c2VjcmV0\",\"passphrase\":\"pass-1\"}"));

            try (Polymarket sdk = sdk(localAuthority())) {
                assertEquals("key-1", sdk.authentication().deriveApiKey().key());
            }

            RecordedRequest request = server.takeRequest();
            assertEquals("GET", request.getMethod());
            assertEquals("/auth/derive-api-key", request.getPath());
        }

        @Test
        void shouldReturnTypedApiKeysWhenListingKeys() throws Exception {
            server.enqueue(new MockResponse().setBody("{\"apiKeys\":[\"key-1\",\"key-2\"]}"));

            try (Polymarket sdk = sdk(localAuthority())) {
                assertEquals(List.of(new ApiKey("key-1"), new ApiKey("key-2")),
                        sdk.authentication().apiKeys());
            }
            assertEquals("/auth/api-keys", server.takeRequest().getPath());
        }

        @Test
        void shouldRedactListedApiKeysWhenRenderingList() throws Exception {
            server.enqueue(new MockResponse().setBody("{\"apiKeys\":[\"key-1\"]}"));

            try (Polymarket sdk = sdk(localAuthority())) {
                List<ApiKey> keys = sdk.authentication().apiKeys();

                assertFalse(keys.toString().contains("key-1"),
                        "a logged listing must not disclose the key");
                assertEquals("key-1", keys.get(0).value(),
                        "the caller can still read the key deliberately");
            }
            server.takeRequest();
        }

        @Test
        void shouldThrowExceptionWhenReadingBlankApiKey() throws Exception {
            assertThrows(IllegalArgumentException.class, () -> new ApiKey(" "));
            assertThrows(NullPointerException.class, () -> new ApiKey(null));

            server.enqueue(new MockResponse().setBody("{\"apiKeys\":[\"key-1\",\"\"]}"));
            try (Polymarket sdk = sdk(localAuthority())) {
                assertThrows(IOException.class, () -> sdk.authentication().apiKeys(),
                        "a blank wire entry is a read failure, not a usable key");
            }
            server.takeRequest();
        }

        @Test
        void shouldReportValidationRejectionAsDataWhenCredentialsAreRejected() throws Exception {
            server.enqueue(new MockResponse().setResponseCode(401));

            try (Polymarket sdk = sdk(localAuthority().withApiCredentials(creds()))) {
                ApiKeyValidation validation = sdk.authentication().validate();
                assertFalse(validation.valid());
                assertEquals("HTTP 401", validation.detail().orElseThrow());
            }
        }

        @Test
        void shouldAcceptCredentialsWhenValidationSucceeds() throws Exception {
            server.enqueue(new MockResponse().setBody("{\"closed_only\":false}"));

            try (Polymarket sdk = sdk(localAuthority().withApiCredentials(creds()))) {
                assertTrue(sdk.authentication().validate().valid());
            }

            RecordedRequest request = server.takeRequest();
            assertEquals("/auth/ban-status/closed-only", request.getPath());
            assertEquals(creds().key(), request.getHeader("POLY_API_KEY"));
            assertEquals(creds().passphrase(), request.getHeader("POLY_PASSPHRASE"));
            assertEquals(signer().address(), request.getHeader("POLY_ADDRESS"));
        }

        @Test
        void shouldThrowIOExceptionWhenCredentialResponseIsIncomplete() {
            List<String> incomplete = List.of(
                    "{\"secret\":\"c2VjcmV0\",\"passphrase\":\"pass-1\"}",
                    "{\"apiKey\":\"key-1\",\"secret\":null,\"passphrase\":\"pass-1\"}",
                    "{\"apiKey\":\"key-1\",\"secret\":\"c2VjcmV0\",\"passphrase\":\"   \"}");
            incomplete.forEach(body -> server.enqueue(new MockResponse().setBody(body)));

            try (Polymarket sdk = sdk(localAuthority())) {
                for (String ignored : incomplete) {
                    assertThrows(IOException.class, () -> sdk.authentication().createApiKey());
                }
            }
        }

        @Test
        void shouldReturnTypedDeletionWhenDeletingApiKey() throws Exception {
            server.enqueue(new MockResponse().setBody("OK"));

            try (Polymarket sdk = sdk(localAuthority().withApiCredentials(creds()))) {
                ApiKeyDeletion deletion = sdk.authentication().deleteApiKey();
                assertTrue(deletion.deleted());
            }

            assertEquals(1, server.getRequestCount());
            RecordedRequest request = server.takeRequest();
            assertEquals("DELETE", request.getMethod());
            assertEquals("/auth/api-key", request.getPath());
        }
    }
}
