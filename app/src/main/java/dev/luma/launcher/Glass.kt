package dev.luma.launcher

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.TextView
import kotlin.math.max

/** Reusable wallpaper-aware optical surface, shared by dock, controls and panels. */
class GlassDrawable(private val host: View, private val wallpaper: WallpaperView,
    private val preferences: Preferences, private val radius: Float, private val dense: Boolean = false,
    private val opaque: Boolean = false) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shape = Path()
    private val box = RectF()
    private val position = IntArray(2)
    private val origin = IntArray(2)
    private val frostBox=RectF()
    private val reflectionMatrix=Matrix()
    private var wash:Shader?=null
    private var rim:Shader?=null
    private var light:Shader?=null
    private var controlTone:Shader?=null
    private var overlayTone:Shader?=null
    private var controlSheen:Shader?=null
    private var shaderWidth=-1
    private var shaderHeight=-1
    private fun prepare(){
        if(shaderWidth==bounds.width() && shaderHeight==bounds.height())return
        shaderWidth=bounds.width();shaderHeight=bounds.height()
        val w=shaderWidth.toFloat().coerceAtLeast(1f);val h=shaderHeight.toFloat().coerceAtLeast(1f)
        wash=LinearGradient(0f,0f,w*.8f,h,intArrayOf(0x38FFFFFF,0x09FFFFFF,0x20070C19),null,Shader.TileMode.CLAMP)
        rim=LinearGradient(0f,0f,w,h,intArrayOf(0xB8FFFFFF.toInt(),0x20FFFFFF,0x70D9E8FF),null,Shader.TileMode.CLAMP)
        light=RadialGradient(w*.18f,0f,w,intArrayOf(0x32E1EFFF,Color.TRANSPARENT),null,Shader.TileMode.CLAMP)
        controlTone=LinearGradient(0f,0f,w,h,intArrayOf(0xC21F3044.toInt(),0xCE142538.toInt(),0xD8091728.toInt()),
            floatArrayOf(0f,.52f,1f),Shader.TileMode.CLAMP)
        overlayTone=LinearGradient(0f,0f,w,h,intArrayOf(0xDE28394D.toInt(),0xE5152539.toInt(),0xEC0A1728.toInt()),
            floatArrayOf(0f,.52f,1f),Shader.TileMode.CLAMP)
        controlSheen=LinearGradient(0f,0f,0f,h*.32f,
            intArrayOf(0x24E6F5FF,0x0DE6F5FF,Color.TRANSPARENT),null,Shader.TileMode.CLAMP)
    }
    var focus = 0f
        set(value) { field=value; invalidateSelf() }
    override fun draw(c: Canvas) {
        prepare();box.set(bounds); box.inset(1f,1f)
        shape.reset(); shape.addRoundRect(box,radius,radius,Path.Direction.CW)
        c.save(); c.clipPath(shape)
        val frost=wallpaper.frost
        val useFrost=(opaque || preferences.renderMode != 2) && preferences.number("blur",2)>0 && frost != null && wallpaper.width>0
        if (useFrost && frost != null) {
            host.getLocationOnScreen(position); wallpaper.getLocationOnScreen(origin)
            val s=max(wallpaper.width.toFloat()/frost.width,wallpaper.height.toFloat()/frost.height)
            val w=frost.width*s;val h=frost.height*s
            val x=(wallpaper.width-w)/2-position[0]+origin[0]
            val y=(wallpaper.height-h)/2-position[1]+origin[1]
            paint.color=Color.WHITE;paint.alpha=if(opaque)255 else 80+preferences.number("blur",2)*35
            // Optical magnification uses the tiny cached frost texture, never a live blur pass.
            val lens=if(preferences.renderMode==0)1.012f+focus*.008f else 1.006f
            val dx=w*(lens-1f)*.5f;val dy=h*(lens-1f)*.5f
            frostBox.set(x-dx,y-dy,x+w+dx,y+h+dy)
            c.drawBitmap(frost,null,frostBox,paint)
        }
        val tone=when(preferences.number("tint",0)){1->0xFF0E1D2E.toInt();2->0xFF221B18.toInt();else->0xFF131B27.toInt()}
        val opacity=preferences.number("opacity",1).coerceIn(0,2)
        paint.shader=null;paint.color=Color.argb(((if(dense)190 else 105)+(opacity-1)*25).coerceIn(0,255),Color.red(tone),Color.green(tone),Color.blue(tone))
        if(opaque){paint.color=Color.WHITE;paint.shader=if(useFrost)controlTone else overlayTone;paint.alpha=255}
        c.drawRoundRect(box,radius,radius,paint)
        if(opaque){
            paint.shader=controlSheen;paint.alpha=255
            c.drawRect(box.left,box.top,box.right,box.top+box.height()*.32f,paint)
        }
        reflectionMatrix.setTranslate(focus*box.width()*.08f,0f);wash?.setLocalMatrix(reflectionMatrix)
        paint.alpha=(if(opaque)105 else 200+focus*55).toInt().coerceIn(0,255);paint.shader=wash
        c.drawRoundRect(box,radius,radius,paint)
        if(opaque || preferences.renderMode==0){
            light?.setLocalMatrix(reflectionMatrix);paint.alpha=255;paint.shader=light
            c.drawRoundRect(box,radius,radius,paint)
        }
        paint.shader=null; c.restore()
        paint.style=Paint.Style.STROKE;paint.strokeWidth=max(1f,host.resources.displayMetrics.density*.7f)
        paint.alpha=((if(opaque)105 else 140)+focus*110).toInt().coerceIn(0,255);paint.shader=rim
        c.drawRoundRect(box,radius,radius,paint)
        paint.shader=null;paint.color=0x16FFFFFF;box.inset(2.5f,2.5f);c.drawRoundRect(box,radius-2,radius-2,paint)
        paint.style=Paint.Style.FILL
    }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

class GlassButton(context: Context, wallpaper: WallpaperView, prefs: Preferences, text: String) : TextView(context) {
    private val glass=GlassDrawable(this,wallpaper,prefs,18f*resources.displayMetrics.density)
    private val spring=Spring { v ->
        if(prefs.renderMode==0)glass.focus=v
        scaleX=1f;scaleY=1f;alpha=.83f+v*.17f
        if(v==0f || v==1f)setLayerType(LAYER_TYPE_NONE,null)
    }
    init {
        this.text=text;setTextColor(Color.WHITE);textSize=DesignTokens.rowSp;gravity=android.view.Gravity.CENTER_VERTICAL
        setPadding(20.dp,10.dp,20.dp,10.dp);background=glass;isFocusable=true;isClickable=true
        includeFontPadding=false;maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END
        setOnFocusChangeListener { _,focused ->
            if(prefs.renderMode!=0) {glass.focus=if(focused)1f else 0f;setLayerType(LAYER_TYPE_HARDWARE,null)}
            spring.to(if(focused) 1f else 0f)
        }
        accessibilityDelegate=object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: android.view.accessibility.AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host,info);info.className="android.widget.Button"
            }
        }
    }
    private val Int.dp get()=(this*resources.displayMetrics.density).toInt()
    override fun onDetachedFromWindow() { spring.stop();super.onDetachedFromWindow() }
}
