package dev.luma.launcher

import kotlin.math.max
import kotlin.math.min

/** Physical design-pixel geometry, independent of Android density. Focus reserves up to 1.14x. */
data class HomeGeometry(val unit:Float,val outer:Float,val viewportTop:Int,val viewportHeight:Int,
    val footerTop:Int,val footerHeight:Int,val cellWidth:Int,val cellHeight:Int,val iconWidth:Float,
    val columnGap:Float,val left:Float,val firstY:Int,val rowPitch:Int) {
    companion object {
        fun calculate(width:Int,height:Int,safePercent:Int,size:Int,spacing:Int,allApps:Boolean):HomeGeometry {
            val u=min(width/1920f,height/1080f)
            val outer=max(80*u,width*safePercent.coerceIn(0,8)/100f)
            val safeY=max(40*u,height*safePercent.coerceIn(0,8)/100f)
            val gap=(4+spacing.coerceIn(0,2)*4)*u
            val cell=((width-2*outer-5*gap)/6).toInt()
            val multiplier=listOf(.88f,1f,1.06f)[size.coerceIn(0,2)]
            val icon=min(250*u*multiplier,(cell-12*u)/1.14f)
            val cellH=(24*u+icon*.6f+28*u+40*u+24*u).toInt()
            val footerH=(48*u).toInt();val footerTop=(height-safeY-footerH).toInt()
            val viewportTop=(safeY+88*u).toInt()
            val viewportHeight=footerTop-(16*u).toInt()-viewportTop
            val first=if(allApps)(20*u).toInt() else min((height*.70f).toInt()-viewportTop,viewportHeight-cellH-(16*u).toInt())
            return HomeGeometry(u,outer,viewportTop,viewportHeight,footerTop,footerH,cell,cellH,icon,gap,
                (width-6*cell-5*gap)/2,first.coerceAtLeast(0),cellH+(44*u).toInt())
        }
    }
}
