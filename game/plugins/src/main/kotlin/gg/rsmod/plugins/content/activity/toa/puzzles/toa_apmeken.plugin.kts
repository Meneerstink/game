package gg.rsmod.plugins.content.activity.toa.puzzles

import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.content.activity.toa.*
import gg.rsmod.plugins.content.combat.PlayerAttackRules

ToaRooms.register(ToaRoomKey(ToaPath.APMEKEN, false), ::ApmekenRoom)
ToaIds.loc(45497)?.let { id -> on_obj_option(id, "Take") { (ToaPuzzles.of(player) as? ApmekenRoom)?.takeHammer(player) } }
ToaIds.loc(45498)?.let { id -> on_obj_option(id, "Take") { (ToaPuzzles.of(player) as? ApmekenRoom)?.takePotions(player) } }
listOf(45494, 45495).mapNotNull(ToaIds::loc).distinct().forEach { id ->
    on_obj_option(id, "Repair") { (ToaPuzzles.of(player) as? ApmekenRoom)?.repair(player, player.getInteractingGameObj()) }
}
ToaIds.item(ApmekenRoom.POTION)?.let { id ->
    on_item_option(id, "Pour") { (ToaPuzzles.of(player) as? ApmekenRoom)?.pour(player) }
    on_item_on_player(id) { (ToaPuzzles.of(player) as? ApmekenRoom)?.pour(player, player.getInteractingPlayer()) }
}
ApmekenPuzzle.Baboon.values().filter { it != ApmekenPuzzle.Baboon.SPECIAL }.mapNotNull { ToaIds.npc(it.osrs) }.distinct().forEach { id ->
    on_npc_combat(npc = id) { } //Room tick owns every attack/summon; no simultaneous generic auto-retaliation.
}
PlayerAttackRules.register { player, npc, hitType ->
    val room = npc.attr[ToaPuzzles.OWNER] as? ApmekenRoom
    val style = when (hitType) {
        HitType.MELEE, HitType.CRIT_MELEE -> CombatClass.MELEE
        HitType.RANGE, HitType.CRIT_RANGE -> CombatClass.RANGED
        HitType.MAGIC, HitType.CRIT_MAGIC -> CombatClass.MAGIC
        else -> null
    }
    if (room != null && style != null && room.eligible(player) && room.maximumHit(npc, style)) PlayerAttackRules.Rule.MAX_HIT else null
}
