package com.armanmaurya.internetradio.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.media3.common.util.BitmapLoader
import coil3.BitmapImage
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CoilBitmapLoader(private val context: Context) : BitmapLoader {
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun supportsMimeType(mimeType: String): Boolean = true

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        future.setException(UnsupportedOperationException("decodeBitmap not implemented"))
        return future
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()
        scope.launch {
            try {
                var targetUrl = uri.toString()
                if (targetUrl.startsWith("content://") && targetUrl.contains(".svgproxy/")) {
                    val base64 = uri.lastPathSegment
                    if (base64 != null) {
                        targetUrl = String(android.util.Base64.decode(base64, android.util.Base64.URL_SAFE))
                    }
                }

                val request = ImageRequest.Builder(context)
                    .data(targetUrl)
                    .size(512) // Optimal size for notifications
                    .build()
                val result = context.imageLoader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = (result.image as? BitmapImage)?.bitmap 
                        ?: (result.image.asDrawable(context.resources) as? BitmapDrawable)?.bitmap
                    
                    if (bitmap != null) {
                        future.set(bitmap)
                    } else {
                        future.setException(IllegalArgumentException("Unsupported image type"))
                    }
                } else {
                    future.setException(Exception("Failed to load image"))
                }
            } catch (e: Exception) {
                future.setException(e)
            }
        }
        return future
    }
}
