package dev.luma.launcher

import android.service.dreams.DreamService
import android.widget.FrameLayout

/** Android's idle screensaver, using the active Luma profile's chosen wallpaper. */
class WallpaperDreamService:DreamService(){
    private var images:WallpaperRepository?=null
    private var saver:Screensaver?=null
    private var ready=false
    private var dreaming=false

    override fun onAttachedToWindow(){
        super.onAttachedToWindow()
        isInteractive=false;isFullscreen=true
        val prefs=Preferences(this)
        val root=FrameLayout(this)
        val wallpaper=WallpaperView(this).apply{
            darkness=0f;cinematicShade=false
            contentDescription=getString(R.string.wallpaper_dream_name)
        }
        root.addView(wallpaper,FrameLayout.LayoutParams(-1,-1))
        setContentView(root)
        saver=Screensaver(this,root,prefs,wallpaper)
        val repository=WallpaperRepository(this).also{images=it}
        fun show(bitmap:android.graphics.Bitmap?,frost:android.graphics.Bitmap?){
            if(bitmap==null || frost==null)return
            wallpaper.show(bitmap,frost,false);ready=true
            if(dreaming)saver?.start()
        }
        repository.load(prefs.text("wallpaper"),if(prefs.renderMode==2)1280 else 1920){bitmap,frost->
            if(bitmap!=null && frost!=null)show(bitmap,frost)
            else repository.load(""){fallback,soft->show(fallback,soft)}
        }
    }
    override fun onDreamingStarted(){super.onDreamingStarted();dreaming=true;if(ready)saver?.start()}
    override fun onDreamingStopped(){dreaming=false;saver?.stop(false);super.onDreamingStopped()}
    override fun onDetachedFromWindow(){
        dreaming=false;ready=false;saver?.stop(false);saver=null
        images?.close();images=null
        super.onDetachedFromWindow()
    }
}
