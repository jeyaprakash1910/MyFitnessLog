package com.myfitnesslog.core.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * Round-trip tests for the transport serializers.
 *
 * The literal JSON strings here are the contract: they are what the backend
 * (Jackson JSR-310 for Instant, BigDecimal → Postgres NUMERIC for decimals)
 * actually emits and accepts. Asserting on the exact text — not just that
 * encode/decode are inverses — is what catches a format drift.
 */
class SerializersTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Serializable
    private data class InstantHolder(
        @Serializable(with = InstantSerializer::class) val value: Instant,
    )

    @Serializable
    private data class DecimalHolder(
        @Serializable(with = BigDecimalSerializer::class) val value: BigDecimal,
    )

    @Test
    fun `instant encodes as ISO-8601 with a Z offset`() {
        val instant = Instant.parse("2026-07-21T10:15:30Z")
        assertEquals("""{"value":"2026-07-21T10:15:30Z"}""", json.encodeToString(InstantHolder(instant)))
    }

    @Test
    fun `instant round-trips without losing sub-second precision`() {
        val instant = Instant.parse("2026-07-21T10:15:30.123456789Z")
        val encoded = json.encodeToString(InstantHolder(instant))
        assertEquals("""{"value":"2026-07-21T10:15:30.123456789Z"}""", encoded)
        assertEquals(instant, json.decodeFromString<InstantHolder>(encoded).value)
    }

    @Test
    fun `instant decodes the whole-second form Jackson emits`() {
        assertEquals(
            Instant.parse("2026-07-21T10:15:30Z"),
            json.decodeFromString<InstantHolder>("""{"value":"2026-07-21T10:15:30Z"}""").value,
        )
    }

    @Test
    fun `decimal encodes as an unquoted JSON number`() {
        assertEquals("""{"value":100.5}""", json.encodeToString(DecimalHolder(BigDecimal("100.5"))))
    }

    @Test
    fun `decimal preserves trailing-zero scale`() {
        // 60.0 must not collapse to 60 — the backend column is NUMERIC(6,2) and
        // scale is part of the value.
        val encoded = json.encodeToString(DecimalHolder(BigDecimal("60.00")))
        assertEquals("""{"value":60.00}""", encoded)
        val decoded = json.decodeFromString<DecimalHolder>(encoded).value
        assertEquals(BigDecimal("60.00"), decoded)
        assertEquals(2, decoded.scale())
    }

    @Test
    fun `decimal round-trips a value that is not exactly representable as a double`() {
        val value = BigDecimal("142.35")
        val decoded = json.decodeFromString<DecimalHolder>(json.encodeToString(DecimalHolder(value))).value
        assertEquals(value, decoded)
        // Guards against a future refactor routing this through Double.
        assertEquals("142.35", decoded.toPlainString())
    }

    @Test
    fun `decimal decodes a quoted string as well as a bare number`() {
        assertEquals(
            BigDecimal("7.5"),
            json.decodeFromString<DecimalHolder>("""{"value":"7.5"}""").value,
        )
    }

    @Test
    fun `decimal round-trips zero and a large value`() {
        listOf(BigDecimal("0"), BigDecimal("0.00"), BigDecimal("999999.99")).forEach { value ->
            val decoded = json.decodeFromString<DecimalHolder>(
                json.encodeToString(DecimalHolder(value)),
            ).value
            assertEquals(value, decoded)
        }
    }
}
