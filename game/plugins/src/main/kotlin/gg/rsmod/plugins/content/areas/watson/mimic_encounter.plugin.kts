package gg.rsmod.plugins.content.areas.watson

import gg.rsmod.plugins.content.scrolls.ClueCasketRewards
import gg.rsmod.plugins.content.scrolls.ClueInterfaces

val mimicTimer = TimerKey()
on_world_init {
    world.timers[mimicTimer] = 1
    world.plugins.bindCanAttack { attacker,target -> MimicMinions.canAttack(attacker,target) }
}
on_timer(mimicTimer) { MimicEncounter.cycle(world); world.timers[mimicTimer] = 1 }
on_npc_option(Npcs.THE_MIMIC, "challenge") { MimicEncounter.challenge(player, player.getInteractingNpc()) }
on_npc_pre_death(Npcs.THE_MIMIC_14402) { MimicEncounter.victory(npc) }
MimicEncounter.assets.keyhole?.let { keyhole ->
    on_obj_option(keyhole, "exit") { MimicEncounter.leave(player) }
}
on_logout { MimicEncounter.leave(player, moveOutside = false) }
on_player_death { MimicEncounter.leave(player, moveOutside = false) }
on_login { MimicEncounter.recover(player) }

// One opening route for the casket, before/inside/after the encounter.
on_item_option(Items.MIMIC, "open") {
    val source = player.getInteractingItem()
    val slot = player.getInteractingItemSlot()
    when {
        MimicEncounter.inArena(player) -> player.message("It'd be bad taste to try opening that in here.")
        !MimicChallenge.defeated(source) -> player.message(StrangeCasket.OPEN_BEFORE_FIGHT)
        else -> {
            if (!ClueInterfaces.requireRewardScreen(player)) return@on_item_option
            val plan = MimicCaskets.rewards(player, source) ?: return@on_item_option
            val award = ClueCasketRewards.award(player, slot, source, plan) ?: return@on_item_option
            ClueInterfaces.rewards(player, award.rewards)
        }
    }
}
