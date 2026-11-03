package com.david.nightkitchen.scene

import android.content.Context
import android.util.Log
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.math.Vector3
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Prebaked glTF. Nothing here builds a mesh on the device.
 *
 * Wok, rice bowl, egg, scallion, spatula, and plate are Seed3D meshes
 * (doubao-seed3d-2-0). The other dish kits are the round-2 Seed3D meshes;
 * see [DishKits]. The five files that were over 5 MB were vertex-clustered
 * from those meshes and repacked as 512² JPEG, with no Draco and no
 * `KHR_mesh_quantization` (SDK 6.1.9 does not document either extension).
 * `meshopt` simplify stops after a few percent on these shells, so the
 * reduction is a grid weld of the Seed3D vertices, not a hand-authored
 * replacement. Packed sizes: plated-chow 265328 bytes / 8493 tris
 * (was 19582408 / 249162), noodle-brick 357252 / 9848 (was 13356188 /
 * 138271), chow-noodles 335500 / 11387 (was 10133560 / 124716),
 * chow-cabbage 313064 / 9127 (was 7788272 / 76768), noodle-veg 275344 /
 * 5629 (was 5361444 / 41265).
 *
 * [Entity.loadSuspend] always hops to `Dispatchers.Main`. Calling it from
 * a worker `runBlocking`s back onto that same native decode. There is no
 * public API that parses glTF off the frame loop, so [load] does not pretend
 * to. It copies the bytes on [Dispatchers.IO] first, then [yieldMain] so the
 * decode is its own looper message, then `loadSuspend(File)`. A [Mutex]
 * keeps decodes one at a time. Callers still wait between files. See [StallBoot].
 */
internal data class BakedModel(
    val file: String,
    val scale: Float,
    val minX: Float,
    val minY: Float,
    val minZ: Float,
    val maxX: Float,
    val maxY: Float,
    val maxZ: Float,
    val bottom: Boolean,
    val nudgeY: Float = 0f,
    val yaw: Float = 0f,
)

internal data class SceneryModel(
    val model: BakedModel,
    val at: Vec3,
)

internal object StallAssets {
    private const val TAG = "NightKitchen"
    private val loadGate = Mutex()
    private val copyLock = Any()

    @Volatile
    private var appContext: Context? = null

    fun bind(context: Context) {
        appContext = context.applicationContext
    }

    val counter =
        scenery(
            "counter.glb",
            1f,
            -0.4581f, 0f, -0.2201f,
            0.4581f, 0.5331f, 0.2199f,
            Vec3(0f, StallLayout.tableFootY, 0f),
        )
    val lantern =
        scenery(
            "lantern.glb",
            0.88f,
            -0.07f, 0f, -0.07f,
            0.07f, 0.30f, 0.07f,
            Vec3(0.26f, StallLayout.SURFACE_Y, -0.16f),
        )

    val wok = seated("wok.glb", 0.40f, -0.4974f, -0.1957f, -0.4267f, 0.4983f, 0.1970f, 0.4264f)
    val plate = seated("plate.glb", 0.22f, -0.4983f, -0.0457f, -0.4999f, 0.4993f, 0.0459f, 0.4995f)
    val bell = seated("serve-bell.glb", 1.25f, -0.04f, 0f, -0.04f, 0.04f, 0.09f, 0.04f)
    val egg = seated("egg.glb", 0.064f, -0.3886f, -0.5000f, -0.3902f, 0.3890f, 0.4993f, 0.3901f)
    val scallion = seated("scallion.glb", 0.15f, -0.0798f, -0.5f, -0.1171f, 0.0803f, 0.4973f, 0.1172f)
    val spatula =
        seated(
            "spatula.glb",
            0.20f,
            -0.0562f,
            -0.4995f,
            -0.1548f,
            0.0561f,
            0.4998f,
            0.1547f,
            yaw = 1.5708f,
        )
    val riceBowl =
        seated("rice-bowl.glb", 0.17f, -0.4927f, -0.3133f, -0.4951f, 0.4808f, 0.3067f, 0.4872f)
    val dropRing = centered("drop-ring.glb", 1.05f, -0.1095f, -0.0045f, -0.1095f, 0.1095f, 0.0045f, 0.1095f)

    val scenery = listOf(counter, lantern)

