package com.david.nightkitchen.scene

import com.david.nightkitchen.game.KitchenState
import com.pico.spatial.core.container.SpatialViewEntityManager
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Vector3

/** Small earned-order lanterns share the stall's volume/fit, never a second placement owner. */
class MarketLamps {
 private val lights=mutableListOf<Entity>()
 private val materials=mutableListOf<PhysicallyBasedMaterial>()
 private var signature=""
 fun update(host:SpatialViewEntityManager?,s:KitchenState){
  if(host==null)return
  if(lights.isEmpty())repeat(4){i->
   val material=PhysicallyBasedMaterial.create().apply{setBaseColor(Color4(.22f,.19f,.11f,1f));setMetallic(.3f);setRoughness(.5f)}
   val e=StallAssets.anchor();e.setName("market.earned-lantern.$i")
   e.components.set(ModelComponent(MeshResource.createSphere(.024f),material))
   e.components[TransformComponent::class.java]?.setPosition(Vector3(-.24f+i*.16f,.08f,-.22f))
   host.addEntity(e);lights.add(e);materials.add(material)
  }
  val earned=s.market.grades.mapIndexed{i,v->v>0||(i==s.market.ticket&&s.market.passed)}
  val key="${s.market.active}/$earned";if(key==signature)return;signature=key
  lights.forEachIndexed{i,e->e.enabled=s.market.active;materials[i].setBaseColor(if(earned[i])Color4(1f,.66f,.18f,1f)else Color4(.22f,.19f,.11f,1f))}
 }
}
