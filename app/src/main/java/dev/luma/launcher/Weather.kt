package dev.luma.launcher

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/** Small foreground-only client. Cached values are keyed to the selected location. */
class Weather(context: Context, private val cacheName:String="weather", private val request:((String)->JSONObject)?=null, private val changed: (String,String)->Unit) {
    data class Place(val name:String,val latitude:Double,val longitude:Double)
    private val prefs=context.getSharedPreferences(cacheName,Context.MODE_PRIVATE)
    private val worker=Executors.newSingleThreadExecutor()
    private val main=Handler(Looper.getMainLooper())
    private var active=false
    private var closed=false
    private var busy=false
    private var generation=0
    private var failed=false
    val location:String get()=prefs.getString("name","London, UK")!!
    private val tick=object:Runnable{override fun run(){if(active){refresh();main.postDelayed(this,20*60*1000L)}}}
    fun start(){active=true;render();main.removeCallbacks(tick);main.post(tick)}
    fun stop(){active=false;main.removeCallbacks(tick)}
    fun close(){closed=true;stop();main.removeCallbacksAndMessages(null);worker.shutdownNow()}
    fun select(place:Place){generation++;prefs.edit().clear().putString("name",place.name).putString("lat",place.latitude.toString()).putString("lon",place.longitude.toString()).apply();failed=false;render();refresh()}
    fun search(query:String,done:(List<Place>?)->Unit){worker.execute{
        val result=runCatching{val json=fetch("https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(query,"UTF-8")}&count=5&language=en&format=json")
            val values=json.optJSONArray("results");(0 until (values?.length()?:0)).map{val p=values!!.getJSONObject(it);Place(listOf(p.getString("name"),p.optString("admin1"),p.optString("country")).filter{it.isNotBlank()}.distinct().joinToString(", "),p.getDouble("latitude"),p.getDouble("longitude"))}}.getOrNull()
        main.post{if(!closed)done(result)}
    }}
    private fun refresh(){
        val age=System.currentTimeMillis()-prefs.getLong("saved",0)
        if(busy || age in 0 until 20*60*1000L)return
        busy=true;val request=generation
        val lat=prefs.getString("lat","51.5074");val lon=prefs.getString("lon","-0.1278")
        worker.execute{
            val result=runCatching{val c=fetch("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code,is_day&temperature_unit=celsius&timezone=auto").getJSONObject("current")
                val temp=c.getDouble("temperature_2m");require(temp.isFinite() && temp in -100.0..70.0)
                Triple(temp,c.getInt("weather_code"),c.getInt("is_day"))}
            main.post{if(!closed){busy=false;if(request==generation){failed=result.isFailure;result.onSuccess{(t,c,d)->prefs.edit().putFloat("temperature",t.toFloat()).putInt("code",c).putInt("day",d).putLong("saved",System.currentTimeMillis()).apply()};render()}else if(active)refresh()}}
        }
    }
    private fun render(){
        val saved=prefs.getLong("saved",0)
        if(saved==0L){changed(if(failed)"—  Weather unavailable" else "—  Loading weather",location);return}
        val (symbol,label)=condition(prefs.getInt("code",0),prefs.getInt("day",1)==1)
        val stale=failed || System.currentTimeMillis()-saved>40*60*1000L
        val reading="$symbol  ${prefs.getFloat("temperature",0f).roundToInt()}°C  ·  $label"
        changed(if(stale)"$symbol  ${prefs.getFloat("temperature",0f).roundToInt()}°C · Cached" else reading, "$location · $reading${if(stale)" · Cached reading" else ""}")
    }
    private fun fetch(address:String):JSONObject{
        request?.let{return it(address)}
        val c=URL(address).openConnection() as HttpURLConnection
        c.connectTimeout=8000;c.readTimeout=8000
        try{check(c.responseCode==200);val text=c.inputStream.bufferedReader().use{reader->val out=StringBuilder();val buffer=CharArray(2048);var count=reader.read(buffer);while(count>=0){out.append(buffer,0,count);check(out.length<100000);count=reader.read(buffer)};out.toString()};return JSONObject(text)}finally{c.disconnect()}
    }
    companion object {
        fun condition(code:Int,day:Boolean):Pair<String,String> = when(code){
            0->if(day)"☀" to "Sunny" else "☾" to "Clear"
            1,2->"☁" to "Partly cloudy"
            3->"☁" to "Cloudy"
            45,48->"≋" to "Foggy"
            51,53,55,56,57->"☂" to "Drizzle"
            61,63,65,66,67,80,81,82->"☂" to "Rainy"
            71,73,75,77,85,86->"❄" to "Snowy"
            95,96,99->"ϟ" to "Stormy"
            else->"◇" to "Weather"
        }
    }
}
