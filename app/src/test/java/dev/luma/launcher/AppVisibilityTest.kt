package dev.luma.launcher

import android.content.ComponentName
import org.junit.Assert.*
import org.junit.Test

class AppVisibilityTest {
    private fun app(id:String="example.app",system:Boolean=false,category:Int=-1,home:Boolean=false)=AppEntry(id,"Example",ComponentName(id,"Main"),home,system,category,true)
    @Test fun preinstalledStreamingRemainsEntertainment(){assertEquals(AppCategory.ENTERTAINMENT,AppVisibility.classify(app("com.amazon.amazonvideo.livingroom",true)))}
    @Test fun mediaMetadataWorksWithoutPackageAllowlist(){assertEquals(AppCategory.ENTERTAINMENT,AppVisibility.classify(app(category=2)))}
    @Test fun manufacturerMediaUtilitiesStayHidden(){assertEquals(AppCategory.MANUFACTURER,AppVisibility.classify(app("com.softwinner.awsource",true,2)))}
    @Test fun developmentAndSystemAreDistinct(){assertEquals(AppCategory.DEVELOPMENT,AppVisibility.classify(app("com.yhk.factory",true)));assertEquals(AppCategory.SYSTEM,AppVisibility.classify(app(system=true)))}
    @Test fun explicitShowOutranksAllAutomaticRules(){assertTrue(AppVisibility.visible(app(system=true),"show",setOf("example.app"),emptySet(),setOf("example.app")))}
    @Test fun explicitHideOutranksAllowlist(){assertFalse(AppVisibility.visible(app(category=2),"hide",emptySet(),setOf("example.app"),emptySet()))}
    @Test fun oldHiddenChoiceSurvivesMigration(){assertFalse(AppVisibility.visible(app(category=2),"",setOf("example.app"),emptySet(),emptySet()))}
    @Test fun unknownUserAppsRequireOptIn(){assertFalse(AppVisibility.visible(app(),"",emptySet(),emptySet(),emptySet()))}
}
