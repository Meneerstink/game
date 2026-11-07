package gg.rsmod.plugins.content.activity.toa

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.FOOD_DELAY
import gg.rsmod.game.model.timer.POTION_DELAY
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Anims
import gg.rsmod.plugins.api.cfg.Sfx
import gg.rsmod.plugins.api.ext.filterableMessage
import gg.rsmod.plugins.api.ext.heal
import gg.rsmod.plugins.api.ext.playSound
import gg.rsmod.plugins.api.ext.restorePrayer
import gg.rsmod.plugins.content.items.potion.PotionType
import gg.rsmod.plugins.content.mechanics.run.RunEnergy

/**
 * N462 ToA raid supplies (the Helpful Spirit's bag): the items existed but no option did anything. Effects from the pinned OSRS Wiki
 * snapshots in team\dossiers\N462-notities\toa\21-supplies-dood.tsv (Tombs of Amascut?oldid=15391794 Consumables, lines 457-497;
 * Smelling salts?oldid=15191036, Liquid adrenaline?oldid=15355208, Silk dressing?oldid=15191035, Blessed crystal scarab?oldid=15191032,
 * Honey locust?oldid=15352003). Doses are OSRS item ids two apart (the noted id in between); the last dose leaves nothing.
 * Levels are 1:1 here (99 hitpoints). Sounds: drinks the drink sound and Honey locust the eat sound, as every potion/food here;
 * ONZEKER: the OSRS cues for Apply/Crack/Crush are not in our sources, so those three stay silent (no invented sound).
 */
object ToaConsumables {
    val ADRENALINE_TIMER = TimerKey()
    val SILK_TIMER = TimerKey()
    val SCARAB_TIMER = TimerKey()
    val SALTS_TIMER = TimerKey()
    private val SILK_LEFT = AttributeKey<Int>()
    private val SCARAB_LEFT = AttributeKey<Int>()
    private val SALTS_LEFT = AttributeKey<Int>()

    /** Liquid adrenaline: 150 seconds = 250 ticks. */
    const val ADRENALINE_TICKS = 250
    const val SILK_INTERVAL = 5
    const val SILK_EVENTS = 20
    const val SCARAB_INTERVAL = 4
    const val SCARAB_EVENTS = 9
    const val SALTS_INTERVAL = 25
    /** 8 minutes = 800 ticks, refreshed every 25 ticks. */
    const val SALTS_EVENTS = 800 / SALTS_INTERVAL

    private val COMBAT = intArrayOf(Skills.ATTACK, Skills.STRENGTH, Skills.DEFENCE, Skills.RANGED, Skills.MAGIC)

    enum class Kind(val osrsFull: Int, val doses: Int, val option: String) {
        NECTAR(27315, 4, "Drink"),
        SILK_DRESSING(27323, 2, "Apply"),
        TEARS(27327, 4, "Drink"),
        SCARAB(27335, 2, "Crack"),
        ADRENALINE(27339, 2, "Drink"),
        SALTS(27343, 2, "Crush"),
        AMBROSIA(27347, 2, "Drink"),
        HONEY_LOCUST(27351, 1, "Eat"),
        ;

        val drink: Boolean get() = option == "Drink"
    }

    /** OSRS dose ids of [kind], full first. */
    fun osrsDoses(kind: Kind): List<Int> = (0 until kind.doses).map { kind.osrsFull + 2 * it }

    /** Consumes one dose of the item in [slot] (local id [localId]); true when it was used. */
    fun use(player: Player, kind: Kind, localId: Int, slot: Int): Boolean {
        val item = player.inventory[slot] ?: return false
        if (item.id != localId) return false
        val food = kind == Kind.HONEY_LOCUST
        if (player.timers.has(if (food) FOOD_DELAY else POTION_DELAY)) return false
        if (food) {
            if (!player.inventory.remove(localId, 1, beginSlot = slot).hasSucceeded()) return false
        } else {
            val doses = osrsDoses(kind)
            val index = doses.indexOfFirst { ToaIds.item(it) == localId }
            if (index < 0) return false
            val next = doses.getOrNull(index + 1)?.let(ToaIds::item)
            player.inventory[slot] = next?.let { gg.rsmod.game.model.item.Item(it) }
        }
        if (kind.drink || food) {
            player.animate(Anims.EAT_FOOD)
            player.playSound(if (food) Sfx.EAT else Sfx.LIQUID)
        }
        try {
            apply(player, kind)
        } finally {
            player.timers[FOOD_DELAY] = 3
            if (!food) player.timers[POTION_DELAY] = 3
        }
        gg.rsmod.game.model.AvTrace.log { "toa supply ${kind.name} used item=$localId slot=$slot hp=${player.getCurrentLifepoints()} prayer=${player.getCurrentPrayerPoints()}" }
        return true
    }

