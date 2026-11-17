package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.plugins.content.combat.attack.NpcAttacks

// Source numbers and fight lifecycle are ready; missing visual evidence cannot enable placeholder combat.
ClueGuardianProfiles.all.filter { it.source in setOf(6587,6588,7307,7308,7309,7310) }.forEach { p ->
    val id=ClueNpcs.local(p.source)
    val visual=ClueGuardianCombat.visuals(world,p.source)
    if(id!=null && visual!=null && TreasureTrailDigGuards.register(world,p.source,id)) {
        set_combat_def(id,ClueGuardianProfiles.definition(p,visual.attack,visual.block,visual.death))
        NpcAttacks.register(ClueGuardianCombat.row(p,id,visual))
        ClueGuardianCombat.hooks(p)
        world.definitions.get(NpcDef::class.java,id).examine=p.examine
        check(ClueGuardianDropCatalogue.register(world,p.source,id))
        on_npc_pre_death(id) { TreasureTrailGuardians.killed(npc) }
        on_npc_death(id) {
            TreasureTrailAgents.dropOwner(npc)?.let { owner -> ClueGuardianDropCatalogue.drop(world,owner,id,npc.tile) }
        }
    }
}
TreasureTrailStepCatalogue.current.rows.filter { TreasureTrailDigGuards.available(world,it) }.forEach {
    ClueScrollManager.registerStep(TreasureTrailSourceStep(it))
}
on_world_init { world.plugins.bindCanAttack { attacker,target -> ClueGuardianCombat.canHitFlying(attacker,target) } }
