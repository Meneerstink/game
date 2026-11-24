package gg.rsmod.plugins.content.skills.slayer

// Wiki Slayer bell / Molanisk; native wall loc 22545, creature 5751, ringing seq6083/sfx3522.
// The existing bulk kill hook keeps its droptable/Slayer rewards. Only the wall lifecycle is new.
on_item_option(Items.SLAYER_BELL, "Ring") { Molanisks.ring(player) }
on_npc_death(Npcs.MOLANISK) { Molanisks.died(npc) }
