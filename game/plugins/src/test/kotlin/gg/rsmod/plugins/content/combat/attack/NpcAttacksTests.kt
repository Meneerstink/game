package gg.rsmod.plugins.content.combat.attack

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.AnimDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.fs.def.SpotAnimDef
import gg.rsmod.plugins.api.cfg.Npcs
import org.junit.AfterClass
import org.junit.BeforeClass
import java.nio.file.Files
import java.nio.file.Paths
import java.util.stream.Collectors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * RCV-005 shared npc attack model ([NpcAttacks], Void `Attack.kt` + `*.combat.toml`), proven against the real
 * rev-667 cache for every row, not against one boss.
 */
class NpcAttacksTests {
    @Test
    fun `every attack anim, gfx, projectile and sound id exists in the 667 cache`() {
        val failures = mutableListOf<String>()
        NpcAttacks.rows().forEach { row ->
            row.attacks.forEach { a ->
                val where = "npc ${row.id} (${row.voidKey}.${a.id})"
                listOf(a.anim, a.targetAnim, a.impactAnim).filter { it >= 0 }.forEach { id ->
                    if (definitions.getNullable(AnimDef::class.java, id) == null) failures += "$where: anim $id absent"
                }
                (a.gfx + a.targetGfx + a.impactGfx + a.missGfx).map { it.id }.forEach { id ->
                    if (definitions.getNullable(SpotAnimDef::class.java, id) == null) failures += "$where: gfx $id absent"
                }
                a.projectiles.filter { it.id >= 0 }.forEach { p ->
                    if (definitions.getNullable(SpotAnimDef::class.java, p.id) == null) failures += "$where: projectile ${p.id} absent"
                }
                (a.sounds + a.targetSounds + a.impactSounds + a.missSounds).map { it.id }.forEach { id ->
                    val data = store.data(SYNTH_SOUNDS_INDEX, id, 0)
                    if (data == null || data.isEmpty()) failures += "$where: sound $id absent"
                }
            }
        }
        assertTrue(failures.isEmpty(), "${failures.size} missing ids:\n" + failures.joinToString("\n"))
    }

    @Test
    fun `frost dragon dragonfire freezes like the KBD ice breath and no other attack changes`() {
        val kbdIce = NpcAttacks.rows().single { it.combatDef == "king_black_dragon" }.attacks.single { it.id == "ice" }.freeze
        assertEquals(kbdIce, gg.rsmod.plugins.content.combat.formula.DragonfireTable.FROST_DRAGON_FREEZE_TICKS)
        var frostFreezing = 0
        var icyBreaths = 0
        NpcAttacks.rows().forEach { row ->
            row.attacks.forEach { a ->
                val frostFire = row.combatDef == "frost_dragon" && a.hits.any { it.offense == "dragonfire" }
                val icyBreath = a.hits.any { it.offense == "icy_breath" }
                // Wyvern icy breath: OSRS Wiki "Dragonfire" 6.6 seconds = 11 ticks (replaces the Void table value).
                val expected = if (icyBreath) 11 else if (frostFire) kbdIce else a.freeze
                if (frostFire) frostFreezing++
                if (icyBreath) icyBreaths++
                assertEquals(expected, NpcAttacks.freezeTicks(row.combatDef, a), "npc ${row.id} ${row.combatDef}.${a.id}")
            }
        }
        assertEquals(5 * 2, frostFreezing, "five frost dragon rows (51, 11633-11636) x breath_swipe + dragonfire_ranged")
        assertEquals(4, icyBreaths, "skeletal wyverns 3068-3071 ice_breath")
        val source = Paths.get("src", "main", "kotlin", "gg", "rsmod", "plugins", "content", "combat", "attack", "NpcAttacks.kt").toFile().readText()
        assertTrue("DragonfireTable.blocksFreeze(combatDef, DragonfireFormula.protectionOf(target))" in source)
        assertTrue("WyvernIcyBreath.blocksFreeze(target)" in source && "WyvernIcyBreath.maxHit(target, h.max / 10.0)" in source)
    }

