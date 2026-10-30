package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.GroundItem
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.playSound
import java.util.Random

/**
 * N423 deel 5 (owner 2026-10-29 23:5x, BUGLIJST N423 "DROPKANSEN BESLOTEN"): the three smaller burden trinkets drop; Tortoise and
 * Pack yak only come from the Donator store (for now).
 *
 *   per kill of a boss (also a breach boss)   Kalphite 1/250    Bull ant 1/500    Terrorbird 1/1,000
 *   per kill of a breach monster              Kalphite 1/1,000  Bull ant 1/2,000  Terrorbird 1/4,000
 *   per Deadman's Chest opened                Kalphite 1/100    Bull ant 1/200    Terrorbird 1/400
 *
 * No bracket boost (unlike the pets' Pets.roll): every player and the Drop Viewer see the same chance. Each kind is its own roll;
 * at most one trinket per kill (the rarest that hit). Every drop is announced to the world in the Deadman's Chest purple and
 * recorded in the Collection Log.
 */
object BurdenTrinketDrops {
    enum class Source { BOSS, BREACH_MONSTER, CHEST }

    /** The three dropped kinds, rarest first (the order the rolls are read in). */
    val DROPPED = listOf(BurdenTrinkets.Tier.TERRORBIRD, BurdenTrinkets.Tier.BULL_ANT, BurdenTrinkets.Tier.KALPHITE)

    /** 1 in this many, per [Source]. */
    fun denominator(
        source: Source,
        tier: BurdenTrinkets.Tier,
    ): Int {
        val base =
            when (source) {
                Source.BOSS -> 250
                Source.BREACH_MONSTER -> 1_000
                Source.CHEST -> 100
            }
        return when (tier) {
            BurdenTrinkets.Tier.KALPHITE -> base
            BurdenTrinkets.Tier.BULL_ANT -> base * 2
            BurdenTrinkets.Tier.TERRORBIRD -> base * 4
            else -> 0
        }
    }

    fun chance(
        source: Source,
        tier: BurdenTrinkets.Tier,
    ): Double = denominator(source, tier).let { if (it <= 0) 0.0 else 1.0 / it }

    /** One kill's roll: the rarest kind that hit, or null. */
    fun roll(
        random: Random,
        source: Source,
    ): BurdenTrinkets.Tier? = DROPPED.firstOrNull { random.nextInt(denominator(source, it)) == 0 }

    /**
     * The bosses that exist here: the Drop Viewer's boss names, every npc that drops a boss pet (pet_tiers.tsv drop npcs - the
     * breach bosses among them) and the chest guardian Zemouregal. Every other breach-roster npc is a breach monster.
     */
    fun sourceOf(
        world: World,
        npcId: Int,
    ): Source? {
        val breach = gg.rsmod.plugins.content.mechanics.pvp.breach.BreachMonsters.ROSTER.firstOrNull { it.id == npcId }
        val guardian = gg.rsmod.plugins.content.mechanics.pvp.chest.DeadmanChestIds.guardianNpc
        if (Pets.dropFor(npcId) != null || (guardian > 0 && npcId == guardian)) return Source.BOSS
        if (breach != null) return if (breach.summon) null else Source.BREACH_MONSTER
        val name = world.definitions.getNullable(NpcDef::class.java, npcId)?.name?.lowercase() ?: return null
        return if (name in gg.rsmod.plugins.content.quests.tab.DropSources.BOSS_NAMES) Source.BOSS else null
    }

    /**
     * Gives [tier] to [player]: the inventory, else a ground item at [at] for them only (like the chest's reward); the world hears
     * of it and the Collection Log records it under [logSource] (the Drop Viewer key: "npc:<id>", "breach:<id>", "chest").
     */
    fun award(
        player: Player,
        tier: BurdenTrinkets.Tier,
        at: gg.rsmod.game.model.Tile,
        logSource: String?,
        from: String,
    ) {
        if (!player.inventory.add(tier.item, 1).hasSucceeded()) {
            player.world.spawn(GroundItem(tier.item, 1, at, player))
            player.message("<col=ef1020>Your inventory is full - your ${tier.trinketName} lies on the ground.</col>")
        }
        BurdenTrinkets.recordOwned(player)
        player.playSound(BurdenTrinkets.BIND_SOUND)
        player.world.players.forEach {
            it.message("<col=${BurdenTrinkets.PURPLE}>${player.username.replaceFirstChar { c -> c.uppercase() }} has received a ${tier.trinketName} from $from!</col>")
        }
        logSource?.let { key -> runCatching { gg.rsmod.plugins.content.quests.tab.CollectionLog.record(player, key, listOf(tier.item to 1)) } }
        gg.rsmod.game.model.Trace.log(gg.rsmod.game.model.Trace.Cat.SUMMON) { "burden trinket drop ${tier.trinketName} to ${player.username} from $from" }
    }

    /** A kill of [npcId] by [player] outside the breach (the breach rolls per contributor in DeadmanBreach). */
    fun onKill(
        player: Player,
        npcId: Int,
        at: gg.rsmod.game.model.Tile,
        random: Random = Random(),
    ) {
        val source = sourceOf(player.world, npcId) ?: return
        val tier = roll(random, source) ?: return
        val name = player.world.definitions.getNullable(NpcDef::class.java, npcId)?.name ?: "a boss"
        award(player, tier, at, gg.rsmod.plugins.content.quests.tab.DropSources.forNpc(player, npcId)?.key, name)
    }
}
