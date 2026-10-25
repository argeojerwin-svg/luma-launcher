package dev.luma.launcher

import org.junit.Assert.*
import org.junit.Test

class HomeGeometryTest {
    @Test fun projectedLayoutReservesFooterAndLabelSpaceAtEverySetting() {
        for(safe in 0..8)for(size in 0..2)for(gap in 0..2)for(all in listOf(false,true)) {
            val g=HomeGeometry.calculate(1920,1080,safe,size,gap,all)
            assertTrue(g.viewportTop+g.viewportHeight<g.footerTop)
            assertTrue(g.footerTop+g.footerHeight<=1040)
            assertTrue(g.iconWidth*1.14f+12*g.unit<=g.cellWidth+.1f)
            val labelTop=24*g.unit+g.iconWidth*.6f+28*g.unit
            val focusedIconBottom=24*g.unit+g.iconWidth*.6f*(1+1.14f)/2-8*g.unit
            assertTrue(labelTop-focusedIconBottom>=24*g.unit)
            assertTrue(labelTop+40*g.unit+23*g.unit<=g.cellHeight+1)
            assertTrue(g.rowPitch>g.cellHeight+40*g.unit)
            assertTrue(g.firstY+g.cellHeight<g.viewportHeight)
        }
    }
    @Test fun scaled720pLayoutStillSeparatesFooter() {
        val g=HomeGeometry.calculate(1280,720,5,1,1,false)
        assertTrue(g.viewportTop+g.viewportHeight<g.footerTop)
        assertTrue(g.firstY+g.cellHeight<g.viewportHeight)
    }
}
