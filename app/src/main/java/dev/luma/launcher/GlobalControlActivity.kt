package dev.luma.launcher

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import java.util.Date

/** Translucent Control Centre task that returns to the application beneath it. */
class GlobalControlActivity : Activity() {
    private var panel: GlassPanel? = null
    private var closing=false
    private var animateClose=true
    private var stopNetworkObserve:(()->Unit)?=null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Let the sheet animate over the existing app; avoid a second task/window fade.
        overridePendingTransition(0,0)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        val prefs = Preferences(this)
        animateClose=prefs.motion!=0
        fun launch(target: String) {
            finish()
            startActivity(Intent(this, LauncherActivity::class.java).apply {
                action = LauncherActivity.ACTION_GLOBAL_CONTROL
                putExtra(LauncherActivity.EXTRA_GLOBAL_TARGET, target)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            })
        }
        val actions = listOf(
            PanelAction("All Apps") { launch("all_apps") },
            PanelAction("App Switcher") { launch("app_switcher") },
            PanelAction("Search") { launch("search") },
            PanelAction("Audio settings") {
                finish(); startActivity(Intent(Settings.ACTION_SOUND_SETTINGS))
            },
            PanelAction("Wallpaper") { launch("wallpaper") },
            PanelAction("Start screensaver") { launch("screensaver") },
            PanelAction("More controls") { launch("more") }
        )
        val root = FrameLayout(this)
        val sheet = GlassPanel(this, WallpaperView(this), prefs, "Control Centre", prefs.profileName, actions,
            networkAction=PanelAction(NetworkState.current.detail){
                finish();runCatching{startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))}
                    .onFailure{startActivity(Intent(Settings.ACTION_SETTINGS))}
            },controlOpenMs=280,controlCloseMs=260)
        sheet.setClockText(android.text.format.DateFormat.getTimeFormat(this).format(Date()))
        root.addView(sheet, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        panel = sheet
        sheet.enter(prefs.motion != 0)
        root.post { sheet.focusAt(0) }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); dismiss() }
    override fun onResume(){super.onResume();stopNetworkObserve=NetworkState.observe(this){panel?.setNetworkStatus(it.detail)}}
    override fun onPause(){stopNetworkObserve?.invoke();stopNetworkObserve=null;super.onPause()}
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) dismiss()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
    private fun dismiss(){
        if(closing)return
        closing=true
        panel?.leave(animateClose){finish();overridePendingTransition(0,0)}
            ?:run{finish();overridePendingTransition(0,0)}
    }
}
