package dev.luma.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri

/** Decode a cover frame in software: the P6 vendor thumbnail decoder corrupts chroma. */
internal object VideoPoster {
    fun firstFrame(context:Context,uri:Uri,maxSize:Int):Bitmap {
        val extractor=MediaExtractor()
        var codec:MediaCodec?=null
        try{
            extractor.setDataSource(context,uri,null)
            val track=(0 until extractor.trackCount).first{extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/")==true}
            extractor.selectTrack(track)
            val format=extractor.getTrackFormat(track)
            // Skip AI clips' unsettled opening frame; short clips use a frame one quarter in.
            val duration=if(format.containsKey(MediaFormat.KEY_DURATION))format.getLong(MediaFormat.KEY_DURATION)else 8_000_000L
            val targetUs=minOf(2_000_000L,(duration/4).coerceAtLeast(0))
            extractor.seekTo(targetUs,MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val mime=format.getString(MediaFormat.KEY_MIME)!!
            val rotation=if(format.containsKey(MediaFormat.KEY_ROTATION))format.getInteger(MediaFormat.KEY_ROTATION)else 0
            val name=MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.firstOrNull{
                !it.isEncoder && (it.name.startsWith("OMX.google.") || it.name.startsWith("c2.android.")) && it.supportedTypes.any{type->type.equals(mime,true)}
            }?.name?:error("No software decoder for this video")
            val decoder=MediaCodec.createByCodecName(name).also{codec=it}
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            decoder.configure(format,null,null,0);decoder.start()
            val info=MediaCodec.BufferInfo()
            var ended=false
            val deadline=System.nanoTime()+8_000_000_000L
            while(System.nanoTime()<deadline && !Thread.currentThread().isInterrupted){
                if(!ended){
                    val index=decoder.dequeueInputBuffer(10000)
                    if(index>=0){
                        val buffer=decoder.getInputBuffer(index)!!
                        val size=extractor.readSampleData(buffer,0)
                        if(size<0){decoder.queueInputBuffer(index,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);ended=true}
                        else{decoder.queueInputBuffer(index,0,size,extractor.sampleTime,0);extractor.advance()}
                    }
                }
                val index=decoder.dequeueOutputBuffer(info,10000)
                if(index>=0){
                    try{
                        if(info.size>0 && info.presentationTimeUs>=targetUs){
                            val image=decoder.getOutputImage(index)?:error("No decoded image")
                            val bitmap=try{
                                val crop=image.cropRect
                                val scale=minOf(1f,maxSize.toFloat()/maxOf(crop.width(),crop.height()))
                                val width=(crop.width()*scale).toInt().coerceAtLeast(1)
                                val height=(crop.height()*scale).toInt().coerceAtLeast(1)
                                val pixels=IntArray(width*height)
                                val planes=image.planes
                                val bases=planes.map{it.buffer.position()}
                                fun sample(plane:Int,x:Int,y:Int):Int{
                                    val p=planes[plane]
                                    return p.buffer.get(bases[plane]+y*p.rowStride+x*p.pixelStride).toInt() and 255
                                }
                                for(y in 0 until height)for(x in 0 until width){
                                    val sx=crop.left+x*crop.width()/width;val sy=crop.top+y*crop.height()/height
                                    val yy=(sample(0,sx,sy)-16).coerceAtLeast(0)
                                    val u=sample(1,sx/2,sy/2)-128;val v=sample(2,sx/2,sy/2)-128
                                    val r=((298*yy+409*v+128) shr 8).coerceIn(0,255)
                                    val g=((298*yy-100*u-208*v+128) shr 8).coerceIn(0,255)
                                    val b=((298*yy+516*u+128) shr 8).coerceIn(0,255)
                                    pixels[y*width+x]=(255 shl 24) or (r shl 16) or (g shl 8) or b
                                }
                                Bitmap.createBitmap(pixels,width,height,Bitmap.Config.ARGB_8888)
                            }finally{image.close()}
                            if(rotation==0)return bitmap
                            return Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,Matrix().apply{postRotate(rotation.toFloat())},true)
                                .also{if(it!==bitmap)bitmap.recycle()}
                        }
                        if(info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0)break
                    }finally{decoder.releaseOutputBuffer(index,false)}
                }
            }
            error("Could not decode first video frame")
        }finally{
            codec?.let{runCatching{it.stop()};it.release()}
            extractor.release()
        }
    }
}
