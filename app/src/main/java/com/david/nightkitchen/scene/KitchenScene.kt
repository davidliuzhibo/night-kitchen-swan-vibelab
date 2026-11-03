package com.david.nightkitchen.scene

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.david.nightkitchen.BootTrace
import com.david.nightkitchen.BuildConfig
import com.david.nightkitchen.game.CookBook
import com.david.nightkitchen.game.Heat
import com.david.nightkitchen.game.GrabSlot
import com.david.nightkitchen.game.NIGHT_MENU
import com.david.nightkitchen.game.KitchenEvent
import com.david.nightkitchen.game.KitchenHub
import com.david.nightkitchen.game.KitchenState
import com.david.nightkitchen.game.Phase
import com.david.nightkitchen.game.Station
import com.david.nightkitchen.game.TapCooking
import com.david.nightkitchen.game.canDrag
import com.pico.spatial.core.container.SpatialViewEntityManager
import com.pico.spatial.core.ecs.DirectionalLightComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.ecs.EnvironmentLightingSettingsComponent
import com.pico.spatial.core.ecs.PointLightComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.content.SpatialViewAttachments
import com.pico.spatial.ui.foundation.gesture.detectSpatialDragGesture
import com.pico.spatial.ui.foundation.gesture.detectSpatialTapGesture
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.spatial.ui.platform.LocalPhysicalLengthConverter
import com.pico.spatial.ui.platform.PhysicalLengthConverter
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "NightKitchen"

private const val REVEAL_MS = 400L

private suspend fun loadWhilePaused(load: suspend () -> Unit) {
    KitchenHub.beginLoading()
    try { load() }
    catch(cancelled: CancellationException) { throw cancelled }
    catch(error: Exception) { Log.e(TAG, "dish loading failed", error); KitchenHub.failScene() }
    finally { KitchenHub.endLoading() }
}

private fun pxToMeters(
    pixels: Float,
    density: Float,
    converter: PhysicalLengthConverter,
): Float = converter.dpToLength(Dp(pixels / density), LengthUnit.Meters)