    @Test
    fun `every row names its 667 npc`() {
        val failures =
            NpcAttacks.rows().mapNotNull { row ->
                val def = definitions.getNullable(NpcDef::class.java, row.id)
                if (def == null || def.name != row.name) "npc ${row.id} (${row.voidKey}): cache '${def?.name}' != '${row.name}'" else null
            }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun `Kalphite Queen attacks equal Void kalphite combat toml`() {
        val kq = assertNotNull(NpcAttacks.rowFor(Npcs.KALPHITE_QUEEN))
        val attacks = kq.attacks.associateBy { it.id }
        assertEquals(setOf("melee", "claws", "range", "magic"), attacks.keys)
        attacks.getValue("melee").let {
            assertEquals(6241, it.anim)
            assertEquals("stab" to 310, it.hits.single().let { h -> h.offense to h.max })
        }
        attacks.getValue("range").let {
            assertEquals(10, it.range)
            assertEquals(288, it.projectiles.single().id) // kaplhite_queen_spines
            assertEquals("kalphite_queen_lair", it.multiTargetArea?.name) // spines hit everyone in the lair
        }
        attacks.getValue("magic").let {
            assertEquals(listOf(278), it.gfx.map { g -> g.id }) // lightning cast
            assertEquals(280, it.projectiles.single().id) // lightning travel
            assertEquals(listOf(281), it.impactGfx.map { g -> g.id }) // lightning impact
            assertEquals(16, it.sounds.single().radius)
        }
        assertEquals(10, NpcAttacks.rowFor(Npcs.KALPHITE_QUEEN)!!.attacks.maxOf { it.range })
    }

    @Test
    fun `hit delay converts client cycles to ticks like Void Hit kt`() {
        assertEquals(1, NpcAttacks.hitDelayTicks(0)) // Void 0 = this tick; earliest hit here is 1
        assertEquals(2, NpcAttacks.hitDelayTicks(30))
        assertEquals(3, NpcAttacks.hitDelayTicks(64))
        assertTrue(NpcAttacks.isMelee("typeless_crush"))
        assertFalse(NpcAttacks.isMelee("dragonfire"))
    }

    @Test
    fun `Kalphite Queen is no longer bound to the placeholder combat script`() {
        val binding =
            Paths.get("src", "main", "kotlin", "gg", "rsmod", "plugins", "content", "combat", "scripts", "combat_script_binding.plugin.kts")
        val text = String(Files.readAllBytes(binding))
        assertFalse(text.contains("on_npc_combat(*KalphiteQueenCombatScript.ids)"))
    }

    @Test
    fun `every attack condition in the table is ported or explicitly not ported yet`() {
        val sources =
            Files.walk(Paths.get("src", "main", "kotlin")).use { stream ->
                stream.filter { it.toString().endsWith(".kt") || it.toString().endsWith(".kts") }
                    .map { String(Files.readAllBytes(it)) }
                    .collect(Collectors.toList())
            }.joinToString("\n")
        val ported = Regex("""NpcAttacks\.condition\("([a-z_]+)"""").findAll(sources).map { it.groupValues[1] }.toSet()
        // Registered by no Void script, so Void's CombatApi.condition treats it as true.
        val voidUnregistered = setOf("no_attackers")
        // Void script conditions whose mechanics are not ported yet; their attacks are never selected.
        val notPortedYet =
            setOf(
                "has_food", "has_no_food", "has_druid_pouch", "has_no_druid_pouch", // Ghasts
                "weakened_nearby_monsters", // TzHaar healers
                "insulated", "not_insulated",
                "tormented_melee", "tormented_range", "tormented_magic", // TD script picks sections by name
            )
        val used = NpcAttacks.rows().flatMap { row -> row.attacks.map { it.condition } }.filter { it.isNotEmpty() }.toSet()
        val unaccounted = used - ported - voidUnregistered - notPortedYet
        assertTrue(unaccounted.isEmpty(), "attack conditions neither ported nor listed: $unaccounted")
    }

    @Test
    fun `bosses on the shared model are no longer bound to hand-written attack scripts`() {
        val root = Paths.get("src", "main", "kotlin", "gg", "rsmod", "plugins", "content")
        val binding = String(Files.readAllBytes(root.resolve(Paths.get("combat", "scripts", "combat_script_binding.plugin.kts"))))
        listOf(
            "KalphiteQueenCombatScript", "KingBlackDragonCombatScript", "DragonCombatScript", "DagannothKingsCombatScript",
            "ChaosElementalCombatScript", "MetalDragonCombatScript", "FrostDragonCombatScript", "SkeletalWyvernCombatScript",
            "SpinolypCombatScript",
        ).forEach { script -> assertFalse(binding.contains("on_npc_combat(*$script.ids)"), "$script is still bound") }
        val generals = String(Files.readAllBytes(root.resolve(Paths.get("areas", "godwars", "godwars_generals.plugin.kts"))))
        assertFalse(generals.contains("on_npc_combat("), "God Wars generals still bound to scripts")
    }

    @Test
    fun `attack animations are checked against each npc's rev-667 skeleton`() {
        var known = 0
        var total = 0
        val mismatches = mutableListOf<String>()
        NpcAttacks.rows().forEach { row ->
            row.attacks.filter { it.anim >= 0 }.forEach { a ->
                total++
                val npcSkeleton = AnimSkeletons.skeletonOfNpc(definitions, store, row.id)
                val animSkeleton = AnimSkeletons.skeletonOfAnim(definitions, store, a.anim)
                if (npcSkeleton >= 0 && animSkeleton >= 0) known++
                if (!AnimSkeletons.fits(definitions, store, row.id, a.anim)) {
                    mismatches += "${row.id} ${row.name} ${a.id}: anim ${a.anim} skeleton $animSkeleton != npc $npcSkeleton"
                }
            }
        }
        println("AnimSkeletons: $known/$total section anims with both skeletons known, ${mismatches.size} mismatches")
        mismatches.forEach { println("ANIM_MISMATCH $it") }
        assertTrue(known * 10 >= total * 9, "skeletons must be decodable for at least 90% of section anims ($known/$total)")
    }

    @Test
    fun `K'ril Tsutsaroth's 667 overrides animate his own skeleton`() {
        val kril = assertNotNull(NpcAttacks.rowFor(Npcs.KRIL_TSUTSAROTH))
        val wrong = kril.attacks.filter { !AnimSkeletons.fits(definitions, store, kril.id, it.anim) }.map { "${it.id}=${it.anim}" }
        assertTrue(wrong.isEmpty(), "K'ril attack anims not on his skeleton: $wrong")
        assertEquals(14963, kril.attacks.first { it.id == "melee" }.anim) // Novite KrilTsutsaroth.java:88
    }

    @Test
    fun `npc definition clones inherit their base combat definition`() {
        assertEquals("frost_dragon", NpcAttacks.rowFor(Npcs.FROST_DRAGON)?.combatDef) // Void frost_dragon_5 clone, id 51
        assertEquals("tormented_demon", NpcAttacks.rowFor(8354)?.combatDef) // Void tormented_demon_range clone
    }

    companion object {
        private const val SYNTH_SOUNDS_INDEX = 4
        private val definitions = DefinitionSet()
        private lateinit var store: CacheLibrary

        @BeforeClass
        @JvmStatic
        fun load() {
            store = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            definitions.loadAll(store)
            val rows = NpcAttacks.load(Paths.get("..", "..", "data", "cfg", "npcs", "npc-attacks.json").toFile())
            assertTrue(rows > 1000, "the generated attack table must cover the Void roster")
        }

        @AfterClass
        @JvmStatic
        fun close() {
            store.close()
        }
    }
}
