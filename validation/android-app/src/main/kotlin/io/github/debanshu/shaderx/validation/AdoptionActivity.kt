package io.github.debanshu.shaderx.validation

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.debanshu.shaderlab.shaderx.effect.impl.NativeBlurEffect
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.factory.*
import com.debanshu.shaderlab.shaderx.effect.impl.GrayscaleEffect
import com.debanshu.shaderlab.shaderx.result.ShaderResult

/** Small consumer startup/R8 probe; factory pixels are covered separately by device tests. */
class AdoptionActivity : Activity() {
    private val factory = ShaderFactory.create()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val result = factory.createRenderEffect(NativeBlurEffect(12f), 256f, 256f)
        check(result is ShaderResult.Success) { "Blur creation failed: $result" }
        if (android.os.Build.VERSION.SDK_INT < 33) {
            val preserved = factory.createRenderEffectWithFallback(GrayscaleEffect(), 256f, 256f)
            check(preserved.fallbackUsed && preserved.result.getOrThrow() == null)
            check(preserved.cause is com.debanshu.shaderlab.shaderx.result.ShaderError.PlatformNotSupported)
        }
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        layout.addView(TextView(this).apply {
            text = "ShaderX API ${android.os.Build.VERSION.SDK_INT}: native blur OK"
            textSize = 20f
        })
        layout.addView(object : View(this) {
            private val paint = Paint()
            init { setRenderEffect(result.value.asAndroidRenderEffect()) }
            override fun onDraw(canvas: Canvas) {
                for (row in 0..7) for (column in 0..7) {
                    paint.color = if ((row + column) % 2 == 0) Color.RED else Color.BLUE
                    canvas.drawRect(column * width / 8f, row * height / 8f,
                        (column + 1) * width / 8f, (row + 1) * height / 8f, paint)
                }
            }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 512))
        setContentView(layout)
    }

    override fun onDestroy() {
        factory.close()
        super.onDestroy()
    }
}