@Composable
fun KitchenScene() {
    val generation = KitchenHub.inputGeneration
    val input = remember(generation) { KitchenInput { KitchenHub.inputGeneration == generation } }
    val currentInput by rememberUpdatedState(input)
    val rig = remember { StallRig { currentInput.held } }
    var selected by remember(input) { mutableStateOf<GrabSlot?>(null) }
    val scope = key(input) { rememberCoroutineScope() }
    val context = LocalContext.current
    val converter = LocalPhysicalLengthConverter.current
    val density = LocalDensity.current
    LaunchedEffect(rig) {
        snapshotFlow { KitchenHub.state.phase }.collect { phase ->
            if (phase == Phase.PLATE || phase == Phase.SERVE || phase == Phase.REVIEW) {
                loadWhilePaused { rig.ensurePlated() }
            }
        }
    }
    LaunchedEffect(rig) {
        snapshotFlow { KitchenHub.state.active.id }.collect { id ->
            loadWhilePaused { rig.presentDish(id) }
        }
    }
    LaunchedEffect(rig) {
        try {
            rig.bind(context)
            rig.loadStall()
            if (rig.isBuilt) KitchenHub.markSceneReady() else KitchenHub.failScene()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(TAG, "scene load failed", error)
            KitchenHub.failScene()
        }
    }
    LaunchedEffect(rig, input) {
        rig.apply(KitchenHub.state)
        while (isActive) {
            rig.tickReveals()
            if (input.held == null) rig.applyIfChanged(KitchenHub.state)
            rig.tickCooking()
            rig.observe()
            if (input.expireGrace(SystemClock.uptimeMillis())) {
                rig.apply(KitchenHub.state)
            }
            delay(32)
        }
    }
    LaunchedEffect(rig) {
        while (isActive && !rig.isBuilt) {
            delay(32)
        }
        if (!isActive || !rig.isBuilt) return@LaunchedEffect
        val start = System.nanoTime()
        while (isActive) {
            val elapsed = (System.nanoTime() - start) / 1_000_000_000f
            val pulse =
                if (elapsed < 2f) {
                    abs(sin(elapsed * (PI.toFloat() * 2.5f)))
                } else {
                    0f
                }
            if (rig.held == null) {
                rig.apply(KitchenHub.state, pulse)
            }
            if (elapsed >= 2f) break
            delay(32)
        }
    }

    SpatialView(
        modifier =
            Modifier.fillMaxSize().pointerInput("contacts", input) {
                // Observe edges without consuming them. Process downs first so a hand
                // handoff in a single event isn't interpreted as an all-hands release.
                awaitPointerEventScope {
                    while (true) {
                        val changes = awaitPointerEvent(PointerEventPass.Initial).changes
                        for (change in changes) {
                            if (change.pressed && !change.previousPressed && input.pointerDown(change.id.value)) {
                                rig.apply(KitchenHub.state)
                                if (BuildConfig.DEBUG) Log.i("NightInputGate", "recovered after release session=$generation")
                            }
                        }
                        for (change in changes) {
                            if (!change.pressed && change.previousPressed) input.pointerUp(change.id.value)
                        }
                    }
                }
            }.pointerInput("tap", input) {
                detectSpatialTapGesture(context, targetedToEntity = TargetEntity.any()) { tap ->
                    val time = SystemClock.uptimeMillis()
                    val slot = rig.idOf(tap.targetEntity)
                    val hitStation = rig.stationOf(tap.targetEntity)
                    val ready = input.canTap(time)
                    if (BuildConfig.DEBUG) Log.i("NightInputGate", "tap-entry session=$generation current=${KitchenHub.inputGeneration} allowed=${KitchenHub.canInteract} tapReady=$ready held=${input.held} flight=${input.tapInFlight} kind=${tap.interactionKind} slot=$slot station=$hitStation")
                    if (!KitchenHub.canInteract || !ready) return@detectSpatialTapGesture
                    // An ingredient already in the pan is decoration, not a new
                    // selectable prop. It must not block the pan's tap target.
                    val station = hitStation ?: slot?.takeIf {
                        !canDrag(it, KitchenHub.state)
                    }?.let { StallLayout.nearest(rig.position(it)) }
                    Log.i(TAG, "tap slot=$slot station=$station kind=${tap.interactionKind}")
                    val pickup = TapCooking.pickup(slot, station, KitchenHub.state)
                    if (pickup != null) {
                        selected = TapCooking.select(pickup, KitchenHub.state)
                        KitchenHub.selectedLabel = selected?.let { rig.label(it) }
                        return@detectSpatialTapGesture
                    }
                    val event = station?.let { TapCooking.drop(selected, it, KitchenHub.state) }
                        ?: return@detectSpatialTapGesture
                    if (!input.beginTap(event.grab, time)) return@detectSpatialTapGesture
                    val before = KitchenHub.state
                    selected = null
                    scope.launch {
                        try {
                            val arrived = rig.animateTo(event.grab, event.station) {
                                input.tapInFlight && KitchenHub.canInteract
                            }
                            if (arrived && KitchenHub.canInteract &&
                                input.canDeliverTap(event.grab, before, KitchenHub.state)) {
                                KitchenHub.dispatch(event)
                            }
                        } finally {
                            input.endTap()
                            if (KitchenHub.inputGeneration == generation) {
                                KitchenHub.selectedLabel = null
                                rig.apply(KitchenHub.state)
                            }
                        }
                    }
                }
            }.pointerInput("drag", input) {
                fun finishGesture(cancelled: Boolean) {
                    val now = SystemClock.uptimeMillis()
                    val slot = input.dragged ?: return
                    if (!KitchenHub.canInteract || !canDrag(slot, KitchenHub.state)) {
                        input.abandonDrag(now)
                        rig.apply(KitchenHub.state)
                        return
                    }
                    val id = input.endDrag(cancelled, now) ?: return
                    val decision = GesturePlay.resolve(rig.position(id), cancelled)
                    Log.i(TAG, "drag end $id cancel=$cancelled pos=${rig.position(id)} decision=$decision")
                    when (decision) {
                        is GesturePlay.Decision.Drop -> {
                            KitchenHub.dispatch(KitchenEvent.Drop(id, decision.station))
                            rig.apply(KitchenHub.state)
                        }
                        GesturePlay.Decision.Home -> {
                            rig.apply(KitchenHub.state)
                        }
                        GesturePlay.Decision.Hold -> {
                            KitchenHub.pauseForInterruptedGrab()
                            Log.i(TAG, "gesture tracking hold $id")
                        }
                    }
                }
                try {
                    detectSpatialDragGesture(
                        context,
                        targetedToEntity = TargetEntity.any(),
                        onDragStart = { _, value ->
                            val previous = input.held
                            val id = TapCooking.pickup(rig.idOf(value.targetEntity),
                                rig.stationOf(value.targetEntity), KitchenHub.state)
                            val allowed = input.beginDrag(id.takeIf { KitchenHub.canInteract }, SystemClock.uptimeMillis())
                            if (BuildConfig.DEBUG) Log.i("NightInputGate", "drag-entry session=$generation previous=$previous picked=$id accepted=$allowed held=${input.held}")
                            Log.i(TAG, "drag start id=$id allowed=$allowed kind=${value.interactionKind}")
                            if (previous != null && previous != input.held) rig.apply(KitchenHub.state)
                            if (allowed) {
                                selected = null
                                KitchenHub.selectedLabel = null
                            }
                        },
                        onDragEnd = {
                            if (BuildConfig.DEBUG) Log.i("NightInputGate", "drag-end-entry session=$generation held=${input.dragged}")
                            finishGesture(cancelled = false)
                        },
                        onDragCancel = {
                            if (BuildConfig.DEBUG) Log.i("NightInputGate", "drag-cancel-entry session=$generation held=${input.dragged}")
                            finishGesture(cancelled = true)
                        },
                    ) { drag ->
                        if (!KitchenHub.canInteract) return@detectSpatialDragGesture
                        val id = input.dragged ?: return@detectSpatialDragGesture
                        val dx = GesturePlay.travel(pxToMeters(drag.dragAmount.x, density.density, converter))
                        val dy = GesturePlay.travel(pxToMeters(drag.dragAmount.y, density.density, converter))
                        val dz = GesturePlay.travel(pxToMeters(drag.dragAmount.z, density.density, converter))
                        rig.move(id, dx, -dy, dz)
                    }
                } finally {
                    if (input.dragged != null) {
                        input.abandonDrag(SystemClock.uptimeMillis())
                        rig.apply(KitchenHub.state)
                    }
                }
            },
        initial = { content, attachments ->
            rig.attachHost(content, attachments)
        },
        update = { content, attachments ->
            rig.attachHost(content, attachments)
            rig.tickReveals()
            if (input.held == null) rig.applyIfChanged(KitchenHub.state)
            rig.tickCooking()
            rig.observe()
            if (input.held == null) {
                rig.apply(KitchenHub.state, rig.openingPulse)
            }
        },
        attachments = {
            if (KitchenHub.showBadges) {
                val kit = DishKits.forId(KitchenHub.state.active.id)
                AttachmentPanel(id = "badge_rice") { BadgeFace(kit.grabBadges[0]) }
                AttachmentPanel(id = "badge_egg") { BadgeFace(kit.grabBadges[1]) }
                AttachmentPanel(id = "badge_scallion") { BadgeFace(kit.grabBadges[2]) }
                AttachmentPanel(id = "badge_spatula") { BadgeFace("铲") }
                AttachmentPanel(id = "badge_wok") { BadgeFace(kit.stationBadge) }
                AttachmentPanel(id = "badge_plate") { BadgeFace("盘") }
                AttachmentPanel(id = "badge_serve") { BadgeFace("出餐") }
            }
        },
    )
}