    /** Copy [files] into the cache dir. Does not decode. Safe to call off the main thread. */
    fun writeCaches(files: Collection<String>) {
        val context = appContext ?: return
        for (file in files.distinct()) {
            try {
                ensureCached(context, file)
            } catch (error: Throwable) {
                Log.w(TAG, "cache copy failed: $file", error)
            }
        }
    }

    suspend fun cacheCopies(files: Collection<String>) {
        withContext(Dispatchers.IO) { writeCaches(files) }
    }

    suspend fun load(model: BakedModel): Entity? =
        loadGate.withLock {
            val started = System.nanoTime()
            try {
                val cached =
                    withContext(Dispatchers.IO) {
                        val context = appContext ?: return@withContext null
                        try {
                            ensureCached(context, model.file)
                        } catch (error: Throwable) {
                            Log.w(TAG, "cache copy failed: ${model.file}", error)
                            null
                        }
                    }
                // withContext(IO) resumes on Main.immediate. Decode needs a new message.
                yieldMain()
                val entity =
                    if (cached != null) {
                        try {
                            Entity.loadSuspend(cached)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            Log.w(TAG, "file decode failed, using asset ${model.file}", error)
                            Entity.loadSuspend("asset://cookware/${model.file}")
                        }
                    } else {
                        Entity.loadSuspend("asset://cookware/${model.file}")
                    }
                Log.i(
                    TAG,
                    "loaded ${model.file} in ${(System.nanoTime() - started) / 1_000_000}ms",
                )
                entity
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "gltf load failed: ${model.file}", error)
                null
            }
        }

    private fun ensureCached(
        context: Context,
        file: String,
    ): File? {
        synchronized(copyLock) {
            val out = File(context.cacheDir, "cookware/$file")
            if (out.isFile && out.length() > 0L) return out
            out.parentFile?.mkdirs()
            val part = File(out.parentFile, "$file.part")
            context.assets.open("cookware/$file").use { input ->
                part.outputStream().use { output -> input.copyTo(output) }
            }
            if (out.exists()) out.delete()
            if (!part.renameTo(out)) {
                part.copyTo(out, overwrite = true)
                part.delete()
            }
            return if (out.isFile && out.length() > 0L) out else null
        }
    }

    fun arm(entity: Entity, radius: Float = .10f, lift: Float = .04f) {
        entity.components.set(InteractableComponent())
        entity.components.set(HoverEffectComponent())
        val palm = ShapeResource.createSphere(radius).offsetByTranslation(Vector3(0f,lift,0f))
        entity.components.set(
            CollisionComponent(
                collisionShape = listOf(palm),
                physicsMaterial = PhysicsMaterialResource(),
            ),
        )
    }

    fun fit(model: Entity, spec: BakedModel) {
        val cx = (spec.minX + spec.maxX) / 2f
        val cz = (spec.minZ + spec.maxZ) / 2f
        val cy = if (spec.bottom) spec.minY else (spec.minY + spec.maxY) / 2f
        val scale = spec.scale
        val transform =
            model.components[TransformComponent::class.java]
                ?: TransformComponent().also { model.components.set(it) }
        transform.setScaleVector(Vector3(scale, scale, scale))
        transform.setPosition(
            Vector3(
                -cx * scale,
                -cy * scale + spec.nudgeY,
                -cz * scale,
            ),
        )
    }

    fun anchor(): Entity = Entity().apply { components.set(TransformComponent()) }

    private fun centered(
        file: String,
        scale: Float,
        minX: Float,
        minY: Float,
        minZ: Float,
        maxX: Float,
        maxY: Float,
        maxZ: Float,
        nudgeY: Float = 0f,
    ) = BakedModel(file, scale, minX, minY, minZ, maxX, maxY, maxZ, bottom = false, nudgeY = nudgeY)

    private fun seated(
        file: String,
        scale: Float,
        minX: Float,
        minY: Float,
        minZ: Float,
        maxX: Float,
        maxY: Float,
        maxZ: Float,
        nudgeY: Float = 0f,
        yaw: Float = 0f,
    ) = BakedModel(file, scale, minX, minY, minZ, maxX, maxY, maxZ, bottom = true, nudgeY = nudgeY, yaw = yaw)

    private fun scenery(
        file: String,
        scale: Float,
        minX: Float,
        minY: Float,
        minZ: Float,
        maxX: Float,
        maxY: Float,
        maxZ: Float,
        at: Vec3,
    ) = SceneryModel(
        BakedModel(file, scale, minX, minY, minZ, maxX, maxY, maxZ, bottom = true),
        at,
    )
}
