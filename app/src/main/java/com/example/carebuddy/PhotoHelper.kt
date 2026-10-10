package com.example.carebuddy

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream

object PhotoHelper {

    // ===== REQUEST CODES =====
    const val REQUEST_PICK_IMAGE = 500
    const val REQUEST_CAPTURE_IMAGE = 501

    /**
     * Launch gallery picker.
     */
    fun openGallery(activity: Activity) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        activity.startActivityForResult(intent, REQUEST_PICK_IMAGE)
    }

    /**
     * Launch camera.
     */
    fun openCamera(activity: Activity) {
        val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
        activity.startActivityForResult(intent, REQUEST_CAPTURE_IMAGE)
    }

    /**
     * Decode a Uri (from gallery) into a Bitmap, applying EXIF rotation.
     */
    fun bitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return null
            val bitmap = BitmapFactory.decodeStream(input)
            input.close()

            // Handle EXIF rotation
            val input2 = context.contentResolver.openInputStream(uri)
            val exif = input2?.let { ExifInterface(it) }
            input2?.close()

            val rotation = when (exif?.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }

            if (rotation != 0f) rotateBitmap(bitmap, rotation) else bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun rotateBitmap(source: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    /**
     * Compress a Bitmap to JPEG and encode as Base64 string.
     * Scales down so the longest edge is ≤ maxSize.
     * Reduces quality until byte size ≤ maxBytes.
     */
    fun bitmapToBase64(bitmap: Bitmap, maxSize: Int = 400, maxBytes: Int = 200_000): String? {
        return try {
            val scaled = scaleBitmap(bitmap, maxSize)
            val output = ByteArrayOutputStream()

            var quality = 75
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, output)

            while (output.size() > maxBytes && quality > 20) {
                output.reset()
                quality -= 10
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, output)
            }

            Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun scaleBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxSize && h <= maxSize) return bitmap

        val ratio = w.toFloat() / h.toFloat()
        val newW: Int
        val newH: Int
        if (w > h) {
            newW = maxSize
            newH = (maxSize / ratio).toInt()
        } else {
            newH = maxSize
            newW = (maxSize * ratio).toInt()
        }
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }

    /**
     * Decode a Base64 string back into a Bitmap.
     */
    fun base64ToBitmap(base64: String): Bitmap? {
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}