private class StallRig(private val heldSlot: () -> GrabSlot?) {
    private var appliedPoseKey=""
    fun applyIfChanged(s:KitchenState){val key="${s.active.id}/${s.phase}/${s.cooking.mask}/${s.cooking.lifted}/${s.cooking.coldServed}/${s.cooking.hotServed}";if(appliedPoseKey!=key){appliedPoseKey=key;apply(s)}}
    fun observe(){val id=shownDish;val correct=id!=null&&dishMesh?.let{pool.owns(it)==DishKits.forId(if(id=="beer-plate")"skewer"else id).plated.file}==true;NightLiveProbe.observe(props,id,correct)}
    private val foodFinish=FoodFinish()
    private val marketLamps=MarketLamps()
    private var hotRoot:Entity?=null
    private var hotMesh:Entity?=null
    private val flipCenters=mutableMapOf<Entity,Vector3>()
    private var sauceLayer:Entity?=null
    private var rolledFood:Entity?=null
    private var lid:Entity?=null
    private val steam=mutableListOf<Entity>()
    private var flipAngle=0f
    fun tickCooking(){
        if(!isBuilt)return
        val s=KitchenHub.state;val c=s.cooking;val grilling=CookBook.grilling(s.active.id)
        marketLamps.update(host,s)
        val target=if(grilling)c.face*180f else 0f
        flipAngle+=(target-flipAngle)*.16f
        if(grilling){
            val pairs=if(s.active.id=="beer-plate")listOf(hotRoot to hotMesh)else (0..2).filter{c.contains(it)}.map{props[GrabSlot.entries[it]] to grabMeshes[it]}
            for((anchor,mesh)in pairs)if(anchor!=null&&mesh!=null){
                val center=flipCenters.getOrPut(anchor){val b=mesh.getVisualBounds(anchor,recursive=true,enabledOnly=false);if(b.isEmpty())Vector3()else Vector3((b.min.x+b.max.x)/2,(b.min.y+b.max.y)/2,(b.min.z+b.max.z)/2)}
                val radians=flipAngle*PI.toFloat()/180;val rotatedX=center.x*cos(radians)-center.y*sin(radians);val rotatedY=center.x*sin(radians)+center.y*cos(radians)
                val at=if(anchor==hotRoot)Vec3(StallLayout.wok.x,StallLayout.wok.y+.1f,StallLayout.wok.z)else StallLayout.rest(s,props.entries.first{it.value==anchor}.key)
                anchor.components[TransformComponent::class.java]?.apply{setEulerAngles(EulerAngles(0f,0f,flipAngle));setPosition(Vector3(at.x+center.x-rotatedX,at.y+center.y-rotatedY,at.z))}
            }
        }
        if(lid==null){lid=StallAssets.anchor().also{e->e.setName("cooking.lid");e.components.set(ModelComponent(MeshResource.createCylinder(.025f,.17f),PhysicallyBasedMaterial.create().apply{setBaseColor(Color4(.35f,.43f,.45f,1f));setMetallic(.7f);setRoughness(.3f)}));host?.addEntity(e)}}
        lid?.let{it.enabled=s.active.id=="dumplings"&&c.covered&&s.phase in listOf(Phase.PREP,Phase.COOK);place(it,Vec3(StallLayout.wok.x,StallLayout.wok.y+.13f,StallLayout.wok.z),1f)}
        if(steam.isEmpty())repeat(8){steam.add(StallAssets.anchor().also{e->e.setName("cooking.steam.$it");e.components.set(ModelComponent(MeshResource.createSphere(.009f),PhysicallyBasedMaterial.create().apply{setBaseColor(Color4(.78f,.86f,.87f,1f));setRoughness(1f)}));host?.addEntity(e)})}
        if(sauceLayer==null){sauceLayer=StallAssets.anchor().also{e->e.setName("cooking.sauce");e.components.set(ModelComponent(MeshResource.createBox(Vector3(.18f,.003f,.10f)),PhysicallyBasedMaterial.create().apply{setBaseColor(Color4(.43f,.14f,.04f,1f));setRoughness(.32f)}));host?.addEntity(e)}}
        if(rolledFood==null){rolledFood=StallAssets.anchor().also{e->e.setName("cooking.rolled-noodle");e.components.set(ModelComponent(MeshResource.createCylinder(.18f,.033f),PhysicallyBasedMaterial.create().apply{setBaseColor(Color4(.75f,.42f,.14f,1f));setRoughness(.5f)}));e.components[TransformComponent::class.java]?.setEulerAngles(EulerAngles(0f,0f,90f));host?.addEntity(e)}}
        val coldActive=s.active.id=="cold-noodle"&&s.phase in listOf(Phase.PREP,Phase.COOK)
        sauceLayer?.let{it.enabled=coldActive&&c.brushed&&!c.rolled;place(it,Vec3(StallLayout.wok.x,StallLayout.wok.y+.095f,StallLayout.wok.z),1f)}
        rolledFood?.let{it.enabled=coldActive&&c.rolled;place(it,Vec3(StallLayout.wok.x,StallLayout.wok.y+.08f,StallLayout.wok.z),1f)}
        for(i in 0..2)props[GrabSlot.entries[i]]?.enabled=!(coldActive&&c.rolled)
        steam.forEachIndexed{i,e->e.enabled= KitchenHub.canInteract&&(c.mask>0||c.hotStarted&&!c.hotServed)&&c.heat!=Heat.OFF&&s.phase in listOf(Phase.PREP,Phase.COOK);val t=((SystemClock.uptimeMillis()+i*190)%1800)/1800f;place(e,Vec3(StallLayout.wok.x+sin(i*2.4f)*.075f,StallLayout.wok.y+.13f+t*.13f,StallLayout.wok.z+cos(i*2.4f)*.04f),.45f+sin(t*PI.toFloat())*.9f)}
        for(i in 0..2){val heat=if(!c.contains(i)||s.active.id=="beer-plate")0f else if(s.active.id=="dumplings")(c.stage*.3f+c.stageHeat/60f).coerceAtMost(1.5f)else (if(grilling&&c.face==1)c.b[i]else c.a[i])/CookBook.times(s.active.id)[i];foodFinish.apply(grabMeshes[i],heat)}
        val dishHeat=if(s.active.id=="dumplings")c.bottomHeat/18f else if(grilling)(c.a[0]+c.b[0])/46f else c.a.average().toFloat()/27f
        foodFinish.apply(dishMesh,dishHeat)
        foodFinish.apply(hotMesh,(if(c.face==0)c.a[0]else c.b[0])/23f)
        hotRoot?.enabled=s.active.id=="beer-plate"&&c.hotStarted&&!c.hotServed&&s.phase in listOf(Phase.PREP,Phase.COOK)
        if(!KitchenHub.playerPaused&&s.phase==Phase.COOK&&c.time-c.lastAction<=1&&s.active.id in listOf("egg-fried-rice","chow-mein")){
            val t=(SystemClock.uptimeMillis()%700)/700f*2*PI.toFloat()
            props[GrabSlot.SPATULA]?.takeIf{held!=GrabSlot.SPATULA}?.let{place(it,Vec3(StallLayout.wok.x+cos(t)*.07f,StallLayout.wok.y+.12f,StallLayout.wok.z+sin(t)*.06f),1f)}
        }
    }

