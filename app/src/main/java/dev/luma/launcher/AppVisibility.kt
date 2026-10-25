package dev.luma.launcher

/** Classification is independent of launcher visibility and never changes Android package state. */
enum class AppCategory(val title:String){ENTERTAINMENT("Entertainment"),USER("User app"),SYSTEM("System app"),MANUFACTURER("Projector utility"),DEVELOPMENT("Development tool")}
object AppVisibility {
    private val entertainment=setOf("com.apple.atve.androidtv.appletv","com.google.android.youtube.tv","com.google.android.youtube","com.spotify.tv.android","com.spotify.music","com.stremio.one","com.netflix.ninja","com.netflix.mediaclient","com.amazon.amazonvideo.livingroom","com.amazon.avod.thirdpartyclient","com.disney.disneyplus","com.plexapp.android","org.videolan.vlc","org.xbmc.kodi","com.google.android.videos","tv.twitch.android.app","com.bskyb.nowtv.beta","com.bbc.iplayer","air.ITVMobilePlayer","com.channel4.ondemand","com.hbo.hbonow","com.wbd.stream","com.cbs.ott","com.cbs.ca","com.apple.android.music")
    fun classify(app:AppEntry):AppCategory {
        if(app.id in entertainment)return AppCategory.ENTERTAINMENT
        val evidence="${app.id} ${app.label}".lowercase()
        if(listOf("factory","factest","developtools","webview_shell","dragonbox","dragonatt","debug","tester").any{evidence.contains(it)})return AppCategory.DEVELOPMENT
        if(app.isHome || listOf("com.softwinner.","com.yhk.","com.aumi.","com.allwinnertech.","com.efercro.").any{app.id.startsWith(it)})return AppCategory.MANUFACTURER
        // Android CATEGORY_AUDIO, CATEGORY_VIDEO and CATEGORY_GAME, including preinstalled media apps.
        if(app.applicationCategory in setOf(0,1,2))return AppCategory.ENTERTAINMENT
        if(app.id=="com.android.vending" || app.id=="zeasn.open.technical.tv.store")return AppCategory.USER
        if(app.systemApp)return AppCategory.SYSTEM
        return AppCategory.USER
    }
    fun visible(app:AppEntry,override:String,hidden:Set<String>,allow:Set<String>,block:Set<String>):Boolean = when {
        override=="show"->true
        override=="hide"->false
        app.id in hidden->false
        app.id in block->false
        app.id in allow->true
        else->classify(app)==AppCategory.ENTERTAINMENT
    }
    fun visible(app:AppEntry,prefs:Preferences)=visible(app,prefs.text("visibility:${app.id}"),
        (prefs.list("hidden")+prefs.list("removed")).toSet(),prefs.list("allowlist").toSet(),prefs.list("blocklist").toSet())
    fun dock(apps:List<AppEntry>,prefs:Preferences):List<String> {
        if(prefs.text("curatedDockInitialized").isBlank()){
            val visible=apps.filter{visible(it,prefs)}
            val saved=prefs.list("favourites").filter{id->visible.any{it.id==id}}
            prefs.putList("dock",if(saved.isNotEmpty())saved.take(6)else visible.take(6).map{it.id})
            prefs.put("curatedDockInitialized","1")
        }
        val folders=Folders.read(prefs).map{it.tileId}.toSet()
        return prefs.list("dock").filter{id->id in folders || apps.any{it.id==id && visible(it,prefs)}}.take(6)
    }
}
