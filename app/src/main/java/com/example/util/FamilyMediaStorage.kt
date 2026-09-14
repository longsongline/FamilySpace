package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object FamilyMediaStorage {
    private const val TAG = "FamilyMediaStorage"
    private const val MEDIA_FOLDER = "family_media"

    /**
     * Get or create the local private media directory
     */
    fun getMediaDir(context: Context): File {
        val dir = File(context.filesDir, MEDIA_FOLDER)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Copy an external content/file URI to the app's private internal storage
     * This avoids Android permission revocation when the app or device restarts.
     */
    fun copyUriToLocalStorage(context: Context, uri: Uri, isVideo: Boolean): File? {
        return try {
            val extension = if (isVideo) "mp4" else "jpg"
            val file = File(getMediaDir(context), "media_${System.currentTimeMillis()}.$extension")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy URI to local storage", e)
            null
        }
    }

    /**
     * Copy an audio recording / voice file to the app's private storage and measure its duration
     */
    fun copyAudioUriToLocalStorage(context: Context, uri: Uri): Pair<File?, Int> {
        return try {
            val file = File(getMediaDir(context), "audio_${System.currentTimeMillis()}.mp3")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            val durationSec = getMediaDurationSeconds(context, file)
            Pair(file, durationSec)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy audio URI to local storage", e)
            Pair(null, 0)
        }
    }

    /**
     * Measure media file duration in seconds
     */
    fun getMediaDurationSeconds(context: Context, file: File): Int {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val timeStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = timeStr?.toLongOrNull() ?: 0L
            (durationMs / 1000L).toInt().coerceAtLeast(1)
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting media duration", e)
            0
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    /**
     * Extract a thumbnail image from a video file and save locally
     */
    fun extractVideoThumbnail(context: Context, videoFile: File): File? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoFile.absolutePath)
            // Retrieve frame around 1 second
            val bitmap = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
            if (bitmap != null) {
                val thumbFile = File(getMediaDir(context), "thumb_${System.currentTimeMillis()}.jpg")
                FileOutputStream(thumbFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 75, out)
                }
                thumbFile
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting video thumbnail", e)
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    /**
     * Compress an image file to a compact Base64 JPEG string (approx 30-70 KB)
     * for seamless multi-device GitHub Gist syncing.
     */
    fun compressImageToBase64(file: File, maxDimension: Int = 800, quality: Int = 70): String? {
        return try {
            // First decode bounds
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            val origWidth = options.outWidth
            val origHeight = options.outHeight

            if (origWidth <= 0 || origHeight <= 0) return null

            var inSampleSize = 1
            val maxSide = maxOf(origWidth, origHeight)
            while (maxSide / (inSampleSize * 2) >= maxDimension) {
                inSampleSize *= 2
            }

            // Decode bitmap with sample size
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
            }
            val originalBitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return null

            // Scale if still exceeds maxDimension
            val actualW = originalBitmap.width
            val actualH = originalBitmap.height
            val scaledBitmap = if (actualW > maxDimension || actualH > maxDimension) {
                val ratio = minOf(maxDimension.toFloat() / actualW, maxDimension.toFloat() / actualH)
                val targetW = (actualW * ratio).toInt().coerceAtLeast(1)
                val targetH = (actualH * ratio).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(originalBitmap, targetW, targetH, true)
            } else {
                originalBitmap
            }

            val baos = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            val bytes = baos.toByteArray()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Error compressing image to Base64", e)
            null
        }
    }

    /**
     * Safety stub: Do NOT encode full video into Base64 for SQLite storage,
     * as Android SQLite CursorWindow has a strict 2MB limit that crashes the app.
     */
    fun encodeVideoToBase64(file: File, maxSizeLimit: Long = 4_500_000): String? {
        // Return null to ensure no massive video blobs are saved to SQLite table
        return null
    }

    /**
     * Save a received Base64 payload as a local file in app internal storage
     */
    fun saveBase64ToFile(context: Context, base64Str: String, prefix: String, extension: String): File? {
        if (base64Str.isBlank()) return null
        return try {
            val bytes = Base64.decode(base64Str, Base64.DEFAULT)
            val file = File(getMediaDir(context), "${prefix}_${System.currentTimeMillis()}.$extension")
            FileOutputStream(file).use { out ->
                out.write(bytes)
            }
            file
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding Base64 to file", e)
            null
        }
    }
}
