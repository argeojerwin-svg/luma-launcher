package dev.luma.launcher

import android.test.InstrumentationTestCase
import android.graphics.*
import java.io.File

/** Design review only: no launch, preference, default Home or installed-artwork changes. */
@Suppress("DEPRECATION")
class TileComparisonTest:InstrumentationTestCase(){
    fun testExportBrandPreservingComparison(){
        val context=instrumentation.targetContext
        val repo=AppRepository(context)
        val ids=listOf("com.apple.atve.androidtv.appletv","com.google.android.youtube.tv","com.spotify.tv.android","com.stremio.one","com.android.vending")
        val apps=ids.map{id->repo.discover().first{it.id==id}}
        val names=listOf("Apple TV","YouTube","Spotify","Stremio","Apps")
        val dir=File(context.filesDir,"tile-comparison").apply{mkdirs()}
        val board=Bitmap.createBitmap(1920,1220,Bitmap.Config.ARGB_8888)
        val c=Canvas(board);val p=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        p.shader=LinearGradient(0f,0f,1920f,1220f,0xFF172334.toInt(),0xFF080D18.toInt(),Shader.TileMode.CLAMP);c.drawRect(0f,0f,1920f,1220f,p);p.shader=null
        fun text(value:String,x:Float,y:Float,size:Float,color:Int=Color.WHITE){p.color=color;p.textSize=size;p.typeface=Typeface.create("sans-serif",Typeface.NORMAL);c.drawText(value,x,y,p)}
        text("Familiar identities. A quieter glass finish.",80f,82f,44f)
        text("Apple TV · YouTube · Spotify · Stremio · Apps     /     Design comparison",80f,129f,24f,0xFFB3C5DA.toInt())
        val titles=listOf("01  Installed app artwork","02  Current custom redesign","03  Proposed · brand first, restrained glass")
        for(row in 0..2){
            val y=215f+row*320f
            text(titles[row],80f,y-25f,26f,if(row==2)0xFFC4E2FF.toInt() else 0xFFB3C5DA.toInt())
            for(i in apps.indices){
                val original=original(apps[i])
                val bitmap=when(row){0->original;1->{val art=TileArtwork.render(repo.identity(apps[i]));val result=art.bitmap.copy(Bitmap.Config.ARGB_8888,true);val canvas=Canvas(result);canvas.drawBitmap(art.middle!!,0f,0f,null);canvas.drawBitmap(art.foreground!!,0f,0f,null);result};else->proposed(original,i)}
                val x=80f+i*354f
                val r=RectF(x,y,x+320f,y+192f)
                val path=Path().apply{addRoundRect(r,31f,31f,Path.Direction.CW)}
                c.save();c.clipPath(path);p.color=Color.WHITE;c.drawBitmap(bitmap,null,r,p);c.restore()
                p.style=Paint.Style.STROKE;p.strokeWidth=1.2f;p.color=0x40FFFFFF;c.drawRoundRect(r,31f,31f,p);p.style=Paint.Style.FILL
                val label=when{row==0 && i==4->"Google Play Store";row==1 && i==1->"Video";row==1 && i==2->"Music";else->names[i]}
                text(label,x,y+233f,25f)
                if(row==2)File(dir,"${names[i].replace(' ','-')}-proposed.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            }
        }
        text("Proposed: original brand artwork + subtle surface light. Apps retains a custom marketplace mark.",80f,1156f,22f,0xFFAAC0D7.toInt())
        text("Preview only · no launcher artwork or Home default changed by this comparison",80f,1190f,20f,0xFF829AB4.toInt())
        File(dir,"Tile-comparison.png").outputStream().use{board.compress(Bitmap.CompressFormat.PNG,100,it)}
        assertTrue(File(dir,"Tile-comparison.png").length()>0)
    }
    private fun original(app:AppEntry):Bitmap {
        val pm=instrumentation.targetContext.packageManager
        val banner=pm.getActivityBanner(app.component)?:pm.getApplicationBanner(app.id)
        val drawable=banner?:pm.getActivityIcon(app.component)
        val bitmap=Bitmap.createBitmap(320,192,Bitmap.Config.ARGB_8888)
        val c=Canvas(bitmap);c.drawColor(0xFF1C2838.toInt())
        if(banner!=null)drawable.setBounds(0,0,320,192)else drawable.setBounds(84,20,236,172)
        drawable.draw(c)
        return bitmap
    }
    private fun proposed(original:Bitmap,index:Int):Bitmap {
        val bitmap=original.copy(Bitmap.Config.ARGB_8888,true)
        val c=Canvas(bitmap);val p=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if(index==4){
            p.shader=LinearGradient(20f,0f,300f,192f,intArrayOf(0xFF29B9F4.toInt(),0xFF177AE7.toInt(),0xFF254ED6.toInt()),null,Shader.TileMode.CLAMP);c.drawRect(0f,0f,320f,192f,p);p.shader=null
            // A marketplace mark drawn as three interlocking strokes, sized as the tile's primary identity.
            p.style=Paint.Style.STROKE;p.strokeCap=Paint.Cap.ROUND;p.strokeWidth=15f
            val a=Path().apply{moveTo(125f,144f);lineTo(180f,49f);moveTo(146f,49f);lineTo(201f,144f);moveTo(111f,117f);lineTo(210f,117f)}
            c.save();c.translate(0f,2f);p.color=0x25012D9F;c.drawPath(a,p);c.restore()
            p.color=Color.WHITE;p.shader=LinearGradient(135f,45f,195f,150f,Color.WHITE,0xFFE1F4FF.toInt(),Shader.TileMode.CLAMP);c.drawPath(a,p);p.shader=null;p.style=Paint.Style.FILL
        }
        // The logo/wordmark remains intact. Lighting belongs to the tile surface, not a floating badge.
        p.shader=LinearGradient(0f,0f,0f,192f,intArrayOf(0x19FFFFFF,0x02FFFFFF,0x10000000),floatArrayOf(0f,.40f,1f),Shader.TileMode.CLAMP);c.drawRect(0f,0f,320f,192f,p);p.shader=null
        p.shader=RadialGradient(40f,-65f,260f,intArrayOf(0x10FFFFFF,0x00FFFFFF),null,Shader.TileMode.CLAMP);c.drawRect(0f,0f,320f,192f,p);p.shader=null
        p.style=Paint.Style.STROKE;p.strokeWidth=1.2f
        p.shader=LinearGradient(0f,0f,320f,192f,0x66FFFFFF,0x08FFFFFF,Shader.TileMode.CLAMP);c.drawRoundRect(RectF(1f,1f,319f,191f),31f,31f,p);p.shader=null
        return bitmap
    }
}
