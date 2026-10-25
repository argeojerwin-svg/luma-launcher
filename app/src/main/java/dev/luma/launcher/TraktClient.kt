package dev.luma.launcher

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService

data class ContinueItem(val id:String,val title:String,val subtitle:String,val progress:Float?,
    val pausedAt:String,val imageUrl:String,val stremioUri:String,
    val sourcePackage:String="",val sourceLabel:String="Trakt",val openUri:String="",val origin:String="Trakt",
    val tileImageUrl:String="")
data class TraktDeviceCode(val code:String,val url:String,val deviceCode:String,val expiresIn:Int,val interval:Int)

/** Official Trakt device OAuth and /sync/playback; network work never runs on the UI thread. */
class TraktClient(private val prefs:Preferences, private val secure:SecureStore,private val worker:ExecutorService){
    private val main=Handler(Looper.getMainLooper())
    @Volatile private var generation=0
    val connected get()=secure.get("traktTokens")!=null
    val cached:List<ContinueItem> get()=parseCached(secure.get("traktHistory")?:"[]")
    fun cancel(){generation++}
    fun disconnect(){generation++;secure.clear("traktTokens");secure.clear("traktHistory");prefs.put("traktSyncAt","")}
    fun begin(done:(TraktDeviceCode?,String?)->Unit){
        val clientId=prefs.text("traktClientId").trim()
        if(clientId.isBlank()){done(null,"Enter your Trakt client ID first.");return}
        val request=++generation
        worker.execute{
            val result=runCatching{
                val response=http("https://auth.trakt.tv/oauth/device/code","POST",JSONObject().put("client_id",clientId),null,clientId)
                if(response.first!=200)error("Trakt code request failed (${response.first}).")
                val json=JSONObject(response.second)
                TraktDeviceCode(json.getString("user_code"),json.getString("verification_url"),json.getString("device_code"),json.getInt("expires_in"),json.getInt("interval"))
            }
            main.post{if(generation==request)done(result.getOrNull(),result.exceptionOrNull()?.message)}
            val code=result.getOrNull()?:return@execute
            val deadline=System.currentTimeMillis()+code.expiresIn*1000L
            while(generation==request && System.currentTimeMillis()<deadline){
                try{Thread.sleep(code.interval.coerceAtLeast(5)*1000L)}catch(_:InterruptedException){return@execute}
                if(generation!=request)break
                val response=runCatching{http("https://auth.trakt.tv/oauth/device/token","POST",
                    JSONObject().put("code",code.deviceCode).put("client_id",clientId),null,clientId)}.getOrNull()?:continue
                if(response.first==200){saveTokens(JSONObject(response.second));main.post{if(generation==request)done(null,null)};break}
                if(response.first==429){try{Thread.sleep(5000)}catch(_:InterruptedException){return@execute}}
                if(response.first in listOf(404,410,418)){main.post{if(generation==request)done(null,"Trakt authorization ended (${response.first}).")};break}
            }
            if(generation==request && !connected)main.post{if(generation==request)done(null,"Trakt authorization timed out. Try Connect again.")}
        }
    }
    fun refresh(done:(List<ContinueItem>?,String?)->Unit){
        val clientId=prefs.text("traktClientId").trim()
        if(clientId.isBlank()){done(null,"Trakt client ID is missing.");return}
        val request=++generation
        worker.execute{
            val result=runCatching{
                var tokens=JSONObject(secure.get("traktTokens")?:error("Connect Trakt first."))
                val expires=tokens.optLong("created_at")+tokens.optLong("expires_in")
                if(expires<System.currentTimeMillis()/1000+300){
                    val body=JSONObject().put("refresh_token",tokens.getString("refresh_token"))
                        .put("client_id",clientId).put("redirect_uri",prefs.text("traktRedirectUri"))
                        .put("grant_type","refresh_token")
                    val response=http("https://auth.trakt.tv/oauth/token","POST",body,null,clientId)
                    if(response.first!=200)error("Trakt connection expired. Reconnect in settings.")
                    tokens=JSONObject(response.second);saveTokens(tokens)
                }
                val response=http("https://api.trakt.tv/sync/playback?limit=16&extended=full","GET",null,tokens.getString("access_token"),clientId)
                if(response.first!=200)error("Trakt sync unavailable (${response.first}).")
                val upNextResult=runCatching{
                    val next=http("https://api.trakt.tv/sync/progress/up_next?limit=16&extended=full,images","GET",null,tokens.getString("access_token"),clientId)
                    prefs.put("traktUpNextStatus",if(next.first==200)"Available" else "Unavailable (${next.first})")
                    if(next.first==200)next.second else "[]"
                }
                if(upNextResult.isFailure)prefs.put("traktUpNextStatus","Network error")
                val upNext=upNextResult.getOrDefault("[]")
                val items=merge(parsePlayback(response.second),parseUpNext(upNext))
                secure.put("traktHistory",JSONObject().put("playback",JSONArray(response.second)).put("up_next",JSONArray(upNext)).toString())
                prefs.put("traktSyncAt",System.currentTimeMillis().toString())
                items
            }
            main.post{if(generation==request)done(result.getOrNull(),result.exceptionOrNull()?.message)}
        }
    }
    private fun saveTokens(tokens:JSONObject){secure.put("traktTokens",tokens.toString())}
    private fun parseCached(json:String):List<ContinueItem> = runCatching{
        if(json.trimStart().startsWith("["))parsePlayback(json)
        else JSONObject(json).let{merge(parsePlayback(it.optJSONArray("playback")?.toString()?:"[]"),parseUpNext(it.optJSONArray("up_next")?.toString()?:"[]"))}
    }.getOrDefault(emptyList())
    private fun merge(paused:List<ContinueItem>,next:List<ContinueItem>):List<ContinueItem>{
        val pausedShowIds=paused.mapNotNull{it.stremioUri.substringAfter("/series/","").substringBefore('/').ifBlank{null}}.toSet()
        return (paused+next.filter{it.stremioUri.substringAfter("/series/","").substringBefore('/') !in pausedShowIds}).take(16)
    }
    internal fun parsePlayback(json:String):List<ContinueItem> = runCatching{
        val array=JSONArray(json)
        (0 until minOf(array.length(),16)).mapNotNull{index->
            val row=array.getJSONObject(index)
            val type=row.optString("type")
            val media=if(type=="movie")row.optJSONObject("movie")else row.optJSONObject("show")
            val episode=row.optJSONObject("episode")
            if(media==null)return@mapNotNull null
            val title=media.optString("title").ifBlank{return@mapNotNull null}
            val subtitle=if(type=="episode" && episode!=null)"S${episode.optInt("season")} E${episode.optInt("number")} · ${episode.optString("title")}" else "Film"
            val images=media.optJSONObject("images")
            val image=images?.optJSONArray("fanart")?.optString(0).orEmpty().ifBlank{images?.optJSONArray("poster")?.optString(0).orEmpty()}
            val imdb=media.optJSONObject("ids")?.optString("imdb").orEmpty()
            val link=if(imdb.startsWith("tt") && type=="movie")"stremio:///detail/movie/$imdb/$imdb" else if(imdb.startsWith("tt") && episode!=null)
                "stremio:///detail/series/$imdb/$imdb:${episode.optInt("season")}:${episode.optInt("number")}" else ""
            ContinueItem(row.optString("id","$type:$title"),title,subtitle,row.optDouble("progress",0.0).toFloat().coerceIn(0f,100f),
                row.optString("paused_at"),if(image.startsWith("https://"))image else if(image.isNotBlank())"https://$image" else "",link)
        }
    }.getOrDefault(emptyList())
    internal fun parseUpNext(json:String):List<ContinueItem> = runCatching{
        val array=JSONArray(json)
        (0 until minOf(array.length(),16)).mapNotNull{index->
            val row=array.getJSONObject(index);val show=row.optJSONObject("show")?:return@mapNotNull null
            val progress=row.optJSONObject("progress")?:return@mapNotNull null
            val episode=progress.optJSONObject("next_episode")?:return@mapNotNull null
            val title=show.optString("title").ifBlank{return@mapNotNull null}
            val season=episode.optInt("season");val number=episode.optInt("number")
            val imdb=show.optJSONObject("ids")?.optString("imdb").orEmpty()
            val image=show.optJSONObject("images")?.let{it.optJSONArray("fanart")?.optString(0).orEmpty().ifBlank{it.optJSONArray("poster")?.optString(0).orEmpty()}}.orEmpty()
            ContinueItem("next:$imdb:$season:$number",title,"Next · S$season E$number · ${episode.optString("title")}",null,
                progress.optString("last_watched_at"),if(image.startsWith("https://"))image else if(image.isNotBlank())"https://$image" else "",
                if(imdb.startsWith("tt"))"stremio:///detail/series/$imdb/$imdb:$season:$number" else "")
        }
    }.getOrDefault(emptyList())
    private fun http(address:String,method:String,body:JSONObject?,token:String?,clientId:String):Pair<Int,String>{
        val conn=URL(address).openConnection() as HttpURLConnection
        try{
            conn.requestMethod=method;conn.connectTimeout=8000;conn.readTimeout=8000
            conn.setRequestProperty("Content-Type","application/json")
            conn.setRequestProperty("trakt-api-version","2")
            conn.setRequestProperty("trakt-api-key",clientId)
            if(token!=null)conn.setRequestProperty("Authorization","Bearer $token")
            if(body!=null){conn.doOutput=true;conn.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}}
            val code=conn.responseCode
            val stream=if(code in 200..299)conn.inputStream else conn.errorStream
            val bytes=stream?.use{input->
                val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                while(out.size()<256*1024){val count=input.read(buffer,0,minOf(buffer.size,256*1024-out.size()));if(count<0)break;out.write(buffer,0,count)}
                out.toByteArray()
            }?:byteArrayOf()
            return code to String(bytes,Charsets.UTF_8)
        }finally{conn.disconnect()}
    }
}
