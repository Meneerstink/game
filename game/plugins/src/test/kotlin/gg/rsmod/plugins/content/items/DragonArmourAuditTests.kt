package gg.rsmod.plugins.content.items

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import gg.rsmod.plugins.api.cfg.Items
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * OSRS-IMPORT audit round 2026-09-17b, native Dragon armour (pre-2011 content, re-verified against
 * OSRS Wiki raw wikitext point by point). Found and fixed a real, live bug on Dragon chainbody
 * (`Items.DRAGON_CHAINBODY` = 2513, the id every drop table/shop in this codebase actually uses -
 * Kalphite Queen, the four revenants, `CorruptArmorCharges`, `CombinationData`, `OsrsOrnamentKits`):
 * `items.yml` wrongly gave it `equip_slot: 0` (HEAD) instead of `4` (CHEST), `remove_arms: false`
 * instead of `true`, and was missing its `skill_reqs` (60 Defence) entirely. A second, unused
 * duplicate entry at id 3140 (`Items.DRAGON_CHAINBODY_3140`, referenced nowhere else in the
 * codebase) already had all three fields correct and was used as the reference to fix 2513 from,
 * cross-checked against the OSRS Wiki's own "requiring a Defence level of 60 to equip" text and
 * matching `appearance_id` (613, identical on both copies - a body-slot item's model, not a
 * head-slot one). The other 6 pieces checked in the same pass (Dragon sq shield, platelegs,
 * plateskirt, full helm, boots, platebody) all matched the wiki exactly with no code change needed.
 */
class DragonArmourAuditTests {
    private val yml by lazy { ObjectMapper(YAMLFactory()).readTree(Paths.get("..", "..", "data", "cfg", "items.yml").toFile()).toList() }

    private fun equipment(id: Int) = yml.first { it.path("id").asInt() == id }.path("equipment")

    private fun bonuses(id: Int): List<Int> {
        val eq = equipment(id)
        return listOf(
            eq.path("attack_magic").asInt(), eq.path("attack_ranged").asInt(),
            eq.path("defence_stab").asInt(), eq.path("defence_slash").asInt(), eq.path("defence_crush").asInt(),
            eq.path("defence_magic").asInt(), eq.path("defence_ranged").asInt(), eq.path("melee_strength").asInt(),
        )
    }

    private fun reqs(id: Int) = equipment(id).path("skill_reqs").associate { it.path("skill").asInt() to it.path("level").asInt() }

    @Test
    fun `Dragon chainbody's live item id is a correctly-equipped chest piece, matching its own unused duplicate and the wiki`() {
        val live = equipment(Items.DRAGON_CHAINBODY)
        assertEquals(4, live.path("equip_slot").asInt(), "Dragon chainbody must equip into the chest slot, not head")
        assertEquals(true, live.path("remove_arms").asBoolean(), "Dragon chainbody hides sleeves like every other chainbody")
        assertEquals(mapOf(1 to 60), reqs(Items.DRAGON_CHAINBODY), "60 Defence, sourced from the OSRS Wiki")
        // The unused duplicate at 3140 already had this right - both ids must now agree on slot/arms/reqs.
        val unused = equipment(Items.DRAGON_CHAINBODY_3140)
        assertEquals(unused.path("equip_slot").asInt(), live.path("equip_slot").asInt())
        assertEquals(unused.path("remove_arms").asBoolean(), live.path("remove_arms").asBoolean())
        assertEquals(reqs(Items.DRAGON_CHAINBODY_3140), reqs(Items.DRAGON_CHAINBODY))
        assertEquals(bonuses(Items.DRAGON_CHAINBODY_3140), bonuses(Items.DRAGON_CHAINBODY), "both ids share the same OSRS bonuses")
    }

    @Test
    fun `every other Dragon armour piece matches the sourced OSRS Wiki bonuses exactly`() {
        // (amagic, arange, dstab, dslash, dcrush, dmagic, drange, str)
        assertEquals(listOf(-6, 0, 50, 52, 48, 0, 50, 0), bonuses(Items.DRAGON_SQ_SHIELD), "Dragon sq shield")
        assertEquals(listOf(-15, 0, 81, 93, 98, -3, 82, 0), bonuses(Items.DRAGON_CHAINBODY), "Dragon chainbody")
        assertEquals(listOf(-21, -11, 68, 66, 63, -4, 65, 0), bonuses(Items.DRAGON_PLATELEGS), "Dragon platelegs")
        assertEquals(listOf(-21, -11, 68, 66, 63, -4, 65, 0), bonuses(Items.DRAGON_PLATESKIRT), "Dragon plateskirt")
        assertEquals(listOf(-6, -3, 45, 48, 41, -1, 46, 0), bonuses(Items.DRAGON_FULL_HELM), "Dragon full helm")
        assertEquals(listOf(-3, -1, 16, 17, 18, 0, 0, 4), bonuses(Items.DRAGON_BOOTS), "Dragon boots")
        assertEquals(listOf(-30, -15, 109, 107, 97, -6, 106, 0), bonuses(Items.DRAGON_PLATEBODY), "Dragon platebody")
        assertEquals(mapOf(1 to 60), reqs(Items.DRAGON_SQ_SHIELD), "Dragon sq shield requirement")
        assertEquals(mapOf(1 to 60), reqs(Items.DRAGON_PLATELEGS), "Dragon platelegs requirement")
        assertEquals(mapOf(1 to 60), reqs(Items.DRAGON_PLATESKIRT), "Dragon plateskirt requirement")
        assertEquals(mapOf(1 to 60), reqs(Items.DRAGON_FULL_HELM), "Dragon full helm requirement")
        assertEquals(mapOf(1 to 60), reqs(Items.DRAGON_BOOTS), "Dragon boots requirement")
        assertEquals(mapOf(1 to 60), reqs(Items.DRAGON_PLATEBODY), "Dragon platebody requirement")
    }
}
