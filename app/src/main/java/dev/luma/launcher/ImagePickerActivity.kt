package dev.luma.launcher

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

/** Remote-friendly local gallery. Picks are copied into private storage for lasting access. */
class ImagePickerActivity:Activity(){
    private data class Photo(val uri:Uri,val name:String,val saved:Boolean,val video:Boolean=false)
    private val allowVideo get()=intent.getBooleanExtra("allow_video",false)
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    private val thumbnails=android.util.LruCache<String,Bitmap>(20)
    private var photos=emptyList<Photo>()
    private var selecting=false
    private var closed=false
    private lateinit var list:ListView
    private lateinit var message:TextView
    private lateinit var access:Button
    private fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
    private fun permission()=if(Build.VERSION.SDK_INT>=33)Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
    private fun allowed()=checkSelfPermission(permission())==PackageManager.PERMISSION_GRANTED
    private fun videosAllowed()=checkSelfPermission(if(Build.VERSION.SDK_INT>=33)Manifest.permission.READ_MEDIA_VIDEO else permission())==PackageManager.PERMISSION_GRANTED
    private fun requestMediaAccess(){
        requestPermissions(if(Build.VERSION.SDK_INT>=33 && allowVideo)arrayOf(permission(),Manifest.permission.READ_MEDIA_VIDEO)else arrayOf(permission()),7)
    }
    override fun onCreate(state:Bundle?){
        super.onCreate(state)
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(42),dp(24),dp(42),dp(20));setBackgroundColor(0xFF101D2E.toInt())}
        root.addView(TextView(this).apply{text=if(allowVideo)"Choose image or video" else "Choose image";textSize=28f;setTextColor(Color.WHITE)})
        message=TextView(this).apply{text="Photos saved in Luma and on this projector";textSize=15f;setTextColor(0xFFBECDE2.toInt())}
        root.addView(message)
        val controls=LinearLayout(this)
        access=Button(this).apply{text=if(allowed() && (!allowVideo || videosAllowed()))"Refresh media" else "Allow access to local media";setOnClickListener{
            if(allowed() && (!allowVideo || videosAllowed()))load()else requestMediaAccess()
        }}
        controls.addView(access)
        controls.addView(Button(this).apply{text="Back";setOnClickListener{finish()}})
        root.addView(controls)
        list=ListView(this).apply{dividerHeight=dp(5);isFocusable=true;setOnItemClickListener{_,_,position,_->choose(photos[position])}}
        root.addView(list,LinearLayout.LayoutParams(-1,0,1f));setContentView(root);load()
    }
    override fun onRequestPermissionsResult(code:Int,permissions:Array<out String>,results:IntArray){
        super.onRequestPermissionsResult(code,permissions,results)
        if(code==7)load()
    }
    private fun load(){
        val mediaAllowed=allowed()
        worker.execute{
            val found=mutableListOf<Photo>()
            listOf("wallpapers","images").forEach{folder->
                File(filesDir,folder).listFiles()?.filter{it.isFile && it.extension.lowercase() in setOf("jpg","jpeg","png","webp","img")}
                    ?.forEach{found.add(Photo(Uri.fromFile(it),it.name,true))}
            }
            if(allowVideo)File(filesDir,"videos").listFiles()?.filter{it.isFile && it.extension.lowercase() in setOf("mp4","m4v","webm","mkv")}
                ?.forEach{found.add(Photo(Uri.fromFile(it),it.name,true,true))}
            val result=runCatching{
                if(mediaAllowed)contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Images.Media._ID,MediaStore.Images.Media.DISPLAY_NAME),null,null,
                    "${MediaStore.Images.Media.DATE_MODIFIED} DESC")?.use{cursor->
                    while(cursor.moveToNext() && found.size<300)found.add(Photo(
                        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,cursor.getLong(0)),
                        cursor.getString(1)?:"Photo",false))
                }
            }
            val videoResult=runCatching{
                if(allowVideo && videosAllowed())contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Video.Media._ID,MediaStore.Video.Media.DISPLAY_NAME),null,null,
                    "${MediaStore.Video.Media.DATE_MODIFIED} DESC")?.use{cursor->
                    while(cursor.moveToNext() && found.size<600)found.add(Photo(
                        ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,cursor.getLong(0)),
                        cursor.getString(1)?:"Video",false,true))
                }
            }
            main.post{if(!closed){
                photos=found.distinctBy{it.uri.toString()}
                access.text=if(allowed() && (!allowVideo || videosAllowed()))"Refresh media" else "Allow access to local media"
                message.text=if(result.isFailure || videoResult.isFailure)"Some shared media could not be read. Saved Luma media remains available."
                    else if(photos.isEmpty())"Copy images to Pictures or videos to Movies, then allow access and refresh."
                    else "${photos.size} items · Videos: still frame on Home, playback in screensaver · Images work as before"
                list.adapter=PhotoAdapter();if(photos.isNotEmpty()){list.requestFocus();list.setSelection(0)}
            }}
        }
    }
    private inner class PhotoAdapter:BaseAdapter(){
        override fun getCount()=photos.size
        override fun getItem(position:Int)=photos[position]
        override fun getItemId(position:Int)=position.toLong()
        override fun getView(position:Int,reuse:View?,parent:ViewGroup):View{
            val photo=photos[position]
            val row=LinearLayout(this@ImagePickerActivity).apply{
                gravity=Gravity.CENTER_VERTICAL;setPadding(dp(10),dp(8),dp(10),dp(8))
                background=StateListDrawable().apply{
                    fun fill(color:Int)=GradientDrawable().apply{setColor(color);cornerRadius=dp(10).toFloat()}
                    addState(intArrayOf(android.R.attr.state_activated),fill(0xFF355B8E.toInt()))
                    addState(intArrayOf(android.R.attr.state_selected),fill(0xFF355B8E.toInt()))
                    addState(intArrayOf(),fill(0xFF1A2B41.toInt()))
                }
            }
            val preview=ImageView(this@ImagePickerActivity).apply{scaleType=ImageView.ScaleType.CENTER_CROP;contentDescription=photo.name}
            row.addView(preview,LinearLayout.LayoutParams(dp(130),dp(76)))
            row.addView(TextView(this@ImagePickerActivity).apply{
                text=(if(photo.saved)"Saved in Luma · " else "Local · ")+(if(photo.video)"Video · " else "Image · ")+photo.name
                textSize=17f;setTextColor(Color.WHITE);maxLines=2;ellipsize=TextUtils.TruncateAt.END;setPadding(dp(18),0,0,0)
            },LinearLayout.LayoutParams(0,-2,1f))
            val key=photo.uri.toString()
            thumbnails.get(key)?.let{preview.setImageBitmap(it)}?:worker.execute{
                val bitmap=runCatching{
                    if(photo.video)return@runCatching videoFrame(photo.uri,256)
                    val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
                    contentResolver.openInputStream(photo.uri)?.use{BitmapFactory.decodeStream(it,null,bounds)}
                    var sample=1;while(maxOf(bounds.outWidth,bounds.outHeight)/sample>256)sample*=2
                    contentResolver.openInputStream(photo.uri)?.use{BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply{inSampleSize=sample})}
                }.getOrNull()
                main.post{if(!closed && bitmap!=null){thumbnails.put(key,bitmap);preview.setImageBitmap(bitmap)}}
            }
            return row
        }
    }
    private fun choose(photo:Photo){
        if(selecting)return
        selecting=true;message.text=if(photo.video)"Saving video and wallpaper frame…" else "Saving image…"
        worker.execute{
            var imported:File?=null
            var poster:File?=null
            var videoUri:String?=null
            val result=runCatching{
                if(photo.video){
                    val file=if(photo.saved)File(photo.uri.path!!)else{
                        val folder=File(filesDir,"videos").apply{mkdirs()}
                        val name=photo.name.replace(Regex("[^A-Za-z0-9._-]"),"_").takeLast(100)
                        File(folder,"${UUID.randomUUID()}-$name").also{out->
                            imported=out
                            contentResolver.openInputStream(photo.uri)!!.use{input->out.outputStream().use{output->ImageImport.copy(input,output,256L*1024*1024)}}
                        }
                    }
                    val video=Uri.fromFile(file)
                    val frame=videoFrame(video,1920)?:error("No readable video frame")
                    val output=File(File(filesDir,"video_posters").apply{mkdirs()},"${UUID.randomUUID()}.jpg").also{poster=it}
                    try{output.outputStream().use{check(frame.compress(Bitmap.CompressFormat.JPEG,94,it))}}finally{frame.recycle()}
                    videoUri=video.toString()
                    return@runCatching Uri.fromFile(output)
                }
                val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
                contentResolver.openInputStream(photo.uri)?.use{BitmapFactory.decodeStream(it,null,bounds)}
                check(bounds.outWidth>0 && bounds.outHeight>0){"Unsupported image"}
                if(photo.saved)photo.uri else{
                    val folder=File(filesDir,"images").apply{mkdirs()}
                    val name=photo.name.replace(Regex("[^A-Za-z0-9._-]"),"_").takeLast(100)
                    val file=File(folder,"${UUID.randomUUID()}-$name").also{imported=it}
                    contentResolver.openInputStream(photo.uri)!!.use{input->file.outputStream().use{output->ImageImport.copy(input,output)}}
                    Uri.fromFile(file)
                }
            }
            main.post{if(closed){imported?.delete();poster?.delete();return@post}
                result.onSuccess{uri->setResult(RESULT_OK,Intent().putExtra(EXTRA_IMAGE,uri.toString()).putExtra(EXTRA_VIDEO,videoUri));finish()}
                    .onFailure{imported?.delete();poster?.delete();selecting=false;message.text=if(photo.video)"Could not use this video. Try a playable video under 256 MB." else "Could not use this image. Choose a readable image under 32 MB."}
            }
        }
    }
    override fun onDestroy(){closed=true;worker.shutdownNow();thumbnails.evictAll();super.onDestroy()}
    private fun videoFrame(uri:Uri,width:Int):Bitmap?{
        return VideoPoster.firstFrame(this,uri,width)
    }
    companion object {const val EXTRA_IMAGE="local_image_uri";const val EXTRA_VIDEO="local_video_uri"}
}
