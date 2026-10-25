package dev.luma.launcher

import android.os.SystemClock
import android.view.Choreographer
import kotlin.math.abs
import kotlin.math.min

/** One retargetable physical spring. No queued animations when a remote repeats. */
class Spring(private val onFrame: (Float) -> Unit) : Choreographer.FrameCallback {
    private companion object { const val STIFFNESS=460f; const val DAMPING=38f }
    var value = 0f; private set
    private var velocity = 0f
    private var target = 0f
    private var running = false
    private var previous = 0L
    fun snap(value:Float){stop();velocity=0f;this.value=value;target=value;onFrame(value)}
    fun to(value: Float) {
        target = value
        if (!running) { running = true; previous = SystemClock.elapsedRealtimeNanos(); Choreographer.getInstance().postFrameCallback(this) }
    }
    override fun doFrame(frameTimeNanos: Long) {
        var dt = min((frameTimeNanos - previous).coerceAtLeast(0) / 1e9f, .064f); previous = frameTimeNanos
        while (dt > 0) {
            val step = min(dt, .008f)
            velocity += (STIFFNESS * (target - value) - DAMPING * velocity) * step
            value += velocity * step; dt -= step
        }
        onFrame(value)
        if (abs(target - value) < .001f && abs(velocity) < .008f) { value = target; onFrame(value); running = false }
        else Choreographer.getInstance().postFrameCallback(this)
    }
    fun stop() { Choreographer.getInstance().removeFrameCallback(this); running = false }
}
