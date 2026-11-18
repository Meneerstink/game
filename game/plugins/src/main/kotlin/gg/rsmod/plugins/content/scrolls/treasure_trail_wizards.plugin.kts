package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.plugins.content.combat.attack.NpcAttacks

TreasureTrailWizards.installConditions()
TreasureTrailWizards.profiles.forEach { p ->
    TreasureTrailWizards.actor(world,p.source)?.let { id ->
        set_combat_def(id,TreasureTrailWizards.definition(p))
        NpcAttacks.register(TreasureTrailWizards.attackRow(p))
        world.definitions.get(NpcDef::class.java,id).examine=p.examine
        check(ClueGuardianDropCatalogue.register(world,p.source,id))
        on_npc_pre_death(id) { TreasureTrailGuardians.killed(npc) }
        on_npc_death(id) { TreasureTrailAgents.dropOwner(npc)?.let { owner -> ClueGuardianDropCatalogue.drop(world,owner,id,npc.tile) } }
    }
}
TreasureTrailStepCatalogue.current.rows.filter { TreasureTrailWizards.available(world,it) }.forEach {
    ClueScrollManager.registerStep(TreasureTrailSourceStep(it))
}
