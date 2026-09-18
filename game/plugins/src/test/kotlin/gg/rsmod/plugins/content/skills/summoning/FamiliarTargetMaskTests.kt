package gg.rsmod.plugins.content.skills.summoning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the special-move button's `TargetMask` bits, and the 78/78 consequence of getting them
 * right.
 *
 * ## The bug this exists to prevent coming back
 *
 * The owner's requirement H10 - "Pack yak Winter Storage must enter inventory-item targeting and
 * bank exactly the selected item" - was not a bug in Winter Storage's effect, which was correct.
 * It was one wrong bit: an inventory-item-targeted special was published with `TGT_OBJ` (0x01),
 * the **ground**-item mask, instead of `TGT_BUTTON` (0x20).
 *
 * An inventory item is a component slot, so the menu entry that turns a click on it into the
 * interface-target packet is added by `InterfaceManager.addMiniMenuOptions`, which tests
 * `targetMask & TGT_BUTTON`. `TGT_OBJ` is tested in `MiniMenu`'s ground-item scan instead. With
 * 0x01 the button entered target mode and then silently refused every inventory slot.
 *
 * The bit values are the client's own
 * (`com.jagex.game.runetek6.config.iftype.TargetMask`), and the choice of `TGT_BUTTON` for an
 * inventory item is confirmed against the cache's own working precedent: High Alchemy (192:38),
 * Low Alchemy (192:59) and Enchant Jewellery (192:29) are all item-targeted spells in this
 * revision and every one bakes `events=0x010000`, which is exactly `TGT_BUTTON shl 11`.
 */
class FamiliarTargetMaskTests {
    /** `com.jagex.game.runetek6.config.iftype.TargetMask`, verbatim. */
    private val tgtObj = 0x01
    private val tgtNpc = 0x02
    private val tgtPlayer = 0x08
    private val tgtButton = 0x20

    /** `ServerActiveProperties.targetMaskFrom`: `(events >> 11) & 0x7F`. */
    private val maskShift = 11

    /** The baked `events` on 192:38 / 192:59 / 192:29, the cache's own item-targeted spells. */
    private val alchemyEvents = 0x010000

    @Test
    fun `an inventory-item special uses the same mask the cache's own item-targeted spells use`() {
        val mask = SummoningSpecialMoves.targetMaskFor(FamiliarSpecialTarget.INVENTORY_ITEM)
        assertEquals("inventory-item targeting is TGT_BUTTON", tgtButton, mask)
        assertEquals(
            "the published events must match High/Low Alchemy's baked 0x010000",
            alchemyEvents,
            mask shl maskShift,
        )
    }

    @Test
    fun `an inventory-item special is never published with the ground-item mask`() {
        val mask = SummoningSpecialMoves.targetMaskFor(FamiliarSpecialTarget.INVENTORY_ITEM)
        assertNotEquals("TGT_OBJ is the ground-item mask and would refuse every inventory slot", tgtObj, mask)
        assertEquals("TGT_OBJ must not be set at all", 0, mask and tgtObj)
    }

    @Test
    fun `npc-targeted specials accept npcs and players, player-targeted only players`() {
        assertEquals(tgtNpc or tgtPlayer, SummoningSpecialMoves.targetMaskFor(FamiliarSpecialTarget.NPC))
        assertEquals(tgtPlayer, SummoningSpecialMoves.targetMaskFor(FamiliarSpecialTarget.PLAYER))
    }

    @Test
    fun `an instant special publishes no target mask`() {
        assertEquals(
            "an INSTANT special fires from op1, not from target selection",
            0,
            SummoningSpecialMoves.targetMaskFor(FamiliarSpecialTarget.INSTANT),
        )
        assertEquals(0, SummoningSpecialMoves.targetMaskFor(null))
    }

    @Test
    fun `every mask fits the seven bits the client reads back`() {
        FamiliarSpecialTarget.values().forEach { target ->
            val mask = SummoningSpecialMoves.targetMaskFor(target)
            assertEquals(
                "$target's mask does not survive the client's own (events >> 11) & 0x7F round trip",
                mask,
                ((mask shl maskShift) shr maskShift) and 0x7F,
            )
        }
    }

    /**
     * The 78/78 half. Every familiar that has a special move must publish a mask that matches how
     * that special is actually dispatched, or its button enters the wrong target mode - which is
     * indistinguishable, in play, from the button being broken.
     */
    @Test
    fun `every familiar's published mask matches its special's target mode`() {
        SummoningPouchData.values().forEach { pouch ->
            val capabilities = FamiliarCapabilityTable.forNpc(pouch.npc)!!
            val target = capabilities.specialTarget ?: return@forEach
            val mask = SummoningSpecialMoves.targetMaskFor(target)
            when (target) {
                FamiliarSpecialTarget.INSTANT ->
                    assertEquals("${pouch.name}: an instant special must publish no mask", 0, mask)
                FamiliarSpecialTarget.INVENTORY_ITEM ->
                    assertEquals("${pouch.name}: item targeting must be TGT_BUTTON", tgtButton, mask)
                FamiliarSpecialTarget.NPC, FamiliarSpecialTarget.PLAYER ->
                    assertTrue("${pouch.name}: a targeted special must accept players", mask and tgtPlayer != 0)
                // Compost mound / Beaver / Hydra: only a location (TGT_LOC 0x04) is a valid target.
                FamiliarSpecialTarget.OBJECT ->
                    assertEquals("${pouch.name}: object targeting must be TGT_LOC", 0x04, mask)
            }
        }
    }

    /**
     * H10 by name. Pack yak is the familiar the owner reported, and Winter Storage is the only
     * inventory-item special most players ever use, so a regression here is named for what they
     * will look for.
     */
    @Test
    fun `pack yak's winter storage is inventory-item targeted and publishes TGT_BUTTON`() {
        val packYak = FamiliarCapabilityTable.forNpc(SummoningPouchData.PACK_YAK.npc)!!
        assertEquals(
            "Pack yak's special is Winter Storage",
            SummoningScrollData.WINTER_STORAGE_SCROLL,
            packYak.special?.scroll,
        )
        assertEquals(FamiliarSpecialTarget.INVENTORY_ITEM, packYak.specialTarget)
        assertEquals(tgtButton, SummoningSpecialMoves.targetMaskFor(packYak.specialTarget))
    }

    /** H11 by name: the Unicorn's Healing Aura is instant, so it must fire without target mode. */
    @Test
    fun `unicorn stallion's healing aura is instant on both surfaces`() {
        val unicorn = FamiliarCapabilityTable.forNpc(SummoningPouchData.UNICORN_STALLION.npc)!!
        assertEquals(
            SummoningScrollData.HEALING_AURA_SCROLL,
            unicorn.special?.scroll,
        )
        assertEquals(FamiliarSpecialTarget.INSTANT, unicorn.specialTarget)
        assertEquals(0, SummoningSpecialMoves.targetMaskFor(unicorn.specialTarget))
    }
}
