package dev.luma.launcher

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.ExecutorService
import kotlin.math.abs

/** Recently opened apps, not Android task snapshots. All artwork comes from Luma's bounded tile cache. */
class AppSwitcherView(context:Context, private val prefs:Preferences, private val repo:AppRepository,
    private val worker:ExecutorService, private val entries:List<AppEntry>, private val open:(AppEntry)->Unit,
    private val dismiss:()->Unit, private val frozenHome:Bitmap?=null):FrameLayout(context){
    private val backgroundPaint=Paint(Paint.FILTER_BITMAP_FLAG)
    private val backgroundBounds=Rect()
    private val row=LinearLayout(context).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;clipChildren=false;clipToPadding=false}
    private val scroll=HorizontalScrollView(context).apply{isHorizontalScrollBarEnabled=false;isFocusable=false;descendantFocusability=FOCUS_AFTER_DESCENDANTS;clipToPadding=false;addView(row)}
    private val cards=mutableListOf<AppTile>()
    private var selected=0
    private var selectionReady=false
    private val unit=minOf(resources.displayMetrics.widthPixels/1920f,resources.displayMetrics.heightPixels/1080f)
    private val cardWidth=(500*unit).toInt()
    private val cardHeight=(390*unit).toInt()
    private val gap=(42*unit).toInt()
    private val focusValues=mutableListOf<Float>()
    private val focusAnimators=mutableListOf<ValueAnimator?>()
    private val detailed=mutableMapOf<Int,AppArtwork>()
    private val loading=mutableSetOf<Int>()
    private var scrollAnimator:ValueAnimator?=null
    private val glide=DecelerateInterpolator(1.5f)
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int){super.onSizeChanged(w,h,oldw,oldh);backgroundBounds.set(0,0,w,h)}
    override fun onDraw(canvas:Canvas){
        super.onDraw(canvas)
        frozenHome?.let{canvas.drawBitmap(it,null,backgroundBounds,backgroundPaint);canvas.drawColor(0xD9091323.toInt())}
    }
    init{
        isFocusable=true;isFocusableInTouchMode=true;descendantFocusability=FOCUS_AFTER_DESCENDANTS
        if(frozenHome==null)setBackgroundColor(0xD9091323.toInt()) else setWillNotDraw(false)
        val margin=(resources.displayMetrics.widthPixels-cardWidth)/2
        addView(TextView(context).apply{text="Recently opened";setTextColor(Color.WHITE);textSize=32f;typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)},
            LayoutParams(-2,(70*unit).toInt()).apply{leftMargin=(96*unit).toInt();topMargin=(100*unit).toInt()})
        addView(TextView(context).apply{text="Apps opened through Luma · Select to reopen";setTextColor(0xBFFFFFFF.toInt());textSize=18f},
            LayoutParams(-2,(40*unit).toInt()).apply{leftMargin=(96*unit).toInt();topMargin=(170*unit).toInt()})
        addView(scroll,LayoutParams(-1,(cardHeight+28*unit).toInt(),Gravity.CENTER_VERTICAL))
        row.setPadding(margin,0,margin,0)
        entries.forEachIndexed{index,app->
            val tile=AppTile(context,app,repo.displayName(app),prefs)
            tile.isFocusableInTouchMode=true;tile.isLongClickable=false
            tile.externalFocusMotion=true
            tile.configure((cardWidth-36*unit),unit)
            row.addView(tile,LinearLayout.LayoutParams(cardWidth,cardHeight).apply{rightMargin=gap})
            cards.add(tile)
            focusValues.add(0f);focusAnimators.add(null)
            renderCard(index,0f)
            tile.setOnClickListener{open(app)}
            tile.selected={focused->if(focused)select(index)}
            tile.art=repo.cachedArtwork(app)
        }
        addView(TextView(context).apply{text="◀  ▶  Browse       OK  Open       Back  Close";gravity=Gravity.CENTER;setTextColor(0xCFFFFFFF.toInt());textSize=18f},
            LayoutParams(-1,(56*unit).toInt(),Gravity.BOTTOM).apply{bottomMargin=(74*unit).toInt()})
        alpha=0f;animate().alpha(1f).setDuration(if(prefs.motion==0)0 else 180).start()
        post{focusCard(0)}
    }
    private fun focusCard(index:Int){
        if(cards.isEmpty())return
        val next=index.coerceIn(cards.indices)
        cards[next].requestFocus()
        select(next)
    }
    /** The visible modal owns remote navigation even if Android temporarily clears child focus. */
    fun handleKeyEvent(event:KeyEvent):Boolean=when(event.keyCode){
        KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT->{
            if(event.action==KeyEvent.ACTION_DOWN)focusCard(selected+if(event.keyCode==KeyEvent.KEYCODE_DPAD_RIGHT)1 else -1)
            true
        }
        KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_DOWN->true
        KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.KEYCODE_ENTER,KeyEvent.KEYCODE_NUMPAD_ENTER->{
            if(event.action==KeyEvent.ACTION_UP && !event.isCanceled)cards.getOrNull(selected)?.performClick()
            true
        }
        KeyEvent.KEYCODE_BACK->{if(event.action==KeyEvent.ACTION_UP && !event.isCanceled)dismiss();true}
        else->false
    }
    override fun dispatchKeyEvent(event:KeyEvent):Boolean=handleKeyEvent(event) || super.dispatchKeyEvent(event)
    override fun onWindowFocusChanged(hasWindowFocus:Boolean){
        super.onWindowFocusChanged(hasWindowFocus)
        if(hasWindowFocus)post{focusCard(selected)}
    }
    private fun select(index:Int){
        if(selectionReady && index==selected)return
        val previous=selected
        selected=index
        updateArtwork(index)
        cards.forEachIndexed{i,card->
            card.setLayerType(if(abs(i-index)<=1 || i==previous)View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_NONE,null)
        }
        if(!selectionReady){
            cards.indices.forEach{i->animateCard(i,if(i==index)1f else 0f,prefs.motion==0)}
            selectionReady=true
        }else{
            animateCard(previous,0f,prefs.motion==0)
            animateCard(index,1f,prefs.motion==0)
        }
        val target=index*(cardWidth+gap)
        scrollAnimator?.cancel()
        if(prefs.motion==0)scroll.scrollTo(target,0)
        else scrollAnimator=ValueAnimator.ofInt(scroll.scrollX,target).apply{
            duration=230;interpolator=glide
            addUpdateListener{scroll.scrollTo(it.animatedValue as Int,0)}
            start()
        }
    }
    private fun renderCard(index:Int,value:Float){
        val focus=value.coerceIn(0f,1f)
        focusValues[index]=focus
        cards[index].apply{
            scaleX=.975f+.025f*focus;scaleY=scaleX
            alpha=.9f+.1f*focus
            translationY=0f
        }
    }
    private fun animateCard(index:Int,target:Float,instant:Boolean){
        focusAnimators[index]?.cancel()
        cards[index].renderFocus(target)
        if(instant){renderCard(index,target);return}
        focusAnimators[index]=ValueAnimator.ofFloat(focusValues[index],target).apply{
            duration=170;interpolator=glide
            addUpdateListener{renderCard(index,it.animatedValue as Float)}
            start()
        }
    }
    private fun updateArtwork(index:Int){
        cards.forEachIndexed{i,card->
            if(abs(i-index)>1){
                detailed.remove(i)
                card.art=repo.cachedArtwork(entries[i])
            }else{
                detailed[i]?.let{card.art=it}
                if(i !in detailed && loading.add(i)){
                    worker.execute{
                        val art=runCatching{repo.switcherArtwork(entries[i])}.getOrNull()
                        card.post{
                            loading.remove(i)
                            if(card.parent!=null && abs(i-selected)<=1 && art!=null){detailed[i]=art;card.art=art}
                        }
                    }
                }
            }
        }
    }
    fun leave(done:()->Unit){animate().cancel();animate().alpha(0f).setDuration(if(prefs.motion==0)0 else 150).withEndAction(done).start()}
    override fun onDetachedFromWindow(){focusAnimators.forEach{it?.cancel()};scrollAnimator?.cancel();animate().cancel();detailed.clear();loading.clear();super.onDetachedFromWindow()}
}
