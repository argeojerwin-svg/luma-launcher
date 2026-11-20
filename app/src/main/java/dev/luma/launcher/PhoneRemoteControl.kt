package dev.luma.launcher

import android.content.Context

/** Fixed remote actions over this P6's existing loopback control transport. */
object PhoneRemoteControl {
    @Volatile private var retained:PhoneTextSession?=null
    fun textTarget():PhoneTextSession?=retained?.takeIf{it.isOpen}
    fun callback(context:Context):((String)->Boolean)? {
        val app=context.applicationContext
        if(!FastPhoneInput.prepare(app))return null
        return {command->
            if(command=="controlCentre"){
                runCatching{
                    app.startActivity(android.content.Intent(app,GlobalControlActivity::class.java).apply{
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    })
                    true
                }.getOrDefault(false)
            }else if(command=="apps" || command=="recents" || command=="home" || command=="appSwitcher"){
                runCatching{
                    app.startActivity(android.content.Intent(app,LauncherActivity::class.java).apply{
                        action=if(command=="home") android.content.Intent.ACTION_MAIN else LauncherActivity.ACTION_GLOBAL_CONTROL
                        if(command!="home") putExtra(LauncherActivity.EXTRA_GLOBAL_TARGET,
                            if(command=="appSwitcher") "app_switcher" else "all_apps")
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    })
                    true
                }.getOrDefault(false)
            }else{
            if(command.startsWith("move:") || command.startsWith("tap:"))PhoneMouseCursor.move(app,command)
            FastPhoneInput.command(app,command)
            }
        }
    }

    @Synchronized fun register(session:PhoneTextSession){retained?.takeIf{it!==session}?.close();retained=session}
    @Synchronized fun detach(session:PhoneTextSession){
        if(retained===session && session.isOpen && session.hasRemote)session.detachText() else session.close()
    }
}
