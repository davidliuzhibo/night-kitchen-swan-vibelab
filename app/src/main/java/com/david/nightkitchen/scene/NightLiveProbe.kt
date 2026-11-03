package com.david.nightkitchen.scene
import com.david.nightkitchen.BuildConfig
import com.david.nightkitchen.game.*
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
object NightLiveProbe {
 data class Snapshot(val frame:Long=0,val dish:String="",val ready:Boolean=false,val positions:Map<String,Vec3> = emptyMap(),val visible:Set<String> = emptySet(),val platedAssetReady:Boolean=false)
 @Volatile var latest=Snapshot();private set
 private var frame=0L
 fun observe(props:Map<GrabSlot,Entity>,actualDish:String?,platedReady:Boolean){if(!BuildConfig.DEBUG||++frame%5L!=0L)return;latest=Snapshot(frame,actualDish.orEmpty(),KitchenHub.assetsReady,props.mapNotNull{(k,e)->e.components[TransformComponent::class.java]?.position?.let{k.name to Vec3(it.x,it.y,it.z)}}.toMap(),props.filterValues{it.enabled}.keys.map{it.name}.toSet(),platedReady)}
}
