package gg.rsmod.plugins.content.skills.summoning

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.EnumDef
import gg.rsmod.game.fs.def.NpcDef
import org.junit.BeforeClass
import java.nio.file.Paths
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The familiar Interact conversation's chatbox portrait (owner failures F4 and F5).
 *
 * The owner reports the familiar dialogue's presentation and its chatbox model behaviour as wrong,
 * separately from world animation. The client decides both, and it is strict about them:
 *
 * * `NPCType.headModel` returns `null` the instant an npc has no `headModels` (config opcode 60).
 *   A `chatNpc` conversation with such an npc draws an **empty portrait box** - there is no
 *   fallback to the world model.
 * * The portrait is then animated with whatever sequence the server puts on the component. The
 *   shared `chatNpc` helper defaults to a `FacialExpression`, and those are humanoid
 *   facial-expression sequences; a familiar's head model has no such frames.
 *
 * The server could not previously even ask the first question - `NpcDef` skipped opcode 60 - so
 * this was invisible to every test written before now. `NpcDef.chatheadModels` decodes it, and
 * these tests turn the answer into evidence for all 78 rather than an assumption about any one.
 *
 * These tests deliberately do **not** assert that every familiar has a chathead. Whether it does is
 * a fact about the 2011 cache, not a requirement; the point is to record which ones do, so the
 * dialogue code can be made to do the right thing for both groups instead of silently drawing
 * nothing.
 */
class FamiliarChatheadTests {
    /**
     * The census. Fails only if the roster itself cannot be resolved, and prints the split so the
     * ledger figure can never drift from the cache.
     */
    @Test
    fun `every familiar's chathead presence is resolvable from the production cache`() {
        val withHead = mutableListOf<String>()
        val withoutHead = mutableListOf<String>()
        SummoningPouchData.values().forEach { pouch ->
            val def = DEFINITIONS.get(NpcDef::class.java, pouch.npc)
            val models = def.chatheadModels
            if (models != null && models.isNotEmpty()) {
                withHead += "${pouch.name}(${pouch.npc})"
            } else {
                withoutHead += "${pouch.name}(${pouch.npc})"
            }
        }
        assertTrue(
            withHead.size + withoutHead.size == SummoningPouchData.values().size,
            "the chathead census did not cover the whole roster",
        )
        println("FAMILIAR_CHATHEADS present=${withHead.size} absent=${withoutHead.size}")
        println("FAMILIAR_CHATHEADS_PRESENT=$withHead")
        println("FAMILIAR_CHATHEADS_ABSENT=$withoutHead")
    }

    /**
     * Whatever the split turns out to be, a familiar the server will hold a conversation with must
     * be one the client can actually draw a portrait for - otherwise the owner sees an empty box
     * where the familiar should be, which is the reported symptom.
     *
     * This asserts the property the dialogue code has to satisfy, for every familiar that has a
     * sourced conversation. If it fails, the fix is in the dialogue presentation, not here.
     */
    @Test
    fun `every familiar with a sourced conversation can be drawn in the chatbox, all applicable`() {
        val undrawable =
            SummoningPouchData.values().filter { pouch ->
                val hasConversation = SummoningDialogueData.conversationsFor(pouch).isNotEmpty()
                val models = DEFINITIONS.get(NpcDef::class.java, pouch.npc).chatheadModels
                hasConversation && (models == null || models.isEmpty())
            }
        assertTrue(
            undrawable.isEmpty(),
            "${undrawable.size} familiars have a sourced Interact conversation but no chathead model, " +
                "so the client draws an empty portrait for them: " +
                undrawable.joinToString { "${it.name}(npc ${it.npc})" },
        )
    }

    /**
     * Void routes both Follower Details and familiar dialogue through the same chathead selector:
     * varbit 4282 selects enum 1276, or enum 1275 with 50 subtracted above 50. This roster-wide test
     * pins that donor map and the target cache outputs so a world BAS animation cannot return.
     */
    @Test
    fun `Void chathead selector and cache enum route covers all mapped familiars`() {
        val mapped = SummoningPouchData.values.filter { SummoningChatheadAnimations.selector(it) != null }
        assertEquals(77, mapped.size)
        assertEquals(listOf(SummoningPouchData.PHOENIX), SummoningPouchData.values.toList() - mapped.toSet())

        val selectorLedger =
            mapped.sortedBy { it.name }.joinToString("|") {
                "${it.name}=${SummoningChatheadAnimations.selector(it)}"
            }
        val selectorHash =
            MessageDigest.getInstance("SHA-256").digest(selectorLedger.toByteArray())
                .joinToString("") { "%02x".format(it) }
        assertEquals(
            "19c92bcd46fede5b288b136177be2e88919e4e6664727ee72818f4d6261caa88",
            selectorHash,
            "all 77 selectors must remain byte-for-byte equal to Void's source map",
        )

        val unresolved = mapped.filter { SummoningChatheadAnimations.resolve(DEFINITIONS, it) == null }
        assertEquals(emptyList(), unresolved, "mapped familiar chathead animations missing from enum 1275/1276")

        assertEquals(6551, SummoningChatheadAnimations.resolve(DEFINITIONS, SummoningPouchData.SPIRIT_WOLF))
        assertEquals(8413, SummoningChatheadAnimations.resolve(DEFINITIONS, SummoningPouchData.ABYSSAL_PARASITE))
        assertEquals(8463, SummoningChatheadAnimations.resolve(DEFINITIONS, SummoningPouchData.SMOKE_DEVIL))
        assertEquals(8488, SummoningChatheadAnimations.resolve(DEFINITIONS, SummoningPouchData.VOID_SPINNER))
        assertEquals(8373, SummoningChatheadAnimations.resolve(DEFINITIONS, SummoningPouchData.DESERT_WYRM))
        assertEquals(8374, SummoningChatheadAnimations.resolve(DEFINITIONS, SummoningPouchData.UNICORN_STALLION))
        assertNull(SummoningChatheadAnimations.resolve(DEFINITIONS, SummoningPouchData.PHOENIX))
    }

    companion object {
        private val DEFINITIONS = DefinitionSet()
        private lateinit var store: CacheLibrary

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            store = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            DEFINITIONS.load(store, NpcDef::class.java)
            DEFINITIONS.load(store, EnumDef::class.java)
            assertTrue(DEFINITIONS.getCount(NpcDef::class.java) > 0)
            assertTrue(DEFINITIONS.getCount(EnumDef::class.java) > 0)
        }
    }
}
