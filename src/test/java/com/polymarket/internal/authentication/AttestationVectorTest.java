package com.polymarket.internal.authentication;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.polymarket.authentication.PrivateKeySigner;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

/** Protocol-critical L1/L2 vectors are computed independently from the documented algorithms. */
class AttestationVectorTest {

    // Uses the key from src/test/resources/protocol/signing-vectors.json.
    private static final String TEST_KEY =
            "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";
    private static final long TIMESTAMP = 1773890758L;
    private static final int CHAIN_ID = 137;

    private static final String DOMAIN_ENCODE_TYPE =
            "EIP712Domain(string name,string version,uint256 chainId)";
    private static final String CLOB_AUTH_ENCODE_TYPE =
            "ClobAuth(address address,string timestamp,uint256 nonce,string message)";

    @Test
    void shouldSignL1HeadersWhenDigestIsIndependentlyDerived() throws Exception {
        PrivateKeySigner signer = PrivateKeySigner.of(TEST_KEY);
        int nonce = 0;

        Map<String, String> headers = L1Attestation.headers(signer, CHAIN_ID, TIMESTAMP, nonce);

        assertEquals(expectedSignature(nonce), headers.get("POLY_SIGNATURE"));
        assertEquals(signer.address(), headers.get("POLY_ADDRESS"));
        assertEquals(String.valueOf(TIMESTAMP), headers.get("POLY_TIMESTAMP"));
        assertEquals(String.valueOf(nonce), headers.get("POLY_NONCE"));
    }

    /** HMAC-SHA256 uses the documented base64url algorithm from builder-gateway.json. */
    @Test
    void shouldMatchL2SignatureWhenUsingDocumentedAlgorithm() throws Exception {
        String secret = "c2VjcmV0LXNlY3JldC1zZWNyZXQtc2VjcmV0LXNlY3JldA==";

        assertEquals(
                expectedHmac(secret, TIMESTAMP + "GET" + "/auth/api-keys"),
                L2Attestation.sign(secret, TIMESTAMP, "GET", "/auth/api-keys", null));
        assertEquals(
                expectedHmac(secret, TIMESTAMP + "POST" + "/order" + "{\"a\":1}"),
                L2Attestation.sign(secret, TIMESTAMP, "POST", "/order", "{\"a\":1}"));
    }

    private static String expectedHmac(String secret, String message) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(Base64.getUrlDecoder().decode(secret), "HmacSHA256"));
        return Base64.getUrlEncoder()
                .encodeToString(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
    }

    /** EIP-712 is assembled byte-by-byte so encoder changes cannot hide a defect. */
    private static String expectedSignature(int nonce) {
        String address = PrivateKeySigner.of(TEST_KEY).address();

        byte[] domainSeparator = keccakConcat(
                keccak(DOMAIN_ENCODE_TYPE),
                keccak("ClobAuthDomain"),
                keccak("1"),
                uint256(CHAIN_ID));

        byte[] structHash = keccakConcat(
                keccak(CLOB_AUTH_ENCODE_TYPE),
                leftPad(Numeric.hexStringToByteArray(address)),
                keccak(String.valueOf(TIMESTAMP)),
                uint256(nonce),
                keccak(L1Attestation.MESSAGE));

        byte[] preimage = new byte[66];
        preimage[0] = 0x19;
        preimage[1] = 0x01;
        System.arraycopy(domainSeparator, 0, preimage, 2, 32);
        System.arraycopy(structHash, 0, preimage, 34, 32);

        Sign.SignatureData signature = Sign.signMessage(
                Hash.sha3(preimage), Credentials.create(TEST_KEY).getEcKeyPair(), false);

        byte[] combined = new byte[65];
        System.arraycopy(signature.getR(), 0, combined, 0, 32);
        System.arraycopy(signature.getS(), 0, combined, 32, 32);
        combined[64] = signature.getV()[0];
        return Numeric.toHexString(combined);
    }

    private static byte[] keccak(String value) {
        return Hash.sha3(value.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] keccakConcat(byte[]... words) {
        byte[] buffer = new byte[words.length * 32];
        for (int i = 0; i < words.length; i++) {
            System.arraycopy(words[i], 0, buffer, i * 32, 32);
        }
        return Hash.sha3(buffer);
    }

    private static byte[] uint256(long value) {
        byte[] word = new byte[32];
        for (int i = 0; i < 8; i++) {
            word[31 - i] = (byte) (value >>> (8 * i));
        }
        return word;
    }

    private static byte[] leftPad(byte[] address20) {
        byte[] word = new byte[32];
        System.arraycopy(address20, 0, word, 12, 20);
        return word;
    }
}
