package gg.rsmod.plugins.content.scrolls

on_world_init { ClueNpcs.install(world) }
ClueNpcs.local(7306)?.let { falo ->
    // Wiki source map square around 2689,3549, radius4; exactly one service NPC.
    spawn_npc(npc = falo, x = 2689, z = 3549, height = 0, walkRadius = 4)
    val source = TreasureTrailStepCatalogue.current.rows.single { it.action == TreasureTrailStepCatalogue.Action.FALO }
    ClueScrollManager.registerStep(TreasureTrailSourceStep(source))
    on_npc_option(falo, "talk-to") { TreasureTrailFaloDialogue.talk(player, player.getInteractingNpc()) }
    on_any_item_on_npc(falo) { TreasureTrailFaloDialogue.show(player, player.getInteractingNpc(), player.getInteractingItem()) }
}
