package com.abhimankolte.aethertube.common.settings

/**
 * The wire format for a portable settings code: a short string that carries a device's whole
 * configuration so it can be typed into another TV instead of re-entering forty settings by remote.
 *
 * ```
 *   60 bits total  ->  12 characters  ->  "A7K2M-9QT4X-B3NP"  (displayed in groups of four)
 *
 *   [ 4 bits ] format version
 *   [48 bits ] payload, laid out by SettingsRegistry
 *   [ 8 bits ] CRC-8
 * ```
 *
 * ### Why a fixed layout rather than tagged fields
 *
 * At this size every bit is contested, and a field tag costs more than the value it labels. The
 * layout is instead positional and **append-only**: [SettingsRegistry] declares fields in a fixed
 * order and new ones may only be added at the end. That gives compatibility in both directions
 * without spending a single bit on metadata:
 *
 *  - a newer app reading an older code runs out of bits early and leaves the settings it did not
 *    find alone;
 *  - an older app reading a newer code stops at the end of the layout it knows and ignores the rest.
 *
 * The version nibble exists for the one thing that discipline cannot absorb - a change to the
 * *meaning* or width of an existing field. If that ever becomes necessary, bump the version and keep
 * a decoder for the old one; codes already written down stay valid.
 *
 * ### Alphabet
 *
 * Crockford's Base32. It excludes I, L, O and U, and treats `I`/`L` as `1` and `O` as `0` on input,
 * so the two mistakes people actually make reading a code off a screen decode correctly rather than
 * failing. Case-insensitive, because entering capitals on a TV remote is its own small misery.
 *
 * Base62 would fit 71 bits into the same twelve characters, but only by being case-sensitive, which
 * on a D-pad keyboard costs the user far more than the extra bits are worth.
 *
 * ### Integrity
 *
 * CRC-8 over the version and payload, which rejects roughly 255 of every 256 corrupted inputs. Eight
 * bits of a sixty-bit budget is a lot to spend on a checksum, and it is spent deliberately: the
 * failure it prevents is silently applying somebody else's - or a garbled - configuration, which is
 * both hard to notice and annoying to undo. A code that fails its checksum is rejected outright.
 */
object SettingsCode {
    /** Bumped only if an existing field changes width or meaning. Adding fields does not need it. */
    const val VERSION = 1

    const val VERSION_BITS = 4
    const val PAYLOAD_BITS = 48
    const val CRC_BITS = 8

    private const val TOTAL_BITS = VERSION_BITS + PAYLOAD_BITS + CRC_BITS

    /** 60 bits / 5 bits per character. */
    const val LENGTH = TOTAL_BITS / 5

    private const val GROUP = 4

    /** Crockford Base32: no I, L, O or U. */
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    private val DECODE: Map<Char, Int> = buildMap {
        ALPHABET.forEachIndexed { index, c -> put(c, index) }
        // Crockford's confusables: what people read off a screen should decode as what was meant.
        put('I', 1)
        put('L', 1)
        put('O', 0)
    }

    /** Thrown for anything the user could plausibly have mistyped, with a message worth showing. */
    class InvalidCodeException(message: String) : Exception(message)

    /**
     * Renders the current configuration as a code. Deterministic: the same settings always produce
     * the same string, on any device.
     */
    fun encode(payload: Long): String {
        require(payload ushr PAYLOAD_BITS == 0L) { "payload exceeds $PAYLOAD_BITS bits" }

        val body = (VERSION.toLong() shl PAYLOAD_BITS) or payload
        val value = (body shl CRC_BITS) or crc8(body).toLong()

        val chars = CharArray(LENGTH)
        for (i in LENGTH - 1 downTo 0) {
            chars[i] = ALPHABET[((value ushr ((LENGTH - 1 - i) * 5)) and 0x1F).toInt()]
        }

        return chars.concatToString().chunked(GROUP).joinToString("-")
    }

    /**
     * Parses a code back to its payload. Hyphens, spaces and case are all ignored, so a code can be
     * typed however it is easiest.
     *
     * @throws InvalidCodeException if the length, alphabet, checksum or version is wrong.
     */
    // @Throws so Java callers can catch it specifically - Kotlin exceptions are unchecked, so
    // without this the Java compiler rejects the catch block as unreachable.
    @Throws(InvalidCodeException::class)
    fun decode(input: String): Long {
        val cleaned = input.filterNot { it == '-' || it.isWhitespace() }.uppercase()

        if (cleaned.length != LENGTH) {
            throw InvalidCodeException("A settings code is $LENGTH characters; this one has ${cleaned.length}.")
        }

        var value = 0L
        for (c in cleaned) {
            val digit = DECODE[c] ?: throw InvalidCodeException("'$c' isn't a valid character in a settings code.")
            value = (value shl 5) or digit.toLong()
        }

        val body = value ushr CRC_BITS
        val checksum = (value and 0xFF).toInt()

        if (crc8(body) != checksum) {
            throw InvalidCodeException("That code doesn't look right - check for a mistyped character.")
        }

        val version = (body ushr PAYLOAD_BITS).toInt()

        if (version > VERSION) {
            throw InvalidCodeException("This code was made by a newer version of AetherTube.")
        }

        return body and ((1L shl PAYLOAD_BITS) - 1)
    }

    /**
     * CRC-8/ATM (polynomial 0x07) over the six bytes of version+payload, most significant first.
     */
    private fun crc8(body: Long): Int {
        var crc = 0

        for (byteIndex in 5 downTo 0) {
            crc = crc xor ((body ushr (byteIndex * 8)) and 0xFF).toInt()

            repeat(8) {
                crc = if (crc and 0x80 != 0) ((crc shl 1) xor 0x07) and 0xFF else (crc shl 1) and 0xFF
            }
        }

        return crc
    }
}

/**
 * Writes fields into a fixed-width bit payload, most significant bit first.
 *
 * Silently ignores anything written past [SettingsCode.PAYLOAD_BITS] rather than throwing, which is
 * what makes the append-only rule safe: a registry that has grown past the budget still produces a
 * valid code for the fields that fit. [SettingsRegistry] has a test asserting the total stays within
 * budget, so overflow is caught at build time rather than discovered here.
 */
class BitWriter {
    private var value = 0L
    private var used = 0

    fun write(bits: Int, raw: Int) {
        if (used + bits > SettingsCode.PAYLOAD_BITS) {
            used += bits
            return
        }

        val masked = raw.toLong() and ((1L shl bits) - 1)
        value = (value shl bits) or masked
        used += bits
    }

    /** Left-aligned in the payload, so trailing unused bits are zero and the layout can grow. */
    fun payload(): Long = value shl (SettingsCode.PAYLOAD_BITS - used).coerceAtLeast(0)

    fun bitsUsed(): Int = used
}

/**
 * Reads a payload written by [BitWriter].
 *
 * Returns null once the stream is exhausted, which is how an older code decodes on a newer app: the
 * fields added since simply report "not present" and the caller leaves them at whatever the device
 * already has.
 */
class BitReader(payload: Long, private val availableBits: Int = SettingsCode.PAYLOAD_BITS) {
    private var value = payload
    private var consumed = 0

    fun read(bits: Int): Int? {
        if (consumed + bits > availableBits) {
            return null
        }

        val shift = SettingsCode.PAYLOAD_BITS - consumed - bits
        val result = ((value ushr shift) and ((1L shl bits) - 1)).toInt()
        consumed += bits
        return result
    }
}
