package gg.rsmod.plugins.content.scrolls

import com.google.gson.GsonBuilder
import gg.rsmod.game.fs.DefinitionSet
import gg.rsmod.game.fs.Definition
import gg.rsmod.game.fs.def.*
import gg.rsmod.game.model.World
import gg.rsmod.game.tools.importer.ModernCacheReader
import io.mockk.*
import io.netty.buffer.Unpooled
import java.io.File

/** Offline execution of the actual pool predicates using decoded target definitions.
 * No game boot, registration, player save or cache writer. Does not prove scene playability.
 */
object ClueAppliedAssetAudit {
    @JvmStatic fun main(args: Array<String>) {
        ModernCacheReader(File(args[0])).use { cache ->
            val loaded=HashMap<Pair<Class<*>,Int>,Definition?>()
            fun definition(type: Class<*>,id: Int): Definition? = loaded.getOrPut(type to id) {
                val index=when(type) {
                    ItemDef::class.java->19; NpcDef::class.java->18; ObjectDef::class.java->16
                    AnimDef::class.java->20; SpotAnimDef::class.java->21
                    else->error("Unexpected definition type $type")
                }
                val bits=if(index in setOf(16,19,21))8 else 7
                val raw=cache.file(index,id ushr bits,id and ((1 shl bits)-1)) ?: return@getOrPut null
                (type.getConstructor(Int::class.javaPrimitiveType).newInstance(id) as Definition).also {
                    it.decode(Unpooled.wrappedBuffer(raw))
                }
            }
            val defs=mockk<DefinitionSet>()
            every { defs.getNullable(any<Class<Definition>>(),any()) } answers { definition(firstArg(),secondArg()) }
            every { defs.get(any<Class<Definition>>(),any()) } answers {
                definition(firstArg(),secondArg()) ?: error("Missing requested ${firstArg<Class<*>>().simpleName}:${secondArg<Int>()}")
            }
            val world=mockk<World>();every { world.definitions } returns defs
            val rows=TreasureTrailStepCatalogue.current.rows.map { row ->
                val adapters=linkedMapOf(
                    "basic" to TreasureTrailBasicSteps.available(world,row),
                    "emote" to TreasureTrailEmotes.available(world,row),
                    "agent" to TreasureTrailAgents.available(world,row),
                    "wizard" to TreasureTrailWizards.available(world,row),
                    "puzzle" to TreasureTrailPuzzleBoxes.available(world,row),
                    "masterPuzzle" to TreasureTrailMasterPuzzles.available(world,row),
                    "falo" to (row.action==TreasureTrailStepCatalogue.Action.FALO && ClueNpcs.local(7306)!=null),
                    "sherlock" to (row.action==TreasureTrailStepCatalogue.Action.SHERLOCK && ClueNpcs.local(6586)!=null),
                    "torn" to (row.tornPart && TreasureTrailTornParts.SOURCE_IDS.all { ClueItems.local(it)!=null })
                ).filterValues { it }.keys
                mapOf("key" to row.key,"tier" to row.tier.key,"adapters" to adapters,"eligible" to adapters.isNotEmpty())
            }
            val report=mapOf("kind" to "OFFLINE_ACTUAL_DEFINITION_PREDICATES_NOT_SCENE_OR_BOOT_PROOF",
                "records" to rows,"eligible" to rows.count { it["eligible"]==true },
                "ineligible" to rows.filter { it["eligible"]!=true },"definitionsDecoded" to loaded.size)
            File(args[1]).writeText(GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n")
            println("Read-only applied-asset predicates: ${rows.count { it["eligible"]==true }}/${rows.size}; ${loaded.size} decoded definitions")
            rows.filter { it["eligible"]!=true }.forEach { println("UNAVAILABLE ${it["key"]}") }
            unmockkAll()
        }
    }
}
