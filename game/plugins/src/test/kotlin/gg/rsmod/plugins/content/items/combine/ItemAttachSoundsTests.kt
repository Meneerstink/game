package gg.rsmod.plugins.content.items.combine

import com.displee.cache.CacheLibrary
import gg.rsmod.game.tools.importer.ModernCacheReader
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Sfx
import gg.rsmod.plugins.content.items.osrs.MaxCapes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.file.Paths

/**
 * Guards owner bug 2026-10-03 211127 ("attaching something to something should make a sound - check every item") and buglist E3
 * (max cape combine without dialogue, message and sound). Rules and sources in [ItemAttachSounds].
 */
class ItemAttachSoundsTests {
    private val pluginRoot = File("src/main/kotlin/gg/rsmod/plugins/content")

    @Test
    fun `every combination row has an attach sound`() {
        val allowed = setOf(ItemAttachSounds.GODSWORD_FIX, ItemAttachSounds.ATTACH_ORB, ItemAttachSounds.ATTACH)
        CombinationData.values.forEach { row ->
            val sound = ItemAttachSounds.forAttach(row.items.toList())
            assertTrue("${row.name} has no attach sound", sound in allowed)
        }
        val action = File(pluginRoot, "items/combine/CombinationAction.kt").readText()
        val granted = action.indexOf("grantOrRefund(Item(data.resultItem")
        val sounded = action.indexOf("ItemAttachSounds.attached(player, *data.items)")
        assertTrue("CombinationAction plays the attach sound", sounded > 0)
        assertTrue("the sound plays only after the result is granted", sounded > granted && granted > 0)
        assertTrue("chisel rows sound like the chisel", "CombinationTool.CHISEL" in action && "ItemAttachSounds.carved(player)" in action)
    }

    @Test
    fun `godsword hilts and orbs get their own sound`() {
        listOf(Items.ARMADYL_GODSWORD, Items.BANDOS_GODSWORD, Items.SARADOMIN_GODSWORD, Items.ZAMORAK_GODSWORD, Items.ANCIENT_GODSWORD)
            .forEach { godsword ->
                val row = CombinationData.values.single { it.resultItem == godsword }
                assertEquals(row.name, ItemAttachSounds.GODSWORD_FIX, ItemAttachSounds.forAttach(row.items.toList()))
            }
        listOf(Items.HARMONISED_NIGHTMARE_STAFF, Items.VOLATILE_NIGHTMARE_STAFF, Items.ELDRITCH_NIGHTMARE_STAFF, Items.ARMADYL_BATTLESTAFF)
            .forEach { staff ->
                val row = CombinationData.values.single { it.resultItem == staff }
                assertEquals(row.name, ItemAttachSounds.ATTACH_ORB, ItemAttachSounds.forAttach(row.items.toList()))
            }
        // An ornament kit on a godsword is no hilt attach.
        val orKit = CombinationData.values.single { it.resultItem == Items.ARMADYL_GODSWORD_OR }
        assertEquals(ItemAttachSounds.ATTACH, ItemAttachSounds.forAttach(orKit.items.toList()))
        assertEquals(2585, ItemAttachSounds.ATTACH_ORB)
        // N444: attach_handle 2287 rendered at peak 13 of 128 (inaudible); cf_attach_cannon 1422 peaks at 65.
        assertEquals(1422, ItemAttachSounds.ATTACH)
        assertEquals(Sfx.HUNTING_DISMANTLE, ItemAttachSounds.DETACH)
    }

    /**
     * Item-on-item routes in the item plugins that are NOT an attach: they add charges / ammunition / scales to an item, or are
     * low-tier production. Every other file binding `on_item_on_item` must sound through [ItemAttachSounds].
     */
    private val notAttach =
        mapOf(
            "items/combine/item_combination.plugin.kts" to "binds CombinationData rows to CombinationAction, which sounds (guarded above)",
            "items/gut_fish.plugin.kts" to "food preparation",
            "items/osrs/crystal.plugin.kts" to "crystal shards charge crystal equipment",
            "items/osrs/demonbane.plugin.kts" to "ancient shards charge Arclight (creation is at an altar)",
            "items/osrs/magic_shortbow_scroll.plugin.kts" to "an imbue, not an attach: the scroll is read onto the bow with an enchant sound (N197, in game)",
            "items/osrs/dizanas_quiver.plugin.kts" to "splinters charge / ammunition is stored",
            "items/osrs/ring_of_shadows.plugin.kts" to "runes charge the ring",
            "items/osrs/ring_of_suffering.plugin.kts" to "recoil rings charge the ring",
            "items/osrs/tomes.plugin.kts" to "pages charge the tome",
            "items/pestle_and_mortar/pestle_and_mortar.plugin.kts" to "grinding (herblore)",
            "items/potion/anti_venom_recipes.plugin.kts" to "potion mixing",
            "items/potion/brew_potions.plugin.kts" to "potion mixing",
            "items/potion/divine_potions.plugin.kts" to "potion mixing",
            "items/potion/potion_decanting.plugin.kts" to "potion decanting",
            "items/potion/stamina_recipes.plugin.kts" to "potion mixing",
        )

