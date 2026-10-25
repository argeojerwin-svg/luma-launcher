package dev.luma.launcher

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.*

/** Shared remote-friendly text editor; Android's keyboard remains responsible for text entry. */
object GlassNameDialog {
    fun show(activity:Activity,wallpaper:WallpaperView,prefs:Preferences,title:String,value:String,limit:Int,saved:(String)->Unit):Dialog{
        fun dp(value:Int)=(value*activity.resources.displayMetrics.density).toInt()
        val dialog=Dialog(activity);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val card=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(24),dp(24),dp(24),dp(24));background=GlassDrawable(this,wallpaper,prefs,dp(28).toFloat(),true)}
        card.addView(TextView(activity).apply{text=title;textSize=DesignTokens.headingSp;setTextColor(Color.WHITE)},LinearLayout.LayoutParams(-1,-2))
        val field=EditText(activity).apply{
            id=View.generateViewId();setText(value);setSingleLine();setTextColor(Color.WHITE);textSize=DesignTokens.rowSp
            filters=arrayOf(android.text.InputFilter.LengthFilter(limit));setPadding(dp(16),dp(12),dp(16),dp(12))
            background=GlassDrawable(this,wallpaper,prefs,dp(14).toFloat());setSelectAllOnFocus(true)
        }
        card.addView(field,LinearLayout.LayoutParams(-1,dp(60)).apply{topMargin=dp(20);bottomMargin=dp(16)})
        val row=LinearLayout(activity).apply{orientation=LinearLayout.HORIZONTAL}
        val save=GlassButton(activity,wallpaper,prefs,"Save").apply{id=View.generateViewId();setOnClickListener{
            val name=field.text.toString().replace('\n',' ').replace('\r',' ').trim()
            if(name.isNotEmpty()){dialog.dismiss();saved(name)}else field.error="Enter a name"
        }}
        val cancel=GlassButton(activity,wallpaper,prefs,"Cancel").apply{id=View.generateViewId();setOnClickListener{dialog.dismiss()}}
        row.addView(save,LinearLayout.LayoutParams(0,dp(58),1f));row.addView(cancel,LinearLayout.LayoutParams(0,dp(58),1f).apply{leftMargin=dp(10)})
        card.addView(row);dialog.setContentView(card)
        field.nextFocusDownId=save.id;save.nextFocusUpId=field.id;cancel.nextFocusUpId=field.id;save.nextFocusRightId=cancel.id;cancel.nextFocusLeftId=save.id
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT));dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        dialog.show();dialog.window?.setLayout((activity.resources.displayMetrics.widthPixels*.48f).toInt(),-2);dialog.window?.setGravity(Gravity.CENTER)
        field.requestFocus()
        return dialog
    }
}
