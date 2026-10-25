package dev.luma.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader

/** Makes a compact landscape card from Stremio's published poster and title logo. */
internal object StremioTitleCard {
    private val imdbId=Regex("tt[0-9]{7,9}")

    fun load(images:TraktImageCache,posterUrl:String,openUri:String):Bitmap? {
        val poster=images.load(posterUrl,480)?:return null
        val id=imdbId.find(openUri)?.value?:return poster
        val logo=images.load("https://images.metahub.space/logo/medium/$id/img",480)?:return poster
        val width=480
        val height=270
        val output=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(output)
        canvas.drawColor(0xFF101923.toInt())
        canvas.drawBitmap(poster,null,RectF(0f,0f,width.toFloat(),height.toFloat()),
            Paint(Paint.FILTER_BITMAP_FLAG).apply{alpha=70})
        canvas.drawColor(0xA6101923.toInt())

        val posterWidth=(height.toFloat()*poster.width/poster.height).coerceAtMost(width*0.48f)
        canvas.drawBitmap(poster,null,RectF(0f,0f,posterWidth,height.toFloat()),Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.drawRect(RectF(posterWidth-32f,0f,posterWidth+48f,height.toFloat()),
            Paint().apply{shader=LinearGradient(posterWidth-32f,0f,posterWidth+48f,0f,
                0x00101923,0xFF101923.toInt(),Shader.TileMode.CLAMP)})

        val availableWidth=width-posterWidth-42f
        val scale=minOf(availableWidth/logo.width,142f/logo.height)
        val logoWidth=logo.width*scale
        val logoHeight=logo.height*scale
        val logoLeft=posterWidth+24f+(availableWidth-logoWidth)/2f
        val logoTop=(height-logoHeight)/2f
        canvas.drawBitmap(logo,null,RectF(logoLeft,logoTop,logoLeft+logoWidth,logoTop+logoHeight),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return output
    }
}
