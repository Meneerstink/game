package gg.rsmod.plugins.content.scrolls

ClueNpcs.local(6586)?.let { sherlock ->
    // Pinned Wiki Sherlock map pin 2733,3413/radius5.
    spawn_npc(npc = sherlock, x = 2733, z = 3413, height = 0, walkRadius = 5)
    TreasureTrailStepCatalogue.current.rows.filter { it.action == TreasureTrailStepCatalogue.Action.SHERLOCK }.forEach {
        ClueScrollManager.registerStep(TreasureTrailSourceStep(it))
    }
    on_npc_option(sherlock, "talk-to") { TreasureTrailSherlockDialogue.talk(player, player.getInteractingNpc()) }
    on_any_item_on_npc(sherlock) {
        player.queue { chatNpc("That's nice, but.... I don't really need one of those.", npc = sherlock, wrap = true) }
    }
}
ClueItems.local(12128)?.let { challenge ->
    on_item_option(challenge, "check completion") { TreasureTrailSherlock.check(player) }
}
on_npc_killed { player, npc -> TreasureTrailSherlock.killed(player, npc) }