    @Test
    fun `every attach route in the item plugins plays the attach sound`() {
        val files =
            File(pluginRoot, "items").walkTopDown().filter { it.isFile && it.name.endsWith(".kts") }.toList() +
                listOf(
                    File(pluginRoot, "skills/fletching/ballista/heavy_ballista.plugin.kts"),
                    File(pluginRoot, "skills/fletching/crossbows/zaryte_crossbow.plugin.kts"),
                )
        val missing =
            files.filter { file ->
                val text = file.readText()
                val rel = file.relativeTo(pluginRoot).invariantSeparatorsPath
                "on_item_on_item(" in text && rel !in notAttach && "ItemAttachSounds" !in text
            }.map { it.relativeTo(pluginRoot).invariantSeparatorsPath }
        assertTrue("item-on-item routes without an attach sound (add ItemAttachSounds or classify them here): $missing", missing.isEmpty())
        notAttach.keys.forEach { assertTrue("stale entry $it", File(pluginRoot, it).isFile) }
    }

    @Test
    fun `charging routes stay silent but their attach parts sound`() {
        val text = File(pluginRoot, "items/osrs/blood_fury.plugin.kts").readText()
        assertTrue("only creating the amulet sounds", "if (amuletId == Items.AMULET_OF_FURY) gg.rsmod.plugins.content.items.combine.ItemAttachSounds.attached" in text)
        val bows = File(pluginRoot, "items/osrs/osrs_bows.plugin.kts").readText()
        assertEquals("Webweaver and Scorching bow attach", 2, Regex("ItemAttachSounds\\.attached").findAll(bows).count())
    }

    @Test
    fun `item-on-item detach routes sound like Dismantle`() {
        val pilot = File(pluginRoot, "items/osrs/osrs_pilot_items.plugin.kts").readText()
        assertEquals("cleaning cloth and Split", 2, Regex("ItemAttachSounds\\.detached\\(player\\)").findAll(pilot).count())
        val capes = File(pluginRoot, "items/osrs/osrs_max_capes.plugin.kts").readText()
        assertTrue("knife split sounds", "ItemAttachSounds.detached(player)" in capes)
    }

    @Test
    fun `max cape combine asks first, then messages and sounds`() {
        val capes = File(pluginRoot, "items/osrs/osrs_max_capes.plugin.kts").readText()
        val block = capes.substring(capes.indexOf("on_item_on_item(item1 = variant.component, item2 = MaxCapes.MAX_CAPE)"))
        val confirm = block.indexOf("confirmItemAction(MaxCapes.MAX_CAPE")
        val recheck = block.indexOf("if (!canCombine(player, variant)) return@queue")
        val change = block.indexOf("inventory[capeSlot] = null")
        val sound = block.indexOf("ItemAttachSounds.attached(player, variant.component, MaxCapes.MAX_CAPE)")
        val message = block.indexOf("MaxCapes.combinedMessage(componentName)")
        assertTrue("confirm, re-check, combine, sound, message in that order", confirm in 1 until recheck && recheck < change && change < sound && sound < message)
        assertEquals("Are you sure you want to combine your max cape with the infernal cape?", MaxCapes.combineQuestion("Infernal cape"))
        assertEquals("You combine your max cape with the imbued Saradomin cape.", MaxCapes.combinedMessage("Imbued Saradomin cape"))
        assertEquals("You separate the fire cape from your max cape.", MaxCapes.separatedMessage("Fire cape"))
    }

    /** CACHE evidence for [ItemAttachSounds.GODSWORD_FIX]: 667 synth 3889 carries exactly the bytes of OSRS godwars_godsword_fix 3857. */
    @Test
    fun `godsword fix sound is the OSRS synth`() {
        val osrsDir = File("C:/RSPS/import-source/openrs2-2686/cache")
        val rsDir = Paths.get("..", "..", "data", "cache").toFile()
        assumeTrue("pinned OSRS cache present", File(osrsDir, "main_file_cache.idx4").isFile)
        assumeTrue("667 cache present", File(rsDir, "main_file_cache.idx4").isFile)
        val osrs = ModernCacheReader(osrsDir).use { it.file(4, 3857, 0) }
        val rs = CacheLibrary(rsDir.toString(), false, null)
        try {
            assertTrue("667 synth 3889 == OSRS 3857", osrs != null && osrs.contentEquals(rs.data(4, ItemAttachSounds.GODSWORD_FIX, 0)))
            val osrsOrb = ModernCacheReader(osrsDir).use { it.file(4, Sfx.ATTACH_ORB, 0) }
            assertTrue("attach_orb same id in both caches", osrsOrb != null && osrsOrb.contentEquals(rs.data(4, Sfx.ATTACH_ORB, 0)))
            val osrsHandle = ModernCacheReader(osrsDir).use { it.file(4, Sfx.ATTACH_HANDLE, 0) }
            assertTrue("attach_handle same id in both caches", osrsHandle != null && osrsHandle.contentEquals(rs.data(4, Sfx.ATTACH_HANDLE, 0)))
        } finally {
            rs.close()
        }
    }
}
