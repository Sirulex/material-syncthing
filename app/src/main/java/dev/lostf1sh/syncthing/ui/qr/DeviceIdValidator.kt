package dev.lostf1sh.syncthing.ui.qr

/**
 * Validates Syncthing device IDs.
 * Format: 8 groups of 7 chars [A-Z2-7], separated by dashes.
 * Each group's 7th character is a base-32 Luhn checksum over the previous six.
 * Example: MFZWI3D-BORSXA-LNFSPM-YFBER-6DY2HT-MVZHQN-Q42XQR-QHGXMH
 */
object DeviceIdValidator {

    private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    private val DEVICE_ID_PATTERN = Regex(
        "^[A-Z2-7]{7}(-[A-Z2-7]{7}){7}$"
    )

    /** Finds a device-ID pattern anywhere in sanitized text. */
    private val EMBEDDED_PATTERN = Regex(
        "[A-Z2-7]{7}(?:-[A-Z2-7]{7}){7}"
    )

    /** Unicode hyphen-like characters users often paste in place of ASCII '-'. */
    private val DASH_LIKE = Regex("[\u2010-\u2015\u2212\uFE58\uFE63\uFF0D]")

    /**
     * Strips whitespace, zero-width chars, and normalizes Unicode dashes to '-'.
     * Does NOT uppercase — callers do that when needed.
     */
    fun sanitize(text: String): String =
        text
            .replace(DASH_LIKE, "-")
            // Remove zero-width joiner, non-joiner, and ZWSP / BOM
            .replace(Regex("[\u200B-\u200D\uFEFF]"), "")
            // Drop every whitespace variant (space, tab, newline, NBSP, …)
            .filter { !it.isWhitespace() }

    /** True if [id] is in the correct format (ignores checksum). */
    fun isValid(id: String): Boolean =
        DEVICE_ID_PATTERN.matches(sanitize(id).uppercase())

    /**
     * Tries to extract a device ID from free-form text (paste, scan, share sheet).
     * Handles case, whitespace, Unicode dashes, and IDs embedded in surrounding text.
     */
    fun extract(text: String): String? {
        val cleaned = sanitize(text).uppercase()
        if (DEVICE_ID_PATTERN.matches(cleaned)) return cleaned
        return EMBEDDED_PATTERN.find(cleaned)?.value
    }

    /**
     * Stricter validation: format + Luhn base-32 checksum on each 7-char group.
     * Syncthing computes a Luhn mod-32 check character over the six data chars
     * of each group; verifying it catches typos the format check cannot.
     */
    fun hasValidChecksum(id: String): Boolean {
        val cleaned = sanitize(id).uppercase()
        if (!DEVICE_ID_PATTERN.matches(cleaned)) return false
        return cleaned.split('-').all { group ->
            val data = group.substring(0, 6)
            val expected = group[6]
            luhn32(data) == expected
        }
    }

    private fun luhn32(data: String): Char {
        var factor = 1
        var sum = 0
        val n = BASE32_ALPHABET.length
        for (i in data.length - 1 downTo 0) {
            val idx = BASE32_ALPHABET.indexOf(data[i])
            if (idx < 0) return '?'
            var addend = factor * idx
            factor = if (factor == 2) 1 else 2
            addend = (addend / n) + (addend % n)
            sum += addend
        }
        val remainder = sum % n
        val checkIdx = (n - remainder) % n
        return BASE32_ALPHABET[checkIdx]
    }
}