    private val props = linkedMapOf<GrabSlot, Entity>()
    private val stations = linkedMapOf<Station, Entity>()
    private val hitOwners = HashMap<Entity, GrabSlot>()
    private var marker: Entity? = null
    private var host: SpatialViewEntityManager? = null
    private var wokRoot: Entity? = null
    private var dishRootEntity: Entity? = null
    private val grabRoots = arrayOfNulls<Entity>(3)
    private var stationMesh: Entity? = null
    private var dishMesh: Entity? = null
    private val grabMeshes = arrayOfNulls<Entity>(3)
    private val pool = MeshPool()
    private val swapGate = Mutex()
    private val reveals = HashMap<Entity, MeshReveal>()
    private var shownDish: String? = null
    private var pendingDish: String = "egg-fried-rice"
    private var attachments: SpatialViewAttachments? = null
    val held: GrabSlot? get() = heldSlot()
    var openingPulse: Float = 0f

    @Volatile
    var isBuilt: Boolean = false
        private set

    @Volatile
    private var loadStarted: Boolean = false

    fun bind(context: Context) {
        StallAssets.bind(context)
    }

    fun attachHost(
        content: SpatialViewEntityManager,
        attachments: SpatialViewAttachments,
    ) {
        val first = host == null
        host = content
        this.attachments = attachments
        if (first) BootTrace.stage("panels ready")
    }

