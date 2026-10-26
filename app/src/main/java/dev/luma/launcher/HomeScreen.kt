package dev.luma.launcher

import android.content.Context
import android.graphics.*
import android.text.TextPaint
import android.text.TextUtils
import android.view.*
import android.widget.*
import java.util.concurrent.ExecutorService
import kotlin.math.*

class AppTile(context: Context, val item: AppEntry?, val label: String, private val prefs: Preferences,
    private val utility: String = "") : FrameLayout(context) {
    var art: AppArtwork?=null
        set(value) {if(field!==value){field=value;icon.invalidate()}}
    var selected: ((Boolean)->Unit)?=null
    var externalFocusMotion=false
    private val icon=IconView(context)
    private val caption=TextView(context).apply {
        text=label.replace('\n',' ').replace('\r',' ')
        setTextColor(Color.WHITE);setSingleLine(true);maxLines=1
        ellipsize=TextUtils.TruncateAt.END;gravity=Gravity.CENTER
        includeFontPadding=false;isFocusable=false
        importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO
        setShadowLayer(2f,0f,1f,Color.BLACK)
    }
    private var unit=1f
    private var focusProgress=0f
    private val spring=Spring { value -> renderFocus(value) }
    fun renderFocus(value:Float){
        focusProgress=value
        val amount=if(prefs.motion==0).025f else if(prefs.motion==2).11f else .09f
        icon.scaleX=if(externalFocusMotion)1f else 1f+value*amount;icon.scaleY=icon.scaleX
        icon.translationY=if(externalFocusMotion)0f else -8f*unit*value
        icon.elevation=(if(externalFocusMotion)3f else 3f+value*9f)*unit
        caption.alpha=.65f+.35f*value.coerceIn(0f,1f)
        icon.rotationX=if(!externalFocusMotion && prefs.motion>0 && prefs.renderMode<2)-1.4f*value else 0f
        icon.rotationY=if(!externalFocusMotion && prefs.motion>0 && prefs.renderMode<2)1.8f*value else 0f
        if(prefs.renderMode==0 && !externalFocusMotion)icon.invalidate()
        else if(value==0f || value==1f)icon.setLayerType(LAYER_TYPE_NONE,null)
    }
    init {
        clipChildren=false;clipToPadding=false
        isFocusable=true;isClickable=true;isLongClickable=item!=null || utility=="folder";contentDescription=label
        descendantFocusability=FOCUS_BLOCK_DESCENDANTS
        addView(icon);addView(caption)
        setOnFocusChangeListener { _,focused ->
            if(externalFocusMotion)icon.invalidate()
            else if(prefs.renderMode!=0) {icon.setLayerType(LAYER_TYPE_HARDWARE,null);icon.invalidate()}
            if(!externalFocusMotion)spring.to(if(focused)1f else 0f)
            selected?.invoke(focused)
        }
    }
    fun configure(iconWidth: Float, scale: Float) {
        unit=scale
        val iconHeight=iconWidth*.6f
        icon.layoutParams=LayoutParams(iconWidth.toInt(),iconHeight.toInt(),Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply{topMargin=(24*unit).toInt()}
        caption.layoutParams=LayoutParams(-1,(40*unit).toInt()).apply{
            leftMargin=(8*unit).toInt();rightMargin=(8*unit).toInt();topMargin=(24*unit+iconHeight+28*unit).toInt()
        }
        caption.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,24*unit*resources.configuration.fontScale.coerceIn(1f,1.25f))
        caption.alpha=if(hasFocus())1f else .65f
    }
    private inner class IconView(context: Context):View(context) {
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val rect=RectF();private val scratch=RectF();private val clip=Path();private val matrix=Matrix()
        private var backgroundGradient:Shader?=null;private var highlight:Shader?=null
        init {
            importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO
            cameraDistance=8000f
            outlineProvider=object:ViewOutlineProvider(){override fun getOutline(view:View,outline:Outline){outline.setRoundRect(0,0,view.width,view.height,view.width*.105f)}}
        }
        override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int) {
            backgroundGradient=LinearGradient(0f,0f,w.toFloat(),h.toFloat(),intArrayOf(0xFF34455D.toInt(),0xFF172334.toInt()),null,Shader.TileMode.CLAMP)
            highlight=LinearGradient(0f,0f,w*.7f,h.toFloat(),intArrayOf(0x18FFFFFF,0x00FFFFFF,0x08000000),null,Shader.TileMode.CLAMP)
        }
        override fun onDraw(c:Canvas) {
            val progress=if(prefs.renderMode==0)focusProgress else if(this@AppTile.hasFocus())1f else 0f
            val w=width.toFloat();val h=height.toFloat();val radius=w*.105f
            rect.set(0f,0f,w,h);paint.color=0xFF1E2837.toInt();c.drawRoundRect(rect,radius,radius,paint)
            clip.reset();clip.addRoundRect(rect,radius,radius,Path.Direction.CW);c.save();c.clipPath(clip)
            val image=art
            if(image!=null){
                paint.color=Color.WHITE
                if(image.banner){
                    c.drawBitmap(image.bitmap,null,rect,paint)
                    val depth=if(prefs.motion>0 && prefs.renderMode<2)progress else 0f
                    image.middle?.let{layer->
                        c.save();c.translate(-w*.006f*depth,h*.005f*depth);c.drawBitmap(layer,null,rect,paint);c.restore()
                    }
                    image.foreground?.let{layer->
                        c.save();c.translate(w*.014f*depth,-h*.015f*depth);c.drawBitmap(layer,null,rect,paint);c.restore()
                    }
                }
                else {
                    paint.shader=backgroundGradient;c.drawRect(rect,paint);paint.shader=null
                    val size=h*.76f;val shift=if(prefs.renderMode==0 && prefs.motion>0)progress*w*.009f else 0f
                    scratch.set((w-size)/2+shift,(h-size)/2,(w+size)/2+shift,(h+size)/2)
                    c.drawBitmap(image.bitmap,null,scratch,paint)
                }
            }else if(item==null){
                paint.color=0xFFD6E9FF.toInt()
                if(utility=="folder"){
                    val left=w*.24f;val top=h*.30f
                    scratch.set(left,top-h*.08f,w*.55f,top+h*.04f);paint.color=0xA8E6F4FF.toInt();c.drawRoundRect(scratch,h*.04f,h*.04f,paint)
                    scratch.set(left,top,w*.76f,h*.72f);paint.color=0xCCD6E9FF.toInt();c.drawRoundRect(scratch,h*.07f,h*.07f,paint)
                    paint.color=0x704D7DA5;scratch.inset(h*.07f,h*.10f);c.drawRoundRect(scratch,h*.04f,h*.04f,paint)
                }else if(utility=="apps"){
                    val size=h*.16f;val gap=h*.08f
                    for(y in 0..1)for(x in 0..2){val left=w/2-(size*3+gap*2)/2+x*(size+gap);val top=h/2-(size*2+gap)/2+y*(size+gap)
                        scratch.set(left,top,left+size,top+size);c.drawRoundRect(scratch,size*.2f,size*.2f,paint)}
                }else{
                    paint.style=Paint.Style.STROKE;paint.strokeWidth=h*.035f
                    for(y in 0..2){val yy=h*(.3f+y*.2f);c.drawLine(w*.35f,yy,w*.65f,yy,paint)
                        paint.style=Paint.Style.FILL;c.drawCircle(w*(if(y==1).58f else .43f),yy,h*.055f,paint);paint.style=Paint.Style.STROKE}
                    paint.style=Paint.Style.FILL
                }
            }
            matrix.setTranslate(progress*w*.1f,0f);highlight?.setLocalMatrix(matrix)
            paint.shader=highlight;paint.alpha=(50+progress*205).toInt().coerceIn(0,255);c.drawRoundRect(rect,radius,radius,paint)
            paint.shader=null;c.restore();paint.style=Paint.Style.STROKE;paint.strokeWidth=1f+progress*1.5f
            paint.color=Color.argb((30+progress*110).toInt().coerceIn(0,255),255,255,255)
            rect.inset(1f,1f);c.drawRoundRect(rect,radius,radius,paint);paint.style=Paint.Style.FILL
        }
    }
    override fun onInitializeAccessibilityNodeInfo(info:android.view.accessibility.AccessibilityNodeInfo){super.onInitializeAccessibilityNodeInfo(info);info.className="android.widget.Button";info.text=label}
    override fun onDetachedFromWindow(){spring.stop();icon.setLayerType(LAYER_TYPE_NONE,null);super.onDetachedFromWindow()}
}

