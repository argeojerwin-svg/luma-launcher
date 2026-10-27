package dev.luma.launcher

import java.net.URLEncoder

/** Explicit destinations for apps whose TV search UI does not request the Android IME. */
data class VideoSearchTarget(val packageName:String,val activityName:String,val label:String){
    fun searchUrl(query:String):String = (if(packageName=="com.amazon.amazonvideo.livingroom")
        "https://app.primevideo.com/search?phrase=" else "https://www.youtube.com/results?search_query=")+
        URLEncoder.encode(query.trim(),"UTF-8").replace("+","%20")
    companion object {
        fun forPackage(packageName:String?):VideoSearchTarget?=when(packageName){
            "com.google.android.youtube.tv"->VideoSearchTarget(packageName,"com.google.android.apps.youtube.tv.activity.ShellActivity","YouTube")
            "io.gh.reisxd.tizentube.cobalt"->VideoSearchTarget(packageName,"dev.cobalt.app.MainActivity","TizenTube")
            "com.amazon.amazonvideo.livingroom"->VideoSearchTarget(packageName,"com.amazon.ignition.IgnitionActivity","Prime Video")
            else->null
        }
    }
}
