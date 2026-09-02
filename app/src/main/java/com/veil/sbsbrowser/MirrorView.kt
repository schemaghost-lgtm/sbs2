package com.veil.sbsbrowser

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View

class MirrorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var source: MasterWebView? = null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        source?.draw(canvas)
    }
}
