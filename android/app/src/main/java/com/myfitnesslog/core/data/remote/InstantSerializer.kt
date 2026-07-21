package com.myfitnesslog.core.data.remote

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant

/**
 * Serializes [Instant] as an ISO-8601 string, matching the backend contract.
 *
 * The backend serializes `java.time.Instant` through Jackson's JSR-310 module
 * with `WRITE_DATES_AS_TIMESTAMPS` disabled (the Spring Boot default), which
 * produces the same ISO-8601 form as [Instant.toString] — e.g.
 * `2026-07-21T10:15:30Z`. [Instant.parse] accepts every variant Jackson emits
 * (with or without fractional seconds), so the round trip is exact.
 *
 * kotlinx-serialization has no built-in Instant serializer, so timestamps must
 * go through this one explicitly (`@Serializable(with = InstantSerializer::class)`).
 */
object InstantSerializer : KSerializer<Instant> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("java.time.Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Instant =
        Instant.parse(decoder.decodeString())
}
