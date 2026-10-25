package dev.luma.launcher

import android.graphics.*
import kotlin.math.*

/** Original art, rasterised once off-thread. Animation only composites three small textures. */
object TileArtwork {
    const val WIDTH=320
    const val HEIGHT=192
    private fun color(h:Float,s:Float,v:Float,alpha:Int=255)=Color.HSVToColor(alpha,floatArrayOf((h%360+360)%360,s,v))
    fun render(id:TileIdentity):AppArtwork {
        val base=Bitmap.createBitmap(WIDTH,HEIGHT,Bitmap.Config.ARGB_8888)
        val middle=Bitmap.createBitmap(WIDTH,HEIGHT,Bitmap.Config.ARGB_8888)
        val front=Bitmap.createBitmap(WIDTH,HEIGHT,Bitmap.Config.ARGB_8888)
        val c=Canvas(base);val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{isDither=true};val hue=id.hue
        p.shader=LinearGradient(20f,0f,290f,205f,intArrayOf(color(hue,.72f,.65f),color(hue,.82f,.27f),color(id.accent,.72f,.36f)),floatArrayOf(0f,.62f,1f),Shader.TileMode.CLAMP)
        c.drawRect(0f,0f,320f,192f,p)
        p.shader=RadialGradient(65f,0f,235f,intArrayOf(color(id.accent,.35f,1f,150),Color.TRANSPARENT),null,Shader.TileMode.CLAMP);c.drawRect(0f,0f,320f,192f,p)
        p.shader=RadialGradient(285f,190f,150f,intArrayOf(color(hue,.6f,1f,125),Color.TRANSPARENT),null,Shader.TileMode.CLAMP);c.drawRect(0f,0f,320f,192f,p)
        // Broad curved reflection, deliberately quiet around the centre symbol.
        val sweep=Path().apply{moveTo(-30f,145f);cubicTo(90f,-45f,210f,60f,350f,-30f);lineTo(350f,-60f);lineTo(-30f,-60f);close()}
        p.shader=LinearGradient(0f,0f,100f,135f,0x20FFFFFF,0x00FFFFFF,Shader.TileMode.CLAMP);c.drawPath(sweep,p);p.shader=null
        val m=Canvas(middle);val f=Canvas(front)
        val shadow=Paint(Paint.ANTI_ALIAS_FLAG).apply{shader=RadialGradient(163f,152f,82f,intArrayOf(0x99000718.toInt(),Color.TRANSPARENT),null,Shader.TileMode.CLAMP)}
        m.save();m.scale(1f,.3f,163f,152f);m.drawCircle(163f,152f,82f,shadow);m.restore()
        val art=Painter(id)
        art.middle(m);art.front(f)
        return AppArtwork(base,true,middle,front)
    }
    private class Painter(val id:TileIdentity) {
        val p=Paint(Paint.ANTI_ALIAS_FLAG)
        val h=id.hue
        fun glass(c:Canvas,r:RectF,radius:Float,bright:Boolean=false){
            p.style=Paint.Style.FILL
            p.shader=LinearGradient(r.left,r.top,r.right,r.bottom,
                intArrayOf(if(bright)0xF5FFFFFF.toInt() else color(h,.15f,1f,165),color(id.accent,.38f,.96f,if(bright)230 else 65),color(h,.55f,.65f,if(bright)255 else 120)),null,Shader.TileMode.CLAMP)
            c.drawRoundRect(r,radius,radius,p);p.shader=null
            p.style=Paint.Style.STROKE;p.strokeWidth=1.2f
            p.shader=LinearGradient(r.left,r.top,r.right,r.bottom,0xCFFFFFFF.toInt(),0x15FFFFFF,Shader.TileMode.CLAMP)
            c.drawRoundRect(r,radius,radius,p);p.shader=null;p.style=Paint.Style.FILL
        }
        fun stroke(c:Canvas,path:Path,width:Float=7f){
            p.style=Paint.Style.STROKE;p.strokeWidth=width;p.strokeCap=Paint.Cap.ROUND;p.strokeJoin=Paint.Join.ROUND
            p.shader=LinearGradient(100f,40f,210f,155f,intArrayOf(Color.WHITE,color(h,.12f,1f),color(id.accent,.3f,.8f)),null,Shader.TileMode.CLAMP)
            c.drawPath(path,p);p.shader=null;p.style=Paint.Style.FILL
        }
        fun line(c:Canvas,x:Float,y:Float,xx:Float,yy:Float,w:Float=7f)=stroke(c,Path().apply{moveTo(x,y);lineTo(xx,yy)},w)
        fun sculpt(c:Canvas,path:Path){
            c.save();c.translate(0f,5f);p.color=color(h,.65f,.32f);c.drawPath(path,p);c.restore()
            p.shader=LinearGradient(112f,48f,207f,148f,intArrayOf(Color.WHITE,color(h,.1f,1f),color(id.accent,.34f,.8f)),floatArrayOf(0f,.52f,1f),Shader.TileMode.CLAMP)
            c.drawPath(path,p);p.shader=null;p.style=Paint.Style.STROKE;p.strokeWidth=1f;p.color=0xAAFFFFFF.toInt();c.drawPath(path,p);p.style=Paint.Style.FILL
        }
        fun rounded(l:Float,t:Float,r:Float,b:Float,rad:Float)=Path().apply{addRoundRect(RectF(l,t,r,b),rad,rad,Path.Direction.CW)}
        fun middle(c:Canvas){
            when(id.motif){
                "wave","note","mic"->{
                    for(i in 0..2){p.style=Paint.Style.STROKE;p.strokeWidth=1.5f;p.color=color(h,.15f,1f,55-i*12);c.drawCircle(160f,92f,48f+i*14,p)};p.style=Paint.Style.FILL
                    p.shader=RadialGradient(142f,65f,90f,intArrayOf(color(h,.3f,1f,70),Color.TRANSPARENT),null,Shader.TileMode.CLAMP);c.drawCircle(160f,90f,78f,p);p.shader=null
                }
                "folder"->{c.save();c.rotate(-9f,160f,100f);glass(c,RectF(102f,44f,213f,137f),12f);glass(c,RectF(114f,57f,201f,130f),5f,true);c.restore()}
                "apps","bag","calculator","keyboard","chip","code","scope"->{
                    c.save();c.rotate(-13f,160f,98f);glass(c,RectF(99f,39f,219f,150f),24f);c.restore()
                }
                "mountain","prism","photo"->{
                    val path=Path().apply{moveTo(87f,132f);lineTo(145f,39f);lineTo(211f,136f);close()}
                    p.shader=LinearGradient(100f,45f,200f,140f,color(id.accent,.2f,1f,145),color(h,.6f,.7f,10),Shader.TileMode.CLAMP);c.drawPath(path,p);p.shader=null
                    stroke(c,path,1f)
                }
                "gear"->{
                    p.shader=LinearGradient(100f,35f,225f,155f,intArrayOf(0xFFB4C2D3.toInt(),0xFF26374D.toInt(),0xFF8395AC.toInt()),null,Shader.TileMode.CLAMP)
                    c.drawCircle(160f,94f,65f,p);p.shader=null;p.color=0xFF17283E.toInt();c.drawCircle(160f,94f,57f,p)
                    p.style=Paint.Style.STROKE;p.strokeWidth=1f;p.color=0x99D8EBFF.toInt();c.drawCircle(160f,94f,63f,p);p.style=Paint.Style.FILL
                }
                "orbit","globe"->{
                    p.style=Paint.Style.STROKE;p.strokeWidth=17f;p.shader=LinearGradient(100f,35f,210f,160f,0x70FFFFFF,0x0533AAFF,Shader.TileMode.CLAMP)
                    c.save();c.rotate(-28f,160f,96f);c.drawOval(RectF(78f,66f,242f,127f),p);c.restore();p.shader=null;p.style=Paint.Style.FILL
                }
                else->{c.save();c.rotate(-8f,160f,96f);glass(c,RectF(91f,41f,226f,147f),26f);c.restore()}
            }
        }
        fun front(c:Canvas){
            when(id.motif){
                "play"->{
                    glass(c,RectF(101f,47f,219f,145f),28f)
                    sculpt(c,Path().apply{moveTo(144f,65f);quadTo(140f,63f,140f,69f);lineTo(140f,122f);quadTo(140f,128f,146f,124f);lineTo(190f,100f);quadTo(196f,96f,190f,92f);close()})
                }
                "wave"->{val heights=floatArrayOf(17f,35f,57f,80f,105f,80f,57f,35f,17f)
                    heights.forEachIndexed{i,v->sculpt(c,rounded(106f+i*12f,96f-v/2,112f+i*12f,96f+v/2,3f))}}
                "apps"->{
                    for(y in 0..2)for(x in 0..2){val left=116f+x*33f;val top=50f+y*33f;glass(c,RectF(left,top,left+25f,top+25f),7f,true)}
                }
                "folder"->{
                    val path=Path().apply{moveTo(99f,75f);quadTo(99f,67f,107f,67f);lineTo(140f,67f);lineTo(151f,79f);lineTo(214f,79f);quadTo(222f,79f,220f,90f);lineTo(211f,139f);quadTo(210f,147f,201f,147f);lineTo(107f,147f);quadTo(99f,147f,99f,139f);close()}
                    sculpt(c,path);line(c,113f,90f,205f,90f,1.2f)
                }
                "gear"->{
                    val path=Path();for(i in 0..79){val a=i*2*PI/80;val radius=if(i%8 in 1..5)51f else 42f;val x=160+cos(a).toFloat()*radius;val y=94+sin(a).toFloat()*radius;if(i==0)path.moveTo(x,y)else path.lineTo(x,y)};path.close()
                    path.addCircle(160f,94f,26f,Path.Direction.CCW);sculpt(c,path)
                    p.style=Paint.Style.STROKE;p.strokeWidth=3f;p.color=0xAADBEBFF.toInt();c.drawCircle(160f,94f,30f,p);p.style=Paint.Style.FILL
                }
                "cinema","film"->{
                    glass(c,RectF(98f,49f,222f,136f),15f,true)
                    p.color=color(h,.65f,.16f);c.drawRoundRect(RectF(105f,56f,215f,127f),10f,10f,p)
                    p.shader=LinearGradient(105f,56f,210f,130f,color(id.accent,.65f,.65f),color(h,.5f,.15f),Shader.TileMode.CLAMP);c.drawRoundRect(RectF(109f,60f,211f,123f),8f,8f,p);p.shader=null
                    line(c,146f,146f,174f,146f,4f)
                    sculpt(c,Path().apply{moveTo(150f,76f);lineTo(150f,108f);lineTo(176f,92f);close()})
                }
                "prism"->{
                    sculpt(c,Path().apply{moveTo(160f,41f);lineTo(217f,94f);lineTo(160f,148f);lineTo(103f,94f);close()})
                    p.shader=LinearGradient(160f,42f,215f,145f,color(id.accent,.55f,1f),color(h,.65f,.5f),Shader.TileMode.CLAMP)
                    c.drawPath(Path().apply{moveTo(160f,41f);lineTo(217f,94f);lineTo(160f,148f);close()},p);p.shader=null
                    line(c,105f,94f,215f,94f,1f)
                }
                "projection"->{
                    stroke(c,Path().apply{moveTo(115f,58f);lineTo(206f,44f);lineTo(216f,141f);lineTo(105f,129f);close()},6f)
                    line(c,160f,69f,160f,120f,2f);line(c,130f,95f,190f,95f,2f)
                }
                "mountain","photo"->{
                    sculpt(c,Path().apply{moveTo(112f,134f);lineTo(168f,48f);lineTo(222f,134f);close()})
                    p.shader=LinearGradient(169f,50f,215f,138f,color(id.accent,.55f,1f),color(h,.78f,.65f),Shader.TileMode.CLAMP)
                    c.drawPath(Path().apply{moveTo(168f,48f);lineTo(171f,134f);lineTo(222f,134f);close()},p);p.shader=null
                    if(id.motif=="photo"){p.color=0xFFF9ECCC.toInt();c.drawCircle(108f,62f,12f,p)}
                }
                "sliders"->{for(i in 0..2){val x=130f+i*31;line(c,x,52f,x,139f,4f);glass(c,RectF(x-10,if(i==1)98f else 66f,x+10,if(i==1)117f else 85f),7f,true)}}
                "note"->{stroke(c,Path().apply{moveTo(145f,124f);lineTo(145f,62f);lineTo(187f,52f);lineTo(187f,114f)},8f);sculpt(c,Path().apply{addOval(RectF(121f,112f,148f,133f),Path.Direction.CW);addOval(RectF(163f,102f,190f,123f),Path.Direction.CW)})}
                "mic"->{sculpt(c,rounded(145f,46f,175f,105f,15f));stroke(c,Path().apply{moveTo(132f,87f);lineTo(132f,95f);cubicTo(132f,132f,188f,132f,188f,95f);lineTo(188f,87f)},5f);line(c,160f,122f,160f,143f,5f);line(c,148f,144f,172f,144f,5f)}
                "cast","input"->{stroke(c,rounded(108f,52f,217f,130f,10f),5f)
                    if(id.motif=="input"){stroke(c,Path().apply{moveTo(90f,92f);lineTo(169f,92f);moveTo(153f,76f);lineTo(169f,92f);lineTo(153f,108f)},7f)}
                    else {p.style=Paint.Style.STROKE;p.strokeWidth=6f;p.color=0xFFE3F7FF.toInt();c.drawArc(RectF(68f,92f,137f,161f),270f,90f,false,p);c.drawArc(RectF(84f,108f,121f,145f),270f,90f,false,p);p.style=Paint.Style.FILL;c.drawCircle(103f,129f,4f,p)}}
                "upload","download"->{stroke(c,Path().apply{moveTo(119f,118f);lineTo(119f,143f);lineTo(201f,143f);lineTo(201f,118f)},5f)
                    c.save();if(id.motif=="download")c.rotate(180f,160f,88f)
                    stroke(c,Path().apply{moveTo(160f,122f);lineTo(160f,52f);moveTo(137f,75f);lineTo(160f,52f);lineTo(183f,75f)},9f);c.restore()}
                "home"->{stroke(c,Path().apply{moveTo(108f,88f);lineTo(160f,48f);lineTo(212f,88f);moveTo(120f,83f);lineTo(120f,138f);lineTo(148f,138f);lineTo(148f,105f);lineTo(174f,105f);lineTo(174f,138f);lineTo(200f,138f);lineTo(200f,83f)},7f)}
                "bag"->{glass(c,RectF(120f,70f,201f,145f),14f,true);stroke(c,Path().apply{moveTo(141f,78f);lineTo(141f,62f);cubicTo(141f,38f,180f,38f,180f,62f);lineTo(180f,78f)},6f)}
                "calculator"->{glass(c,RectF(121f,42f,200f,150f),17f,true);p.color=color(h,.55f,.35f);c.drawRoundRect(RectF(132f,56f,188f,75f),4f,4f,p);for(y in 0..2)for(x in 0..2){p.color=color(h,.3f,.45f,200);c.drawCircle(138f+x*21f,91f+y*19,4f,p)}}
                "keyboard"->{glass(c,RectF(99f,65f,221f,132f),14f,true);p.color=color(h,.4f,.35f);for(y in 0..1)for(x in 0..5)c.drawRoundRect(RectF(110f+x*17,77f+y*17,120f+x*17,87f+y*17),2f,2f,p);c.drawRoundRect(RectF(136f,113f,187f,119f),3f,3f,p)}
                "code"->{stroke(c,Path().apply{moveTo(138f,68f);lineTo(114f,94f);lineTo(138f,120f);moveTo(184f,68f);lineTo(208f,94f);lineTo(184f,120f);moveTo(171f,59f);lineTo(151f,131f)},7f)}
                "scope"->{stroke(c,Path().apply{moveTo(110f,102f);lineTo(130f,102f);lineTo(144f,74f);lineTo(160f,124f);lineTo(180f,63f);lineTo(190f,102f);lineTo(213f,102f)},7f)}
                "chip"->{glass(c,RectF(125f,58f,196f,132f),14f,true);for(i in 0..3){val x=136f+i*16;line(c,x,45f,x,57f,3f);line(c,x,134f,x,146f,3f);val y=69f+i*16;line(c,112f,y,123f,y,3f);line(c,198f,y,209f,y,3f)};glass(c,RectF(144f,78f,178f,112f),5f)}
                "info"->{sculpt(c,rounded(154f,84f,166f,133f,6f));sculpt(c,Path().apply{addCircle(160f,65f,7f,Path.Direction.CW)})}
                "orbit","globe"->{
                    p.shader=RadialGradient(144f,66f,80f,intArrayOf(0xFFE8FFFF.toInt(),color(h,.45f,.95f),color(id.accent,.75f,.45f)),floatArrayOf(0f,.45f,1f),Shader.TileMode.CLAMP);c.drawCircle(160f,94f,45f,p);p.shader=null
                    if(id.motif=="orbit"){p.style=Paint.Style.STROKE;p.strokeWidth=5f;p.color=0xBBDDFFFF.toInt();c.save();c.rotate(-28f,160f,96f);c.drawArc(RectF(78f,66f,242f,127f),5f,180f,false,p);c.restore();p.style=Paint.Style.FILL}
                    else{stroke(c,Path().apply{addOval(RectF(141f,49f,179f,139f),Path.Direction.CW)},2f);line(c,116f,94f,204f,94f,2f)}
                }
                "layers"->{for(i in 0..2){c.save();c.translate(0f,-i*19f);c.rotate(-7f,160f,110f);glass(c,RectF(111f,92f,211f,140f),12f,i==2);c.restore()}}
                else->{
                    val letters=id.name.split(Regex("[^\\p{L}\\p{N}]+")).filter{it.isNotEmpty()}.take(2).joinToString(""){it.take(1)}.uppercase().ifBlank{"◇"}
                    p.typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);p.textSize=59f;p.textAlign=Paint.Align.CENTER
                    p.shader=LinearGradient(120f,55f,200f,140f,Color.WHITE,color(h,.25f,.8f),Shader.TileMode.CLAMP);c.drawText(letters,160f,116f,p);p.shader=null
                }
            }
        }
    }
}
