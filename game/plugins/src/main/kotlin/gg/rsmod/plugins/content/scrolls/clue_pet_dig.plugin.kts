package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.fs.def.NpcDef

// Bind only options actually installed by Claude's two-definition cache patch.
// The existing pet plugin owns Talk-to, Store and Pick-up; it has no Dig binding.
listOf(14631,14844).forEach { id ->
    if(world.definitions.getNullable(NpcDef::class.java,id)?.options?.any { it.equals("Dig",true) }==true) {
        on_npc_option(npc=id,option="dig") { CluePetDig.dig(player,player.getInteractingNpc()) }
    }
}
