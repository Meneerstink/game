package gg.rsmod.plugins.content.combat.specialattack

import com.displee.cache.CacheLibrary
import gg.rsmod.plugins.api.cfg.Sfx
import java.io.File
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A12 (owner 2026-10-10 "dark bow spec maakt geen geluid"), ROOT_CAUSES class "a bulk port of a donor handler copies its
 * numbers and graphics but drops its sound calls": every special attack must be audible.
 *
 * Class rule, over EVERY `SpecialAttacks.register` / `registerInstant` block in every plugin script: the block sends a server
 * sound (playSound / soundToBystanders / AreaSound), or its animation carries a frame sound in the 667 cache (decoded here
 * from the production cache, so the client plays it itself), or it delegates to a function that sends one. The only
 * exceptions are the specials of BUGLIJST item N3 (still silent, open); that list may only shrink - an N3 entry that gets a
 * sound must be removed here, and a new silent special fails this test.
 */
class SpecialAttackSoundTests {
    private val sourceRoot = File("src/main/kotlin")
    private val productionCache = "../../data/cache"

    private val soundCall = Regex("""playSound\(|soundToBystanders\(|AreaSound\(""")

    private data class Block(
        val file: String,
        val text: String,
    ) {
        val header get() = text.lineSequence().take(2).joinToString(" ").replace(Regex("\\s+"), " ").take(120)
    }

    private sealed class Exemption(
        val file: String,
        val marker: String,
    ) {
        /** The animation's own cache frame sound makes the special audible (checked against the production cache). */
        class FrameSound(file: String, marker: String, val animation: Int) : Exemption(file, marker)

        /** The block delegates to code in [sourceFile] that contains [soundText]. */
        class Delegated(file: String, marker: String, val sourceFile: String, val soundText: String) : Exemption(file, marker)

        /** BUGLIJST N3 (open, 2026-10-14): no server sound and a silent animation - a separate item, not part of A12. */
        class OpenN3(file: String, marker: String) : Exemption(file, marker)
    }

    private val exemptions =
        listOf(
            Exemption.FrameSound("dragon_claws.plugin.kts", "Items.DRAGON_CLAWS)", 10961),
            Exemption.FrameSound("melee_specials.plugin.kts", "Items.DRAGON_SCIMITAR,", 12031),
            Exemption.FrameSound("instant_specials.plugin.kts", "Items.DRAGON_PICKAXE,", 12031),
            Exemption.FrameSound("melee_specials.plugin.kts", "Items.DRAGON_2H_SWORD)", 3157),
            Exemption.Delegated(
                "granite_maul.plugin.kts",
                "GraniteMaul.smash(",
                "gg/rsmod/plugins/content/items/osrs/GraniteMaul.kt",
                "playSound(Sfx.QUICKSMASH)",
            ),
            Exemption.OpenN3("melee_specials.plugin.kts", "animate(1064"),
            Exemption.OpenN3("melee_specials.plugin.kts", "Items.BARRELCHEST_ANCHOR"),
            Exemption.OpenN3("melee_specials.plugin.kts", "Items.ABYSSAL_VINE_WHIP"),
            Exemption.OpenN3("melee_specials.plugin.kts", "Items.KORASIS_SWORD_19780"),
            Exemption.OpenN3("melee_specials.plugin.kts", "Items.BRINE_SABRE"),
            Exemption.OpenN3("moon_specials.plugin.kts", "Items.ECLIPSE_ATLATL"),
            Exemption.OpenN3("moon_specials.plugin.kts", "Items.BLUE_MOON_SPEAR"),
            Exemption.OpenN3("nightmare_staves.plugin.kts", "Items.ELDRITCH_NIGHTMARE_STAFF"),
            Exemption.OpenN3("nightmare_staves.plugin.kts", "Items.VOLATILE_NIGHTMARE_STAFF"),
            Exemption.OpenN3("noxious_halberd.plugin.kts", "Items.NOXIOUS_HALBERD"),
            Exemption.OpenN3("revenant_specials.plugin.kts", "Items.URSINE_CHAINMACE"),
            Exemption.OpenN3("revenant_specials.plugin.kts", "CONDEMN_ENERGY"),
            Exemption.OpenN3("toxic_blowpipe.plugin.kts", "Items.ROSEWOOD_BLOWPIPE"),
            Exemption.OpenN3("hand_cannon.plugin.kts", "Items.HAND_CANNON"),
            Exemption.OpenN3("ranged_specials.plugin.kts", "Items.ZANIKS_CROSSBOW"),
            // Added to N3 by this test (A12, 2026-10-14): sequence 12804 has no frame sound in the production cache and
            // StaffOfTheDead.activatePowerOfDeath sends none.
            Exemption.OpenN3("staff_of_the_dead.plugin.kts", "StaffOfTheDead.POWER_OF_DEATH_ENERGY"),
        )