class HomeScreen(context: Context, private val prefs: Preferences, private val wallpaper: WallpaperView,
    private val repo: AppRepository, private val executor: ExecutorService) : FrameLayout(context) {
    var onLaunch: (AppEntry) -> Unit = {}
    var onContext: (AppEntry) -> Unit = {}
    var onFolderOpen: (AppFolder) -> Unit = {}
    var onFolderContext: (AppFolder) -> Unit = {}
    var onFocusApp: (AppEntry?) -> Unit = {}
    var onControl: () -> Unit = {}
    var onTopShelf:(AppEntry)->Unit={}
    var onCinematicContinue:(AppEntry)->Boolean={false}
    var onContinue:()->Unit={}
    var onAllApps: () -> Unit = {}
    var onApps: () -> Unit = {}
    var onMove: ((String, String) -> Unit)? = null
    var allApps=false;private set
    private val scroll=object:FrameLayout(context){
        override fun dispatchDraw(canvas:Canvas){
            val save=canvas.save();canvas.clipRect(0,0,width,height);super.dispatchDraw(canvas);canvas.restoreToCount(save)
        }
    }.apply{clipChildren=true;clipToPadding=true;isFocusable=false}
    private val stage=FrameLayout(context).apply { clipChildren=false;clipToPadding=false }
    private val dock=View(context)
    private val clock=GlassButton(context,wallpaper,prefs,"")
    private val shelf=TopShelfView(context,prefs)
    private var continueCount=0
    private var cinematic=false
    fun setCinematic(active:Boolean){
        cinematic=active
        if(active)shelf.show(null)
        else shelf.show(focusedApp?.let{TopShelfContent.list(prefs,it.id).firstOrNull()})
    }
    private val weather=GlassButton(context,wallpaper,prefs,"").apply { isFocusable=false;isClickable=false }
    private val heading=TextView(context).apply { visibility=GONE;text="All Apps";setTextColor(Color.WHITE);typeface=Typeface.create("sans-serif-light",Typeface.NORMAL);textSize=32f }
    private val hint=TextView(context).apply { setTextColor(0xAFFFFFFF.toInt());textSize=12f;gravity=Gravity.CENTER }
    private var tiles=mutableListOf<AppTile>()
    private var tileIds=listOf<String>()
    private data class Collection(val title:String,val ids:List<String>,val folderId:String?=null)
    private var collection:Collection?=null
    private var collectionTransition=false
    private var currentIndex=0
    private var u=1f
    private var firstY=0
    private var pitch=0
    private var cellHeight=0
    private var viewportHeight=0
    private var maxScroll=0
    private var scrollOffset=0
    private var artworkRange=Int.MIN_VALUE to Int.MIN_VALUE
    private var scrollAnimation:android.animation.ValueAnimator?=null
    private var restoringFocus=false
    private var listGeneration=0
    private val loading=mutableSetOf<String>()
    private data class Pose(val x:Float,val y:Float,val alpha:Float)
    private data class Travel(val view:AppTile,val x:Float,val y:Float,val alpha:Float,val exiting:Boolean)
    private val outgoing=linkedMapOf<String,AppTile>()
    private var travel=emptyList<Travel>()
    private var dockFrom=1f
    private var exitTravel=0f
    var transitioning=false;private set
    private val transition=Spring { progress ->
        val fade=progress.coerceIn(0f,1f)
        travel.forEach { t ->
            t.view.translationX=t.x*(1f-progress)
            t.view.translationY=if(t.exiting)t.y+exitTravel*fade else t.y*(1f-progress)
            t.view.alpha=if(t.exiting)t.alpha*(1f-fade) else t.alpha+(1f-t.alpha)*fade
        }
        dock.alpha=dockFrom+((if(allApps)0f else 1f)-dockFrom)*fade
        if(progress==1f)finishTransition()
    }
    private fun finishTransition(){
        outgoing.values.forEach{stage.removeView(it)};outgoing.clear()
        travel=emptyList();tiles.forEach{it.translationX=0f;it.translationY=0f;it.alpha=1f;it.setLayerType(LAYER_TYPE_NONE,null)}
        dock.visibility=if(allApps || tiles.isEmpty())GONE else VISIBLE;dock.alpha=1f;transitioning=false
    }
    var moveId: String?=null
    val focusedApp: AppEntry? get()=tiles.getOrNull(currentIndex)?.item
    val focusedId: String get()=tileIds.getOrElse(currentIndex){""}
    val currentFolderId:String? get()=collection?.folderId
    val focusedFolder:AppFolder? get()=Folders.find(prefs,focusedId)
    init {
        clipChildren=false;clipToPadding=false
        addView(scroll,LayoutParams(-1,-1));scroll.addView(stage)
        dock.background=GlassDrawable(dock,wallpaper,prefs,40f);stage.addView(dock)
        addView(shelf);addView(clock);addView(weather);addView(heading);addView(hint)
        clock.setOnClickListener { onControl() }
        clock.setOnKeyListener { _,key,event ->
            if(event.action==KeyEvent.ACTION_DOWN && key==KeyEvent.KEYCODE_DPAD_DOWN) {
                if(tiles.isEmpty())onAllApps() else tiles.getOrNull(currentIndex)?.requestFocus()
                true
            } else false
        }
        clock.id=generateViewId();clock.contentDescription="Clock and Control Centre"

    }
    fun weatherText(value:String,description:String){weather.text=value;weather.contentDescription=description}
    fun continueWatching(count:Int){continueCount=count}
    fun clockText(value: String, network:NetworkState.Snapshot) {
        val marker='\uFFFC'
        val content=android.text.SpannableString("$value   $marker")
        val size=(27*u).toInt().coerceAtLeast(24)
        val icon=WifiGlyph(network).apply{setBounds(0,0,size,size)}
        content.setSpan(android.text.style.ImageSpan(icon,android.text.style.DynamicDrawableSpan.ALIGN_CENTER),content.length-1,content.length,0)
        clock.text=content
        clock.contentDescription="$value · ${network.detail}"
    }
    fun suppressClock(suppress:Boolean) {
        clock.visibility=if(suppress)INVISIBLE else VISIBLE
        clock.alpha=if(suppress)0f else 1f
    }
    fun showCollection(apps:List<AppEntry>, title:String, ids:List<String>) {
        val next=Collection(title,ids);collectionTransition=collection!=next;collection=next
        rebuild(apps,true,ids.firstOrNull()?:"@market")
    }
    fun showLibrary(apps:List<AppEntry>,restore:String="") {collectionTransition=collection!=null;collection=null;rebuild(apps,true,restore,restore.isNotBlank())}
    fun showFolder(apps:List<AppEntry>,folder:AppFolder) {
        val next=Collection(folder.name,folder.members,folder.id);collectionTransition=collection!=next;collection=next
        rebuild(apps,true,folder.members.firstOrNull()?:"@market")
    }
    fun rebuild(apps: List<AppEntry>, showAll: Boolean, restore: String=prefs.text("focus"), preferRestore:Boolean=false) {
        if (prefs.text("layoutInitialized").isBlank()) {
            if (prefs.list("order").isEmpty() && prefs.list("favourites").isEmpty()) {
                val suggested=listOf("com.apple.atve.androidtv.appletv", "com.google.android.youtube.tv", "com.netflix.ninja",
                    "com.spotify.tv.android", "com.stremio.one", "com.android.vending", "com.spocky.projengmenu")
                prefs.putList("favourites",suggested.filter { id->apps.any {it.id==id} }.take(6))
            }
            prefs.put("layoutInitialized","1")
        }
        restoringFocus=true
        val wasAllApps=allApps
        val changedScreen=(allApps!=showAll || collectionTransition) && tiles.isNotEmpty()
        collectionTransition=false
        if(!showAll)collection=null
        val destination=if(collection!=null || preferRestore) restore else if(changedScreen)prefs.text(if(showAll)"focusLibrary" else "focusHome") else restore
        val old=linkedMapOf<String,AppTile>().apply{putAll(outgoing);tileIds.zip(tiles).forEach{(id,tile)->put(id,tile)}}
        val poses=old.mapValues{(_,tile)->Pose(tile.x,tile.y+scroll.top+stage.translationY,tile.alpha)}
        transition.stop();travel=emptyList();outgoing.clear();transitioning=changedScreen && prefs.motion!=0
        dockFrom=if(dock.visibility==VISIBLE)dock.alpha else 0f
        exitTravel=if(wasAllApps && !showAll)height*.55f else -18*u
        scrollAnimation?.cancel();scrollAnimation=null;scrollOffset=0;stage.translationY=0f
        old.values.forEach{it.selected=null;it.setOnKeyListener(null)}
        ++listGeneration;loading.clear();artworkRange=Int.MIN_VALUE to Int.MIN_VALUE
        allApps=showAll
        tiles.clear()
        fun obtain(id:String,app:AppEntry?,label:String,utility:String=""):AppTile{
            val previous=old[id]
            val tile=if(previous!=null && previous.label==label && previous.item==app)old.remove(id)!! else AppTile(context,app,label,prefs,utility)
            tile.isFocusable=true;tile.isClickable=true
            if(tile.parent==null)stage.addView(tile)
            if(tile.art==null)tile.art=repo.cachedArtwork(app?:AppEntry(id,label,android.content.ComponentName(context,LauncherActivity::class.java),false))
            tiles.add(tile);return tile
        }
        val folders=Folders.read(prefs)
        val nested=folders.flatMap{it.members}.toSet()
        val visible=apps.filter{AppVisibility.visible(it,prefs)}
        val dockIds=AppVisibility.dock(apps,prefs)
        val libraryIds=visible.map{it.id}.filter{it !in nested}+folders.map{it.tileId}
        val ordered=collection?.ids?.filter{id->apps.any{it.id==id}} ?: if(showAll)GridNavigation.ordered(libraryIds,prefs.list("order"),dockIds,emptySet()) else dockIds
        val byId=apps.associateBy { it.id }
        ordered.forEach { id ->
            val folder=folders.firstOrNull{it.tileId==id}
            val app=byId[id]
            if(folder!=null){
                val tile=obtain(id,null,folder.name,"folder")
                tile.setOnClickListener {if(moveId!=null)finishMove() else onFolderOpen(folder)}
                tile.setOnLongClickListener {onFolderContext(folder);true}
            }else if(app!=null){
                val tile=obtain(id,app,repo.displayName(app))
                tile.setOnClickListener { if(moveId!=null) finishMove() else onLaunch(app) }
                tile.setOnLongClickListener { onContext(app);true }
            }
        }
        if(showAll && collection==null) {
            obtain("@market",null,"Apps","market").also{
                it.setOnClickListener{onApps()}
            }
        }
        tileIds=ordered.filter{it in byId || folders.any{folder->folder.tileId==it}} + if(showAll && collection==null)listOf("@market")else emptyList()
        tiles.forEachIndexed { i,tile ->
            tile.id=generateViewId()
            tile.selected={ focused -> if(focused && !restoringFocus) {
                currentIndex=i
                prefs.put("focus",tileIds[i]);if(collection==null)prefs.put(if(showAll)"focusLibrary" else "focusHome",tileIds[i])
                onFocusApp(if(!showAll && i<6) tile.item else null)
                if(!cinematic)shelf.show(if(!showAll && i<6)tile.item?.let{TopShelfContent.list(prefs,it.id).firstOrNull()} else null)
                val row=i/6
                val tileTop=firstY+row*pitch
                val target=if(!showAll) {if(row==0)0 else tileTop-(32*u).toInt()} else when {
                    tileTop<scrollOffset+16*u -> tileTop-(16*u).toInt()
                    tileTop+cellHeight>scrollOffset+viewportHeight-16*u -> tileTop+cellHeight-viewportHeight+(16*u).toInt()
                    else -> scrollOffset
                }
                animateScrollTo(target)
            } }
            tile.setOnKeyListener { _,key,event ->
                if(event.action!=KeyEvent.ACTION_DOWN) false
                else if(!showAll && moveId==null && key==KeyEvent.KEYCODE_DPAD_DOWN){
                    if(event.repeatCount==0){
                        prefs.put("focusHome",tileIds[i])
                        showLibrary(apps,tileIds[i])
                    }
                    true
                }
                else {
                    val delta=when(key) { KeyEvent.KEYCODE_DPAD_LEFT -> -1 to 0; KeyEvent.KEYCODE_DPAD_RIGHT -> 1 to 0
                        KeyEvent.KEYCODE_DPAD_UP -> 0 to -1;KeyEvent.KEYCODE_DPAD_DOWN -> 0 to 1;else -> null }
                    if(delta==null) false else {
                        val next=GridNavigation.move(i,tiles.size,6,delta.first,delta.second)
                        if(moveId!=null){
                            if(next>=0 && next!=i && (tiles[next].item!=null || tileIds[next].startsWith("@folder:")))onMove?.invoke(moveId!!,tileIds[next])
                        }else if(next<0){
                            val app=tile.item
                            if(showAll && collection==null)rebuild(apps,false)
                            else if(app!=null && !showAll && i<6 && onCinematicContinue(app)){}
                            else if(app!=null && TopShelfContent.list(prefs,app.id).isNotEmpty())onTopShelf(app)
                            else if(app?.id=="com.stremio.one" && continueCount>0)onContinue()
                            else if(app==null && continueCount>0)onContinue()
                            else clock.requestFocus()
                        }else tiles[next].requestFocus()
                        true
                    }
                }
            }
        }
        weather.visibility=if(showAll) GONE else VISIBLE
        heading.visibility=if(showAll) VISIBLE else GONE
        heading.text=collection?.title ?: if(showAll && ordered.isEmpty())"Your library is ready" else "All Apps"
        if(showAll)shelf.show(null)
        hint.text=normalHint()
        currentIndex=tileIds.indexOf(destination).coerceAtLeast(0).coerceAtMost((tiles.size-1).coerceAtLeast(0))
        layoutChildren(width,height)
        if(transitioning){
            val paths=mutableListOf<Travel>()
            val incoming=if(!wasAllApps && showAll && collection==null)
                max(24*u,(poses.values.firstOrNull()?.y?:height*.7f)-(scroll.layoutParams as LayoutParams).topMargin-firstY)
                else 24*u
            tiles.forEachIndexed{i,tile->
                val pose=poses[tileIds[i]];val lp=tile.layoutParams as LayoutParams
                paths.add(Travel(tile,(pose?.x?:lp.leftMargin.toFloat())-lp.leftMargin,
                    if(pose!=null)pose.y-scroll.layoutParams.let{(it as LayoutParams).topMargin}-lp.topMargin else incoming,
                    pose?.alpha?:0f,false))
                tile.setLayerType(LAYER_TYPE_HARDWARE,null)
            }
            old.forEach{(id,tile)->
                tile.isFocusable=false;tile.isClickable=false
                val pose=poses[id]!!;val lp=tile.layoutParams as LayoutParams
                tile.translationX=pose.x-lp.leftMargin
                val y=pose.y-(scroll.layoutParams as LayoutParams).topMargin-lp.topMargin
                paths.add(Travel(tile,tile.translationX,y,pose.alpha,true));outgoing[id]=tile
            }
            travel=paths;dock.visibility=VISIBLE;transition.snap(0f);transition.to(1f)
        }else{old.values.forEach{stage.removeView(it)};finishTransition()}
        requestLayout();val generation=listGeneration;val restoreIndex=currentIndex
        post { if(generation==listGeneration){
            restoringFocus=false;currentIndex=restoreIndex
            tiles.getOrNull(restoreIndex)?.let{tile->tile.requestFocus();if(tile.hasFocus())tile.selected?.invoke(true)}
            if(tiles.isEmpty())clock.requestFocus()
            loadVisibleArtwork()
        } }
        // Warm only the first two library rows, after visible work, without decoding on the UI thread.
        if(!showAll)executor.execute{visible.take(12).forEach{runCatching{repo.artwork(it)}}}
    }
    fun releaseArtwork() {listGeneration++;loading.clear();artworkRange=Int.MIN_VALUE to Int.MIN_VALUE;tiles.forEach {it.art=null}}
    fun readyForSwitcherSnapshot():Boolean=tiles.take(6).filter{it.item!=null}.let{visible->visible.isNotEmpty() && visible.all{it.art!=null}}
    fun reloadArtwork() {loadVisibleArtwork()}
    private fun loadVisibleArtwork() {
        if(height<=0 || pitch<=0)return
        val generation=listGeneration
        val visibleTop=scrollOffset-pitch
        val visibleBottom=scrollOffset+viewportHeight+pitch
        val range=((visibleTop-firstY)/pitch) to ((visibleBottom-firstY)/pitch)
        if(range==artworkRange)return
        artworkRange=range
        tiles.forEachIndexed { index,tile ->
            if(tileIds.getOrNull(index)?.startsWith("@folder:")==true)return@forEachIndexed
            val app=tile.item?:AppEntry(if(tile.label=="All Apps")"@apps" else "@market",tile.label,android.content.ComponentName(context,LauncherActivity::class.java),false)
            val y=firstY+(index/6)*pitch
            if(y+pitch<visibleTop || y>visibleBottom) {tile.art=null;return@forEachIndexed}
            if(tile.art!=null || !loading.add(app.id))return@forEachIndexed
            executor.execute {
                val image=runCatching {repo.artwork(app)}.getOrNull()
                post {
                    if(generation==listGeneration) {
                        loading.remove(app.id)
                        if(y+pitch>=scrollOffset-pitch && y<=scrollOffset+viewportHeight+pitch)tile.art=image
                    }
                }
            }
        }
    }
    private fun normalHint()=if(moveId!=null)"Move with arrows · OK to save · Back to finish"
        else if(allApps && collection==null)"Up from top row for Home · Hold OK to arrange · Menu for Control Centre"
        else if(allApps)"Hold OK to arrange · Menu for Control Centre"
        else "Down for all apps · Hold OK to arrange · Menu for Control Centre"
    fun finishMove() { moveId=null;hint.text=normalHint() }
    fun beginMove(id: String) { moveId=id;hint.text="Move with arrows · OK to save · Back to finish" }
    fun focusGrid() { tiles.getOrNull(currentIndex)?.requestFocus() }
    fun focusFirst() { tiles.firstOrNull()?.requestFocus() }
    override fun onSizeChanged(w: Int,h: Int,oldw: Int,oldh: Int) { super.onSizeChanged(w,h,oldw,oldh);layoutChildren(w,h);post {loadVisibleArtwork()} }
    override fun onLayout(changed: Boolean,l: Int,t: Int,r: Int,b: Int) { super.onLayout(changed,l,t,r,b);if(changed)layoutChildren(r-l,b-t) }
    private fun animateScrollTo(requested:Int) {
        val target=requested.coerceIn(0,maxScroll)
        if(target==scrollOffset)return
        if(transitioning){transition.stop();finishTransition()}
        scrollAnimation?.cancel()
        if(prefs.motion==0){scrollOffset=target;stage.translationY=-target.toFloat();loadVisibleArtwork();return}
        scrollAnimation=android.animation.ValueAnimator.ofInt(scrollOffset,target).apply{
            duration=220;interpolator=android.view.animation.DecelerateInterpolator()
            addUpdateListener{scrollOffset=it.animatedValue as Int;stage.translationY=-scrollOffset.toFloat();loadVisibleArtwork()};start()
        }
    }
    override fun onDetachedFromWindow(){scrollAnimation?.cancel();transition.stop();finishTransition();super.onDetachedFromWindow()}
    private fun layoutChildren(w:Int,h:Int) {
        if(w<=0 || h<=0)return
        val geometry=HomeGeometry.calculate(w,h,prefs.number("safe",5),prefs.number("iconSize",1),prefs.number("gap",1),allApps)
        u=geometry.unit;firstY=geometry.firstY;pitch=geometry.rowPitch;cellHeight=geometry.cellHeight;viewportHeight=geometry.viewportHeight
        scroll.layoutParams=LayoutParams(w,viewportHeight).apply{topMargin=geometry.viewportTop}
        scroll.clipBounds=Rect(0,0,w,viewportHeight)
        val visibleCount=tiles.size
        val rows=ceil(visibleCount/6f).toInt()
        if(allApps)firstY=max((20*u).toInt(),(viewportHeight-((rows-1).coerceAtLeast(0)*pitch+cellHeight))/3)
        val stageHeight=max(viewportHeight,firstY+(rows-1).coerceAtLeast(0)*pitch+cellHeight+(24*u).toInt())
        maxScroll=(stageHeight-viewportHeight).coerceAtLeast(0)
        scrollOffset=scrollOffset.coerceIn(0,maxScroll);stage.translationY=-scrollOffset.toFloat()
        stage.layoutParams=LayoutParams(w,stageHeight)
        dock.visibility=if((allApps && !transitioning) || tiles.isEmpty())GONE else VISIBLE
        val iconInset=(geometry.cellWidth-geometry.iconWidth)/2
        val dockLeft=geometry.left+(6-min(6,visibleCount))*(geometry.cellWidth+geometry.columnGap)/2f+iconInset-24*u
        if(!allApps || dock.layoutParams==null)dock.layoutParams=LayoutParams((w-2*dockLeft).toInt().coerceAtLeast(0),(geometry.iconWidth*.6f+36*u).toInt()).apply{
            leftMargin=dockLeft.toInt();topMargin=firstY+(6*u).toInt()
        }
        tiles.forEachIndexed{i,tile->
            tile.configure(geometry.iconWidth,u)
            tile.layoutParams=LayoutParams(geometry.cellWidth,cellHeight).apply{
                leftMargin=(geometry.left+(6-min(6,visibleCount-(i/6)*6))*(geometry.cellWidth+geometry.columnGap)/2f+(i%6)*(geometry.cellWidth+geometry.columnGap)).toInt();topMargin=firstY+(i/6)*pitch
            }
        }
        clock.layoutParams=LayoutParams((277*u).toInt(),(76*u).toInt()).apply{
            gravity=Gravity.TOP or Gravity.RIGHT;rightMargin=geometry.outer.toInt();topMargin=(geometry.viewportTop-88*u).toInt()
        }
        clock.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,30*u)
        clock.setPadding((26*u).toInt(),0,(26*u).toInt(),0);clock.gravity=Gravity.CENTER;clock.setSingleLine(true)
        weather.layoutParams=LayoutParams((460*u).toInt(),(76*u).toInt()).apply{leftMargin=geometry.outer.toInt();topMargin=(geometry.viewportTop-88*u).toInt()}
        val shelfTop=(geometry.viewportTop+40*u).toInt()
        val shelfHeight=(firstY+geometry.viewportTop-shelfTop-58*u).toInt().coerceIn((180*u).toInt(),(310*u).toInt())
        val shelfWidth=(w-2*geometry.outer).toInt()
        shelf.layoutParams=LayoutParams(shelfWidth,shelfHeight,Gravity.TOP or Gravity.LEFT).apply{leftMargin=geometry.outer.toInt();topMargin=shelfTop}
        shelf.layoutAt(shelfWidth,shelfHeight,u)
        weather.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,28*u)
        weather.setPadding((26*u).toInt(),0,(26*u).toInt(),0);weather.gravity=Gravity.CENTER;weather.setSingleLine(true);weather.ellipsize=TextUtils.TruncateAt.END
        heading.layoutParams=LayoutParams(-2,(64*u).toInt()).apply{leftMargin=geometry.outer.toInt();topMargin=(geometry.viewportTop-88*u).toInt()}
        heading.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,42*u)
        hint.setSingleLine(true);hint.ellipsize=TextUtils.TruncateAt.END;hint.includeFontPadding=false
        hint.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,20*u)
        hint.layoutParams=LayoutParams((w-2*geometry.outer).toInt(),geometry.footerHeight).apply{leftMargin=geometry.outer.toInt();topMargin=geometry.footerTop}
    }
}
