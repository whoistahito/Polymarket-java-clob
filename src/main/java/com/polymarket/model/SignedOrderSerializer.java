package com.polymarket.model;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;

/**
 * Emits the V1 or V2 wire shape for a {@link SignedOrder} based on its resolved protocol version,
 * mirroring the Rust {@code OrderV1WithSignature} / {@code OrderV2WithSignature} serializers in
 * {@code rs-clob-client/src/clob/types/mod.rs}.
 *
 * <p>V1 shape: {@code salt, maker, signer, taker, tokenId, makerAmount, takerAmount, expiration,
 * nonce, feeRateBps, side, signatureType, signature}.
 *
 * <p>V2 shape: {@code salt, maker, signer, tokenId, makerAmount, takerAmount, side, expiration,
 * signatureType, timestamp, metadata, builder, signature} — {@code taker}/{@code nonce}/
 * {@code feeRateBps} are omitted.
 */
public final class SignedOrderSerializer extends JsonSerializer<SignedOrder> {

    @Override
    public void serialize(SignedOrder o, JsonGenerator g, SerializerProvider sp) throws IOException {
        if (o.resolvedVersion() == 1) {
            serializeV1(o, g);
        } else {
            serializeV2(o, g);
        }
    }

    private static void serializeV1(SignedOrder o, JsonGenerator g) throws IOException {
        g.writeStartObject();
        g.writeNumberField("salt", o.salt());
        writeString(g, "maker", o.maker());
        writeString(g, "signer", o.signer());
        writeString(g, "taker", o.taker());
        writeString(g, "tokenId", o.tokenId());
        writeString(g, "makerAmount", o.makerAmount());
        writeString(g, "takerAmount", o.takerAmount());
        writeString(g, "expiration", o.expiration());
        writeString(g, "nonce", o.nonce());
        writeString(g, "feeRateBps", o.feeRateBps());
        g.writeFieldName("side"); g.writeObject(o.side());
        g.writeFieldName("signatureType"); g.writeObject(o.signatureType());
        writeString(g, "signature", o.signature());
        g.writeEndObject();
    }

    private static void serializeV2(SignedOrder o, JsonGenerator g) throws IOException {
        g.writeStartObject();
        g.writeNumberField("salt", o.salt());
        writeString(g, "maker", o.maker());
        writeString(g, "signer", o.signer());
        writeString(g, "tokenId", o.tokenId());
        writeString(g, "makerAmount", o.makerAmount());
        writeString(g, "takerAmount", o.takerAmount());
        g.writeFieldName("side"); g.writeObject(o.side());
        writeString(g, "expiration", o.expiration());
        g.writeFieldName("signatureType"); g.writeObject(o.signatureType());
        writeString(g, "timestamp", o.timestamp());
        writeString(g, "metadata", o.metadata());
        writeString(g, "builder", o.builderCode());
        writeString(g, "signature", o.signature());
        g.writeEndObject();
    }

    private static void writeString(JsonGenerator g, String field, String value) throws IOException {
        if (value == null) {
            g.writeNullField(field);
        } else {
            g.writeStringField(field, value);
        }
    }
}