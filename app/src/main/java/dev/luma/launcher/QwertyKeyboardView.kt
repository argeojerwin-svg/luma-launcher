package dev.luma.launcher

import android.content.Context
import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** A local D-pad keyboard; editing stays in Android's normal EditText/length filter. */
@SuppressLint("ViewConstructor") // Requires the active field and is never inflated from XML.
class QwertyKeyboardView(context:Context,private val field:EditText,private val keyHeight:Int=44,
    private val actions:Actions?=null):LinearLayout(context){
    interface Actions {
        fun insert(value:String)
        fun delete()
        fun move(direction:Int)
        fun clear()
    }
    private data class Key(val view:TextView,val center:Float)
    private val rows=mutableListOf<List<Key>>()
    private var upper=false
    private var symbols=false
    private var save:View?=null
    private var cancel:View?=null
    private var phone:View?=null
    val firstKey:View get()=rows[1].first().view
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
    init{orientation=VERTICAL;importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO;build()}
    private fun surface(fill:Int,stroke:Int)=GradientDrawable().apply{
        cornerRadius=dp(8).toFloat();setColor(fill);setStroke(dp(1),stroke)
    }
    private fun key(label:String,weight:Float=1f,selected:Boolean=false,action:()->Unit):Pair<TextView,Float>{
        val view=TextView(context).apply{
            id=View.generateViewId();text=label;contentDescription=label;gravity=Gravity.CENTER
            textSize=17f;setTextColor(Color.WHITE);includeFontPadding=false
            isFocusable=true;isFocusableInTouchMode=true;isClickable=true;isSelected=selected
            background=StateListDrawable().apply{
                addState(intArrayOf(android.R.attr.state_focused),surface(0xFF3768AD.toInt(),0xFFC1DAFF.toInt()))
                addState(intArrayOf(android.R.attr.state_pressed),surface(0xFF3768AD.toInt(),0xFFC1DAFF.toInt()))
                addState(intArrayOf(android.R.attr.state_selected),surface(0xFF29466C.toInt(),0xFF7195C4.toInt()))
                addState(intArrayOf(),surface(0xFF263649.toInt(),0xFF46566B.toInt()))
            }
            setOnClickListener{action()}
        }
        return view to weight
    }
    private fun character(value:String)=key(value){insert(value)}
    private fun row(keys:List<Pair<TextView,Float>>,padding:Float=0f){
        val container=LinearLayout(context).apply{orientation=HORIZONTAL}
        val total=keys.sumOf{it.second.toDouble()}.toFloat()+2*padding
        if(padding>0)container.addView(View(context),LayoutParams(0,1,padding))
        var position=padding
        val prepared=keys.map{(view,weight)->
            container.addView(view,LayoutParams(0,dp(keyHeight),weight).apply{setMargins(dp(2),dp(2),dp(2),dp(2))})
            Key(view,(position+weight/2)/total).also{position+=weight}
        }
        if(padding>0)container.addView(View(context),LayoutParams(0,1,padding))
        rows.add(prepared);addView(container,LayoutParams(-1,-2))
    }
    private fun build(restore:String?=null){
        removeAllViews();rows.clear()
        row("1234567890".map{character(it.toString())})
        row((if(symbols)listOf("@","#","$","%","&","*","-","_","+","=")else "qwertyuiop".map{if(upper)it.uppercase()else it.toString()}).map{character(it)})
        row((if(symbols)listOf("!","?","/",":",";","'","\"","(",")")else "asdfghjkl".map{if(upper)it.uppercase()else it.toString()}).map{character(it)},.5f)
        val lower=if(symbols)listOf(".",",","\\","[","]","<",">")else "zxcvbnm".map{if(upper)it.uppercase()else it.toString()}
        row(listOf(key("Shift",1.5f,upper){upper=!upper;build("Shift")})+lower.map{character(it)}+key("Delete",1.5f){delete()})
        row(listOf(key(if(symbols)"ABC" else "?123",1.6f){symbols=!symbols;build(if(symbols)"ABC" else "?123")},
            key("Space",4f){insert(" ")},key("←"){move(-1)},key("→"){move(1)},key("Clear",2.4f){actions?.clear()?:field.text.clear()}))
        wireFocus()
        if(restore!=null)post{rows.flatten().firstOrNull{it.view.text==restore}?.view?.requestFocus()}
    }
    fun connectFocus(save:View,cancel:View,phone:View){this.save=save;this.cancel=cancel;this.phone=phone;wireFocus()}
    private fun wireFocus(){
        rows.forEachIndexed{rowIndex,keys->keys.forEachIndexed{index,key->
            key.view.nextFocusLeftId=keys.getOrElse((index-1).coerceAtLeast(0)){key}.view.id
            key.view.nextFocusRightId=keys.getOrNull(index+1)?.view?.id?:phone?.id?:key.view.id
            key.view.nextFocusUpId=if(rowIndex==0)(if(field.isFocusable)field.id else key.view.id) else rows[rowIndex-1].minByOrNull{kotlin.math.abs(it.center-key.center)}!!.view.id
            key.view.nextFocusDownId=if(rowIndex==rows.lastIndex)(if(key.center<.5f)save else cancel)?.id?:key.view.id
                else rows[rowIndex+1].minByOrNull{kotlin.math.abs(it.center-key.center)}!!.view.id
        }}
        field.nextFocusDownId=firstKey.id
        save?.nextFocusUpId=rows.last().minByOrNull{kotlin.math.abs(it.center-.25f)}!!.view.id
        cancel?.nextFocusUpId=rows.last().minByOrNull{kotlin.math.abs(it.center-.75f)}!!.view.id
        phone?.nextFocusLeftId=rows[1].last().view.id
    }
    private fun range():Pair<Int,Int>{
        val start=field.selectionStart.takeIf{it>=0}?:field.length()
        val end=field.selectionEnd.takeIf{it>=0}?:start
        return minOf(start,end) to maxOf(start,end)
    }
    private fun insert(value:String){
        actions?.let{it.insert(value);return}
        val(start,end)=range();field.text.replace(start,end,value)
        field.setSelection((start+value.length).coerceAtMost(field.length()))
    }
    private fun delete(){
        actions?.let{it.delete();return}
        val(start,end)=range()
        if(end>start){field.text.delete(start,end);field.setSelection(start)}
        else if(start>0){val previous=Character.offsetByCodePoints(field.text,start,-1);field.text.delete(previous,start);field.setSelection(previous)}
    }
    private fun move(direction:Int){
        actions?.let{it.move(direction);return}
        val(start,end)=range()
        val cursor=if(start!=end){if(direction<0)start else end}
            else if(direction<0 && start>0)Character.offsetByCodePoints(field.text,start,-1)
            else if(direction>0 && end<field.length())Character.offsetByCodePoints(field.text,end,1)else start
        field.setSelection(cursor)
    }
}
