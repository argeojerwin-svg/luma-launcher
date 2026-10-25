package dev.luma.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import java.util.Date

/** Optional system-enabled shortcut. It observes window package changes and the Menu key only. */
class GlobalControlService:AccessibilityService(){
    private var foregroundPackage:String?=null
    private var overlay:FrameLayout?=null
    private var panel:GlassPanel?=null
    private var closing=false
    private var menuStream=false
    private var menuHeld=false
    private val handler=Handler(Looper.getMainLooper())
    private val menuHold=Runnable{
        if(menuStream){menuHeld=true;openLuma("app_switcher")}
    }
    private val clockTick=object:Runnable{
        override fun run(){panel?.setClockText(android.text.format.DateFormat.getTimeFormat(this@GlobalControlService).format(Date()))
            if(overlay!=null)handler.postDelayed(this,60000-System.currentTimeMillis()%60000)}
    }
    private val windows by lazy{getSystemService(Context.WINDOW_SERVICE) as WindowManager}

    override fun onServiceConnected(){super.onServiceConnected();Log.d("LumaGlobal","service connected flags=${serviceInfo.flags}")}

    override fun onAccessibilityEvent(event:AccessibilityEvent?){
        if(overlay==null && event?.eventType==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
            event.packageName?.toString()?.let{foregroundPackage=it;Log.d("LumaGlobal","foreground=$it")}
    }
    override fun onInterrupt(){handler.removeCallbacks(menuHold);menuStream=false;close(false)}
    override fun onDestroy(){close(false);handler.removeCallbacks(clockTick);handler.removeCallbacks(menuHold);super.onDestroy()}

    override fun onKeyEvent(event:KeyEvent):Boolean{
        if(event.keyCode==KeyEvent.KEYCODE_MENU){
            Log.d("LumaGlobal","menu action=${event.action} repeat=${event.repeatCount} foreground=$foregroundPackage overlay=${overlay!=null}")
            if(event.action==KeyEvent.ACTION_UP){
                val consumed=menuStream
                handler.removeCallbacks(menuHold)
                menuStream=false
                if(consumed && !menuHeld && !event.isCanceled){if(overlay!=null)close(true)else open()}
                menuHeld=false
                return consumed
            }
            if(event.action!=KeyEvent.ACTION_DOWN)return false
            if(event.repeatCount>0){
                if(menuStream && !menuHeld && (event.isLongPress || event.eventTime-event.downTime>=550)){
                    handler.removeCallbacks(menuHold);menuHeld=true;openLuma("app_switcher")
                }
                return menuStream
            }
            if(menuStream)return true
            // Android may connect the service after the other app's window event has already fired.
            // An unknown foreground must not make the first Menu press disappear.
            if(overlay==null && (foregroundPackage==packageName || foregroundPackage=="com.android.systemui" || !Settings.canDrawOverlays(this)))return false
            menuStream=true;menuHeld=false;handler.postDelayed(menuHold,550)
            return true
        }
        if(overlay!=null && event.keyCode==KeyEvent.KEYCODE_BACK){
            if(event.action==KeyEvent.ACTION_UP)close(true)
            return true
        }
        return false
    }

    private fun open():Boolean{
        if(overlay!=null || closing)return false
        if(!Settings.canDrawOverlays(this)){
            Log.w("LumaGlobal","Draw-over-apps permission is not enabled")
            return false
        }
        val prefs=Preferences(this)
        val root=object:FrameLayout(this){
            override fun dispatchKeyEvent(event:KeyEvent):Boolean{
                if(event.keyCode==KeyEvent.KEYCODE_MENU){onKeyEvent(event);return true}
                if(event.keyCode==KeyEvent.KEYCODE_BACK){
                    if(event.action==KeyEvent.ACTION_UP)close(true)
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }
        val actions=listOf(
            PanelAction("All Apps"){openLuma("all_apps")},
            PanelAction("App Switcher"){openLuma("app_switcher")},
            PanelAction("Search"){openLuma("search")},
            PanelAction("Audio settings"){openSystem(Settings.ACTION_SOUND_SETTINGS)},
            PanelAction("Wallpaper"){openLuma("wallpaper")},
            PanelAction("Start screensaver"){openLuma("screensaver")},
            PanelAction("More controls"){openLuma("more")}
        )
        val sheet=GlassPanel(this,WallpaperView(this),prefs,"Control Centre",
            "${prefs.profileName} · ${networkStatus()}",actions)
        root.addView(sheet,FrameLayout.LayoutParams(-1,-1))
        val params=WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT).apply{gravity=Gravity.TOP or Gravity.LEFT;title="Luma Control Centre"}
        try{
            windows.addView(root,params)
            overlay=root;panel=sheet
            sheet.enter(prefs.motion!=0);root.post{sheet.focusAt(0)}
            handler.removeCallbacks(clockTick);handler.post(clockTick)
            return true
        }catch(error:RuntimeException){Log.e("LumaGlobal","overlay failed",error);root.removeAllViews();overlay=null;panel=null;return false}
    }
    private fun close(animated:Boolean){
        val root=overlay?:return
        if(closing)return
        closing=true;handler.removeCallbacks(clockTick)
        val done={
            runCatching{windows.removeView(root)}
            if(overlay===root){overlay=null;panel=null}
            closing=false
        }
        panel?.leave(animated && Preferences(this).motion!=0,done)?:done()
    }
    private fun openLuma(target:String){
        close(false)
        val intent=Intent(this,LauncherActivity::class.java).apply{
            action=LauncherActivity.ACTION_GLOBAL_CONTROL
            putExtra(LauncherActivity.EXTRA_GLOBAL_TARGET,target)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        runCatching{startActivity(intent)}
    }
    private fun openSystem(action:String){
        close(false)
        runCatching{startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}
            .onFailure{openLuma("more")}
    }
    private fun networkStatus():String{
        val network=getSystemService(ConnectivityManager::class.java)
        val caps=network.getNetworkCapabilities(network.activeNetwork)?:return "Network · disconnected"
        val name=when{caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)->"Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)->"Ethernet";else->"Network"}
        return "$name · ${if(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))"internet available" else "connected"}"
    }
}
