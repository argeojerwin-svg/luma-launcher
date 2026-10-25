package dev.luma.launcher

import android.content.Context
import android.content.pm.PackageManager
import android.media.tv.TvContract
import android.os.Build
import android.util.Log

/** Reads only searchable Android TV Watch Next entries with the user's TV-listings grant. */
object WatchNextSource {
    const val PERMISSION="android.permission.READ_TV_LISTINGS"
    private val supported=setOf("com.amazon.amazonvideo.livingroom","com.disney.disneyplus",
        "com.apple.atve.androidtv.appletv","com.stremio.one")
    fun permitted(context:Context)=Build.VERSION.SDK_INT>=26 &&
        context.checkSelfPermission(PERMISSION)==PackageManager.PERMISSION_GRANTED
    fun read(context:Context):List<ContinueItem>{
        if(!permitted(context))return emptyList()
        val columns=arrayOf("_id","title","package_name","season_display_number","episode_display_number",
            "episode_title","duration_millis","last_playback_position_millis","last_engagement_time_utc_millis",
            "poster_art_uri","intent_uri","watch_next_type")
        return runCatching{
            val result=mutableListOf<ContinueItem>()
            context.contentResolver.query(TvContract.WatchNextPrograms.CONTENT_URI,columns,null,null,null)?.use{cursor->
                fun string(name:String)=cursor.getString(cursor.getColumnIndexOrThrow(name)).orEmpty()
                fun number(name:String)=cursor.getLong(cursor.getColumnIndexOrThrow(name))
                while(cursor.moveToNext() && result.size<30){
                    val pkg=string("package_name");val title=string("title")
                    // Android TV types 0 and 1 are Continue and Next; later types are recommendations.
                    if(pkg !in supported || title.isBlank() || number("watch_next_type") !in 0L..1L)continue
                    val duration=number("duration_millis");val position=number("last_playback_position_millis")
                    val percent=if(duration>0 && position>0 && position<duration)(position*100f/duration).coerceIn(0f,99.9f) else null
                    val season=string("season_display_number");val episode=string("episode_display_number")
                    val detail=listOfNotNull(if(season.isNotBlank() && episode.isNotBlank())"S${season} E${episode}" else null,
                        string("episode_title").ifBlank{null}).joinToString(" · ")
                    val label=when(pkg){"com.amazon.amazonvideo.livingroom"->"Prime Video";"com.disney.disneyplus"->"Disney+";
                        "com.apple.atve.androidtv.appletv"->"Apple TV";else->"Stremio"}
                    val link=string("intent_uri")
                    result.add(ContinueItem("watchnext:${number("_id")}",title,detail,percent,
                        number("last_engagement_time_utc_millis").toString(),string("poster_art_uri"),"",pkg,label,
                        link,"Android TV Watch Next",
                        if(label=="Apple TV")AppleTitleArtwork.forLink(context,link) else ""))
                }
            }
            result.sortedByDescending{it.pausedAt.toLongOrNull()?:0L}
        }.onFailure{Log.w("LumaWatchNext","Watch Next unavailable: ${it.javaClass.simpleName}")}.getOrDefault(emptyList())
    }
    /** Stremio publishes its own Continue Watching TV channel, not Watch Next rows on the P6. */
    fun readStremioChannel(context:Context):List<ContinueItem>{
        if(!permitted(context))return emptyList()
        return runCatching{
            val channels=mutableListOf<Long>()
            context.contentResolver.query(TvContract.Channels.CONTENT_URI,
                arrayOf("_id","package_name","display_name"),null,null,null)?.use{cursor->
                while(cursor.moveToNext())if(cursor.getString(1)=="com.stremio.one" &&
                    cursor.getString(2)?.equals("Continue Watching",true)==true)
                    channels.add(cursor.getLong(0))
            }
            val items=mutableListOf<ContinueItem>()
            val columns=arrayOf("_id","channel_id","title","season_display_number","episode_display_number",
                "duration_millis","last_playback_position_millis","thumbnail_uri","poster_art_uri","intent_uri","package_name")
            for(channel in channels){
                context.contentResolver.query(TvContract.PreviewPrograms.CONTENT_URI,columns,
                    null,null,null)?.use{cursor->
                    fun string(name:String)=cursor.getString(cursor.getColumnIndexOrThrow(name)).orEmpty()
                    fun number(name:String)=cursor.getLong(cursor.getColumnIndexOrThrow(name))
                    while(cursor.moveToNext() && items.size<30){
                        if(number("channel_id")!=channel || string("package_name")!="com.stremio.one")continue
                        val title=string("title");val link=string("intent_uri")
                        if(title.isBlank() || !link.startsWith("stremio:///detail/"))continue
                        val duration=number("duration_millis");val position=number("last_playback_position_millis")
                        val progress=if(duration>0 && position>0 && position<duration)
                            (position*100f/duration).coerceIn(0f,99.9f) else null
                        val season=string("season_display_number");val episode=string("episode_display_number")
                        val detail=if(season.isNotBlank() && episode.isNotBlank())"S$season E$episode" else ""
                        val art=string("thumbnail_uri").ifBlank{string("poster_art_uri")}
                        val poster=string("poster_art_uri")
                        items.add(ContinueItem("stremio-channel:${number("_id")}",title,detail,progress,
                            "",art,"","com.stremio.one","Stremio",link,"Stremio TV channel",poster))
                    }
                }
            }
            items
        }.onFailure{Log.w("LumaWatchNext","Stremio channel unavailable: ${it.javaClass.simpleName}")}
            .getOrDefault(emptyList())
    }
}
