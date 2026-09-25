package gg.rsmod.plugins.content.items.osrs

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.AnimDef
import org.junit.AfterClass
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Owner 2026-09-25: "some osrs ported weapons have no sound". Guard over every imported OSRS weapon and every attack sequence it
 * plays (each style, against players and npcs): the swing must be heard - the sequence carries cache frame sounds, or the server
 * plays a cue ([OsrsWeaponLooks.attackSound], the ranged weapon-type cue, or the item's attack_audio).
 */
class OsrsWeaponSoundsTests {
    private class Row(val name: String, val weaponType: Int, val attackAudio: Int)

    private fun importedWeapons(): Map<Int, Row> {
        val master = File("../../../../OSRS_IMPORT_MASTER.yml").readText().replace("\r\n", "\n")
        val imported =
            Regex("""local_item_id: (\d+)\s*\n\s*name: (.*)\n\s*upstream_item_id: (\d+)""")
                .findAll(master).associate { it.groupValues[1].toInt() to it.groupValues[2].trim() }
        val items = File("../../data/cfg/items.yml").readText().replace("\r\n", "\n")
        val rows = mutableMapOf<Int, Row>()
        items.split(Regex("\n(?=- id: )")).forEach { block ->
            val id = Regex("""^-? ?id: (\d+)""").find(block.trim())?.groupValues?.get(1)?.toInt() ?: return@forEach
            if (id !in imported || "equip_slot: 3\n" !in block) return@forEach
            val type = Regex("""weapon_type: (\d+)""").find(block)?.groupValues?.get(1)?.toInt() ?: 0
            val audio = Regex("""attack_audio: (-?\d+)""").find(block)?.groupValues?.get(1)?.toInt() ?: -1
            rows[id] = Row(imported.getValue(id), type, audio)
        }
        return rows
    }

    /** Ranged weapon types whose silent sequence gets a cue in `RangedCombatStrategy` (bow, crossbow, thrown, chinchompa). */
    private val rangedCue = setOf(16, 17, 18, 19)

    private fun silentSwings(): List<String> {
        val out = mutableListOf<String>()
        importedWeapons().forEach { (id, row) ->
            val seqs =
                (0..3).flatMap { style ->
                    listOf(false, true).mapNotNull { npc -> OsrsWeaponLooks.attackAnimation(id, style, npc, stabStyle = style == 2) }
                }.distinct()
            seqs.forEach { seq ->
                val framed = DEFINITIONS.getNullable(AnimDef::class.java, seq)?.hasFrameSounds == true
                val cue =
                    OsrsWeaponLooks.attackSound(id, seq) != null || row.weaponType in rangedCue || row.attackAudio > -1 ||
                        id in OsrsWeaponSounds.COVERED || id in noSwing
                if (!framed && !cue) out += "$id ${row.name} type=${row.weaponType} seq=$seq"
            }
        }
        return out
    }

    /** Imported weapons whose attack is not a melee swing of the weapon, and why. */
    private val noSwing =
        mapOf(
            23690 to "Ale of the gods - attacks unarmed",
            23128 to "Eclipse atlatl - ranged, weapon type bow (shortbow cue)",
        ).keys

    @Test
    fun `every imported OSRS weapon swing makes a sound`() {
        assertEquals(emptyList(), silentSwings(), "silent imported weapon swings")
    }

    @Test
    fun `category sounds follow the xrsps attack-type fallback`() {
        val g = gg.rsmod.plugins.api.cfg.Items
        assertEquals(gg.rsmod.plugins.api.cfg.Sfx.HACKSWORD_SLASH, OsrsWeaponSounds.melee(g.ARCLIGHT, gg.rsmod.game.model.combat.StyleType.SLASH))
        assertEquals(gg.rsmod.plugins.api.cfg.Sfx.HACKSWORD_STAB, OsrsWeaponSounds.melee(g.ARCLIGHT, gg.rsmod.game.model.combat.StyleType.STAB))
        assertEquals(gg.rsmod.plugins.api.cfg.Sfx.STAFF_STAB, OsrsWeaponSounds.melee(g.STAFF_OF_THE_DEAD, gg.rsmod.game.model.combat.StyleType.SLASH))
        assertEquals(gg.rsmod.plugins.api.cfg.Sfx.BAXE_SLASH, OsrsWeaponSounds.melee(g.THIRDAGE_AXE, gg.rsmod.game.model.combat.StyleType.STAB))
        assertEquals(gg.rsmod.plugins.api.cfg.Sfx.WARHAMMER_CRUSH, OsrsWeaponSounds.melee(g.DRAGON_CANE, gg.rsmod.game.model.combat.StyleType.CRUSH))
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()
        private lateinit var LIBRARY: CacheLibrary

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            LIBRARY = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            DEFINITIONS.loadAll(LIBRARY)
        }

        @AfterClass
        @JvmStatic
        fun close() {
            LIBRARY.close()
        }
    }
}
