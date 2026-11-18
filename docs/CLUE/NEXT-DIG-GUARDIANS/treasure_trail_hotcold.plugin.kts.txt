package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.fs.def.NpcDef

// The coordinate adapter registers a hot/cold step only when every possible guardian can really fight.
ClueItems.local(19939)?.let { device ->
    val def=world.definitions.getNullable(ItemDef::class.java,device)
    if(def?.name=="Strange device" && def.inventoryMenu.any { it.equals("Feel",true) }) {
        on_item_option(device,"feel") { TreasureTrailHotCold.current.feel(player,player.getInteractingItem()) }
    }
}
on_world_init {
    world.plugins.bindNpcOverride { player,npc,option -> TreasureTrailHotColdDialogue.talk(player,npc,option) }
    TreasureTrailStepCatalogue.current.rows.filter { it.key=="master:jorral" }.flatMap { it.existingNpcs }.forEach { id ->
        world.definitions.getNullable(NpcDef::class.java,id)?.takeIf { it.name=="Jorral" }?.examine="A sensible looking man."
    }
}
