package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.content.mechanics.pvp.BeginnerProtection

on_world_init {
    WildernessHotspot.start(world)
    // Owner 2026-09-19: the OSRS Deadman breaches (mechanics/pvp/breach) replace this server's own revenant "Wilderness Breach";
    // WildernessBreach is no longer started so the two never run side by side.
}

// Audit finding 13 (R14.26): a beginner-protected player must not participate in a Breach at
// all, in either direction - not just be excluded from the reward roll (see
// WildernessBreach.BREACH_NPC_ATTR's doc comment for why the earlier participant-list filter
// alone left the spawned npcs fully attackable for them). Only messages when the player is
// the attacker (they clicked Attack themselves) - an aggressive Breach npc's own AI retries
// its aggro check every few ticks while uncommitted to combat, and it would spam the same
// line on every retry if this also messaged for the npc-initiates-it direction.
can_attack { attacker, target ->
    val blockedNpc = (target as? Npc)?.takeIf { WildernessBreach.isBreachNpc(it) } ?: (attacker as? Npc)?.takeIf { WildernessBreach.isBreachNpc(it) }
    if (blockedNpc == null) {
        true
    } else {
        val self = attacker as? Player ?: target as? Player
        if (self != null && BeginnerProtection.isProtected(self)) {
            if (attacker is Player && world.plugins.notifyAttackRefusal) {
                self.filterableMessage("You can't take part in a Wilderness Breach while under Beginner Protection.")
            }
            false
        } else {
            true
        }
    }
}

// Audit D-16: no "+15% reward/XP" claim - no live system pays that bonus. The hotspot is the daily-objective location.
on_command("hotspot") {
    player.filterableMessage("Current Wilderness hotspot (daily objective): ${WildernessHotspot.current.label}.")
}
