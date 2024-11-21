package com.icm.igosafeapp

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Shader
import com.squareup.picasso.Transformation

class CircleTransform(
    private val targetSize: Int = 100,
    private val borderColor: Int = Color.WHITE,
    private val borderWidth: Float = 4f
) : Transformation {
    override fun transform(source: Bitmap): Bitmap {
        val size = Math.min(source.width, source.height)

        val x = (source.width - size) / 2
        val y = (source.height - size) / 2

        val squaredBitmap = Bitmap.createBitmap(source, x, y, size, size)

        if (squaredBitmap != source) {
            source.recycle()
        }

        val scaledBitmap = Bitmap.createScaledBitmap(squaredBitmap, targetSize, targetSize, true)
        squaredBitmap.recycle()

        val result = Bitmap.createBitmap(
            (targetSize + borderWidth * 2).toInt(),
            (targetSize + borderWidth * 2).toInt(),
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(result)
        val paint = Paint()
        paint.isAntiAlias = true

        paint.color = borderColor
        val radiusWithBorder = (targetSize + borderWidth * 2) / 2f
        canvas.drawCircle(radiusWithBorder, radiusWithBorder, radiusWithBorder, paint)

        val shader = BitmapShader(scaledBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        paint.shader = shader
        val radius = targetSize / 2f
        canvas.drawCircle(radiusWithBorder, radiusWithBorder, radius, paint)

        scaledBitmap.recycle()

        return result
    }

    override fun key(): String {
        return "circle-$targetSize-$borderColor-$borderWidth"
    }
}