    suspend fun loadStall() {
        if (loadStarted) return
        loadStarted = true
        var spins = 0
        while ((host == null || attachments == null) && spins < 40) {
            delay(50)
            spins++
        }
        val content = host ?: return
        var labels = attachments ?: return
        val started = System.nanoTime()

        // Empty anchors and nameplates only. No glTF on this first hop.
        val anchorsStarted = SystemClock.elapsedRealtime()
        hangLights(content)
        val wokAnchor = mount(content, StallLayout.wok)
        val plateRoot = mount(content, StallLayout.plate)
        val serveRoot = mount(content, StallLayout.serve)
        val riceRoot = mount(content, StallLayout.rest(KitchenHub.state, GrabSlot.A))
        val eggRoot = mount(content, StallLayout.rest(KitchenHub.state, GrabSlot.B))
        val scallionRoot = mount(content, StallLayout.rest(KitchenHub.state, GrabSlot.C))
        val spatulaRoot = mount(content, StallLayout.rest(KitchenHub.state, GrabSlot.SPATULA))
        val dishAnchor = mount(content, StallLayout.rest(KitchenHub.state, GrabSlot.DISH))
        wokRoot = wokAnchor
        dishRootEntity = dishAnchor
        grabRoots[0] = riceRoot
        grabRoots[1] = eggRoot
        grabRoots[2] = scallionRoot
        dishAnchor.enabled = StallLayout.dishVisible(KitchenHub.state)
        BootTrace.span("empty anchors", anchorsStarted)
        val badgesStarted = SystemClock.elapsedRealtime()
        KitchenHub.markShowBadges()
        var badgeSpins = 0
        while (labels.entity("badge_wok") == null && badgeSpins < 40) {
            delay(50)
            labels = this.attachments ?: labels
            badgeSpins++
        }
        Log.i(TAG, "badge views after $badgeSpins yields")
        BootTrace.span("badge views", badgesStarted)
        val badges =
            listOf(
                BadgePin("badge_rice", riceRoot, GrabSlot.A, 0.12f),
                BadgePin("badge_egg", eggRoot, GrabSlot.B, 0.09f),
                BadgePin("badge_scallion", scallionRoot, GrabSlot.C, 0.17f),
                BadgePin("badge_spatula", spatulaRoot, GrabSlot.SPATULA, 0.22f),
                BadgePin("badge_wok", wokAnchor, null, 0.18f),
                BadgePin("badge_plate", plateRoot, null, 0.08f),
                BadgePin("badge_serve", serveRoot, null, 0.14f),
            )
        val pinStarted = SystemClock.elapsedRealtime()
        var pinned = 0
        for (badge in badges) {
            if (labels.entity(badge.id) != null) pinned++
            pinBadge(labels, badge.id, badge.root, badge.slot, badge.lift)
        }
        Log.i(TAG, "pinned $pinned/${badges.size} badges in one looper turn")
        BootTrace.span("pin badges", pinStarted)
        BootTrace.stage("nameplates mounted")
        Log.i(TAG, "nameplates mounted; cold start has no glb and no collider")
        KitchenHub.markNameplatesMounted()

        var wokReady = false
        var plateReady = false
        var serveReady = false
        suspend fun loadOne(step: BootStep) {
            Log.i(TAG, "boot idle ${step.idleBeforeMs}ms before ${step.slot}")
            breatheMain(step.idleBeforeMs)
            when (step.slot) {
                BootSlot.WOK -> {
                    val mesh = attachModel(wokAnchor, StallAssets.wok)
                    if (mesh != null) {
                        pool.remember(StallAssets.wok, mesh)
                        stationMesh = mesh
                        wokReady = true
                        stations[Station.WOK] = wokAnchor
                    }
                }
                BootSlot.RICE -> {
                    val mesh = attachModel(riceRoot, StallAssets.riceBowl)
                    if (mesh != null) {
                        pool.remember(StallAssets.riceBowl, mesh)
                        grabMeshes[0] = mesh
                        armAfterYield(GrabSlot.A, riceRoot)
                    }
                }
                BootSlot.EGG -> {
                    val mesh = attachModel(eggRoot, StallAssets.egg)
                    if (mesh != null) {
                        pool.remember(StallAssets.egg, mesh)
                        grabMeshes[1] = mesh
                        armAfterYield(GrabSlot.B, eggRoot)
                    }
                }
                BootSlot.COUNTER -> {
                    val piece = StallAssets.counter
                    attachModel(mount(content, piece.at), piece.model)
                }
                BootSlot.LANTERN -> {
                    val piece = StallAssets.lantern
                    attachModel(mount(content, piece.at), piece.model)
                }
                BootSlot.SCALLION -> {
                    val mesh = attachModel(scallionRoot, StallAssets.scallion)
                    if (mesh != null) {
                        pool.remember(StallAssets.scallion, mesh)
                        grabMeshes[2] = mesh
                        armAfterYield(GrabSlot.C, scallionRoot)
                    }
                }
                BootSlot.SPATULA -> {
                    if (attachModel(spatulaRoot, StallAssets.spatula) != null) {
                        armAfterYield(GrabSlot.SPATULA, spatulaRoot)
                    }
                }
                BootSlot.PLATE -> {
                    if (attachModel(plateRoot, StallAssets.plate) != null) {
                        plateReady = true
                        stations[Station.PLATE] = plateRoot
                    }
                }
                BootSlot.BELL -> {
                    if (attachModel(serveRoot, StallAssets.bell) != null) {
                        serveReady = true
                        stations[Station.SERVE] = serveRoot
                    }
                }
                BootSlot.DISH -> {
                    val mesh = attachModel(dishAnchor, StallAssets.riceBowl)
                    if (mesh != null) {
                        pool.remember(StallAssets.riceBowl, mesh)
                        dishMesh = mesh
                        armAfterYield(GrabSlot.DISH, dishAnchor)
                    }
                }
                BootSlot.RING -> {
                    val ringModel = StallAssets.load(StallAssets.dropRing)
                    if (ringModel != null) {
                        StallAssets.fit(ringModel, StallAssets.dropRing)
                        breatheMain(StallBoot.AFTER_DECODE_MS)
                        val ring = StallAssets.anchor()
                        ring.addChild(ringModel)
                        beginReveal(ringModel, StallAssets.dropRing)
                        ring.enabled = false
                        marker = ring
                        content.addEntity(ring)
                    }
                }
            }
        }
        for (step in StallBoot.coldSteps) {
            loadOne(step)
        }
        Log.i(
            TAG,
            "cold glb=${StallBoot.coldSteps.size} in ${(System.nanoTime() - started) / 1_000_000}ms; " +
                "waiting for the order board",
        )
        coroutineScope {
            val warming = async(Dispatchers.IO) { StallAssets.writeCaches(bootFiles()) }
            // Loading is automatic; a first-time player need not discover an invisible trigger.
            KitchenHub.markOrderBoardTouched()
            warming.await()
            Log.i(TAG, "order board touched; streaming the opening dish, one file per looper turn")
            for (step in StallBoot.deferredSteps) {
                loadOne(step)
            }
        }

        val ready =
            wokReady &&
                plateReady &&
                serveReady &&
                props.containsKey(GrabSlot.A) &&
                props.containsKey(GrabSlot.B) &&
                props.containsKey(GrabSlot.C) &&
                props.containsKey(GrabSlot.SPATULA)
        if (!ready) {
            Log.e(TAG, "stall models missing; grab loop stays idle")
            return
        }
        stations.forEach { (station, entity) ->
            val hit = StallLayout.stationHitTarget(station)
            StallAssets.arm(entity, hit.radius, hit.lift)
        }
        breatheMain(StallBoot.BETWEEN_MODELS_MS)
        attachModel(mount(content, DishKits.clothAt), DishKits.cloth)
        shownDish = "egg-fried-rice"
        isBuilt = true
        apply(KitchenHub.state)
        presentDish(pendingDish)
        prefetchNext(pendingDish)
    }

