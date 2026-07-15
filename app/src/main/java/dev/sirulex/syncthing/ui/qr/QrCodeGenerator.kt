package dev.sirulex.syncthing.ui.qr

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/**
 * Generates QR code bitmaps for Syncthing device IDs.
 * Uses ZXing core (no zxing-android-embedded needed).
 */
object QrCodeGenerator {

    fun generate(content: String, size: Int = 512): Bitmap {
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        val matrix = QRCodeWriter().encode(
            content, BarcodeFormat.QR_CODE, size, size, hints
        )
        // Build pixels in one IntArray and apply with a single setPixels call.
        // Per-pixel setPixel() was ~50× slower and blocked the main thread on
        // first composition at size=512 (262k calls).
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            val rowOffset = y * size
            for (x in 0 until size) {
                pixels[rowOffset + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565).apply {
            setPixels(pixels, 0, size, 0, 0, size, size)
        }
    }
}
