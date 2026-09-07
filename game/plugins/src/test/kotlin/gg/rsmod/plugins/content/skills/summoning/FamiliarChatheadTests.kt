package gg.rsmod.plugins.content.skills.summoning

import com.displee.cache.CacheLibrary
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.NpcDef
import org.junit.BeforeClass
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertNotEquals
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

    companion object {
        private val DEFINITIONS = DefinitionSet()
        private lateinit var store: CacheLibrary

        @BeforeClass
        @JvmStatic
        fun loadCache() {
            store = CacheLibrary(Paths.get("..", "..", "data", "cache").toFile().toString())
            DEFINITIONS.loadAll(store)
            assertNotEquals(DEFINITIONS.getCount(ItemDef::class.java), 0)
        }
    }
}