    /**
     * Swap the three grabs and the center cookware for [id].
     * The plated mesh stays unloaded until the phase shows the finished dish,
     * so prep does not decode it. One new file per looper turn. A mesh already
     * decoded is reparented. Badge children stay on the anchors.
     * Bytes for this kit and the plated file are copied on IO before any decode.
     */
    suspend fun presentDish(id: String) {
        pendingDish = id
        if (!isBuilt) return
        swapGate.withLock {
            val raw = DishKits.forId(id)
            val kit = if(id=="beer-plate")raw.copy(station=DishKits.forId("skewer").station,plated=DishKits.forId("skewer").plated)else raw
            if (shownDish != id) {
                StallAssets.cacheCopies(kitFiles(kit, plated = true))
                stationMesh = retarget(wokRoot, kit.station, stationMesh)
                for (index in 0..2) {
                    grabMeshes[index] = retarget(grabRoots[index], kit.grabs[index], grabMeshes[index])
                }
                pool.release(dishMesh)
                dishMesh = null
                if(id=="beer-plate"&&hotRoot==null){StallAssets.cacheCopies(listOf(DishKits.forId("skewer").grabs[0].file));val anchor=StallAssets.anchor();host?.addEntity(anchor);place(anchor,Vec3(StallLayout.wok.x,StallLayout.wok.y+.1f,StallLayout.wok.z),1f);hotRoot=anchor;hotMesh=attachModel(anchor,DishKits.forId("skewer").grabs[0]);anchor.enabled=false}
                props.values.forEach{it.components[TransformComponent::class.java]?.setEulerAngles(EulerAngles())}
                flipCenters.clear();flipAngle=0f
                shownDish = id
                Log.i(TAG, "dish kit $id")
            }
            if (StallLayout.dishVisible(KitchenHub.state)) {
                mountPlated(kit)
            }
            apply(KitchenHub.state)
        }
    }

    /** Decode the finished dish the first time the phase actually shows it. */
    suspend fun ensurePlated() {
        if (!isBuilt) return
        swapGate.withLock {
            if (!StallLayout.dishVisible(KitchenHub.state)) return@withLock
            mountPlated(DishKits.forId(if((shownDish ?: pendingDish)=="beer-plate")"skewer"else shownDish ?: pendingDish))
            apply(KitchenHub.state)
        }
    }

    private suspend fun mountPlated(kit: DishKit) {
        val root = dishRootEntity ?: return
        val current = dishMesh
        if (current != null && pool.owns(current) == kit.plated.file) return
        StallAssets.cacheCopies(listOf(kit.plated.file))
        val mesh = retarget(root, kit.plated, current)
        dishMesh = mesh
        if (mesh != null && !props.containsKey(GrabSlot.DISH)) {
            armAfterYield(GrabSlot.DISH, root)
        }
        if (mesh != null) beginReveal(mesh, kit.plated)
    }

    private suspend fun prefetchNext(id: String) {
        val ids = NIGHT_MENU.map { it.id }
        val index = ids.indexOf(id)
        if (index < 0) return
        val next = DishKits.forId(ids[(index + 1) % ids.size])
        StallAssets.cacheCopies(kitFiles(next, plated = true))
    }

    private fun bootFiles(): List<String> =
        listOf(
            StallAssets.wok.file,
            StallAssets.riceBowl.file,
            StallAssets.egg.file,
            StallAssets.counter.model.file,
            StallAssets.lantern.model.file,
            StallAssets.scallion.file,
            StallAssets.spatula.file,
            StallAssets.plate.file,
            StallAssets.bell.file,
            StallAssets.dropRing.file,
        )

    private fun kitFiles(
        kit: DishKit,
        plated: Boolean,
    ): List<String> =
        buildList {
            add(kit.station.file)
            kit.grabs.forEach { add(it.file) }
            if (plated) add(kit.plated.file)
        }.distinct()

    private suspend fun retarget(
        root: Entity?,
        spec: BakedModel,
        previous: Entity?,
    ): Entity? {
        if (root == null) return previous
        if (previous != null && pool.owns(previous) == spec.file) {
            StallAssets.fit(previous, spec)
            return previous
        }
        val cached = pool.hasFree(spec.file)
        pool.release(previous)
        if (!cached) breatheMain(StallBoot.SWAP_GAP_MS)
        val model = pool.acquire(spec) ?: error("Missing model: ${spec.file}")
        if (!cached) breatheMain(StallBoot.AFTER_DECODE_MS)
        model.removeFromParent()
        root.addChild(model)
        beginReveal(model, spec)
        return model
    }

