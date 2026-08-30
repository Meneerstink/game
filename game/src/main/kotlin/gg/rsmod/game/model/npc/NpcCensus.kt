package gg.rsmod.game.model.npc

import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.World
import java.io.File

/**
 * Server-side NPC census (R04.2): every NPC definition loaded from the cache at startup
 * (not only currently-spawned instances), cross-referenced against the plugin registries
 * that are populated by the time [World.postLoad] runs. This is the single reusable source
 * both the owner `::npc_inventory` command and the dev-mode auto-dump below read from, so
 * they can never drift apart.
 *
 * Known limitation: "live_count"/"currently spawned" only reflects npcs spawned from static
 * spawn files at boot. Npcs only spawned dynamically by an activity (Nex, Barrows waves,
 * Breach mobs, ...) show live_count=0 until that activity actually runs; their definition,
 * options and combat-def binding are still reported correctly since those come from the
 * full cache definition set, not from [World.npcs].
 */
object NpcCensus {
    data class Row(
        val id: Int,
        val name: String,
        val combatLevel: Int,
        val cacheAttackable: Boolean,
        val options: List<String>,
        val boundOptions: List<String>,
        val transforms: List<Int>,
        val hasCombatDef: Boolean,
        val liveCount: Int,
    )

    fun collect(world: World): List<Row> {
        val liveCounts = HashMap<Int, Int>()
        world.npcs.forEach { npc -> liveCounts[npc.id] = (liveCounts[npc.id] ?: 0) + 1 }

        val ids = world.definitions.getAllKeys(NpcDef::class.java).sorted()
        return ids.map { id ->
            val def = world.definitions.get(NpcDef::class.java, id)
            val boundSlots = world.plugins.boundNpcOptions(id)
            val options = mutableListOf<String>()
            val bound = mutableListOf<String>()
            def.options.forEachIndexed { i, opt ->
                if (!opt.isNullOrBlank()) {
                    options += opt
                    if ((i + 1) in boundSlots) bound += opt
                }
            }
            Row(
                id = id,
                name = def.name,
                combatLevel = def.combatLevel,
                cacheAttackable = def.isAttackable(),
                options = options,
                boundOptions = bound,
                transforms = def.transforms?.toList() ?: emptyList(),
                hasCombatDef = world.plugins.npcCombatDefs.containsKey(id),
                liveCount = liveCounts[id] ?: 0,
            )
        }
    }

    /**
     * Writes the full census to [path] and returns a short summary line (never dumps
     * thousands of rows to the console/log).
     */
    fun writeCsv(
        world: World,
        path: String = "./npc_inventory.csv",
    ): String {
        val rows = collect(world)
        val lines = mutableListOf(
            "id,name,combat_level,cache_attackable,options,bound_options,transforms,has_combat_def,live_count",
        )
        rows.forEach { r ->
            lines += listOf(
                r.id.toString(),
                "\"${r.name}\"",
                r.combatLevel.toString(),
                r.cacheAttackable.toString(),
                "\"${r.options.joinToString("|")}\"",
                "\"${r.boundOptions.joinToString("|")}\"",
                "\"${r.transforms.joinToString("|")}\"",
                r.hasCombatDef.toString(),
                r.liveCount.toString(),
            ).joinToString(",")
        }
        File(path).writeText(lines.joinToString("\n"))

        val attackable = rows.count { it.cacheAttackable }
        val missingCombatDef = rows.count { it.cacheAttackable && !it.hasCombatDef }
        val liveTypes = rows.count { it.liveCount > 0 }
        val deadOptions = rows.count { it.options.size > it.boundOptions.size }
        return "npc_inventory.csv written: ${rows.size} cache npc definitions ($liveTypes currently " +
            "spawned live), $attackable cache-attackable, $missingCombatDef attackable types missing a " +
            "real combat def, $deadOptions types with at least one unbound (dead) option."
    }
}
