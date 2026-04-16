package dev.lostf1sh.syncthing.ui.qr

/**
 * Validates Syncthing device IDs.
 * Format: 8 groups of 7 chars [A-Z2-7], separated by dashes.
 * Example: MFZWI3D-BORSXA-LNFSPM-YFBER-6DY2HT-MVZHQN-Q42XQR-QHGXMH
 */
object DeviceIdValidator {

    private val DEVICE_ID_PATTERN = Regex(
        "^[A-Z2-7]{7}(-[A-Z2-7]{7}){7}$"
    )

    fun isValid(id: String): Boolean =
        DEVICE_ID_PATTERN.matches(id.trim().uppercase())

    /**
     * Tries to extract a device ID from scanned text.
     * Handles common scan artifacts (whitespace, lowercase).
     */
    fun extract(text: String): String? {
        val cleaned = text.trim().uppercase().replace(" ", "")
        return if (isValid(cleaned)) cleaned else null
    }
}
