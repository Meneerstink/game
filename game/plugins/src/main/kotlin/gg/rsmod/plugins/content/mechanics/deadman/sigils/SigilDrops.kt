package gg.rsmod.plugins.content.mechanics.deadman.sigils

import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.addXp
import gg.rsmod.plugins.api.ext.filterableMessage
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
     * The Deadman-only drop table (S-K5 b): the chance of a sigil per kill, by tier. The wiki gives NO number for it (checked pages
     * "Deadman: Armageddon", "Deadman: Armageddon/Sigils": "obtained as drops ... as part of the Deadman-only drop table"), so the chances
     * stay 0 until the owner gives them (STAND.txt VRAGEN) - nothing is invented. Sigil of Prosperity raises the chance by 20%.
     */
    val GLOBAL_TABLE_CHANCE: Map<Int, Double> = mapOf(1 to 0.0, 2 to 0.0, 3 to 0.0)

    /** Rolls the Deadman-only table for one kill of [npcId]; returns the sigil item to drop, or null. */
    fun rollGlobalTable(
        player: Player,
        npcId: Int,
    ): Item? {
        val tier = 1 + player.world.random(2)
        val base = GLOBAL_TABLE_CHANCE[tier] ?: return null
        if (base <= 0.0) return null
        val chance = if (Sigils.effective(player, Sigil.PROSPERITY)) base * 1.2 else base
        if (player.world.randomDouble() >= chance) return null
        val sigil = Sigil.values().filter { it.tier == tier }.random()
        return Item(sigil.unattunedId)
    }

    /**
     * S-K5: "A random sigil can also be claimed every 24 hours as part of a starter pack" (OSRS Wiki Deadman: Armageddon, Sigils).
     * The wiki gives no tier or weight, so it is one of the sigils that do something on this server, each equally likely.
     */
    val STARTER_SIGIL_AT = gg.rsmod.game.model.attr.AttributeKey<Int>(persistenceKey = "p48_sigil_starter_at")
    const val STARTER_SIGIL_SECONDS = 24 * 60 * 60

    /** The sigils that have no effect on this server (their content does not exist here): never handed out. */
    val WITHOUT_EFFECT: Set<Sigil> = setOf(Sigil.SLAUGHTER, Sigil.THE_FORTUNE_FARMER, Sigil.THE_GUARDIAN_ANGEL)

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
        val pool = Sigil.values().filter { it !in WITHOUT_EFFECT }
        return pool[player.world.random(pool.size - 1)]
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

    /** S18 Treasure Hunter: "Clue Scrolls drop 3x more often" - the factor on every clue drop chance. */
    fun clueFactor(player: Player): Int = if (Sigils.effective(player, Sigil.THE_TREASURE_HUNTER)) 3 else 1

    /** S50 Bloodhound: a 75% chance that a completed clue step returns the reward casket. */
    fun bloodhound(player: Player): Boolean = Sigils.effective(player, Sigil.THE_BLOODHOUND) && player.world.randomDouble() < 0.75
}