    private fun hangLights(content: SpatialViewEntityManager) {
        try {
            val env = StallAssets.anchor()
            env.components.set(EnvironmentLightingSettingsComponent(0.48f))
            content.addEntity(env)
            val key = StallAssets.anchor()
            key.components.set(PointLightComponent(Color4(1f, 0.74f, 0.42f, 1f), 9.5f, 1.85f))
            content.addEntity(key)
            place(key, Vec3(0.02f, StallLayout.SURFACE_Y + 0.34f, 0.18f), 1f)
            val lantern = StallAssets.anchor()
            lantern.components.set(PointLightComponent(Color4(1f, 0.52f, 0.18f, 1f), 7.5f, 1.25f))
            content.addEntity(lantern)
            place(lantern, Vec3(0.26f, StallLayout.SURFACE_Y + 0.28f, -0.12f), 1f)
            val fill = StallAssets.anchor()
            fill.components.set(PointLightComponent(Color4(0.55f, 0.64f, 0.82f, 1f), 1.6f, 1.4f))
            content.addEntity(fill)
            place(fill, Vec3(-0.16f, StallLayout.SURFACE_Y + 0.22f, 0.22f), 1f)
            val sun = StallAssets.anchor()
            sun.components.set(DirectionalLightComponent(Color4(1f, 0.84f, 0.64f, 1f), 1.35f))
            content.addEntity(sun)
            Log.i(TAG, "warm stall lights hung")
        } catch (error: Throwable) {
            Log.w(TAG, "stall lights skipped", error)
        }
    }

    private fun mount(
        content: SpatialViewEntityManager,
        at: Vec3,
    ): Entity {
        val root = StallAssets.anchor()
        content.addEntity(root)
        place(root, at, 1f)
        return root
    }

    private suspend fun attachModel(
        root: Entity,
        spec: BakedModel,
    ): Entity? {
        val started = System.nanoTime()
        val model = StallAssets.load(spec) ?: return null
        Log.i(
            TAG,
            "decoded ${spec.file} in ${(System.nanoTime() - started) / 1_000_000}ms " +
                "before yielding the main looper",
        )
        StallAssets.fit(model, spec)
        // Parenting is a separate looper message from the native decode.
        breatheMain(StallBoot.AFTER_DECODE_MS)
        val holder =
            if (spec.yaw == 0f) {
                root
            } else {
                val pivot = StallAssets.anchor()
                val half = spec.yaw / 2f
                pivot.components[TransformComponent::class.java]?.setQuaternion(
                    Quat(0f, sin(half), 0f, cos(half)),
                )
                root.addChild(pivot)
                pivot
            }
        holder.addChild(model)
        beginReveal(model, spec)
        Log.i(TAG, "attached ${spec.file}")
        return model
    }

    fun tickReveals() {
        if (reveals.isEmpty()) return
        val now = SystemClock.elapsedRealtime()
        val iterator = reveals.entries.iterator()
        while (iterator.hasNext()) {
            val (mesh, reveal) = iterator.next()
            val t = ((now - reveal.started).toFloat() / REVEAL_MS).coerceIn(0f, 1f)
            val smooth = t * t * (3f - 2f * t)
            setMeshScale(mesh, reveal.scale * (0.04f + 0.96f * smooth))
            if (t >= 1f) iterator.remove()
        }
    }

    private fun beginReveal(
        mesh: Entity,
        spec: BakedModel,
    ) {
        reveals[mesh] = MeshReveal(spec.scale, SystemClock.elapsedRealtime())
        setMeshScale(mesh, spec.scale * 0.04f)
    }

    private fun setMeshScale(
        mesh: Entity,
        scale: Float,
    ) {
        mesh.components[TransformComponent::class.java]?.setScaleVector(Vector3(scale, scale, scale))
    }

    private suspend fun armAfterYield(
        slot: GrabSlot,
        root: Entity,
    ) {
        breatheMain(StallBoot.AFTER_ATTACH_MS)
        arm(slot, root)
    }

    private fun arm(
        slot: GrabSlot,
        root: Entity,
    ) {
        val hit = StallLayout.hitTarget(slot)
        StallAssets.arm(root, hit.radius, hit.lift)
        props[slot] = root
        hitOwners[root] = slot
    }

    private data class BadgePin(
        val id: String,
        val root: Entity,
        val slot: GrabSlot?,
        val lift: Float,
    )

    private fun pinBadge(
        attachments: SpatialViewAttachments,
        id: String,
        parent: Entity?,
        slot: GrabSlot?,
        lift: Float,
    ) {
        val host = parent ?: return
        val label = attachments.entity(id) ?: return
        label.removeFromParent()
        label.components[TransformComponent::class.java]?.setPosition(Vector3(0f, lift, 0f))
        host.addChild(label)
        if (slot != null) indexHits(slot, label)
    }

    private fun indexHits(
        slot: GrabSlot,
        entity: Entity,
    ) {
        hitOwners[entity] = slot
        for (child in entity.getChildren()) {
            indexHits(slot, child)
        }
    }

