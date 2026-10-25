package dev.luma.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.KeyEvent
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import java.util.concurrent.ExecutorService

/** Shared viewing row; each source states what it knows and supplies. */
class ContinueWatchingView(context:Context, private val wallpaper:WallpaperView,private val prefs:Preferences,
    private val images:TraktImageCache,private val worker:ExecutorService,items:List<ContinueItem>,
    private val open:(ContinueItem)->Unit,private val hide:(ContinueItem)->Unit,private val dismiss:()->Unit):FrameLayout(context){
    private val entries=items.toMutableList()
    private val art=ImageView(context).apply{scaleType=ImageView.ScaleType.CENTER_CROP}
    private val title=TextView(context).apply{textSize=42f;setTextColor(Color.WHITE);typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);maxLines=2}
    private val subtitle=TextView(context).apply{textSize=23f;setTextColor(0xDBFFFFFF.toInt());maxLines=2}
    private val progress=ProgressBar(context,null,android.R.attr.progressBarStyleHorizontal).apply{max=1000;progressTintList=android.content.res.ColorStateList.valueOf(0xFFE9F4FF.toInt());progressBackgroundTintList=android.content.res.ColorStateList.valueOf(0x55FFFFFF)}
    private val controls=LinearLayout(context).apply{orientation=LinearLayout.HORIZONTAL}
    private val counter=TextView(context).apply{textSize=18f;setTextColor(0xBAFFFFFF.toInt())}
    private var selected=0
    private var imageGeneration=0
    private val u=minOf(resources.displayMetrics.widthPixels/1920f,resources.displayMetrics.heightPixels/1080f)
    init{
        setBackgroundColor(0xEF07111F.toInt())
        val left=(112*u).toInt();val textWidth=(resources.displayMetrics.widthPixels*.38f).toInt()
        addView(TextView(context).apply{text="CONTINUE WATCHING";textSize=18f;letterSpacing=.12f;setTextColor(0xBFFFFFFF.toInt())},
            LayoutParams(textWidth,(50*u).toInt()).apply{leftMargin=left;topMargin=(182*u).toInt()})
        addView(title,LayoutParams(textWidth,(140*u).toInt()).apply{leftMargin=left;topMargin=(250*u).toInt()})
        addView(subtitle,LayoutParams(textWidth,(95*u).toInt()).apply{leftMargin=left;topMargin=(405*u).toInt()})
        addView(progress,LayoutParams(textWidth,(8*u).toInt()).apply{leftMargin=left;topMargin=(520*u).toInt()})
        addView(counter,LayoutParams(textWidth,(48*u).toInt()).apply{leftMargin=left;topMargin=(545*u).toInt()})
        addView(controls,LayoutParams(textWidth,(76*u).toInt()).apply{leftMargin=left;topMargin=(625*u).toInt()})
        art.background=GlassDrawable(art,wallpaper,prefs,28*u,true)
        addView(art,LayoutParams((resources.displayMetrics.widthPixels*.48f).toInt(),(resources.displayMetrics.heightPixels*.62f).toInt(),Gravity.RIGHT or Gravity.CENTER_VERTICAL).apply{rightMargin=(90*u).toInt()})
        addView(TextView(context).apply{text="◀  ▶  Browse       OK  Select       Back  Dock";gravity=Gravity.CENTER;textSize=18f;setTextColor(0xBFFFFFFF.toInt())},
            LayoutParams(-1,(62*u).toInt(),Gravity.BOTTOM).apply{bottomMargin=(52*u).toInt()})
        render()
        alpha=0f;animate().alpha(1f).setDuration(if(prefs.motion==0)0 else 210).start()
    }
    private fun button(text:String,action:()->Unit):GlassButton{
        val button=GlassButton(context,wallpaper,prefs,text).apply{setOnClickListener{action()};setOnKeyListener{_,key,event->
            if(event.action!=KeyEvent.ACTION_DOWN)false else when(key){
                KeyEvent.KEYCODE_DPAD_LEFT->{step(-1);true};KeyEvent.KEYCODE_DPAD_RIGHT->{step(1);true}
                KeyEvent.KEYCODE_BACK->{dismiss();true};else->false
            }
        }}
        controls.addView(button,LinearLayout.LayoutParams(0,-1,1f).apply{rightMargin=(12*u).toInt()})
        return button
    }
    private fun render(){
        if(entries.isEmpty()){dismiss();return}
        val item=entries[selected];title.text=item.title;subtitle.text=listOf(item.sourceLabel,item.subtitle).filter{it.isNotBlank()}.joinToString(" · ")
        progress.visibility=if(item.progress==null)GONE else VISIBLE
        progress.progress=((item.progress?:0f)*10).toInt()
        counter.text="${item.progress?.let{"${it.toInt()}% watched"}?:"Position unavailable"} · ${selected+1} of ${entries.size}"
        controls.removeAllViews()
        button(if(item.openUri.isNotBlank())"Open title" else if(item.stremioUri.isNotBlank())"Try Stremio details" else "Open app"){open(item)}
        button("Hide title"){val removed=entries.removeAt(selected);hide(removed);selected=selected.coerceAtMost(entries.lastIndex);render()}
        controls.getChildAt(0)?.post{controls.getChildAt(0)?.requestFocus()}
        val generation=++imageGeneration;art.animate().cancel();art.animate().alpha(.18f).setDuration(if(prefs.motion==0)0 else 100).start()
        worker.execute{val bitmap=runCatching{images.load(item.imageUrl)}.getOrNull();art.post{if(generation==imageGeneration){art.setImageBitmap(bitmap);art.animate().alpha(1f).setDuration(if(prefs.motion==0)0 else 200).start()}}}
    }
    fun step(delta:Int){if(entries.size<2)return;selected=(selected+delta+entries.size)%entries.size;render()}
    fun leave(done:()->Unit){animate().cancel();animate().alpha(0f).setDuration(if(prefs.motion==0)0 else 170).withEndAction(done).start()}
}
