package com.david.nightkitchen.game

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64

/** Versioned local checkpoint. No file paths, networking or device state. */
object KitchenCheckpoint {
    fun encode(state: KitchenState): String {
        val bytes=ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(6)
            with(state) {
                out.writeInt(shiftSeconds);out.writeInt(orderSeconds);out.writeUTF(active.id)
                out.writeUTF(queue.joinToString(",") { it.id })
                out.writeInt(added);out.writeBoolean(plated);out.writeInt(servedCount)
                out.writeInt(mistakes);out.writeInt(ticketMistakes);out.writeInt(score)
                out.writeUTF(phase.name);out.writeUTF(feedback.name);out.writeUTF(lastReview.orEmpty())
                out.writeInt(reviewLog.takeLast(20).size)
                reviewLog.takeLast(20).forEach { out.writeUTF(it) }
                out.writeUTF(unlocked.joinToString(","));out.writeUTF(unlockedThisShift.joinToString(","))
                out.writeBoolean(running)
                with(cooking){out.writeInt(mask);out.writeUTF(heat.name);a.forEach(out::writeFloat);b.forEach(out::writeFloat);out.writeInt(face);out.writeInt(lifted);out.writeInt(stirs);out.writeInt(lastAction);out.writeInt(time);out.writeInt(stage);out.writeFloat(stageHeat);out.writeBoolean(watered);out.writeBoolean(covered);out.writeBoolean(brushed);out.writeBoolean(rolled);out.writeUTF(taste);out.writeBoolean(coldServed);out.writeBoolean(hotStarted);out.writeUTF(seasoned);out.writeUTF(message);out.writeBoolean(hotServed);out.writeFloat(bottomHeat);out.writeFloat(steamHeat);out.writeInt(messageUntil)}
                out.writeInt(holdSeconds)
                MarketCheckpoint.write(out,market)
            }
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray())
    }

    fun decode(encoded: String?): KitchenState? = runCatching {
        if(encoded.isNullOrBlank() || encoded.length>40000) return null
        DataInputStream(ByteArrayInputStream(Base64.getDecoder().decode(encoded))).use { input ->
            val version=input.readInt();require(version in 1..6)
            val shift=input.readInt();val order=input.readInt()
            require(shift in 0..SHIFT_SECONDS && order in 0..ORDER_SECONDS)
            val menu=NIGHT_MENU.associateBy { it.id }
            val active=menu.getValue(input.readUTF())
            fun ids(text: String)=if(text.isBlank()) emptyList() else text.split(",")
            val queue=ids(input.readUTF()).map(menu::getValue)
            require(queue.size<=NIGHT_MENU.size)
            val added=input.readInt();require(added in 0..active.ingredients.size)
            val plated=input.readBoolean()
            val served=input.readInt();val mistakes=input.readInt();val ticket=input.readInt();val score=input.readInt()
            require(listOf(served,mistakes,ticket,score).all { it in 0..100000 })
            val phase=Phase.valueOf(input.readUTF());val feedback=Feedback.valueOf(input.readUTF())
            val review=input.readUTF().ifEmpty { null }
            val count=input.readInt();require(count in 0..20)
            val reviews=List(count) { input.readUTF() }
            val unlocked=ids(input.readUTF()).toSet().intersect(menu.keys)
            val fresh=ids(input.readUTF()).toSet().intersect(menu.keys)
            val running=input.readBoolean()
            if(version==1){require(input.available()==0);return freshShift(unlocked).copy(cooking=Cooking(message="玩法已升级，已解锁菜谱保留；从新的火候课程开始。",messageUntil=5))}
            val mask=input.readInt();require(mask in 0..7);val heat=Heat.valueOf(input.readUTF())
            val a=List(3){input.readFloat()};val b=List(3){input.readFloat()};require((a+b).all{it.isFinite()&&it in 0f..10000f})
            val face=input.readInt();val lifted=input.readInt();val stirs=input.readInt();val lastAction=input.readInt();val time=input.readInt();val stage=input.readInt();val stageHeat=input.readFloat()
            require(face in 0..1&&lifted in 0..7&&stirs in 0..10000&&time in 0..100000&&stage in 0..2&&stageHeat.isFinite()&&stageHeat>=0)
            val cooking=Cooking(mask,heat,a,b,face,lifted,stirs,lastAction,time,stage,stageHeat,input.readBoolean(),input.readBoolean(),input.readBoolean(),input.readBoolean(),input.readUTF(),coldServed=input.readBoolean(),hotStarted=input.readBoolean())
            var restoredCooking=if(version>=3)cooking.copy(seasoned=input.readUTF(),message=input.readUTF())else cooking
            if(version>=4){val hot=input.readBoolean();val bottom=input.readFloat();val steam=input.readFloat();require(bottom.isFinite()&&steam.isFinite()&&bottom>=0&&steam>=0);restoredCooking=restoredCooking.copy(hotServed=hot,bottomHeat=bottom,steamHeat=steam)}
            restoredCooking=restoredCooking.copy(messageUntil=if(version>=5)input.readInt() else if(restoredCooking.message.isBlank())0 else time+5)
            require(restoredCooking.messageUntil in 0..100005)
            val hold=input.readInt();require(hold in 0..100000)
            val market=if(version>=6)MarketCheckpoint.read(input)else MarketSession()
            require(input.available()==0)
            KitchenState(shift,order,active,queue,added,plated,served,mistakes,ticket,score,
                phase,feedback,review,reviews,unlocked,fresh,running,restoredCooking,hold,market)
        }
    }.getOrNull()
}
