package dev.luma.launcher

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.util.concurrent.Executors

/** User-confirmed text only; never runs against an app different from the menu's source. */
object KeyboardDelivery {
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    fun submit(context:Context,source:String?,text:String){
        val app=context.applicationContext
        if(source==null || !source.matches(Regex("[A-Za-z0-9_.]+")) || source==app.packageName){
            Toast.makeText(app,"Open an app's search field before choosing Keyboard",Toast.LENGTH_LONG).show();return
        }
        main.postDelayed({
            if(LumaInputMethodService.submitOverlay(source,text))return@postDelayed
            worker.execute{
                val result=runCatching{
                    val command=KeyboardTextCommand.build(source,text)
                    check(LoopbackAdb().request("shell:$command").trim()=="LUMA_INPUT_DONE"){
                        "Open the app's search field and try Keyboard again"
                    }
                }
                main.post{result.onFailure{
                    Toast.makeText(app,if(text.any{it.code !in 32..126})
                        "These characters need the app's Android text field and Luma Keyboard"
                        else "Open the app's search field and enable Projector-only Menu, then try again",Toast.LENGTH_LONG).show()
                }}
            }
        },450)
    }
}

internal object KeyboardTextCommand {
    private fun quote(value:String)="'"+value.replace("'","'\\''")+"'"
    fun build(source:String,text:String):String{
        require(source.matches(Regex("[A-Za-z0-9_.]+")))
        require(text.length<=256 && text.all{it.code in 32..126})
        // Android input interprets %s as a space; send literal percent signs separately.
        val pieces=text.split('%')
        val typing=pieces.mapIndexed{index,part->
            (if(index>0)"input text '%'; " else "")+(if(part.isNotEmpty())"input text ${quote(part)}; " else "")
        }.joinToString("")
        return "focus=\$(dumpsys window | grep -m 1 mCurrentFocus); case \"\$focus\" in *\" $source/\"*) "+
            typing+"input keyevent 66; printf LUMA_INPUT_DONE ;; *) printf APP_CHANGED ;; esac"
    }
}
