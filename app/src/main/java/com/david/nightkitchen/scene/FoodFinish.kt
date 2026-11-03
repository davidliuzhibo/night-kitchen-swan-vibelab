package com.david.nightkitchen.scene
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.math.Color4
/** Multiply original PBR base tint; textures and roughness stay authored. */
class FoodFinish {
 private val base=mutableMapOf<PhysicallyBasedMaterial,Color4>()
 private val levels=mutableMapOf<Entity,Int>()
 fun apply(entity:Entity?,heat:Float){if(entity==null)return;val level=(heat.coerceIn(0f,2.5f)*20).toInt();if(levels[entity]==level)return;levels[entity]=level
  fun visit(e:Entity){val model=e.components[ModelComponent::class.java]
   model?.materials?.forEach{m->if(m is PhysicallyBasedMaterial){val c=base.getOrPut(m){m.getBaseColor()};val done=(heat/1f).coerceIn(0f,1f);val over=((heat-1.25f)/1.25f).coerceIn(0f,1f);m.setBaseColor(Color4(c.red*(1f-.40f*over),c.green*(1f-.13f*done-.55f*over),c.blue*(1f-.25f*done-.62f*over),c.alpha))}}
   e.getChildren().forEach(::visit)
  };visit(entity)
 }
}
