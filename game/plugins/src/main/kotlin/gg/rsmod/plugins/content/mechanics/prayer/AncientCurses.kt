package gg.rsmod.plugins.content.mechanics.prayer

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.sync.block.UpdateBlockType
import gg.rsmod.plugins.api.NpcSkills
import gg.rsmod.plugins.api.PrayerIcon
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.inter.attack.AttackTab

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

    // ---- BATCH 2: the 19-curse book (see AncientCurse.kt), book-switching, drain engine and
    // combat hooks. Turmoil above is untouched and stays on its own attribute/command. ----

    enum class PrayerBook { NORMAL, ANCIENT }

    private val CURRENT_BOOK_ATTR = AttributeKey<PrayerBook>(persistenceKey = "prayer_book")

    /**
     * Not persisted, unlike [UNLOCKED_ATTR]: exactly like the normal [Prayer] book, active
     * curses are cleared on logout/death (see curses.plugin.kts) rather than carried across
     * sessions, so there is nothing here that needs a database column.
     */
    private val ACTIVE_CURSES_ATTR = AttributeKey<MutableSet<AncientCurse>>()

    fun getBook(player: Player): PrayerBook = player.attr[CURRENT_BOOK_ATTR] ?: PrayerBook.NORMAL

    fun switchBook(
        player: Player,
        book: PrayerBook,
    ) {
        if (getBook(player) == book) {
            player.filterableMessage("You are already using the ${book.name.lowercase()} prayer book.")
            return
        }
        // The two books' overhead icon and drain loops are not designed to run at once.
        Prayers.deactivateAll(player)
        deactivateAllCurses(player)
        player.attr[CURRENT_BOOK_ATTR] = book
        player.filterableMessage("You switch to the ${book.name.lowercase()} prayer book.")
    }

    private fun activeCurses(player: Player): MutableSet<AncientCurse> {
        var set = player.attr[ACTIVE_CURSES_ATTR]
        if (set == null) {
            set = mutableSetOf()
            player.attr[ACTIVE_CURSES_ATTR] = set
        }
        return set
    }

    fun isCurseActive(
        player: Player,
        curse: AncientCurse,
    ): Boolean = activeCurses(player).contains(curse)

    fun toggleCurse(
        player: Player,
        curse: AncientCurse,
    ) {
        if (isCurseActive(player, curse)) {
            deactivateCurse(player, curse)
            return
        }
        if (player.attr[UNLOCKED_ATTR] != true) {
            player.filterableMessage("You must perform the ritual first - see ::curse unlock.")
            return
        }
        if (getBook(player) != PrayerBook.ANCIENT) {
            player.filterableMessage("You must switch to the ancient book first - see ::curse book ancient.")
            return
        }
        if (player.skills.getMaxLevel(Skills.PRAYER) < curse.level) {
            player.filterableMessage("You need a Prayer level of ${curse.level} to use ${curse.curseName}.")
            return
        }
        if (player.getCurrentPrayerPoints() <= 0) {
            player.filterableMessage("You don't have enough Prayer points left.")
            return
        }

        // Mutual exclusion, see AncientCurse.Group's kdoc.
        activeCurses(player)
            .filter { it.group != AncientCurse.Group.NONE && it.group == curse.group }
            .forEach { deactivateCurse(player, it) }
        if (curse.group == AncientCurse.Group.OFFENSIVE && isTurmoilActive(player)) {
            player.attr[TURMOIL_ACTIVE_ATTR] = false
            player.filterableMessage("You deactivate Turmoil.")
        }

        activeCurses(player).add(curse)
        player.filterableMessage("You activate ${curse.curseName}.")
        refreshCurseOverhead(player)
        curseDrainLoop(player, curse)
    }

    fun deactivateCurse(
        player: Player,
        curse: AncientCurse,
    ) {
        if (activeCurses(player).remove(curse)) {
            player.filterableMessage("You deactivate ${curse.curseName}.")
            refreshCurseOverhead(player)
        }
    }

    fun deactivateAllCurses(player: Player) {
        if (activeCurses(player).isEmpty() && !isTurmoilActive(player)) return
        activeCurses(player).clear()
        player.attr[TURMOIL_ACTIVE_ATTR] = false
        refreshCurseOverhead(player)
    }

    private fun refreshCurseOverhead(player: Player) {
        val icon = activeCurses(player).firstOrNull { it.icon != null }?.icon ?: PrayerIcon.NONE
        if (player.prayerIcon != icon.id) {
            player.prayerIcon = icon.id
            player.addBlock(UpdateBlockType.APPEARANCE)
        }
    }

    private fun curseDrainLoop(
        player: Player,
        curse: AncientCurse,
    ) {
        player.world.queue {
            while (isCurseActive(player, curse) && player.isOnline && !player.isDead()) {
                wait(AncientCurse.LOOP_TICKS)
                if (!isCurseActive(player, curse)) break
                player.decreasePrayerPoints(curse.drainPerInvocation)
                if (player.getCurrentPrayerPoints() <= 0) {
                    player.filterableMessage("You've run out of Prayer points! ${curse.curseName} deactivates.")
                    deactivateCurse(player, curse)
                    break
                }
            }
        }
    }

    /** Flat 10%/point per-hit drain, floored at 1 - see the "flat instead of escalating" note in NIGHT_SERVER_STATUS.md. */
    private fun sapStat(
        target: Pawn,
        playerSkill: Int,
        npcSkill: Int,
        pct: Double = 0.10,
    ) {
        when (target) {
            is Player -> {
                val amount = (target.skills.getMaxLevel(playerSkill) * pct).toInt().coerceAtLeast(1)
                target.skills.alterCurrentLevel(playerSkill, -amount)
            }
            is Npc -> {
                val amount = (target.stats.getMaxLevel(npcSkill) * pct).toInt().coerceAtLeast(1)
                target.stats.alterCurrentLevel(npcSkill, -amount)
            }
        }
    }

    private fun boostSelf(
        player: Player,
        skill: Int,
        pct: Double = 0.05,
    ) {
        val amount = (player.skills.getMaxLevel(skill) * pct).toInt().coerceAtLeast(1)
        player.skills.alterCurrentLevel(skill, amount, capValue = amount)
    }

    /**
     * BATCH 2 combat hook for Sap/Leech/Soul Split, called once per landed hit from the single
     * shared [gg.rsmod.plugins.content.combat.dealHit] entry point every combat style routes
     * through - see PawnExt.kt (combat package). Wrath's on-death explosion is handled
     * separately from curses.plugin.kts's `on_player_death`, since it fires on the CURSE
     * WEARER's own death, not on a kill.
     *
     * Deflect's "reflects a portion back" bonus sub-effect and Berserker's boost-duration
     * extension are deliberately NOT implemented this pass (disclosed gap, see
     * NIGHT_SERVER_STATUS.md) - Deflect's core damage block/reduction already works for free by
     * reusing the real [PrayerIcon] values, and Berserker has no existing boost-duration-timer
     * system in this codebase to extend.
     */
    fun onDamageDealt(
        attacker: Pawn,
        target: Pawn,
        damage: Int,
    ) {
        if (attacker !is Player || damage <= 0) return
        activeCurses(attacker).forEach { curse ->
            when (curse) {
                AncientCurse.SAP_WARRIOR -> {
                    sapStat(target, Skills.ATTACK, NpcSkills.ATTACK)
                    sapStat(target, Skills.STRENGTH, NpcSkills.STRENGTH)
                    sapStat(target, Skills.DEFENCE, NpcSkills.DEFENCE)
                }
                AncientCurse.SAP_RANGER -> {
                    sapStat(target, Skills.RANGED, NpcSkills.RANGED)
                    sapStat(target, Skills.DEFENCE, NpcSkills.DEFENCE)
                }
                AncientCurse.SAP_MAGE -> {
                    sapStat(target, Skills.MAGIC, NpcSkills.MAGIC)
                    sapStat(target, Skills.DEFENCE, NpcSkills.DEFENCE)
                }
                AncientCurse.SAP_SPIRIT ->
                    if (target is Player) {
                        AttackTab.setEnergy(target, (AttackTab.getEnergy(target) - 10).coerceIn(0, 100))
                    }
                AncientCurse.LEECH_ATTACK -> {
                    sapStat(target, Skills.ATTACK, NpcSkills.ATTACK)
                    boostSelf(attacker, Skills.ATTACK)
                }
                AncientCurse.LEECH_RANGED -> {
                    sapStat(target, Skills.RANGED, NpcSkills.RANGED)
                    boostSelf(attacker, Skills.RANGED)
                }
                AncientCurse.LEECH_MAGIC -> {
                    sapStat(target, Skills.MAGIC, NpcSkills.MAGIC)
                    boostSelf(attacker, Skills.MAGIC)
                }
                AncientCurse.LEECH_DEFENCE -> {
                    sapStat(target, Skills.DEFENCE, NpcSkills.DEFENCE)
                    boostSelf(attacker, Skills.DEFENCE)
                }
                AncientCurse.LEECH_STRENGTH -> {
                    sapStat(target, Skills.STRENGTH, NpcSkills.STRENGTH)
                    boostSelf(attacker, Skills.STRENGTH)
                }
                AncientCurse.LEECH_ENERGY ->
                    if (target is Player) {
                        target.runEnergy = (target.runEnergy - 10.0).coerceIn(0.0, 100.0)
                        attacker.runEnergy = (attacker.runEnergy + 10.0).coerceIn(0.0, 100.0)
                    }
                AncientCurse.LEECH_SPECIAL_ATTACK ->
                    if (target is Player) {
                        val stolen = AttackTab.getEnergy(target).coerceAtMost(10)
                        AttackTab.setEnergy(target, AttackTab.getEnergy(target) - stolen)
                        AttackTab.setEnergy(attacker, (AttackTab.getEnergy(attacker) + stolen).coerceAtMost(100))
                    }
                AncientCurse.SOUL_SPLIT -> {
                    attacker.heal((damage * 0.2).toInt().coerceAtLeast(0))
                    if (target is Player) target.decreasePrayerPoints((damage * 0.2 * 10).toInt())
                }
                else -> {}
            }
        }
    }

    /**
     * Wrath: "on death, deals damage up to 250% of Prayer level in a 5x5 radius" fires on the
     * WEARER's own death (a final burst, like a harder Retribution), not on a kill - called from
     * curses.plugin.kts's `on_player_death`, before curses are cleared for the death.
     */
    fun wrathExplosion(player: Player) {
        val damage = (player.skills.getMaxLevel(Skills.PRAYER) * 2.5).toInt()
        if (damage <= 0) return
        val tile = player.tile
        val world = player.world
        val targets = mutableListOf<Pawn>()
        world.npcs.forEach { if (it.tile.isWithinRadius(tile, 2) && !it.isDead()) targets.add(it) }
        world.players.forEach { if (it != player && it.tile.isWithinRadius(tile, 2) && !it.isDead()) targets.add(it) }
        targets.forEach { it.hit(damage = damage) }
    }
}
