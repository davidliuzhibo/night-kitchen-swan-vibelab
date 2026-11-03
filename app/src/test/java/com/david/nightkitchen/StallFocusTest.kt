package com.david.nightkitchen

import com.david.nightkitchen.platform.StallFocus
import org.junit.Assert.assertEquals
import org.junit.Test

class StallFocusTest {
    private var time = 0L
    private val jobs = mutableListOf<() -> Unit>()
    private val events = mutableListOf<Boolean>()
    private val gate = StallFocus<String>({time}, {_,job -> jobs.add(job)}, {events.add(it)})
    @Test fun switchingFromStallToItsOwnBillKeepsSelection() {
        gate.update("stall",true); gate.update("stall",false); time=93; gate.update("bill",true)
        jobs.forEach{it()}; assertEquals(emptyList<Boolean>(),events)
    }
    @Test fun launcherInterruptsOnceAndReturningEnablesFreshActions() {
        gate.update("stall",true); gate.update("stall",false); time=350
        jobs.single()(); jobs.single()(); gate.update("stall",true)
        assertEquals(listOf(false,true),events)
    }
    @Test fun fastReturnToTheSameWindowAlsoDiscardsTheOldSelection() {
        gate.update("stall",true);gate.update("stall",false);time=40;gate.update("stall",true)
        jobs.single()();assertEquals(listOf(false,true),events)
    }
    @Test fun aLateTimerDoesNotAllowUsingAnOldSelectionOnAnotherPanel() {
        gate.update("bill",true);gate.update("bill",false);time=2000;gate.update("stall",true)
        jobs.single()();assertEquals(listOf(false,true),events)
    }
    @Test fun staleLossFromAnotherWindowCannotDisableTheCurrentHost() {
        gate.update("stall",true); gate.update("bill",true); gate.update("stall",false)
        gate.update("stall",false); assertEquals(0,jobs.size);assertEquals(emptyList<Boolean>(),events)
    }
}
