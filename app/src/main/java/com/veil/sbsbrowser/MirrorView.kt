package com.veil.sbsbrowser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View

class MirrorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var source: View? = null

    var useCapturedFrame: Boolean = false
    private var capturedFrame: Bitmap? = null
    private val destRect = Rect()

    fun setCapturedFrame(bitmap: Bitmap) {
        capturedFrame = bitmap
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (useCapturedFrame) {
            capturedFrame?.let { bmp ->
                destRect.set(0, 0, width, height)
                canvas.drawBitmap(bmp, null, destRect, null)
            }
        } else {
            source?.draw(canvas)
        }
    }
}
