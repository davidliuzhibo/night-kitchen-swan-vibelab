package com.david.nightkitchen
import com.david.nightkitchen.game.*
import org.junit.Assert.*
import org.junit.Test
class FeedbackLifetimeTest {
 @Test fun failedLiftRemainsReadableWithoutChangingToolCooldown(){var s=RecipePilot.dish("oden");s=reduce(s,KitchenEvent.Drop(GrabSlot.A,Station.WOK));repeat(12){s=reduce(s,KitchenEvent.Tick)};val cooldown=s.cooking.lastAction;s=reduce(s,KitchenEvent.Lift(0));val message=s.cooking.message;assertTrue(message.contains("还没熟"));repeat(4){s=reduce(s,KitchenEvent.Tick);assertEquals(message,s.cooking.message)};assertEquals(cooldown,s.cooking.lastAction);s=reduce(s,KitchenEvent.Tick);assertEquals("",s.cooking.message)}
 @Test fun heatStartMessageAndCheckpointKeepTheirOwnLifetime(){var s=RecipePilot.dish("beer-plate");repeat(15){s=reduce(s,KitchenEvent.Tick)};s=reduce(s,KitchenEvent.StartHotSide);s=requireNotNull(KitchenCheckpoint.decode(KitchenCheckpoint.encode(s)));repeat(4){s=reduce(s,KitchenEvent.Tick);assertTrue(s.cooking.message.contains("热串开始"))}}
}
