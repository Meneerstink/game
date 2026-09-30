package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.fs.def.AnimDef
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.fs.def.SpotAnimDef
import gg.rsmod.plugins.content.skills.fishing.Fish
import gg.rsmod.plugins.content.skills.mining.RockType
import gg.rsmod.plugins.content.skills.woodcutting.TreeType
import org.junit.BeforeClass
import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The OSRS pet behaviour beyond following (owner 2026-09-30 "alles moet mee"): emotes against the generated table and the cache, the
 * metamorphosis unlock rules against the roster, and the skilling-pet formula (OSRS Wiki Pet).
 */
class PetFeaturesTests {
    companion object {
        private val DIR = Paths.get("..", "..", "data", "cfg", "pets").toFile()

        @BeforeClass
        @JvmStatic
        fun load() {
            assertTrue(Pets.load(File(DIR, "pets.tsv"), File(DIR, "pet_tiers.tsv")) > 0)
            assertTrue(PetEmotes.load(File(DIR, "pet_emotes.tsv")) > 0, "data/cfg/pets/pet_emotes.tsv is missing - run OsrsPetImportTool table")
        }
    }

    @Test
    fun `every follower with an OSRS Emote or Interact op has its emotes, in the cache - Maggot marquess is SOURCE_BLOCKED`() {
        val defs = SummoningTestCache.definitions
        val wrong = mutableListOf<String>()
        Pets.variants.forEach { v ->
            val ops = defs.get(NpcDef::class.java, v.npc).options.filterNotNull().map { it.lowercase() }
            val wants = "emote" in ops || "interact" in ops
            if (wants && !PetEmotes.hasEmotes(v.npc) && v.pet != "Maggot marquess") wrong += "${v.name} (${v.npc}) has ${ops} but no emotes"
            if (!wants && PetEmotes.hasEmotes(v.npc)) wrong += "${v.name} (${v.npc}) has emotes but no Emote/Interact op"
        }
        PetEmotes.byNpc.forEach { (npc, list) ->
            list.forEach { e ->
                e.seqs.filter { defs.getNullable(AnimDef::class.java, it) == null }.forEach { wrong += "npc $npc emote ${e.name}: seq $it missing" }
                if (e.spot >= 0 && defs.getNullable(SpotAnimDef::class.java, e.spot) == null) wrong += "npc $npc emote ${e.name}: spotanim ${e.spot} missing"
            }
        }
        assertEquals(emptyList(), wrong)
        // The wiki orders: Butch 5, Wisp 4, Yami 5, Gull 1, Gulliver 1; Beef 1 plus the rare whirlwind outside the cycle.
        val counts = PetEmotes.byNpc.map { (npc, _) -> Pets.variantOfNpc(npc)!!.name to PetEmotes.cycle(npc).size }.toMap()
        assertEquals(mapOf("Butch" to 5, "Wisp" to 4, "Yami" to 5, "Gull" to 1, "Gulliver" to 1, "Beef" to 1), counts)
    }

    @Test
    fun `every unlock item exists and turns its pet into one of that pet's own variants`() {
        val defs = SummoningTestCache.definitions
        val wrong = mutableListOf<String>()
        PetMorphs.USES.forEach { (pet, uses) ->
            val variants = Pets.variantsOf(pet)
            if (variants.isEmpty()) wrong += "$pet is not in the roster"
            uses.forEach { u ->
                if (defs.getNullable(ItemDef::class.java, u.item) == null) wrong += "$pet: unlock item ${u.item} missing"
                if (variants.none { it.osrsItem == u.target }) wrong += "$pet: target ${u.target} is not one of its variants"
            }
        }
        PetMorphs.RIFT_BY_RUNE.forEach { (rune, osrs) ->
            if (Pets.variantsOf(PetMorphs.RIFT_GUARDIAN).none { it.osrsItem == osrs }) wrong += "rift guardian for $rune ($osrs) missing"
        }
        if (Pets.variantsOf("Gull (pet)").map { it.osrsItem } != listOf(PetMorphs.GULL, PetMorphs.GULLIVER)) wrong += "Gull / Gulliver ids"
        assertEquals(emptyList(), wrong)
    }

    @Test
    fun `the skilling pet chance is 1 in (base - 25 x level), capped at 99, fifteen times better at 200M`() {
        assertEquals(741_600 - 25 * 50, SkillingPets.oneIn(741_600, 50, 0.0))
        assertEquals(741_600 - 25 * 99, SkillingPets.oneIn(741_600, 120, 13_034_431.0))
        assertEquals((42_377 - 25 * 99) / 15, SkillingPets.oneIn(42_377, 99, 200_000_000.0))
    }

    @Test
    fun `every gathering resource here has its pet chance - except what OSRS gives no roll`() {
        assertEquals(RockType.values().toSet() - RockType.ESSENCE, SkillingPets.MINING.keys)
        assertEquals(TreeType.values().toSet() - TreeType.IVY, SkillingPets.WOODCUTTING.keys)
        assertEquals(Fish.values().toSet() - Fish.FROG_SPAWN, SkillingPets.FISHING.keys)
    }
}
