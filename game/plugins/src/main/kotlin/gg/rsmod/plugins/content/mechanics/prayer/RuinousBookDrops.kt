package gg.rsmod.plugins.content.mechanics.prayer

import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.Trace
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.content.mechanics.pvp.breach.BreachMonsters
import gg.rsmod.plugins.content.quests.tab.CollectionLog
import gg.rsmod.plugins.content.quests.tab.DropSources
import gg.rsmod.plugins.content.skills.summoning.BurdenTrinketDrops
import java.util.Random

/** Independent, unboosted rolls like PrayerScrolls; duplicate suppression uses the shared K01 possessions check. */
object RuinousBookDrops {
    enum class Source(val denominator: Int) { CHEST(25), BREACH_MONSTER(300), BOSS(400), NEX(150), REVENANT(1000) }

    /** Same existing boss classification as burden trinkets, including breach bosses and Zemouregal; Nex has its own rate. */
    fun sourceOf(world: World, npcId: Int): Source? {
        val breach = BreachMonsters.ROSTER.firstOrNull { it.id == npcId }
        if (breach?.summon == true) return null
        val name = world.definitions.getNullable(NpcDef::class.java, npcId)?.name?.lowercase() ?: ""
        if (name == "nex") return Source.NEX
        if (name.startsWith("revenant ")) return Source.REVENANT
        return when (BurdenTrinketDrops.sourceOf(world, npcId)) {
            BurdenTrinketDrops.Source.BOSS -> Source.BOSS
            BurdenTrinketDrops.Source.BREACH_MONSTER -> Source.BREACH_MONSTER
            else -> null
        }
    }

    fun roll(player: Player, source: Source, random: Random): Boolean =
        RuinousUnlocks.eligibleForDrop(player) && random.nextInt(source.denominator) == 0

    /** Chest to inventory, NPC rewards to owned ground loot, following their existing reward paths. */
    fun award(player: Player, at: Tile, sourceKey: String?, from: String, inventory: Boolean = false) {
        if (!inventory || player.inventory.add(Items.RUINOUS_POWERS, 1).hasFailed()) {
            player.world.spawn(GroundItem(Items.RUINOUS_POWERS, 1, at, player))
        }
        // A custom boss without a viewer entry still fills the shared log page (kill count is then zero).
        runCatching { CollectionLog.record(player, sourceKey, listOf(Items.RUINOUS_POWERS to 1)) }
            .onFailure { Trace.crash("ruinous powers collection log", it) }
        player.world.players.forEach {
            it.message("<col=${gg.rsmod.plugins.content.mechanics.pvp.chest.DeadmanChestWorld.BROADCAST_COLOUR}>${player.username.replaceFirstChar { c -> c.uppercase() }} has received Ruinous powers from $from!</col>")
        }
    }

    fun onKill(player: Player, npcId: Int, at: Tile, random: Random = Random()) {
        val source = sourceOf(player.world, npcId) ?: return
        if (!roll(player, source, random)) return
        award(player, at, DropSources.forNpc(player, npcId)?.key, DropSources.npcName(player.world, npcId))
    }
}
