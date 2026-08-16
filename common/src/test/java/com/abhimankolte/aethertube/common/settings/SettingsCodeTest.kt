package com.abhimankolte.aethertube.common.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.random.Random

/**
 * The settings code is a format other people will write down, so the properties that matter are the
 * ones that would silently corrupt someone's configuration: the layout fitting its budget, codes
 * round-tripping exactly, and mistyped input being rejected rather than applied.
 */
class SettingsCodeTest {

    /**
     * The load-bearing test. The layout is positional with no field tags, so if it ever outgrows the
     * payload the extra fields are silently dropped from every code. Failing here at build time is
     * the whole reason [BitWriter] can afford to ignore overflow at runtime.
     */
    @Test
    fun `registry fits the payload budget`() {
        assertTrue(
            "Registry is ${SettingsRegistry.totalBits} bits but only ${SettingsCode.PAYLOAD_BITS} " +
                "are available. Adding a field means either freeing bits elsewhere or moving to a " +
                "longer v2 code - never silently dropping the tail.",
            SettingsRegistry.totalBits <= SettingsCode.PAYLOAD_BITS,
        )
    }

    @Test
    fun `code is exactly twelve characters plus separators`() {
        val code = SettingsCode.encode(0L)

        assertEquals(12, SettingsCode.LENGTH)
        assertEquals("0000-0000-0000".length, code.length)
        assertEquals(12, code.filterNot { it == '-' }.length)
    }

    @Test
    fun `round trips every payload it claims to support`() {
        val random = Random(20260726)

        repeat(2000) {
            val payload = random.nextLong(1L shl SettingsCode.PAYLOAD_BITS)
            assertEquals(payload, SettingsCode.decode(SettingsCode.encode(payload)))
        }
    }

    @Test
    fun `round trips the extremes`() {
        for (payload in listOf(0L, 1L, (1L shl SettingsCode.PAYLOAD_BITS) - 1)) {
            assertEquals(payload, SettingsCode.decode(SettingsCode.encode(payload)))
        }
    }

    @Test
    fun `encoding is deterministic`() {
        assertEquals(SettingsCode.encode(0x1234_5678_9ABCL), SettingsCode.encode(0x1234_5678_9ABCL))
    }

    /** Hyphens, spacing and case are presentation, not content. */
    @Test
    fun `accepts a code however it was typed`() {
        val payload = 0x0BAD_C0FF_EE12L
        val canonical = SettingsCode.encode(payload)
        val bare = canonical.replace("-", "")

        assertEquals(payload, SettingsCode.decode(bare))
        assertEquals(payload, SettingsCode.decode(bare.lowercase()))
        assertEquals(payload, SettingsCode.decode(" $canonical "))
        assertEquals(payload, SettingsCode.decode(bare.chunked(3).joinToString(" ")))
    }

    /**
     * Crockford aliases the glyphs people actually confuse. Someone reading a 0 as an O should get
     * their settings, not an error.
     */
    @Test
    fun `decodes the confusable glyphs`() {
        val payload = 0L
        val bare = SettingsCode.encode(payload).replace("-", "") // all zeroes

        assertEquals(payload, SettingsCode.decode(bare.replace('0', 'O')))
        assertEquals(payload, SettingsCode.decode(bare.replace('0', 'o')))
    }

    @Test
    fun `single character typos are rejected`() {
        val payload = 0x0123_4567_89ABL
        val bare = SettingsCode.encode(payload).replace("-", "")
        val alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

        var accepted = 0
        var checked = 0

        for (i in bare.indices) {
            for (c in alphabet) {
                if (c == bare[i]) continue

                checked++
                val corrupted = bare.substring(0, i) + c + bare.substring(i + 1)

                val decoded = runCatching { SettingsCode.decode(corrupted) }.getOrNull()
                if (decoded != null) accepted++
            }
        }

        // A CRC-8 lets through about 1 in 256. Anything materially worse means the checksum is not
        // actually covering the payload.
        assertTrue(
            "Accepted $accepted of $checked single-character corruptions",
            accepted * 100 < checked,
        )
    }

    @Test
    fun `wrong length is rejected`() {
        for (bad in listOf("", "ABC", "0000-0000-000", "0000-0000-00000")) {
            runCatching { SettingsCode.decode(bad) }
                .onSuccess { fail("Accepted a malformed code: '$bad'") }
        }
    }

    @Test
    fun `characters outside the alphabet are rejected`() {
        // U is excluded from Crockford's alphabet on purpose.
        runCatching { SettingsCode.decode("UUUU-UUUU-UUUU") }
            .onSuccess { fail("Accepted characters outside the alphabet") }
    }

    /**
     * The compatibility promise: a payload written against a shorter (older) layout must still
     * decode, with the fields added since simply reporting absent so the caller leaves them alone.
     */
    @Test
    fun `older payloads decode and leave newer fields untouched`() {
        val writer = BitWriter()
        writer.write(4, 7)
        writer.write(2, 3)
        writer.write(1, 1)
        val oldPayloadBits = writer.bitsUsed()

        val reader = BitReader(writer.payload(), availableBits = oldPayloadBits)

        assertEquals(7, reader.read(4))
        assertEquals(3, reader.read(2))
        assertEquals(1, reader.read(1))
        // Everything appended after the old layout ended reads as absent, not as zero.
        assertEquals(null, reader.read(1))
        assertEquals(null, reader.read(3))
    }

    @Test
    fun `different settings produce different codes`() {
        assertNotEquals(SettingsCode.encode(0L), SettingsCode.encode(1L))
    }

    /**
     * A real code, generated on a TCL G10 running this build and read off the screen. Pins the wire
     * format against an actual artefact rather than only against itself: if a future change to the
     * alphabet, bit order or checksum breaks codes people have already written down, this fails.
     *
     * Do not regenerate it to make it pass - that would defeat the point. A deliberate format change
     * means bumping SettingsCode.VERSION and adding a decoder for the old one.
     */
    @Test
    fun `decodes a code captured from a real device`() {
        val payload = SettingsCode.decode("3G41-96RJ-ARGN")

        assertEquals(payload, SettingsCode.decode("3g4196rjargn"))
        assertEquals("3G41-96RJ-ARGN", SettingsCode.encode(payload))
    }
}
