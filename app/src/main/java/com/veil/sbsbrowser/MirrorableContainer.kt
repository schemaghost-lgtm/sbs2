package com.veil.sbsbrowser

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.widget.FrameLayout

class MirrorableContainer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    var mirror: MirrorView? = null

    override fun invalidate() {
        super.invalidate()
        mirror?.postInvalidateOnAnimation()
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        mirror?.postInvalidateOnAnimation()
    }
}
