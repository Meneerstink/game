package gg.rsmod.plugins.content.combat.attack

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.timer.TimerKey

/**
 * Frost dragon mechanics on top of its data-driven attacks (NpcAttacks, Void asgarnian_ice_resource_dungeon
 * frost_dragon sections: melee, breath swipe, ranged dragonfire, ice arrows, magic, orb). Ported from Void
 * `content/area/asgarnia/asgarnian_ice_dungeon/FrostDragons.kt`.
 *
 * - Style state: a frost dragon that fired ice arrows keeps to range, one that cast magic keeps to magic
 *   (`frost_range` / `frost_magic`); both are open until the first of them is used, and reset on death.
 * - `no_frost_orb` is Void's exact check, `hasClock("orb_protection")`. That clock is only started by the orb
 *   attack itself, so in Void the orb is never selected; ported unchanged (SOURCE_CONFLICT with its name).
 */
val FROST_STYLE = AttributeKey<String>()
val ORB_PROTECTION = TimerKey()

NpcAttacks.condition("frost_magic") { npc, _ -> (npc.attr[FROST_STYLE] ?: "magic") == "magic" }
NpcAttacks.condition("frost_range") { npc, _ -> (npc.attr[FROST_STYLE] ?: "range") == "range" }
NpcAttacks.condition("no_frost_orb") { npc, _ -> npc.timers.has(ORB_PROTECTION) }

NpcAttacks.onAttack("frost_dragon", "ice_arrows") { npc, _ -> npc.attr[FROST_STYLE] = "range" }
NpcAttacks.onAttack("frost_dragon", "magic") { npc, _ -> npc.attr[FROST_STYLE] = "magic" }
NpcAttacks.onAttack("frost_dragon", "orb") { npc, _ -> npc.timers[ORB_PROTECTION] = 8 }

on_npc_death(Npcs.FROST_DRAGON) { npc.attr.remove(FROST_STYLE) }
