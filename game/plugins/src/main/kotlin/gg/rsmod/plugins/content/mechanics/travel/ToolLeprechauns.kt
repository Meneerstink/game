package gg.rsmod.plugins.content.mechanics.travel

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Npc

/** Trinket of fairies "Tool-leprechauns" destinations (owner decision 2026-09-30, ADAPTED): see trinket_of_fairies.plugin.kts. */
object ToolLeprechauns {
    /** One destination per leprechaun spot (leprechauns within 12 tiles of each other are one spot), labelled by the nearest patch. */
    fun spots(world: World): List<Pair<String, Tile>> {
        val found = ArrayList<Npc>()
        world.npcs.forEach { n -> if (n.tile.x < 6400 && n.def.name.equals("Tool leprechaun", ignoreCase = true)) found += n }
        val spots = ArrayList<Npc>()
        found.sortedWith(compareBy({ it.tile.x }, { it.tile.z })).forEach { n -> if (spots.none { it.tile.height == n.tile.height && it.tile.isWithinRadius(n.tile, 12) }) spots += n }
        val patchIds = gg.rsmod.plugins.content.skills.farming.data.Patch.values().associateBy { it.id }
        return spots.map { n ->
            var best: gg.rsmod.plugins.content.skills.farming.data.Patch? = null
            var bestDist = Int.MAX_VALUE
            for (dx in -2..2) for (dz in -2..2) {
                val chunk = world.chunks.get(n.tile.transform(dx * 8, dz * 8), createIfNeeded = false) ?: continue
                chunk.getEntities<gg.rsmod.game.model.entity.StaticObject>(gg.rsmod.game.model.EntityType.STATIC_OBJECT).forEach { o ->
                    val patch = patchIds[o.id] ?: return@forEach
                    val d = o.tile.getDistance(n.tile)
                    if (d < bestDist && o.tile.height == n.tile.height) {
                        best = patch
                        bestDist = d
                    }
                }
            }
            val place = best?.name?.let { Regex("^[A-Z][a-z]+").find(it)?.value }
            val label = if (place != null && bestDist <= 20) "$place ${best!!.patchName}" else "Tool leprechaun (${n.tile.x}, ${n.tile.z})"
            label to n.tile
        }.sortedBy { it.first }
    }
}

/** Conformance access. */
fun leprechaunSpotsForTest(world: World): List<Pair<String, Tile>> = ToolLeprechauns.spots(world)
