package dev.luma.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper

/** One process-wide, event-driven source for Home and Control Centre. No location/SSID permission. */
object NetworkState {
    enum class Kind { WIFI, DISCONNECTED, DISABLED, ETHERNET, UNKNOWN }
    data class Snapshot(val kind:Kind, val detail:String, val strength:Int?=null)
    private val main=Handler(Looper.getMainLooper())
    private val observers=linkedSetOf<(Snapshot)->Unit>()
    private var app:Context?=null
    private var manager:ConnectivityManager?=null
    private var wifi:WifiManager?=null
    private var registered=false
    var current=Snapshot(Kind.UNKNOWN,"Network status unavailable")
        private set
    private val callback=object:ConnectivityManager.NetworkCallback(){
        override fun onAvailable(network:Network)=schedule()
        override fun onLost(network:Network)=schedule()
        override fun onCapabilitiesChanged(network:Network,caps:NetworkCapabilities)=schedule()
    }
    private val wifiReceiver=object:BroadcastReceiver(){
        override fun onReceive(context:Context?,intent:Intent?)=schedule()
    }
    private fun schedule(){main.post{refresh()}}
    fun observe(context:Context,observer:(Snapshot)->Unit):()->Unit {
        if(app==null){app=context.applicationContext;manager=app!!.getSystemService(ConnectivityManager::class.java)
            wifi=app!!.getSystemService(Context.WIFI_SERVICE) as? WifiManager}
        observers.add(observer)
        if(!registered){
            registered=true
            runCatching{
                if(Build.VERSION.SDK_INT>=24)manager?.registerDefaultNetworkCallback(callback)
                else manager?.registerNetworkCallback(NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),callback)
            }
            runCatching{app?.registerReceiver(wifiReceiver,IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION))}
        }
        refresh();observer(current)
        return {
            observers.remove(observer)
            if(observers.isEmpty() && registered){
                registered=false
                runCatching{manager?.unregisterNetworkCallback(callback)}
                runCatching{app?.unregisterReceiver(wifiReceiver)}
            }
        }
    }
    private fun refresh(){
        val cm=manager ?: return
        val capabilities=runCatching{cm.getNetworkCapabilities(cm.activeNetwork)}.getOrNull()
        val enabled=runCatching{wifi?.isWifiEnabled}.getOrNull()
        val next=when {
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)==true -> {
                val internet=capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                @Suppress("DEPRECATION") val rssi=runCatching{wifi?.connectionInfo?.rssi}.getOrNull()
                val strength=rssi?.takeIf{it in -110..-20}?.let{((it+100)/15).coerceIn(0,3)}
                Snapshot(Kind.WIFI,if(internet)"Wi-Fi connected · Internet available" else "Wi-Fi connected · No internet",strength)
            }
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)==true -> Snapshot(Kind.ETHERNET,"Ethernet connected · Wi-Fi not active")
            enabled==false -> Snapshot(Kind.DISABLED,"Wi-Fi off")
            enabled==true -> Snapshot(Kind.DISCONNECTED,"Wi-Fi disconnected")
            else -> Snapshot(Kind.UNKNOWN,"Network status unavailable")
        }
        if(next!=current){current=next;observers.toList().forEach{it(next)}}
    }
}
