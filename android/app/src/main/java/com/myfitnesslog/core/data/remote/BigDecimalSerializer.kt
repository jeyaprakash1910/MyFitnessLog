package com.myfitnesslog.core.data.remote

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import java.math.BigDecimal

/**
 * Serializes [BigDecimal] as an unquoted JSON **number**, preserving scale.
 *
 * The backend's weight/rpe/rir are `BigDecimal` mapped to Postgres `NUMERIC`, so
 * precision must survive the wire exactly — routing through Double would silently
 * corrupt logged weights. The value is emitted with [JsonUnquotedLiteral] from
 * [BigDecimal.toPlainString], which keeps both the digits and the scale (`60.0`
 * stays `60.0`, never `60` or `60.000000001`), and is read back from the raw
 * token text rather than through any floating-point step.
 *
 * The descriptor is declared as STRING only because kotlinx-serialization has no
 * arbitrary-precision primitive kind; the emitted token is a bare number. Outside
 * a JSON encoder (no such path today) it degrades gracefully to a quoted string.
 */
@OptIn(ExperimentalSerializationApi::class)
object BigDecimalSerializer : KSerializer<BigDecimal> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("java.math.BigDecimal", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: BigDecimal) {
        val text = value.toPlainString()
        if (encoder is JsonEncoder) {
            encoder.encodeJsonElement(JsonUnquotedLiteral(text))
        } else {
            encoder.encodeString(text)
        }
    }

    override fun deserialize(decoder: Decoder): BigDecimal =
        if (decoder is JsonDecoder) {
            // `content` is the literal token text, so an unquoted number and a
            // quoted string both decode without losing digits.
            BigDecimal((decoder.decodeJsonElement() as JsonPrimitive).content)
        } else {
            BigDecimal(decoder.decodeString())
        }
}
