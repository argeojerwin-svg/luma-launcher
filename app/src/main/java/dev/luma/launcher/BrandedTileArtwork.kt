package dev.luma.launcher

import android.content.pm.PackageManager
import android.graphics.*

/** The approved brand-first treatment. All rasterisation happens on the artwork worker. */
object BrandedTileArtwork {
    fun render(pm:PackageManager,app:AppEntry,identity:TileIdentity,refined:Boolean=true,width:Int=320,height:Int=192):AppArtwork {
        val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        val c=Canvas(bitmap)
        c.scale(width/320f,height/192f)
        val p=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply{isDither=true}
        var sourceBanner=false
        if(app.id=="com.android.vending" && refined)marketplace(c,p)
        else {
            val banner=runCatching{pm.getActivityBanner(app.component)?:pm.getApplicationBanner(app.id)}.getOrNull()
            sourceBanner=banner!=null
            val drawable=banner?:runCatching{pm.getActivityIcon(app.component)}.getOrElse{pm.defaultActivityIcon}
            if(banner!=null){
                // Keep the complete installed composition, including its wordmark and brand colours.
                drawable.setBounds(0,0,320,192);drawable.draw(c)
            }else{
                val hue=identity.hue
                p.shader=LinearGradient(0f,0f,320f,192f,
                    Color.HSVToColor(floatArrayOf(hue,.30f,.30f)),Color.HSVToColor(floatArrayOf(hue,.45f,.14f)),Shader.TileMode.CLAMP)
                c.drawRect(0f,0f,320f,192f,p);p.shader=null
                val ratio=drawable.intrinsicWidth.coerceAtLeast(1).toFloat()/drawable.intrinsicHeight.coerceAtLeast(1)
                val h=if(ratio>1f)144f/ratio else 144f;val w=h*ratio
                val info=runCatching{pm.getActivityInfo(app.component,0)}.getOrNull()
                if(info!=null && info.icon==0 && info.applicationInfo.icon==0){
                    // Unbranded Android fallback: a legible identity, rather than the generic robot.
                    val initials=app.label.ifBlank{identity.name}.split(Regex("[^\\p{L}\\p{N}]+")).filter{it.isNotEmpty()}.take(2).joinToString(""){it.take(1)}.uppercase()
                    p.color=0xFFE1EEFF.toInt();p.typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);p.textSize=72f;p.textAlign=Paint.Align.CENTER
                    c.drawText(initials,160f,121f,p)
                }else{
                    drawable.setBounds(((320-w)/2).toInt(),((192-h)/2).toInt(),((320+w)/2).toInt(),((192+h)/2).toInt());drawable.draw(c)
                }
            }
        }
        if(refined)finish(c,p)
        if(width>320 && sourceBanner)sharpen(bitmap)
        return AppArtwork(bitmap,true)
    }
    /** Restrained edge recovery for installed 320px TV banners enlarged in App Switcher. */
    private fun sharpen(bitmap:Bitmap){
        val width=bitmap.width;val height=bitmap.height
        val src=IntArray(width*height);val dst=IntArray(src.size)
        bitmap.getPixels(src,0,width,0,0,width,height)
        src.copyInto(dst)
        fun channel(center:Int,left:Int,right:Int,up:Int,down:Int,shift:Int):Int{
            val value=center ushr shift and 255
            val neighbours=((left ushr shift and 255)+(right ushr shift and 255)+
                (up ushr shift and 255)+(down ushr shift and 255))/4
            return (value+(value-neighbours)*.45f).toInt().coerceIn(0,255)
        }
        for(y in 1 until height-1)for(x in 1 until width-1){
            val index=y*width+x;val center=src[index]
            val left=src[index-1];val right=src[index+1];val up=src[index-width];val down=src[index+width]
            dst[index]=Color.argb(Color.alpha(center),channel(center,left,right,up,down,16),
                channel(center,left,right,up,down,8),channel(center,left,right,up,down,0))
        }
        bitmap.setPixels(dst,0,width,0,0,width,height)
    }
    private fun marketplace(c:Canvas,p:Paint){
        p.shader=LinearGradient(20f,0f,300f,192f,intArrayOf(0xFF29B9F4.toInt(),0xFF177AE7.toInt(),0xFF254ED6.toInt()),null,Shader.TileMode.CLAMP)
        c.drawRect(0f,0f,320f,192f,p);p.shader=null
        p.style=Paint.Style.STROKE;p.strokeCap=Paint.Cap.ROUND;p.strokeWidth=15f
        val mark=Path().apply{moveTo(125f,144f);lineTo(180f,49f);moveTo(146f,49f);lineTo(201f,144f);moveTo(111f,117f);lineTo(210f,117f)}
        c.save();c.translate(0f,2f);p.color=0x25012D9F;c.drawPath(mark,p);c.restore()
        p.color=Color.WHITE;p.shader=LinearGradient(135f,45f,195f,150f,Color.WHITE,0xFFE1F4FF.toInt(),Shader.TileMode.CLAMP)
        c.drawPath(mark,p);p.shader=null;p.style=Paint.Style.FILL
    }
    private fun finish(c:Canvas,p:Paint){
        p.color=Color.WHITE
        p.shader=LinearGradient(0f,0f,0f,192f,intArrayOf(0x19FFFFFF,0x02FFFFFF,0x10000000),floatArrayOf(0f,.40f,1f),Shader.TileMode.CLAMP)
        c.drawRect(0f,0f,320f,192f,p)
        p.shader=RadialGradient(40f,-65f,260f,intArrayOf(0x10FFFFFF,0x00FFFFFF),null,Shader.TileMode.CLAMP)
        c.drawRect(0f,0f,320f,192f,p);p.shader=null
        p.style=Paint.Style.STROKE;p.strokeWidth=1.2f
        p.shader=LinearGradient(0f,0f,320f,192f,0x66FFFFFF,0x08FFFFFF,Shader.TileMode.CLAMP)
        c.drawRoundRect(RectF(1f,1f,319f,191f),31f,31f,p);p.shader=null;p.style=Paint.Style.FILL
    }
}
