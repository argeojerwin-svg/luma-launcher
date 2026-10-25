package dev.luma.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Edge-to-edge content presentation over the current cinematic wallpaper. */
class TopShelfExpandedView(context:Context, private val wallpaper:WallpaperView, private val prefs:Preferences,
    val app:AppEntry, private val items:List<TopShelfItem>,
    private val openLink:(String)->Unit, private val dismiss:()->Unit):FrameLayout(context){
    private val title=TextView(context).apply{
        setTextColor(Color.WHITE);typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
        maxLines=2;ellipsize=TextUtils.TruncateAt.END;setShadowLayer(9f,0f,3f,Color.BLACK)
    }
    private val description=TextView(context).apply{
        setTextColor(0xE6FFFFFF.toInt());maxLines=3;ellipsize=TextUtils.TruncateAt.END
        setShadowLayer(6f,0f,2f,Color.BLACK)
    }
    private val position=TextView(context).apply{setTextColor(0xCCFFFFFF.toInt());textSize=18f}
    private val controls=LinearLayout(context).apply{orientation=LinearLayout.HORIZONTAL}
    private var index=0
    private val unit=minOf(resources.displayMetrics.widthPixels/1920f,resources.displayMetrics.heightPixels/1080f)
    init{
        require(items.isNotEmpty())
        background=GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(0xDD05101E.toInt(),0x8505101E.toInt(),0x2205101E))
        val left=(112*unit).toInt();val textWidth=(resources.displayMetrics.widthPixels*.50f).toInt()
        addView(title,LayoutParams(textWidth,(170*unit).toInt()).apply{leftMargin=left;topMargin=(255*unit).toInt()})
        addView(description,LayoutParams(textWidth,(132*unit).toInt()).apply{leftMargin=left;topMargin=(445*unit).toInt()})
        addView(controls,LayoutParams((resources.displayMetrics.widthPixels*.42f).toInt(),(78*unit).toInt()).apply{
            leftMargin=left;topMargin=(650*unit).toInt()
        })
        addView(position,LayoutParams(textWidth,(50*unit).toInt()).apply{leftMargin=left;topMargin=(752*unit).toInt()})
        addView(TextView(context).apply{
            text="◀  ▶  Browse       OK  Open       Back  Dock";gravity=Gravity.CENTER
            setTextColor(0xBFFFFFFF.toInt());textSize=18f
        },LayoutParams(-1,(60*unit).toInt(),Gravity.BOTTOM).apply{bottomMargin=(44*unit).toInt()})
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,62*unit)
        description.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,27*unit)
        render()
        alpha=0f;animate().alpha(1f).setDuration(if(prefs.motion==0)0 else 220).start()
    }
    private fun addButton(label:String,action:()->Unit){
        val button=GlassButton(context,wallpaper,prefs,label).apply{
            setOnClickListener{action()}
            setOnKeyListener{_,key,event->
                if(event.action!=KeyEvent.ACTION_DOWN)false else when(key){
                    KeyEvent.KEYCODE_DPAD_LEFT->{step(-1);true}
                    KeyEvent.KEYCODE_DPAD_RIGHT->{step(1);true}
                    KeyEvent.KEYCODE_BACK->{dismiss();true}
                    else->false
                }
            }
        }
        controls.addView(button,LinearLayout.LayoutParams(0,-1,1f).apply{rightMargin=(12*unit).toInt()})
    }
    private fun render(){
        val item=items[index]
        title.text=item.title;description.text=item.description
        description.visibility=if(item.description.isBlank())GONE else VISIBLE
        position.text="${index+1} of ${items.size} · Featured content"
        controls.removeAllViews()
        addButton("Open in app"){openLink(item.openUri)}
        if(item.infoUri.isNotBlank())addButton("More Info"){openLink(item.infoUri)}
        controls.getChildAt(0)?.post{controls.getChildAt(0)?.requestFocus()}
    }
    fun step(delta:Int){if(items.size<2)return;index=(index+delta+items.size)%items.size;render();title.alpha=.75f;title.animate().alpha(1f).setDuration(if(prefs.motion==0)0 else 170).start()}
    fun leave(done:()->Unit){animate().cancel();animate().alpha(0f).setDuration(if(prefs.motion==0)0 else 170).withEndAction(done).start()}
}
