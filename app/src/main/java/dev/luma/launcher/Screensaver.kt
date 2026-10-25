package dev.luma.launcher

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import android.widget.VideoView
import android.view.animation.LinearInterpolator

/** Foreground-only screensaver; never runs while a streaming app is in front. */
class Screensaver(private val context: Context, private val root: FrameLayout, private val prefs: Preferences,
    private val wallpaper: WallpaperView) {
    private var overlay: FrameLayout?=null
    private var movie: VideoView?=null
    private var motion: ValueAnimator?=null
    val active get()=overlay!=null
    fun start() {
        if(active)return
        val cover=FrameLayout(context).apply{setBackgroundColor(Color.BLACK);isFocusable=false}
        val still=WallpaperView(context).apply {darkness=0f;zoom=1.04f;cinematicShade=false}
        val image=wallpaper.bitmap;val frost=wallpaper.frost
        if(image!=null && frost!=null) still.show(image,frost,false)
        cover.addView(still,FrameLayout.LayoutParams(-1,-1));root.addView(cover,FrameLayout.LayoutParams(-1,-1));overlay=cover
        cover.alpha=0f;cover.animate().alpha(1f).setDuration(500).start()
        if(prefs.motion!=0) {
            var last=0L
            motion=ValueAnimator.ofFloat(0f,1f).apply {
                duration=45000;repeatCount=ValueAnimator.INFINITE;repeatMode=ValueAnimator.REVERSE;interpolator=LinearInterpolator()
                addUpdateListener {
                    val now=android.os.SystemClock.uptimeMillis()
                    if(prefs.renderMode==0 || now-last>=32) {last=now;val f=it.animatedValue as Float;still.zoom=1.04f+f*.045f;still.drift=(f-.5f)*still.width*.025f;still.invalidate()}
                };start()
            }
        }
        val uri=prefs.text("video")
        if(uri.isNotBlank()) {
            val video=VideoView(context)
            cover.addView(video,FrameLayout.LayoutParams(-1,-1,android.view.Gravity.CENTER));movie=video
            video.setOnPreparedListener { player -> player.isLooping=true;player.setVolume(0f,0f);video.start() }
            video.setOnErrorListener { _,_,_ ->
                video.stopPlayback();cover.removeView(video);movie=null
                Toast.makeText(context,"This video cannot play. Using the landscape screensaver.",Toast.LENGTH_LONG).show();true
            }
            runCatching { video.setVideoURI(Uri.parse(uri)) }.onFailure {
                cover.removeView(video);movie=null;Toast.makeText(context,"Video unavailable. Using the landscape screensaver.",Toast.LENGTH_LONG).show()
            }
        }
    }
    fun stop(animated: Boolean=true) {
        motion?.cancel();motion=null;movie?.stopPlayback();movie=null
        val cover=overlay?:return;overlay=null
        if(animated)cover.animate().alpha(0f).setDuration(250).withEndAction {root.removeView(cover)}.start()
        else {cover.animate().cancel();root.removeView(cover)}
    }
}
