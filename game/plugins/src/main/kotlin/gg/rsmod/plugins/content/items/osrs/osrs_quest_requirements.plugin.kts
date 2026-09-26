package gg.rsmod.plugins.content.items.osrs

// OSRS quest requirements of ported OSRS items (OsrsQuestRequirements). A global hook, so it never collides with the
// per-item equip requirements other plugins bind.
can_equip_any_item { player, item ->
    val refusal = OsrsQuestRequirements.wearRefusal(player, item)
    if (refusal != null) player.message(refusal)
    refusal == null
}
