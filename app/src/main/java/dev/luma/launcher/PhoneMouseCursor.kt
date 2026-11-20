package dev.luma.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/** A click-through pointer visible over apps using Luma's existing overlay permission. */
object PhoneMouseCursor {
    private val main=Handler(Looper.getMainLooper())
    private var cursor:View?=null
    private var windows:WindowManager?=null
    private var pending:Triple<Context,Int,Int>?=null
    private var updateQueued=false
    private val hide=Runnable{remove()}
    fun move(context:Context,command:String){
        if(!Settings.canDrawOverlays(context))return
        val point=command.substringAfter(':').split(',').map{it.toInt()}
        val metrics=context.resources.displayMetrics
        val x=point[0]*(metrics.widthPixels-1)/1000;val y=point[1]*(metrics.heightPixels-1)/1000
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
            val wm=app.getSystemService(WindowManager::class.java)
            val type=if(android.os.Build.VERSION.SDK_INT>=26)WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
            val params=WindowManager.LayoutParams(34,42,type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT).apply{gravity=Gravity.TOP or Gravity.LEFT;this.x=latestX;this.y=latestY;title="Luma phone pointer"}
            runCatching{
                if(cursor==null){
                    val view=object:View(app){
                        private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
                        private val arrow=Path().apply{moveTo(2f,2f);lineTo(2f,29f);lineTo(9f,22f);lineTo(16f,37f);lineTo(22f,34f);lineTo(15f,20f);lineTo(27f,20f);close()}
                        override fun onDraw(canvas:Canvas){
                            paint.style=Paint.Style.STROKE;paint.strokeWidth=4f;paint.color=Color.BLACK;canvas.drawPath(arrow,paint)
                            paint.style=Paint.Style.FILL;paint.color=Color.WHITE;canvas.drawPath(arrow,paint)
                        }
                    }
                    wm.addView(view,params);cursor=view;windows=wm
                }else wm.updateViewLayout(cursor,params)
            }
            main.removeCallbacks(hide);main.postDelayed(hide,5000)
        }
    }
    fun close(){main.post{remove()}}
    private fun remove(){cursor?.let{runCatching{windows?.removeView(it)}};cursor=null;windows=null;main.removeCallbacks(hide)}
}