    /** Every registration block in every plugin script, cut at the next registration in the same file. */
    private fun blocks(): List<Block> {
        val start = Regex("""SpecialAttacks\.register(Instant)?\(""")
        return sourceRoot.walkTopDown().filter { it.isFile && it.name.endsWith(".plugin.kts") }.flatMap { file ->
            val text = file.readText()
            val starts = start.findAll(text).map { it.range.first }.toList()
            starts.mapIndexed { i, s -> Block(file.name, text.substring(s, starts.getOrElse(i + 1) { text.length })) }
        }.toList()
    }

    private fun exemptionFor(block: Block) = exemptions.firstOrNull { it.file == block.file && it.marker in block.text }

    @Test
    fun `every special attack sends a sound, has an audible animation or is an open N3 entry`() {
        val all = blocks()
        assertTrue(all.size >= 40, "expected every special registration, found ${all.size}")
        val silent = all.filter { !soundCall.containsMatchIn(it.text) && exemptionFor(it) == null }
        assertTrue(silent.isEmpty(), "${silent.size} special attacks make no sound: ${silent.map { "${it.file}: ${it.header}" }}")
    }

    @Test
    fun `every exemption still matches a silent special - fixed N3 entries are removed from the list`() {
        val all = blocks()
        val stale =
            exemptions.filter { ex ->
                val block = all.firstOrNull { it.file == ex.file && ex.marker in it.text }
                block == null || soundCall.containsMatchIn(block.text)
            }
        assertTrue(stale.isEmpty(), "stale exemptions (no block, or the block now sends a sound): ${stale.map { "${it.file} ${it.marker}" }}")
    }

    @Test
    fun `exempted animations really carry a frame sound in the production cache and delegates really play one`() {
        val library = CacheLibrary(productionCache)
        try {
            val mute =
                exemptions.filterIsInstance<Exemption.FrameSound>().filter { ex ->
                    val data = library.data(20, ex.animation ushr 7, ex.animation and 0x7F)
                    data == null || frameSoundIds(data).isEmpty()
                }
            assertTrue(mute.isEmpty(), "animations without a frame sound: ${mute.map { "${it.file} ${it.animation}" }}")
        } finally {
            library.close()
        }
        exemptions.filterIsInstance<Exemption.Delegated>().forEach { ex ->
            assertTrue(ex.soundText in File(sourceRoot, ex.sourceFile).readText(), "${ex.sourceFile} no longer contains ${ex.soundText}")
        }
    }

    // ---- Dark bow (A12): OSRS Wiki "Dark bow" sound table + Void DarkBow.kt structure ----

    private val darkBow: String
        get() {
            val script = File(sourceRoot, "gg/rsmod/plugins/content/combat/specialattack/weapons/ranged_specials.plugin.kts").readText()
            val start = script.indexOf("SpecialAttacks.register(55, *DARK_BOWS)")
            assertTrue(start >= 0, "dark bow registration missing")
            return script.substring(start, script.indexOf("SpecialAttacks.register(", start + 10))
        }

