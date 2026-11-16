package gg.rsmod.plugins.content.activity.cox

import gg.rsmod.game.model.combat.WeaponStyle
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.ext.getEquipment
import gg.rsmod.plugins.content.combat.CombatConfigs
import java.util.concurrent.ConcurrentHashMap

/**
 * Dinh's bulwark styles (OSRS Wiki Dinh's_bulwark, raw work/claude-logs/cox/wiki/dinhs.wiki, 16-11): 'Block' = the player cannot attack,
 * incoming damage -20% (not in PvP), only 8 ticks after switching to the bulwark; Block -> Pummel = 8 ticks without attacking, even when
 * switching to another weapon; no combat spells: 'Your bulwark gets in the way'.
 * Our client has no 'Bulwark' style set: the bulwark uses the 667 hammer set (Pound / Pummel / Block), the 3rd (defensive) box = Block,
 * the other two = Pummel (dossier COX.txt OPEN: own Pummel/Block set). ONZEKER: the Pummel strength passive (Wiki formula) is not built yet.
 */
object DinhsBulwark {
    const val OSRS = 21015
    const val SWITCH_TICKS = 8
    private val since = ConcurrentHashMap<String, Int>()

    fun wields(p: Player): Boolean {
        val id = p.getEquipment(EquipmentType.WEAPON)?.id ?: return false
        return id == CoxLootItems.local(OSRS)
    }

    fun blocking(p: Player): Boolean = wields(p) && runCatching { CombatConfigs.getAttackStyle(p) == WeaponStyle.DEFENSIVE }.getOrDefault(false)

    /** On equipping the bulwark or changing the attack style: the 8 ticks start again; leaving Block locks attacks for 8 ticks. */
    fun changed(p: Player, wasBlocking: Boolean, attackDelay: TimerKey) {
        since[p.username.lowercase()] = p.world.currentCycle
        if (wasBlocking && !blocking(p)) p.timers[attackDelay] = maxOf(SWITCH_TICKS, if (p.timers.has(attackDelay)) p.timers[attackDelay] else 0)
    }

    /** -20% of a hit from an NPC while blocking for at least 8 ticks (Wiki: not in PvP). */
    fun modifyIncoming(p: Player, damage: Int): Int {
        if (damage <= 0 || !blocking(p)) return damage
        val start = since[p.username.lowercase()] ?: return damage
        if (p.world.currentCycle - start < SWITCH_TICKS) return damage
        return damage - damage / 5
    }

    /** Null = may attack; else the refusal ("" = silently, as the Block style has no attack). */
    fun refusal(p: Player, magic: Boolean): String? = when {
        !wields(p) -> null
        magic -> "Your bulwark gets in the way."
        blocking(p) -> ""
        else -> null
    }
}
