package com.polymarket;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Guards issue #7: the SDK authorizes routed API requests and never broadcasts a Polygon transaction. */
class DirectChainSurfaceTest {

    @ParameterizedTest(name = "TC-DC-001: {0} is absent")
    @ValueSource(strings = {
        "com.polymarket.ctf.CtfClient",
        "com.polymarket.ctf.CtfException",
        "com.polymarket.ctf.ConditionIdRequest",
        "com.polymarket.ctf.CollectionIdRequest",
        "com.polymarket.ctf.PositionIdRequest",
        "com.polymarket.ctf.SplitPositionRequest",
        "com.polymarket.ctf.MergePositionsRequest",
        "com.polymarket.ctf.RedeemPositionsRequest",
        "com.polymarket.ctf.RedeemNegRiskRequest"
    })
    void shouldThrowClassNotFoundExceptionWhenLoadingDirectChainCtfClass(String className) {
        assertThrows(ClassNotFoundException.class, () -> Class.forName(className));
    }

    @ParameterizedTest(name = "TC-DC-002: {0} is off the classpath")
    @ValueSource(strings = {
        "org.web3j.protocol.Web3j",
        "org.web3j.protocol.http.HttpService",
        "org.web3j.tx.RawTransactionManager",
        "org.web3j.protocol.core.methods.response.EthGetTransactionReceipt"
    })
    void shouldThrowClassNotFoundExceptionWhenLoadingWeb3jProtocolClass(String className) {
        assertThrows(ClassNotFoundException.class, () -> Class.forName(className));
    }
}
