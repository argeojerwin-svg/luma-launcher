package dev.luma.launcher

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Continuous camera loop with an eased introduction and enough overscan to hide image edges. */
internal object ScreensaverMotion{
    data class Pose(val zoom:Float,val x:Float,val y:Float)
    fun pose(progress:Float,elapsedMillis:Long):Pose{
        val phase=progress.coerceIn(0f,1f)*2.0*PI
        val t=(elapsedMillis/3500f).coerceIn(0f,1f)
        val intro=t*t*(3-2*t)
        return Pose(1f+intro*(.035f+.035f*((1-cos(phase))/2).toFloat()),
            intro*.014f*sin(phase).toFloat(),intro*.007f*sin(phase*2).toFloat())
    }
}
