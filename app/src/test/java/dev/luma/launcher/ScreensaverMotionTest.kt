package dev.luma.launcher
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class ScreensaverMotionTest{
    @Test fun beginsAtTheUnmodifiedWallpaper(){assertEquals(ScreensaverMotion.Pose(1f,0f,0f),ScreensaverMotion.pose(0f,0))}
    @Test fun loopHasNoPositionJump(){
        val first=ScreensaverMotion.pose(0f,80000);val last=ScreensaverMotion.pose(1f,80000)
        assertEquals(first.zoom,last.zoom,.00001f);assertEquals(first.x,last.x,.00001f);assertEquals(first.y,last.y,.00001f)
    }
    @Test fun panStaysInsideOverscan(){
        for(i in 0..1000){val p=ScreensaverMotion.pose(i/1000f,80000)
            assertTrue(p.zoom in 1f..1.071f);assertTrue(abs(p.x)<=(p.zoom-1)/2);assertTrue(abs(p.y)<=(p.zoom-1)/2)
        }
    }
}
