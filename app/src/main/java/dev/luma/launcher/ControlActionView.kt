package dev.luma.launcher

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Icon-led Control Centre action. Only focus transitions animate; nothing renders at idle. */
class ControlActionView(context:Context, wallpaper:WallpaperView, prefs:Preferences,
    title:String, glyph:String, private val hero:Boolean=false, private val subtle:Boolean=false):FrameLayout(context){
    private var titleView:TextView?=null
    fun setTitle(value:String){titleView?.text=value;contentDescription=value}
    private val surface=View(context)
    private val glass=GlassDrawable(surface,wallpaper,prefs,(if(hero)22 else 16).dp.toFloat())
    private var focusValue=0f
    private var focusAnimator:ValueAnimator?=null
    private fun renderFocus(value:Float){
        focusValue=value.coerceIn(0f,1f)
        val base=if(hero).38f else 0f
        surface.alpha=base+(1f-base)*focusValue
        glass.focus=focusValue*.55f
        alpha=if(subtle).82f+.18f*focusValue else .92f+.08f*focusValue
    }
    init{
        isFocusable=true;isFocusableInTouchMode=true;isClickable=true;contentDescription=title
        surface.background=glass;renderFocus(0f)
        addView(surface,LayoutParams(-1,-1))
        val iconSize=(if(hero)46 else if(subtle)28 else 36).dp
        val iconBox=FrameLayout(context).apply{
            background=GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(0x5CEBF6FF,0x2489A8C8,0x183A5270)).apply{
                cornerRadius=(if(hero)iconSize/2f else iconSize*.43f)
                setStroke(1.dp,0x35FFFFFF)
            }
            addView(ControlGlyphView(context,glyph),LayoutParams((if(hero)27 else if(subtle)19 else 22).dp,
                (if(hero)27 else if(subtle)19 else 22).dp,Gravity.CENTER))
        }
        val content=LinearLayout(context).apply{
            orientation=if(hero)LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity=if(hero)Gravity.CENTER_VERTICAL else Gravity.CENTER_VERTICAL
            setPadding((if(hero)19 else if(subtle)12 else 14).dp,0,14.dp,0)
        }
        addView(content,LayoutParams(-1,-1))
        content.addView(iconBox,LinearLayout.LayoutParams(iconSize,iconSize).apply{if(hero)bottomMargin=5.dp else rightMargin=(if(subtle)13 else 16).dp})
        content.addView(TextView(context).apply{
            titleView=this
            text=title;setTextColor(Color.WHITE);textSize=if(hero)17f else if(subtle)15f else 17f
            typeface=android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL)
            includeFontPadding=false;setSingleLine(true);ellipsize=android.text.TextUtils.TruncateAt.END
            gravity=Gravity.CENTER_VERTICAL
        },LinearLayout.LayoutParams(-1,if(hero)27.dp else -1))
        setOnFocusChangeListener{_,focused->
            focusAnimator?.cancel()
            focusAnimator=ValueAnimator.ofFloat(focusValue,if(focused)1f else 0f).apply{
                duration=170;interpolator=DecelerateInterpolator(1.5f)
                addUpdateListener{renderFocus(it.animatedValue as Float)}
                start()
            }
        }
    }
    override fun onDetachedFromWindow(){focusAnimator?.cancel();super.onDetachedFromWindow()}
    private val Int.dp get()=(this*resources.displayMetrics.density).toInt()
}

private class ControlGlyphView(context:Context,private val glyph:String):View(context){
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.WHITE;style=Paint.Style.STROKE;strokeWidth=2.2f;strokeCap=Paint.Cap.ROUND;strokeJoin=Paint.Join.ROUND}
    private val path=Path()
    private val box=RectF()
    override fun onDraw(c:Canvas){
        c.save();c.scale(width/32f,height/32f)
        when(glyph){
            "grid"->{for(y in 0..1)for(x in 0..1){box.set(5f+x*13,5f+y*13,14f+x*13,14f+y*13);c.drawRoundRect(box,2f,2f,paint)}}
            "switch"->{box.set(3f,8f,22f,25f);c.drawRoundRect(box,3f,3f,paint);box.set(10f,4f,29f,21f);c.drawRoundRect(box,3f,3f,paint)}
            "search"->{c.drawCircle(14f,14f,8f,paint);c.drawLine(20f,20f,28f,28f,paint)}
            "wifi"->{box.set(4f,5f,28f,29f);c.drawArc(box,220f,100f,false,paint);box.set(9f,10f,23f,24f);c.drawArc(box,220f,100f,false,paint);c.drawCircle(16f,24f,1.7f,paint)}
            "audio"->{path.reset();path.moveTo(4f,12f);path.lineTo(10f,12f);path.lineTo(17f,6f);path.lineTo(17f,26f);path.lineTo(10f,20f);path.lineTo(4f,20f);path.close();c.drawPath(path,paint);box.set(15f,6f,29f,26f);c.drawArc(box,-65f,130f,false,paint)}
            "wallpaper"->{box.set(3f,5f,29f,27f);c.drawRoundRect(box,3f,3f,paint);c.drawCircle(22f,11f,2f,paint);path.reset();path.moveTo(5f,24f);path.lineTo(13f,14f);path.lineTo(18f,20f);path.lineTo(21f,17f);path.lineTo(28f,25f);c.drawPath(path,paint)}
            "sleep"->{box.set(5f,5f,26f,27f);c.drawArc(box,42f,240f,false,paint);c.drawLine(24f,7f,24f,4f,paint);c.drawLine(22.5f,5.5f,25.5f,5.5f,paint)}
            "tile"->{box.set(4f,6f,28f,26f);c.drawRoundRect(box,4f,4f,paint);c.drawCircle(11f,13f,2f,paint);c.drawLine(8f,22f,17f,16f,paint);c.drawLine(17f,16f,24f,22f,paint)}
            "settings"->{c.drawCircle(16f,16f,5f,paint);for(i in 0..7){val a=i*Math.PI/4;c.drawLine((16+9*kotlin.math.cos(a)).toFloat(),(16+9*kotlin.math.sin(a)).toFloat(),(16+12*kotlin.math.cos(a)).toFloat(),(16+12*kotlin.math.sin(a)).toFloat(),paint)}}
            else->{for(x in 0..2)c.drawCircle(8f+x*8,16f,1.7f,paint)}
        }
        c.restore()
    }
}
