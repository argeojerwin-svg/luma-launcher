package dev.luma.launcher

import android.app.ActivityManager
import android.content.Context

/** Conservative budgets for the P6's 32-bit process and approximately 1.73 GiB RAM. */
object DeviceBudget {
    fun constrained(context: Context): Boolean {
        val manager=context.getSystemService(ActivityManager::class.java)
        val memory=ActivityManager.MemoryInfo();manager.getMemoryInfo(memory)
        return manager.isLowRamDevice || memory.totalMem<=2L*1024*1024*1024 || manager.memoryClass<=192
    }
    fun appCacheBytes(context: Context)=if(constrained(context))3*1024*1024 else 8*1024*1024
    fun wallpaperCacheBytes(context: Context)=if(constrained(context))10*1024*1024 else 24*1024*1024
}
