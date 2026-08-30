package gg.rsmod.plugins.content.npcs.definitions.barrows

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.*

/**
 * Simplified Barrows: no physical mounds/crypts/puzzle door (no verified Morytania Barrows
 * map coordinates in this environment - see IMPLEMENTATION_STATUS.md). `::barrows` summons
 * all six brothers around the player where they stand; each kill raises "potential" (0-6);
 * the run's final reward - rolled when the last brother standing dies - scales with it,
 * matching the real risk/reward shape (more brothers down = better rewards) without the
 * literal dig-and-tunnel presentation.
 */
object Barrows {
    val POTENTIAL_ATTR = AttributeKey<Int>()

    val BROTHERS =
        listOf(
            Npcs.AHRIM_THE_BLIGHTED,
            Npcs.DHAROK_THE_WRETCHED,
            Npcs.GUTHAN_THE_INFESTED,
            Npcs.KARIL_THE_TAINTED,
            Npcs.TORAG_THE_CORRUPTED,
            Npcs.VERAC_THE_DEFILED,
        )

    private val REWARDS =
        listOf(
            Items.AHRIMS_HOOD,
            Items.AHRIMS_STAFF,
            Items.AHRIMS_ROBE_TOP,
            Items.AHRIMS_ROBE_SKIRT,
            Items.DHAROKS_HELM,
            Items.DHAROKS_GREATAXE,
            Items.DHAROKS_PLATEBODY,
            Items.DHAROKS_PLATELEGS,
            Items.GUTHANS_HELM,
            Items.GUTHANS_WARSPEAR,
            Items.GUTHANS_PLATEBODY,
            Items.GUTHANS_CHAINSKIRT,
            Items.KARILS_COIF,
            Items.KARILS_CROSSBOW,
            Items.KARILS_TOP,
            Items.KARILS_SKIRT,
            Items.TORAGS_HELM,
            Items.TORAGS_HAMMERS,
            Items.TORAGS_PLATEBODY,
            Items.TORAGS_PLATELEGS,
            Items.VERACS_HELM,
            Items.VERACS_FLAIL,
            Items.VERACS_BRASSARD,
            Items.VERACS_PLATESKIRT,
        )

    fun startRun(player: Player) {
        val world = player.world
        player.attr[POTENTIAL_ATTR] = 0
        player.filterableMessage("You break into the crypts. The six Barrows brothers stir...")
        val centre = Tile(player.tile)
        BROTHERS.forEachIndexed { index, npcId ->
            val dx = (index % 3) * 2 - 2
            val dz = (index / 3) * 2 - 1
            val n = Npc(npcId, Tile(centre.x + dx, centre.z + dz, centre.height), world)
            n.respawnOverride = false
            n.walkRadius = 0
            world.spawn(n)
        }
    }

    /** Call from each brother's on_npc_death. */
    fun onBrotherDeath(
        npc: Npc,
        killer: Player,
    ) {
        val potential = (killer.attr[POTENTIAL_ATTR] ?: 0) + 1
        killer.attr[POTENTIAL_ATTR] = potential
        killer.filterableMessage("You have slain a Barrows brother. Potential: $potential/6.")

        val world = killer.world
        val remaining = BROTHERS.count { id -> world.npcs.any { it.id == id && !it.isDead() } }
        if (remaining == 0) {
            grantReward(killer, potential)
            killer.attr[POTENTIAL_ATTR] = 0
        }
    }

    private fun grantReward(
        player: Player,
        potential: Int,
    ) {
        player.filterableMessage("The crypt falls silent. You claim your reward (potential $potential/6).")
        player.inventory.add(Items.COINS_995, 300 * potential)
        // Higher potential noticeably improves unique odds - roughly 2x original-chance
        // direction from PROJECT_PLAN SS9, applied as a simple linear scale here.
        val uniqueChance = 40 - (potential * 5) // out of 256, lower is better; 6/6 ~ 1/26
        if (player.world.random(256) < uniqueChance) {
            val reward = REWARDS[player.world.random(REWARDS.size)]
            player.inventory.add(reward)
            player.filterableMessage("A magic tablet crumbles to dust in your hands...")
        } else {
            player.inventory.add(Items.PURE_ESSENCE, 50 * potential)
        }
    }
}
