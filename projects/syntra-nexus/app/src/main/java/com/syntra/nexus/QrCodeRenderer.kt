package com.syntra.nexus

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

internal object QrCodeRenderer {
    fun render(content: String, size: Int = 640): Bitmap {
        require(content.isNotBlank()) { "QR content cannot be blank." }
        require(size in 256..1024) { "QR image size must be between 256 and 1024 pixels." }

        val matrix = QRCodeWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            size,
            size,
            mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 2
            )
        )
        val pixels = IntArray(size * size) { index ->
            val x = index % size
            val y = index / size
            if (matrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, size, 0, 0, size, size)
        }
    }
}
