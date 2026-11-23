package gg.rsmod.plugins.content.mechanics.deadman.sigils

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.addXp
import gg.rsmod.plugins.api.ext.filterableMessage
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.content.skills.prayer.burying.BoneData
import gg.rsmod.plugins.content.skills.prayer.scattering.AshData

/** PROJECT48 SIGILS - what the sigils do to NPC drops (called from the one drop spot, DropTableFactory.createDrop). */
object SigilDrops {
    /**
     * S21 Sigil of Devotion: "Dropped Bones are automatically buried. Ashes are automatically scattered. Prayer XP gained from these
     * activities is increased by 200%" (3x). Returns true when the drop was used up this way (nothing lands on the ground).
     */
    fun devotion(
        player: Player,
        item: Item,
    ): Boolean {
        if (!Sigils.effective(player, Sigil.DEVOTION)) return false
        val xp =
            BoneData.boneDefinitions[item.id]?.experience
                ?: AshData.ashDefinitions[item.id]?.experience
                ?: return false
        player.addXp(Skills.PRAYER, xp * 3.0 * item.amount, checkBrawlingGloves = true)
        player.filterableMessage(if (BoneData.boneDefinitions.containsKey(item.id)) "Your sigil buries the bones." else "Your sigil scatters the ashes.")
        return true
    }

    /**
     * The Deadman-only drop table (S-K5 b). The wiki gives NO number for it ("obtained as drops ... as part of the Deadman-only drop
     * table", pages "Deadman: Armageddon" and "Deadman: Armageddon/Sigils"), so the owner chose the rates (22-11, choice A): on EVERY
     * npc kill tier 1 = 1/600, tier 2 = 1/1200, tier 3 = 1/2000. Sigil of Prosperity raises each chance by 20% (wiki S26).
     */
    val GLOBAL_TABLE_CHANCE: Map<Int, Double> = mapOf(1 to 1.0 / 600, 2 to 1.0 / 1200, 3 to 1.0 / 2000)

    /** Rolls the Deadman-only table for one kill of [npcId]: the rarest tier first, at most one sigil; returns it, or null. */
    fun rollGlobalTable(
        player: Player,
        npcId: Int,
    ): Item? {
        val factor = if (Sigils.effective(player, Sigil.PROSPERITY)) 1.2 else 1.0
        for (tier in 3 downTo 1) {
            val chance = (GLOBAL_TABLE_CHANCE[tier] ?: 0.0) * factor
            if (chance <= 0.0 || player.world.randomDouble() >= chance) continue
            val pool = Sigil.available.filter { it.tier == tier }
            return Item(pool[player.world.random(pool.size - 1)].unattunedId)
        }
        return null
    }

    /**
     * ONTBREKEND 25 (owner 22-11 'wel via skills'): "Sigils can be obtained ... training gathering skills" (OSRS Wiki Deadman:
     * Armageddon). The wiki gives no rate; owner 22-11 chose 'as rare as the drop table': one roll per gathered resource (log, ore,
     * fish, catch) at the kill chances (tier 1 = 1/600, tier 2 = 1/1200, tier 3 = 1/2000; Prosperity x1.2).
     */
    const val GATHER_FACTOR = 1.0

    /** The sigil one gathered resource yields, or null (rarest tier first, at most one). */
    fun gatherRoll(player: Player): Sigil? {
        val factor = GATHER_FACTOR * (if (Sigils.effective(player, Sigil.PROSPERITY)) 1.2 else 1.0)
        for (tier in 3 downTo 1) {
            val chance = (GLOBAL_TABLE_CHANCE[tier] ?: 0.0) * factor
            if (chance <= 0.0 || player.world.randomDouble() >= chance) continue
            val pool = Sigil.available.filter { it.tier == tier }
            return pool[player.world.random(pool.size - 1)]
        }
        return null
    }

    /** Rolls [gatherRoll] for one gathered resource; the sigil goes to the inventory, else the ground under the player. */
    fun rollGathering(player: Player) {
        val sigil = gatherRoll(player) ?: return
        if (!player.inventory.add(sigil.unattunedId, 1).hasSucceeded()) {
            player.world.spawn(gg.rsmod.game.model.entity.GroundItem(Item(sigil.unattunedId), player.tile, player))
        }
        player.message("<col=ef1020>You find a ${sigil.label}!</col>")
    }

    /**
     * S-K5: "A random sigil can also be claimed every 24 hours as part of a starter pack" (OSRS Wiki Deadman: Armageddon, Sigils).
     * The wiki gives no tier or weight: the owner chose (22-11, choice c) ONLY tier 1 sigils, each equally likely.
     */
    val STARTER_SIGIL_AT = gg.rsmod.game.model.attr.AttributeKey<Int>(persistenceKey = "p48_sigil_starter_at")
    const val STARTER_SIGIL_SECONDS = 24 * 60 * 60

    /** The 24-hour starter-pack pool (owner 22-11: tier 1 only). */
    val STARTER_POOL: List<Sigil> by lazy { Sigil.available.filter { it.tier == 1 } }

    private fun nowSeconds(): Int = (System.currentTimeMillis() / 1000L).toInt()

    /** Whether 24 hours have passed since this account's last starter-pack sigil. */
    fun starterSigilReady(
        player: Player,
        now: Int = nowSeconds(),
    ): Boolean = now - (player.attr[STARTER_SIGIL_AT] ?: 0) >= STARTER_SIGIL_SECONDS

    /** The sigil of the starter pack that is being opened, or null when the 24 hours are not over. The caller marks it once it is given. */
    fun rollStarterSigil(
        player: Player,
        now: Int = nowSeconds(),
    ): Sigil? {
        if (!starterSigilReady(player, now)) return null
        return STARTER_POOL[player.world.random(STARTER_POOL.size - 1)]
    }

    fun markStarterSigil(
        player: Player,
        now: Int = nowSeconds(),
    ) {
        player.attr[STARTER_SIGIL_AT] = now
    }

    /**
     * "The three starting sigils cannot be traded on the Grand Exchange until players can access the 71-90 bracket world" (OSRS Wiki
     * Deadman: Armageddon, Sigils): a player below the 71-90 bracket can neither sell nor buy the three starting sigils there.
     */
    fun startSigilLockedOnExchange(
        player: Player,
        itemId: Int,
    ): Boolean {
        val sigil = Sigil.ofUnattuned(itemId) ?: return false
        if (sigil !in Sigils.START_CHOICES) return false
        return gg.rsmod.plugins.content.mechanics.pvp.DeadmanBrackets.of(player).ordinal < gg.rsmod.plugins.content.mechanics.pvp.DeadmanBrackets.Bracket.B71_90.ordinal
    }

    const val START_SIGIL_EXCHANGE_MESSAGE = "The starting sigils cannot be traded on the Grand Exchange until you reach the 71-90 combat bracket."

    /** Owner 23-11 S18: clue drop probability x1.5, capped at certainty by the caller. */
    fun clueFactor(player: Player): Double = if (Sigils.effective(player, Sigil.THE_TREASURE_HUNTER)) 1.5 else 1.0
}
