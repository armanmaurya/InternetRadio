package com.armanmaurya.internetradio.player

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Base64
import coil3.BitmapImage
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SvgProxyProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val urlBase64 = uri.lastPathSegment ?: return null
        val originalUrl = String(Base64.decode(urlBase64, Base64.URL_SAFE))

        val pipe = ParcelFileDescriptor.createReliablePipe()
        val readSide = pipe[0]
        val writeSide = pipe[1]

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val ctx = context ?: return@launch
                val request = ImageRequest.Builder(ctx)
                    .data(originalUrl)
                    .size(256) // Perfect size for grid view
                    .build()

                val result = ctx.imageLoader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = (result.image as? BitmapImage)?.bitmap
                        ?: (result.image.asDrawable(ctx.resources) as? BitmapDrawable)?.bitmap

                    if (bitmap != null) {
                        ParcelFileDescriptor.AutoCloseOutputStream(writeSide).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }
                    } else {
                        writeSide.closeWithError("Image is not a bitmap")
                    }
                } else {
                    writeSide.closeWithError("Failed to load SVG")
                }
            } catch (e: Exception) {
                writeSide.closeWithError(e.message)
            }
        }
        return readSide
    }

    // Required overrides (unused)
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String = "image/png"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    
    companion object {
        fun createProxyUri(context: android.content.Context, svgUrl: String): String {
            val authority = "${context.packageName}.svgproxy"
            val encoded = Base64.encodeToString(svgUrl.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP)
            return "content://$authority/$encoded"
        }
    }
}
