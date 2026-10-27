package dev.luma.launcher

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.widget.FrameLayout
import android.widget.VideoView
import android.net.Uri
import android.view.Gravity
import android.view.animation.LinearInterpolator

/** Foreground-only screensaver; never runs while a streaming app is in front. */
class Screensaver(private val context: Context, private val root: FrameLayout, private val prefs: Preferences,
    private val wallpaper: WallpaperView) {
    private var overlay: FrameLayout?=null
    private var motion: ValueAnimator?=null
    private var video:VideoView?=null
    val active get()=overlay!=null
    fun start() {
        if(active)return
        val cover=FrameLayout(context).apply{setBackgroundColor(Color.BLACK);isFocusable=false}
        val still=WallpaperView(context).apply {darkness=0f;zoom=1f;cinematicShade=false}
        val image=wallpaper.bitmap;val frost=wallpaper.frost
        if(image!=null && frost!=null) still.show(image,frost,false)
        cover.addView(still,FrameLayout.LayoutParams(-1,-1));root.addView(cover,FrameLayout.LayoutParams(-1,-1));overlay=cover
        cover.alpha=0f;cover.animate().alpha(1f).setDuration(500).start()
        val clip=prefs.text("video")
        if(clip.isNotBlank()){
            val player=VideoView(context)
            video=player
            cover.addView(player,FrameLayout.LayoutParams(-1,-1,Gravity.CENTER))
            player.setOnPreparedListener{media->
                if(overlay===cover){media.isLooping=true;media.setVolume(0f,0f);player.start()}
            }
            player.setOnErrorListener{_,_,_->
                if(video===player){player.stopPlayback();cover.removeView(player);video=null}
                true // The saved first frame remains visible if a decoder cannot play the clip.
            }
            runCatching{player.setVideoURI(Uri.parse(clip))}.onFailure{
                player.stopPlayback();cover.removeView(player);video=null
            }
            return
        }
        if(prefs.number("saverMotion",if(prefs.motion==0)0 else 1)!=0) {
            var last=0L
            val started=android.os.SystemClock.uptimeMillis()
            motion=ValueAnimator.ofFloat(0f,1f).apply {
                duration=80000;repeatCount=ValueAnimator.INFINITE;repeatMode=ValueAnimator.RESTART;interpolator=LinearInterpolator()
                addUpdateListener {
                    val now=android.os.SystemClock.uptimeMillis()
                    if(now-last>=if(prefs.renderMode==2)64 else 32) {
                        last=now;val pose=ScreensaverMotion.pose(it.animatedValue as Float,now-started)
                        still.zoom=pose.zoom;still.drift=pose.x*still.width;still.driftY=pose.y*still.height;still.invalidate()
                    }
                };start()
            }
        }
    }
    fun stop(animated: Boolean=true) {
        video?.setOnPreparedListener(null);video?.setOnErrorListener(null);video?.stopPlayback();video=null
        motion?.cancel();motion=null
        val cover=overlay?:return;overlay=null
        if(animated)cover.animate().alpha(0f).setDuration(250).withEndAction {root.removeView(cover)}.start()
        else {cover.animate().cancel();root.removeView(cover)}
    }
}
