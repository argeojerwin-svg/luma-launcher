package dev.luma.launcher

import android.test.InstrumentationTestCase

/** Live-device validation without changing user preferences or launching factory utilities. */
@Suppress("DEPRECATION")
class BrandArtworkTest:InstrumentationTestCase(){
    fun testNamesAndBoundedArtworkForEveryInstalledApp(){
        val repo=AppRepository(instrumentation.targetContext)
        val entries=repo.discover()
        assertTrue(entries.isNotEmpty())
        for(app in entries){
            val originalComponent=app.component
            val art=repo.artwork(app)
            assertEquals(320,art.bitmap.width);assertEquals(192,art.bitmap.height)
            assertTrue("Artwork memory exceeded for ${app.id}",art.bytes<=245760)
            assertNull(art.middle);assertNull(art.foreground)
            assertEquals(originalComponent,app.component)
            if(app.id=="com.android.vending")assertEquals("Apps",repo.displayName(app))
            else if(app.label.isNotBlank())assertEquals(app.label,repo.displayName(app))
        }
        for(id in listOf("com.zeasn.launcher","com.efercro.tvcast")){
            assertTrue(instrumentation.targetContext.packageManager.getApplicationInfo(id,0).enabled)
        }
    }
}
