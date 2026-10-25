package dev.luma.launcher

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/** Compact vector glyph; no bitmap, shader, or per-frame animation. */
class WifiGlyph(private val status:NetworkState.Snapshot):Drawable(){
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{
        color=if(status.kind==NetworkState.Kind.WIFI)Color.WHITE else 0xB8FFFFFF.toInt()
        style=Paint.Style.STROKE;strokeCap=Paint.Cap.ROUND
    }
    private val arc=RectF()
    override fun draw(canvas:Canvas){
        val b=bounds;val size=minOf(b.width(),b.height()).toFloat();if(size<=0f)return
        canvas.save();canvas.translate(b.left+(b.width()-size)/2,b.top+(b.height()-size)/2)
        paint.strokeWidth=size*.145f
        val bars=when(status.kind){
            NetworkState.Kind.WIFI -> (status.strength?:3)+1
            NetworkState.Kind.UNKNOWN -> 2
            else -> 0
        }
        for(i in 0 until bars.coerceAtMost(4)){
            val inset=size*(.035f+i*.175f)
            arc.set(inset,inset,size-inset,size-inset)
            canvas.drawArc(arc,220f,100f,false,paint)
        }
        paint.style=Paint.Style.FILL
        canvas.drawCircle(size*.5f,size*.82f,size*.075f,paint)
        if(status.kind==NetworkState.Kind.DISCONNECTED || status.kind==NetworkState.Kind.DISABLED){
            paint.style=Paint.Style.STROKE;canvas.drawLine(size*.2f,size*.18f,size*.8f,size*.78f,paint)
        }
        paint.style=Paint.Style.STROKE;canvas.restore()
    }
    override fun setAlpha(alpha:Int){paint.alpha=alpha}
    override fun setColorFilter(colorFilter:ColorFilter?){paint.colorFilter=colorFilter}
    @Deprecated("Deprecated in Java") override fun getOpacity()=PixelFormat.TRANSLUCENT
}
