package dev.luma.launcher

import org.junit.Assert.*
import org.junit.Test

class VideoSearchTargetTest {
    @Test fun acceptsOnlyTheSupportedApps(){
        assertEquals("YouTube",VideoSearchTarget.forPackage("com.google.android.youtube.tv")?.label)
        assertEquals("TizenTube",VideoSearchTarget.forPackage("io.gh.reisxd.tizentube.cobalt")?.label)
        assertEquals("Prime Video",VideoSearchTarget.forPackage("com.amazon.amazonvideo.livingroom")?.label)
        assertNull(VideoSearchTarget.forPackage(null));assertNull(VideoSearchTarget.forPackage("example.other.app"))
    }
    @Test fun primeSearchUsesItsOwnEncodedLink(){
        val target=VideoSearchTarget.forPackage("com.amazon.amazonvideo.livingroom")!!
        assertEquals("com.amazon.ignition.IgnitionActivity",target.activityName)
        assertEquals("https://app.primevideo.com/search?phrase=caf%C3%A9%20%26%20music%3F%23",target.searchUrl(" café & music?# "))
    }
    @Test fun searchTextCannotEscapeItsQueryParameter(){
        val target=VideoSearchTarget.forPackage("io.gh.reisxd.tizentube.cobalt")!!
        assertEquals("https://www.youtube.com/results?search_query=caf%C3%A9%20%26%20music%3F%23",target.searchUrl(" café & music?# "))
        assertEquals("dev.cobalt.app.MainActivity",target.activityName)
    }
}