    fun apply(player: Player, kind: Kind) {
        val skills = player.skills
        when (kind) {
            Kind.NECTAR -> {
                // "Hitpoints are restored by floor(BaseLevel * 15/100) + 3" (can overheal); "Combat stats are drained by floor(CurrentLevel/20 + 5)".
                val heal = skills.getMaxLevel(Skills.CONSTITUTION) * 15 / 100 + 3
                player.heal(heal, heal)
                COMBAT.forEach { s -> skills.alterCurrentLevel(s, -(skills.getCurrentLevel(s) / 20 + 5), -124) }
                player.filterableMessage("You drink some of your nectar.")
            }
            Kind.TEARS -> {
                // Self: drained combat stats restored by floor(base/4 + 3), prayer by floor(basePrayer/4) + 10.
                // ONZEKER (dossier T28): the item page table reads +8 for the stats; the main page formula (+3) is used.
                COMBAT.forEach { s -> restoreDrained(player, s, skills.getMaxLevel(s) / 4 + 3) }
                player.restorePrayer(skills.getMaxLevel(Skills.PRAYER) / 4 + 10)
                // Party members in the 3x3 around the drinker: prayer floor(allyBasePrayer/10) + 10.
                val raid = ToaLobby.of(player.world).raid(player.uid)
                if (raid != null) {
                    player.world.players.forEach { ally ->
                        if (ally !== player && raid.contains(ally) && ally.tile.height == player.tile.height &&
                            kotlin.math.abs(ally.tile.x - player.tile.x) <= 1 && kotlin.math.abs(ally.tile.z - player.tile.z) <= 1
                        ) {
                            ally.restorePrayer(ally.skills.getMaxLevel(Skills.PRAYER) / 10 + 10)
                        }
                    }
                }
                player.filterableMessage("You drink some of your tears of Elidinis.")
            }
            Kind.AMBROSIA -> {
                // Fully restores, then boosts: hitpoints floor(base/4 + 2), prayer floor(base/5 + 5); run energy full; a dose of antidote++.
                val hp = skills.getMaxLevel(Skills.CONSTITUTION)
                val hpTarget = hp + hp / 4 + 2
                if (player.getCurrentLifepoints() < hpTarget) player.setCurrentLifepoints(hpTarget)
                val pr = skills.getMaxLevel(Skills.PRAYER)
                val prTarget = pr + pr / 5 + 5
                if (player.getCurrentPrayerPoints() < prTarget) player.setCurrentPrayerPoints(prTarget)
                RunEnergy.renew(player, 100.0)
                PotionType.ANTIPOISON_PLUS_PLUS.apply(player)
                player.filterableMessage("You drink some of your ambrosia.")
            }
            Kind.ADRENALINE -> {
                player.timers[ADRENALINE_TIMER] = ADRENALINE_TICKS
                player.filterableMessage("You drink some of your liquid adrenaline.")
            }
            Kind.SILK_DRESSING -> {
                player.attr[SILK_LEFT] = SILK_EVENTS
                player.timers[SILK_TIMER] = SILK_INTERVAL
            }
            Kind.SCARAB -> {
                player.attr[SCARAB_LEFT] = SCARAB_EVENTS
                player.timers[SCARAB_TIMER] = SCARAB_INTERVAL
            }
            Kind.SALTS -> {
                saltsBoost(player)
                RunEnergy.renew(player, 25.0)
                player.attr[SALTS_LEFT] = SALTS_EVENTS
                player.timers[SALTS_TIMER] = SALTS_INTERVAL
            }
            Kind.HONEY_LOCUST -> {
                // Heals 20 (can overheal) and restores prayer as a dose of prayer potion (floor(level/4) + 7 here).
                player.heal(20, 20)
                player.restorePrayer(skills.getMaxLevel(Skills.PRAYER) / 4 + 7)
            }
        }
    }

    /** "Boosts by floor(BaseLevel * 16/100) + 11", refreshed every 25 ticks for 8 minutes. */
    fun saltsBoost(player: Player) {
        COMBAT.forEach { s ->
            val boost = player.skills.getMaxLevel(s) * 16 / 100 + 11
            player.skills.alterCurrentLevel(s, boost, boost)
        }
    }

    private fun restoreDrained(player: Player, skill: Int, amount: Int) {
        val base = player.skills.getMaxLevel(skill)
        val current = player.skills.getCurrentLevel(skill)
        if (current < base) player.skills.setCurrentLevel(skill, minOf(base, current + amount))
    }

    fun silkTick(player: Player) {
        val left = player.attr[SILK_LEFT] ?: return
        // "Heals 5 hitpoints every 5 ticks, 20 times" (item page: may overheal by up to 5).
        player.heal(5, 5)
        if (left > 1) { player.attr[SILK_LEFT] = left - 1; player.timers[SILK_TIMER] = SILK_INTERVAL } else player.attr.remove(SILK_LEFT)
    }

    fun scarabTick(player: Player) {
        val left = player.attr[SCARAB_LEFT] ?: return
        // "Restores 8 Prayer points every 4 ticks, 9 times."
        player.restorePrayer(8)
        if (left > 1) { player.attr[SCARAB_LEFT] = left - 1; player.timers[SCARAB_TIMER] = SCARAB_INTERVAL } else player.attr.remove(SCARAB_LEFT)
    }

    fun saltsTick(player: Player) {
        val left = player.attr[SALTS_LEFT] ?: return
        saltsBoost(player)
        if (left > 1) { player.attr[SALTS_LEFT] = left - 1; player.timers[SALTS_TIMER] = SALTS_INTERVAL } else player.attr.remove(SALTS_LEFT)
    }

    /** Effects end with the raid (exit/death cleanup). */
    fun clear(player: Player) {
        listOf(ADRENALINE_TIMER, SILK_TIMER, SCARAB_TIMER, SALTS_TIMER).forEach { player.timers.remove(it) }
        listOf(SILK_LEFT, SCARAB_LEFT, SALTS_LEFT).forEach { player.attr.remove(it) }
    }
}
