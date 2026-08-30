package gg.rsmod.plugins.content.mechanics.prayer

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.*

/**
 * Ancient Curses (PROJECT_PLAN SS1/SS22 explicitly confirms era content including Turmoil).
 *
 * ponytail: only Turmoil is implemented this pass - the real curse book (Deflect
 * Melee/Missiles/Magic, Wrath, Soul Split, Berserker, Sap/Leech) needs damage-received,
 * on-death, and damage-dealt pipeline hooks this pass didn't have time to add safely. See
 * IMPLEMENTATION_STATUS.md.
 *
 * Deliberately does NOT reuse the normal [Prayer]/varbit system: this cache's real varbit
 * IDs for curse prayers were not verified in this environment (same reasoning as the Grand
 * Exchange interface - see IMPLEMENTATION_STATUS.md), and guessing one risks silently
 * colliding with an unrelated real varbit used elsewhere. Toggled via `::curse turmoil`
 * instead of the prayer orb tab; the mechanical combat bonus is real, only the client-side
 * prayer icon is not wired up.
 */
object AncientCurses {
    private val TURMOIL_ACTIVE_ATTR = AttributeKey<Boolean>()

    /**
     * Stands in for the real "Temple at Senntisten" unlock quest (PROJECT_PLAN SS18/SS7
     * confirms an unlock miniquest is expected for Ancient Curses) - a one-off ritual
     * (`::curse unlock`) rather than a full quest with its own cutscenes/areas, which was
     * out of scope for this pass. See IMPLEMENTATION_STATUS.md.
     */
    val UNLOCKED_ATTR = AttributeKey<Boolean>(persistenceKey = "ancient_curses_unlocked")
    private const val UNLOCK_COST = 50_000

    const val TURMOIL_LEVEL = 95
    private const val DRAIN_PER_TICK = 24 // matches PIETY-tier ~1pt/2.5s drain shape (drainEffect=120, elite-typical)

    fun unlock(player: Player) {
        if (player.attr[UNLOCKED_ATTR] == true) {
            player.filterableMessage("You have already performed the ritual.")
            return
        }
        if (!player.inventory.remove(Items.COINS_995, UNLOCK_COST).hasSucceeded()) {
            player.filterableMessage("You need $UNLOCK_COST coins to perform the ritual.")
            return
        }
        player.attr[UNLOCKED_ATTR] = true
        player.filterableMessage("You perform the ritual and feel the ancients' power. Ancient Curses unlocked.")
    }

    fun isTurmoilActive(player: Player): Boolean = player.attr[TURMOIL_ACTIVE_ATTR] == true

    fun toggleTurmoil(player: Player) {
        if (isTurmoilActive(player)) {
            player.attr[TURMOIL_ACTIVE_ATTR] = false
            player.filterableMessage("You deactivate Turmoil.")
            return
        }
        if (player.attr[UNLOCKED_ATTR] != true) {
            player.filterableMessage("You must perform the ritual first - see ::curse unlock.")
            return
        }
        if (player.skills.getMaxLevel(Skills.PRAYER) < TURMOIL_LEVEL) {
            player.filterableMessage("You need a Prayer level of $TURMOIL_LEVEL to use Turmoil.")
            return
        }
        if (player.getCurrentPrayerPoints() <= 0) {
            player.filterableMessage("You don't have enough Prayer points left.")
            return
        }
        player.attr[TURMOIL_ACTIVE_ATTR] = true
        player.filterableMessage("You activate Turmoil.")
        drainLoop(player)
    }

    private fun drainLoop(player: Player) {
        player.world.queue {
            while (isTurmoilActive(player) && player.isOnline && !player.isDead()) {
                wait(5)
                if (!isTurmoilActive(player)) break
                player.decreasePrayerPoints(DRAIN_PER_TICK)
                if (player.getCurrentPrayerPoints() <= 0) {
                    player.attr[TURMOIL_ACTIVE_ATTR] = false
                    player.filterableMessage("You've run out of Prayer points! Turmoil deactivates.")
                    break
                }
            }
        }
    }
}
