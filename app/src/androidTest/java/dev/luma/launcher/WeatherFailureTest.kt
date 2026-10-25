package dev.luma.launcher

import android.test.InstrumentationTestCase
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Suppress("DEPRECATION")
class WeatherFailureTest:InstrumentationTestCase(){
    fun testFailedRefreshRetainsCacheAndLocationChangeClearsIt(){
        val context=instrumentation.targetContext
        val prefs=context.getSharedPreferences("weather-test-isolated",0)
        prefs.edit().clear().putString("name","Test place").putFloat("temperature",12f).putInt("code",3).putLong("saved",System.currentTimeMillis()-3600000).commit()
        val cached=CountDownLatch(1);val unavailable=CountDownLatch(1)
        var cachedCallbacks=0
        lateinit var client:Weather
        try{
            instrumentation.runOnMainSync{
                client=Weather(context,"weather-test-isolated",{throw IOException("Simulated offline")}){text,_->
                    if(text.contains("12°C") && text.contains("Cached")){cachedCallbacks++;if(cachedCallbacks>=2)cached.countDown()}
                    if(text.contains("unavailable"))unavailable.countDown()
                };client.start()
            }
            assertTrue(cached.await(5,TimeUnit.SECONDS))
            assertEquals(12f,prefs.getFloat("temperature",0f))
            instrumentation.runOnMainSync{client.select(Weather.Place("Other place",1.0,2.0))}
            assertTrue(unavailable.await(5,TimeUnit.SECONDS))
            assertFalse("Previous city's temperature must not survive location change",prefs.contains("temperature"))
        }finally{instrumentation.runOnMainSync{client.close()};prefs.edit().clear().commit()}
    }
}
