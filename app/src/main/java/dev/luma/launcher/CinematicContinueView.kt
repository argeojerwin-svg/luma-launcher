package dev.luma.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.ExecutorService

/** A transparent Home overlay: artwork is drawn by WallpaperView, with the original dock below. */
class CinematicContinueView(context:Context, private val prefs:Preferences,private val images:TraktImageCache,
    private val worker:ExecutorService,private val entries:List<ContinueItem>,
    private val selected:(ContinueItem)->Unit,private val open:(ContinueItem)->Unit,
    private val dismiss:()->Unit):FrameLayout(context){
    private val u=minOf(resources.displayMetrics.widthPixels/1920f,resources.displayMetrics.heightPixels/1080f)
    private fun px(value:Int)=(value*u).toInt()
    private val provider=TextView(context).apply{setTextColor(0xBFFFFFFF.toInt());textSize=14f;letterSpacing=.14f;maxLines=1}
    private val title=TextView(context).apply{setTextColor(Color.WHITE);textSize=44f;typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END}
    private val detail=TextView(context).apply{setTextColor(0xCFFFFFFF.toInt());textSize=20f;maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END}
    private val progress=View(context).apply{setBackgroundColor(0xD6FFFFFF.toInt())}
    private val progressTrack=View(context).apply{setBackgroundColor(0x38FFFFFF)}
    private val row=LinearLayout(context).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;clipChildren=false;clipToPadding=false}
    private val scroller=HorizontalScrollView(context).apply{isHorizontalScrollBarEnabled=false;clipChildren=false;clipToPadding=false;addView(row)}
    private val cards=mutableListOf<FrameLayout>()
    private var index=0
    init{
        isFocusable=false;clipChildren=false;clipToPadding=false
        addView(View(context).apply{background=GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(0x7107111D,0x2807111D,0x0007111D))},LayoutParams(-1,-1))
        addView(provider,LayoutParams(px(700),px(30)).apply{leftMargin=px(102);topMargin=px(251)})
        addView(title,LayoutParams(px(1280),px(75)).apply{leftMargin=px(100);topMargin=px(289)})
        addView(detail,LayoutParams(px(1200),px(40)).apply{leftMargin=px(102);topMargin=px(374)})
        addView(progressTrack,LayoutParams(px(280),px(2)).apply{leftMargin=px(102);topMargin=px(439)})
        addView(progress,LayoutParams(px(0),px(2)).apply{leftMargin=px(102);topMargin=px(439)})
        addView(scroller,LayoutParams(-1,px(145)).apply{leftMargin=px(96);rightMargin=px(96);topMargin=px(514)})
        entries.forEachIndexed { position,item ->
            val surface=GradientDrawable().apply{setColor(Color.TRANSPARENT);cornerRadius=px(18).toFloat()}
            val card=FrameLayout(context).apply{
                isFocusable=true;isClickable=true;clipChildren=false
                contentDescription="${item.title} · ${item.sourceLabel}"
                background=GradientDrawable().apply{setColor(0xAA142134.toInt());cornerRadius=px(18).toFloat()}
                foreground=surface
                setOnFocusChangeListener{_,focused->
                    surface.setStroke(if(focused)px(2).coerceAtLeast(1) else 0,if(focused)0xCDE9F5FF.toInt() else Color.TRANSPARENT)
                    animate().cancel();animate().scaleX(if(focused)1.055f else 1f).scaleY(if(focused)1.055f else 1f)
                        .setDuration(if(prefs.motion==0)0 else 165).start()
                    elevation=if(focused)px(20).toFloat() else 0f
                    if(focused){index=position;render();selected(item);scroller.smoothScrollTo((position*px(234)-px(100)).coerceAtLeast(0),0)}
                }
                setOnClickListener{open(item)}
                setOnKeyListener{_,key,event->if(event.action!=KeyEvent.ACTION_DOWN)false else when(key){
                    KeyEvent.KEYCODE_DPAD_LEFT->{cards.getOrNull((position-1).coerceAtLeast(0))?.requestFocus();true}
                    KeyEvent.KEYCODE_DPAD_RIGHT->{cards.getOrNull((position+1).coerceAtMost(entries.lastIndex))?.requestFocus();true}
                    KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_BACK->{dismiss();true}
                    else->false
                }}
            }
            val image=ImageView(context).apply{scaleType=ImageView.ScaleType.CENTER_CROP;clipToOutline=true;
                background=GradientDrawable().apply{setColor(0x55405C7B);cornerRadius=px(16).toFloat()}}
            card.addView(image,LayoutParams(-1,-1))
            row.addView(card,LinearLayout.LayoutParams(px(214),px(124)).apply{rightMargin=px(20);topMargin=px(8)})
            cards.add(card)
            val tileImage=item.tileImageUrl.ifBlank{item.imageUrl}
            if(tileImage.isNotBlank())post{worker.execute{val bitmap=runCatching{
                if(item.sourcePackage=="com.stremio.one" && item.tileImageUrl.isNotBlank())
                    StremioTitleCard.load(images,item.tileImageUrl,item.openUri)
                else images.load(tileImage,480)
            }.getOrNull();
                image.post{if(isAttachedToWindow && bitmap!=null)image.setImageBitmap(bitmap)}}}
        }
        render();alpha=0f;translationY=px(8).toFloat()
        animate().alpha(1f).translationY(0f).setInterpolator(DecelerateInterpolator(1.5f))
            .setDuration(if(prefs.motion==0)0 else 300).start()
    }
    fun focusFirst(){cards.firstOrNull()?.requestFocus();render();selected(entries[index])}
    fun step(delta:Int){
        val next=(index+delta).coerceIn(0,entries.lastIndex)
        if(next!=index){index=next;cards[index].requestFocus();render();selected(entries[index]);
            scroller.smoothScrollTo((index*px(234)-px(100)).coerceAtLeast(0),0)}
    }
    fun openCurrent(){open(entries[index])}
    fun currentItem()=entries[index]
    private fun render(){
        val item=entries[index];title.text=item.title
        provider.text=item.sourceLabel.uppercase()+if(item.origin=="Added by you")"  ·  ADDED BY YOU" else ""
        detail.text=item.subtitle
        detail.visibility=if(item.subtitle.isBlank())GONE else VISIBLE
        val show=item.progress!=null
        progressTrack.visibility=if(show)VISIBLE else GONE;progress.visibility=if(show)VISIBLE else GONE
        if(show)progress.layoutParams=LayoutParams((px(280)*(item.progress!!/100f)).toInt().coerceAtLeast(2),px(2)).apply{leftMargin=px(102);topMargin=px(439)}
    }
    fun leave(done:()->Unit){
        animate().cancel()
        animate().alpha(0f).translationY(px(8).toFloat())
            .setInterpolator(DecelerateInterpolator(1.5f))
            .setDuration(if(prefs.motion==0)0 else 300).withEndAction(done).start()
    }
    fun returnToVisible(){
        animate().cancel()
        animate().alpha(1f).translationY(0f)
            .setInterpolator(DecelerateInterpolator(1.5f))
            .setDuration(if(prefs.motion==0)0 else 300).start()
    }
}
