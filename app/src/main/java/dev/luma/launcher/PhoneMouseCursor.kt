package dev.luma.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Rect
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import java.lang.ref.WeakReference

/** A click-through pointer visible over apps using Luma's existing overlay permission. */
object PhoneMouseCursor {
    private val main=Handler(Looper.getMainLooper())
    private var cursor:View?=null
    private var windows:WindowManager?=null
    private var pending:Triple<Context,Int,Int>?=null
    private var updateQueued=false
    private var hoverTarget=WeakReference<View>(null)
    private var absorbed=false
    private var externalRect:Rect?=null
    private var highlight:View?=null
    private var lastX=0
    private var lastY=0
    private var clearQueued=false
    private val clearTransient=Runnable{clearQueued=false;external(null,true)}
    private val magnetRect=Rect()
    @Volatile private var magnetJson="null"
    fun magneticTarget()=magnetJson
    private fun publishTarget(){
        val view=hoverTarget.get()?.takeIf{it.isShown && it.hasWindowFocus()}
        val found=if(view!=null)view.getGlobalVisibleRect(magnetRect) else externalRect?.let{magnetRect.set(it);true}?:false
        val metrics=(view?:cursor)?.resources?.displayMetrics
        magnetJson=if(found && metrics!=null && magnetRect.contains(lastX,lastY)){
            val w=(metrics.widthPixels-1).coerceAtLeast(1);val h=(metrics.heightPixels-1).coerceAtLeast(1)
            "[${magnetRect.left*1000/w},${magnetRect.top*1000/h},${magnetRect.right*1000/w},${magnetRect.bottom*1000/h}]"
        }else "null"
    }
    fun external(rect:Rect?,force:Boolean=false){
        val valid=rect?.takeIf{it.contains(lastX,lastY) && it.width()>8 && it.height()>8}
        if(valid==null && !force && externalRect?.contains(lastX,lastY)==true){
            if(!clearQueued){clearQueued=true;main.postDelayed(clearTransient,240)}
            return
        }
        main.removeCallbacks(clearTransient);clearQueued=false
        if(externalRect==valid)return
        val previous=externalRect
        val stable=if(previous!=null && valid!=null){
            val iw=(minOf(previous.right,valid.right)-maxOf(previous.left,valid.left)).coerceAtLeast(0)
            val ih=(minOf(previous.bottom,valid.bottom)-maxOf(previous.top,valid.top)).coerceAtLeast(0)
            val intersection=iw.toLong()*ih
            val union=previous.width().toLong()*previous.height()+valid.width().toLong()*valid.height()-intersection
            union>0 && intersection.toDouble()/union>=.85
        }else false
        externalRect=valid
        val wm=windows
        if(valid==null || wm==null){
            highlight?.let{runCatching{wm?.removeView(it)}};highlight=null
        }else{
            val app=cursor?.context?:return
            val view=highlight?:object:View(app){
                init{importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS}
                private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
                override fun onDraw(canvas:Canvas){
                    val radius=minOf(width,height)*.16f
                    paint.style=Paint.Style.FILL;paint.color=0x18DCEAFF
                    canvas.drawRoundRect(2f,2f,width-2f,height-2f,radius,radius,paint)
                    paint.style=Paint.Style.STROKE;paint.strokeWidth=2f;paint.color=0xA6E5F0FF.toInt()
                    canvas.drawRoundRect(2f,2f,width-2f,height-2f,radius,radius,paint)
                }
            }
            val params=WindowManager.LayoutParams(valid.width(),valid.height(),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT).apply{gravity=Gravity.TOP or Gravity.LEFT;x=valid.left;y=valid.top;title="Luma pointer highlight"}
            runCatching{
                view.animate().cancel()
                if(highlight==null)wm.addView(view,params)else wm.updateViewLayout(view,params)
                highlight=view
                if(stable){view.alpha=1f;view.scaleX=1f;view.scaleY=1f}
                else{
                    view.alpha=0f;view.scaleX=.98f;view.scaleY=.98f
                    view.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(160).start()
                }
            }.onFailure{externalRect=null}
        }
        updateAppearance()
    }
    fun hover(view:View,inside:Boolean){
        if(inside)hoverTarget=WeakReference(view)
        else if(hoverTarget.get()===view)hoverTarget.clear()
        updateAppearance()
    }
    private fun updateAppearance(){
        publishTarget()
        val next=externalRect!=null || hoverTarget.get()?.let{it.isShown && it.hasWindowFocus()}==true
        if(next==absorbed)return
        absorbed=next
        cursor?.animate()?.cancel()
        cursor?.animate()?.alpha(if(next)0f else 1f)?.scaleX(if(next).35f else 1f)
            ?.scaleY(if(next).35f else 1f)?.setDuration(120L)?.start()
    }
    private val hide=Runnable{remove()}
    fun move(context:Context,command:String){
        if(!Settings.canDrawOverlays(context))return
        val point=command.substringAfter(':').split(',').map{it.toInt()}
        val metrics=context.resources.displayMetrics
        val x=point[0]*(metrics.widthPixels-1)/1000;val y=point[1]*(metrics.heightPixels-1)/1000
        UniversalPointer.move(context,x,y)
        synchronized(this){
            pending=Triple(context.applicationContext,x,y)
            if(updateQueued)return
            updateQueued=true
        }
        main.post{
            val target=synchronized(this){
                val latest=pending
                pending=null;updateQueued=false
                latest
            }?:return@post
            val (app,latestX,latestY)=target
            lastX=latestX;lastY=latestY
            if(externalRect?.contains(lastX,lastY)==false)external(null,true)
            val wm=app.getSystemService(WindowManager::class.java)
            val type=if(android.os.Build.VERSION.SDK_INT>=26)WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
            val size=(24*app.resources.displayMetrics.density).toInt().coerceAtLeast(32)
            val params=WindowManager.LayoutParams(size,size,type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT).apply{gravity=Gravity.TOP or Gravity.LEFT;this.x=latestX-size/2;this.y=latestY-size/2;title="Luma phone pointer"}
            runCatching{
                if(cursor==null){
                    val view=object:View(app){
                        init{importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS}
                        private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
                        private val center=size/2f
                        private val radius=size*.36f
                        private val halo=RadialGradient(center,center,size/2f,
                            intArrayOf(0x44000000,Color.TRANSPARENT),null,Shader.TileMode.CLAMP)
                        override fun onDraw(canvas:Canvas){
                            paint.style=Paint.Style.FILL;paint.shader=halo
                            canvas.drawCircle(center,center,size/2f,paint)
                            paint.shader=null;paint.color=0xA6DCE3EC.toInt()
                            canvas.drawCircle(center,center,radius,paint)
                            paint.style=Paint.Style.STROKE;paint.strokeWidth=1.2f;paint.color=0xB3FFFFFF.toInt()
                            canvas.drawCircle(center,center,radius,paint)
                        }
                    }
                    val hidden=externalRect!=null || hoverTarget.get()?.let{it.isShown && it.hasWindowFocus()}==true
                    absorbed=hidden;view.alpha=if(hidden)0f else 1f
                    view.scaleX=if(hidden).35f else 1f;view.scaleY=view.scaleX
                    wm.addView(view,params);cursor=view;windows=wm
                }else wm.updateViewLayout(cursor,params)
            }
            updateAppearance()
            main.removeCallbacks(hide);main.postDelayed(hide,5000)
        }
    }
    fun suspendHover(){
        synchronized(this){pending=null}
        main.post{hoverTarget.clear();remove()}
    }
    fun close(){main.post{remove()}}
    private fun remove(){
        external(null,true)
        cursor?.let{runCatching{windows?.removeView(it)}};cursor=null;windows=null;magnetJson="null";main.removeCallbacks(hide)
    }
}
