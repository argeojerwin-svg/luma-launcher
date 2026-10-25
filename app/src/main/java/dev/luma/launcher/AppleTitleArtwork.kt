package dev.luma.launcher

import android.content.Context
import android.net.Uri
import java.net.HttpURLConnection
import java.net.URL

/** Apple TV's public show/movie card artwork; Watch Next only provides episode hero art. */
internal object AppleTitleArtwork {
    private const val prefix="https://is1-ssl.mzstatic.com/image/thumb/"
    private val known=mapOf(
        "umc.cmc.vtoh0mn0xn7t3c643xqonfzy" to "MRPCblIBNM67z6sEZkZ95w", // Ted Lasso
        "umc.cmc.3yksgc857px0k0rqe5zd4jice" to "ZtyKiwTObXDZB_UXDuwN_Q", // Silo
        "umc.cmc.4luj45vtqpmjsvb6sc2675oeg" to "am2-K0GPj3nmdWNUZUmbhQ", // Dark Matter
        "umc.cmc.5983fipzqbicvrve6jdfep4x3" to "8ihcMs739r4qHuIb4_eh9Q", // Foundation
        "umc.cmc.5qo7t3nngb2vj0m9dxkwebw1o" to "jhdQx4PiE8EUZFT-BZXo7A", // Lucky
        "umc.cmc.3qvemsb6f04actuyzdporeelt" to "FfqFKw_OyDYO88rTc2y_4w", // The Last First: Winter K2
        "umc.cmc.o5z5ztufuu3uv8lx7m0jcega" to "ZJDhzaXXUQDd01pwHW2rVg" // Greyhound
    )

    fun forLink(context:Context,link:String):String {
        val uri=runCatching{Uri.parse(link)}.getOrNull()?:return ""
        if(uri.scheme!="https" || uri.host!="tv.apple.com")return ""
        val showId=uri.getQueryParameter("showId")
        val id=showId?:uri.lastPathSegment.orEmpty()
        if(!id.matches(Regex("umc\\.cmc\\.[a-z0-9]+")))return ""
        known[id]?.let{return "$prefix$it/960x540.jpg"}
        val cache=context.getSharedPreferences("apple-title-art",Context.MODE_PRIVATE)
        cache.getString(id,null)?.let{return it}
        val type=if(showId!=null)"show" else "movie"
        val region=uri.pathSegments.firstOrNull()?.takeIf{it.matches(Regex("[a-z]{2}"))}?:"ae"
        val page="https://tv.apple.com/$region/$type/x/$id"
        val result=runCatching{
            val connection=URL(page).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout=3500
                connection.readTimeout=3500
                if(connection.responseCode!=200)return@runCatching ""
                val html=connection.inputStream.use{input->
                    val bytes=ByteArray(65536)
                    var count=0
                    while(count<bytes.size){
                        val n=input.read(bytes,count,bytes.size-count)
                        if(n<0)break
                        count+=n
                    }
                    String(bytes,0,count,Charsets.UTF_8)
                }
                val image=Regex("<meta property=\"og:image\" content=\"([^\"]+)\"")
                    .find(html)?.groupValues?.get(1).orEmpty()
                if(image.startsWith(prefix) && image.endsWith(".jpg"))
                    image.substringBeforeLast('/')+"/960x540.jpg" else ""
            } finally {connection.disconnect()}
        }.getOrDefault("")
        if(result.isNotBlank())cache.edit().putString(id,result).apply()
        return result
    }
}
