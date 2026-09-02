package com.veil.sbsbrowser

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.webkit.WebView

class MasterWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : WebView(context, attrs) {

    var mirror: MirrorView? = null

    override fun invalidate() {
        super.invalidate()
        mirror?.postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        mirror?.postInvalidateOnAnimation()
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        mirror?.postInvalidateOnAnimation()
    }
}