    @Test
    fun `dark bow sound ids are the OSRS Wiki ones`() {
        assertEquals(3733, Sfx.DARKBOW_DRAGON_ATTACK) // Descent of Dragons launch
        assertEquals(3736, Sfx.DARKBOW_SHADOW_ATTACK) // Descent of Darkness launch
        assertEquals(3737, Sfx.DARKBOW_SHADOW_IMPACT) // impact of both specials
    }

    @Test
    fun `the dark bow special launches with the dragon or darkness sound, only after the two-arrow check`() {
        val block = darkBow
        val pick = "if (dragon) Sfx.DARKBOW_DRAGON_ATTACK else Sfx.DARKBOW_SHADOW_ATTACK"
        assertTrue(pick in block, "launch sound must pick 3733 (dragon arrows) / 3736 (other arrows)")
        val failed = block.indexOf("specialFailed()")
        assertTrue(failed >= 0 && block.indexOf(pick) > failed, "a failed special (fewer than 2 arrows) must stay silent")
        assertTrue("player.playSound(launch)" in block && "player.soundToBystanders(launch)" in block, "shooter + bystanders hear the launch")
        assertTrue("DARKBOW_DOUBLEFIRE" !in block, "3731 doublefire is the NORMAL attack sound (OSRS Wiki), not the special")
    }

    @Test
    fun `every dark bow special arrow plays the impact sound when it lands`() {
        val block = darkBow
        val shot = block.substringAfter("rangedShot(").substringBefore("victim.graphic(impact")
        assertTrue("onHit = { impactSound() }" in shot, "the impact sound runs in rangedShot's onHit (at landing), once per arrow")
        val impact = block.substringAfter("val impactSound = {").substringBefore("\n    }")
        assertTrue("player.playSound(Sfx.DARKBOW_SHADOW_IMPACT)" in impact, "shooter hears 3737")
        assertTrue("AreaSound(" in impact && "victim.tile" in impact && "Sfx.DARKBOW_SHADOW_IMPACT" in impact, "victim + bystanders hear 3737")
        assertTrue("excluded = player" in impact, "the shooter must not hear the impact twice")
    }

    /** Opcode 13 of the 667 sequence definition: per-frame sound effects (same decode as SeqEmbeddedSoundProbeTests). */
    private fun frameSoundIds(bytes: ByteArray): List<Int> {
        val buf = ByteBuffer.wrap(bytes)
        val sounds = mutableListOf<Int>()
        fun u8() = buf.get().toInt() and 0xFF
        fun u16() = buf.short.toInt() and 0xFFFF
        fun u24() = (u8() shl 16) or (u8() shl 8) or u8()
        while (buf.hasRemaining()) {
            when (val op = u8()) {
                0 -> return sounds
                1 -> {
                    val frames = u16()
                    repeat(3 * frames) { u16() } // frame ids, then the two delay/frame tables
                }
                2 -> u16()
                3 -> repeat(u8()) { u8() }
                4, 14, 15, 16, 18 -> {}
                5, 8, 9, 10, 11, 17 -> u8()
                6, 7 -> u16()
                12 -> {
                    val n = u8()
                    repeat(2 * n) { u16() }
                }
                13 ->
                    repeat(u16()) {
                        val size = u8()
                        if (size > 0) {
                            sounds += u24() shr 8
                            repeat(size - 1) { u16() }
                        }
                    }
                19 -> {
                    u8()
                    u8()
                }
                20 -> {
                    u8()
                    u16()
                    u16()
                }
                249 ->
                    repeat(u8()) {
                        val string = u8() == 1
                        u24()
                        if (string) while (buf.get().toInt() != 0) {} else buf.int
                    }
                else -> error("unknown seq opcode $op")
            }
        }
        return sounds
    }
}
