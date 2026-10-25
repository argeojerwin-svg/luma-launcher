package dev.luma.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView

/** Content-only caption over the cinematic wallpaper. Empty apps draw nothing. */
class TopShelfView(context:Context, private val prefs:Preferences):FrameLayout(context){
    private val shade=Paint(Paint.ANTI_ALIAS_FLAG)
    private var gradient:RadialGradient?=null
    private val title=TextView(context).apply{
        setTextColor(Color.WHITE);maxLines=2;ellipsize=TextUtils.TruncateAt.END
        setShadowLayer(9f,0f,3f,0xCC000000.toInt());includeFontPadding=false
    }
    private val detail=TextView(context).apply{
        setTextColor(0xE6FFFFFF.toInt());maxLines=2;ellipsize=TextUtils.TruncateAt.END
        setShadowLayer(6f,0f,2f,0xCC000000.toInt());includeFontPadding=false
    }
    private var currentUri:String?=null
    init{
        setWillNotDraw(false);isFocusable=false;isClickable=false;visibility=View.GONE
        addView(title);addView(detail)
    }
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int){
        super.onSizeChanged(w,h,oldw,oldh)
        gradient=RadialGradient(w*.18f,h*.60f,w*.64f,
            intArrayOf(0x7D020813,0x3A020813,0x00020813),floatArrayOf(0f,.45f,1f),Shader.TileMode.CLAMP)
    }
    override fun onDraw(canvas:Canvas){
        gradient?.let{shade.shader=it;canvas.drawRect(0f,0f,width.toFloat(),height.toFloat(),shade);shade.shader=null}
    }
    fun show(item:TopShelfItem?){
        if(item==null){currentUri=null;animate().cancel();visibility=View.GONE;alpha=1f;return}
        val changed=currentUri!=item.openUri
        currentUri=item.openUri;title.text=item.title;detail.text=item.description
        detail.visibility=if(item.description.isBlank())View.GONE else View.VISIBLE
        contentDescription="Featured content · ${item.title}"
        if(visibility!=View.VISIBLE){visibility=View.VISIBLE;alpha=0f;animate().alpha(1f).setDuration(if(prefs.motion==0)0 else 210).start()}
        else if(changed){animate().cancel();alpha=.72f;animate().alpha(1f).setDuration(if(prefs.motion==0)0 else 170).start()}
    }
    fun layoutAt(width:Int,height:Int,unit:Float){
        title.layoutParams=LayoutParams((width*.64f).toInt(),(144*unit).toInt(),Gravity.TOP or Gravity.LEFT).apply{
            leftMargin=(34*unit).toInt();topMargin=(88*unit).toInt()
        }
        detail.layoutParams=LayoutParams((width*.64f).toInt(),(75*unit).toInt(),Gravity.TOP or Gravity.LEFT).apply{
            leftMargin=(36*unit).toInt();topMargin=(230*unit).toInt()
        }
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,58*unit)
        detail.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,24*unit)
    }
}
