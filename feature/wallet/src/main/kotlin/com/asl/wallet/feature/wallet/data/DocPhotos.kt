package com.asl.wallet.feature.wallet.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File

/** Shares only the private wallet_docs cache folder with the camera app. Its own class so it can sit beside other FileProviders. */
class WalletFileProvider : FileProvider()

/**
 * Bank-account document photos (PAN, passbook). Camera photos go to the private cache, never the gallery; every
 * photo is downscaled and re-encoded as JPEG (which also drops EXIF such as location) and the original is deleted.
 */
object DocPhotos {
    const val DIR = "wallet_docs"
    const val MAX_BYTES = 2_000_000
    const val AUTHORITY_SUFFIX = ".walletfiles"

    fun newFile(context: Context, name: String): File = File(File(context.cacheDir, DIR).apply { mkdirs() }, "$name-${System.nanoTime()}.jpg")

    fun uriFor(context: Context, file: File): Uri = FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file)

    fun wipeAll(context: Context) { File(context.cacheDir, DIR).listFiles()?.forEach { it.delete() } }

    fun fromFile(file: File): ByteArray? = try { decode { BitmapFactory.decodeFile(file.path, it) } } finally { file.delete() }

    /** A picked gallery image, read through the content resolver (no storage permission). */
    fun fromUri(context: Context, uri: Uri): ByteArray? = runCatching {
        decode { o -> context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) } }
    }.getOrNull()

    private fun decode(read: (BitmapFactory.Options) -> Bitmap?): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        read(bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
        val bmp = read(BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        var q = 85
        var out: ByteArray
        do {
            out = ByteArrayOutputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, q, it); it.toByteArray() }
            q -= 10
        } while (out.size > MAX_BYTES && q >= 35)
        bmp.recycle()
        return out.takeIf { it.size <= MAX_BYTES }
    }
}
