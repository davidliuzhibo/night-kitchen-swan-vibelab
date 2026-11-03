package com.david.nightkitchen.game

import java.io.DataInputStream
import java.io.DataOutputStream

/** Appended to checkpoint v6. Legacy v1–5 never read these fields. */
object MarketCheckpoint {
 fun write(out:DataOutputStream,m:MarketSession){
  out.writeUTF(m.stage.name);out.writeInt(m.night);out.writeInt(m.ticket);out.writeInt(m.coins)
  out.writeUTF(m.gear.name);out.writeInt(m.choice);out.writeBoolean(m.credited);out.writeBoolean(m.passed);out.writeInt(m.ticketBest)
  m.grades.forEach(out::writeInt);m.bests.forEach(out::writeInt);out.writeUTF(m.review)
 }
 fun read(input:DataInputStream):MarketSession {
  val stage=MarketStage.valueOf(input.readUTF());val night=input.readInt();val ticket=input.readInt();val coins=input.readInt()
  val gear=StallGear.valueOf(input.readUTF());val choice=input.readInt();val credited=input.readBoolean();val passed=input.readBoolean();val best=input.readInt()
  val grades=List(4){input.readInt()};val bests=List(3){input.readInt()};val review=input.readUTF()
  require(night in 0..2&&ticket in 0..3&&coins in 0..14&&choice in 0..2&&best in 0..100)
  require(grades.all{it in 0..100}&&bests.all{it in 0..400}&&review.length<=2000)
  require(!passed||credited)
  return MarketSession(stage,night,ticket,coins,gear,choice,credited,passed,best,grades,bests,review)
 }
}
