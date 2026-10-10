package gg.rsmod.plugins.content.mechanics.store

import gg.rsmod.game.fs.def.BasDef
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.World
import gg.rsmod.plugins.content.skills.summoning.Pets
import java.io.File

/**
 * Which store items are previewed as an npc (owner 2026-10-10: "de preview van de pets kloppen niet ... liefst hoe die in game
 * staat"): a pet item shows the pet itself, the npc that follows the player, standing in its idle animation.
 *
 * Item -> npc comes from the two pet tables the game already uses or ports: `pets.tsv` (the OSRS pets, [Pets.parseTable]) and
 * `rs667_pets.tsv` (the revision-667 pets, ported from the Darkan donor's `Pets` enum: baby / grown / overgrown item -> npc).
 * Items without an npc (pet rock, the quest-only baby icefiend) keep their item model.
 */
object StorePreview {
    const val RS667_TABLE_PATH = "./data/cfg/pets/rs667_pets.tsv"

    /** View angle of the npc (2048 = a full turn): looked at slightly from above, facing the player. */
    const val PITCH = 64
    const val YAW = 0

    fun parse667(lines: List<String>): Map<Int, Int> =
        lines.filter { it.isNotBlank() && !it.startsWith("#") }
            .mapNotNull { line ->
                val c = line.split('\t')
                val item = c.getOrNull(0)?.trim()?.toIntOrNull() ?: return@mapNotNull null
                val npc = c.getOrNull(1)?.trim()?.toIntOrNull() ?: return@mapNotNull null
                item to npc
            }.toMap()

    private val NPC_BY_ITEM: Map<Int, Int> by lazy {
        val osrs = File(Pets.TABLE_PATH).takeIf { it.exists() }?.let { f -> Pets.parseTable(f.readLines()).associate { it.item to it.npc } } ?: emptyMap()
        val rs667 = File(RS667_TABLE_PATH).takeIf { it.exists() }?.let { parse667(it.readLines()) } ?: emptyMap()
        rs667 + osrs
    }

    /** The npc a store item is previewed as, or null for an item shown as itself. */
    fun npcFor(item: Int): Int? = NPC_BY_ITEM[item]

    /** The npc's idle (ready) animation from its body animation set, or -1. */
    fun idleAnimation(
        world: World,
        npc: Int,
    ): Int {
        val bas = world.definitions.getNullable(NpcDef::class.java, npc)?.basId ?: return -1
        if (bas < 0) return -1
        return world.definitions.getNullable(BasDef::class.java, bas)?.ready ?: -1
    }
}
