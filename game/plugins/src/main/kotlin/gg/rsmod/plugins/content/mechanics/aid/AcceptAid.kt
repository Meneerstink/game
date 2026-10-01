package gg.rsmod.plugins.content.mechanics.aid

import gg.rsmod.game.model.EntityType
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.ext.getVarp
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.setVarp

/**
 * Accept Aid - the one consent rule for everything another player does TO you (owner 2026-10-01: enforce, default on).
 *
 * The switch is varp 427 (Options tab 261:7, settings_tab.plugin.kts). Its meaning comes from the cache: clientscript 1269
 * draws "Accept aid (currently off)" when the varp is 0 and "(currently on)" when it is 1 (BRANCH_IF_FALSE = jump when 0,
 * ScriptRunner).
 *
 * What needs it (RuneScape Wiki "Accept Aid", 2011 list): Tele-other Lumbridge / Falador / Camelot, every Tele Group spell,
 * Cure Other, Cure Group, Stat Restore Pot Share, Boost Potion Share, Heal Other, Vengeance Other, Vengeance Group, Heal Group,
 * assistance requests; OSRS Wiki "Energy Transfer": "The target's Accept Aid setting must be enabled."; OSRS Wiki "Hunter's
 * mix" / "Moonlight moth": shared mix and moth effects reach only players "who have Accept Aid toggled on".
 * Group spells silently skip a player with Accept Aid off (they only reach those who accept); single-target spells are refused.
 *
 * Messages: no 2011 or OSRS source quotes the refusal. [REFUSED] is Novite's line (Donors\Novite StealingCreationController),
 * [TELEOTHER_REFUSED] Void's (Donors\void TeleportOther.kt) - ADAPTED.
 *
 * Default ON: a varp that is 0 is not written to the save, so "off" and "never set" look the same. [applyDefault] switches it
 * on once per account (persisted [DEFAULT_APPLIED]); after that the player's own choice is kept.
 */
object AcceptAid {
    const val VARP = 427
    const val ON = 1

    const val REFUSED = "That player currently does not want your aid."
    const val TELEOTHER_REFUSED = "That player won't let you teleport them."

    val DEFAULT_APPLIED = AttributeKey<Boolean>(persistenceKey = "accept_aid_default_applied")

    fun accepts(player: Player): Boolean = player.getVarp(VARP) == ON

    /** True when [target] accepts aid; otherwise tells [caster] and returns false. */
    fun check(
        caster: Player,
        target: Player,
        refusal: String = REFUSED,
    ): Boolean {
        if (accepts(target)) return true
        caster.message(refusal)
        return false
    }

    /** Online players other than [exclude] within [radius] tiles of [center] (same plane) who accept aid. */
    fun nearbyAccepting(
        world: World,
        center: Tile,
        radius: Int,
        exclude: Player? = null,
    ): List<Player> {
        val result = mutableListOf<Player>()
        for (x in -radius..radius) {
            for (z in -radius..radius) {
                val tile = center.transform(x, z)
                val chunk = world.chunks.get(tile, createIfNeeded = false) ?: continue
                chunk.getEntities<Player>(tile, EntityType.PLAYER, EntityType.CLIENT).forEach { other ->
                    if (other !== exclude && other.isOnline && accepts(other) && other !in result) result.add(other)
                }
            }
        }
        return result
    }

    fun applyDefault(player: Player) {
        if (player.attr[DEFAULT_APPLIED] == true) return
        player.setVarp(VARP, ON)
        player.attr[DEFAULT_APPLIED] = true
    }
}