    fun idOf(entity: Entity?): GrabSlot? {
        var current = entity
        var guard = 0
        while (current != null && guard < 24) {
            hitOwners[current]?.let { return it }
            current = current.getParent()
            guard++
        }
        return null
    }

    fun stationOf(entity: Entity?): Station? {
        var current = entity
        repeat(24) {
            if (current == null) return null
            stations.entries.firstOrNull { it.value == current }?.let { return it.key }
            current = current?.getParent()
        }
        return null
    }

    fun label(slot: GrabSlot): String = when (slot) {
        GrabSlot.A, GrabSlot.B, GrabSlot.C -> DishKits.forId(KitchenHub.state.active.id).grabBadges[slot.prepIndex!!]
        GrabSlot.SPATULA -> "锅铲"
        GrabSlot.DISH -> "做好的菜"
    }

    suspend fun animateTo(slot: GrabSlot, station: Station, stillActive: () -> Boolean): Boolean {
        val from = position(slot)
        val to = when (station) { Station.WOK -> StallLayout.wok; Station.PLATE -> StallLayout.plate; Station.SERVE -> StallLayout.serve }
        repeat(12) { frame ->
            if (!stillActive()) return false
            val t = (frame + 1) / 12f
            val smooth = t * t * (3f - 2f * t)
            props[slot]?.let { place(it, Vec3(from.x + (to.x-from.x)*smooth,
                from.y + (to.y-from.y)*smooth + sin(t*PI.toFloat())*.08f,
                from.z + (to.z-from.z)*smooth), 1f) }
            delay(25)
        }
        return stillActive()
    }

    fun position(id: GrabSlot): Vec3 {
        val transform = props[id]?.components?.get(TransformComponent::class.java)
        val position = transform?.position ?: return StallLayout.hidden
        return Vec3(position.x, position.y, position.z)
    }

    fun move(
        id: GrabSlot,
        dx: Float,
        dy: Float,
        dz: Float,
    ) {
        val transform = props[id]?.components?.get(TransformComponent::class.java) ?: return
        val position = transform.position
        val next =
            StallLayout.withinReach(
                Vec3(position.x + dx, position.y + dy, position.z + dz),
            )
        transform.setPosition(Vector3(next.x, next.y, next.z))
    }

    fun apply(
        state: KitchenState,
        pulse: Float = 0f,
    ) {
        openingPulse = if (isBuilt) pulse else 0f
        val target = state.expectedGrab
        props.forEach { (id, root) ->
            if (id == held) return@forEach
            val live =
                id == target &&
                    id != GrabSlot.DISH &&
                    (state.phase == Phase.PREP || state.phase == Phase.COOK)
            val hiddenDish = id == GrabSlot.DISH && !StallLayout.dishVisible(state)
            val scale =
                when {
                    hiddenDish -> 0.01f
                    live -> 1.14f + 0.16f * pulse.coerceIn(0f, 1f)
                    else -> 1f
                }
            place(root, StallLayout.rest(state, id), scale)
            if (id == GrabSlot.DISH) {
                root.enabled = StallLayout.dishVisible(state)
            }
        }
        val hotStation = state.expectedStation
        stations.forEach { (station, root) ->
            val hot = station == hotStation
            val pose =
                when (station) {
                    Station.WOK -> StallLayout.wok
                    Station.PLATE -> StallLayout.plate
                    Station.SERVE -> StallLayout.serve
                }
            place(root, pose, if (hot) 1.06f else 1f)
        }
        val ring = marker
        if (ring != null) {
            if (hotStation == null) {
                ring.enabled = false
            } else {
                val at =
                    when (hotStation) {
                        Station.WOK -> StallLayout.wok
                        Station.PLATE -> StallLayout.plate
                        Station.SERVE -> StallLayout.serve
                    }
                ring.enabled = true
                place(ring, at, 1f)
            }
        }
    }

    private fun place(
        entity: Entity,
        pose: Vec3,
        scale: Float,
    ) {
        entity.components[TransformComponent::class.java]?.apply {
            setPosition(Vector3(pose.x, pose.y, pose.z))
            setScaleVector(Vector3(scale, scale, scale))
        }
    }
}

private data class MeshReveal(
    val scale: Float,
    val started: Long,
)

private class MeshPool {
    private val free = HashMap<String, ArrayDeque<Entity>>()
    private val fileOf = HashMap<Entity, String>()

    fun owns(entity: Entity): String? = fileOf[entity]

    fun hasFree(file: String): Boolean = free[file]?.isNotEmpty() == true

    fun remember(
        spec: BakedModel,
        entity: Entity,
    ) {
        fileOf[entity] = spec.file
    }

    fun release(entity: Entity?) {
        if (entity == null) return
        val file = fileOf.remove(entity) ?: return
        entity.removeFromParent()
        free.getOrPut(file) { ArrayDeque() }.addLast(entity)
    }

    suspend fun acquire(spec: BakedModel): Entity? {
        val reused = free[spec.file]?.removeFirstOrNull()
        val model = reused ?: StallAssets.load(spec) ?: return null
        StallAssets.fit(model, spec)
        fileOf[model] = spec.file
        return model
    }
}

@Composable
private fun BadgeFace(label: String) {
    Box(
            modifier =
                Modifier
                    .size(96.dp, 44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    // design-style: design-surface food-badge
                    .background(PicoTheme.colorScheme.fillPrimary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                color = PicoTheme.colorScheme.labelPrimaryLight,
                fontSize = 20.sp,
                lineHeight = 26.sp,
                textAlign = TextAlign.Center,
            )
        }
}

