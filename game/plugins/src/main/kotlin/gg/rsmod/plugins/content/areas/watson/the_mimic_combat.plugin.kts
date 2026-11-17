package gg.rsmod.plugins.content.areas.watson

import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.plugins.content.combat.attack.NpcAttacks
import gg.rsmod.plugins.content.scrolls.ClueGuardianCombat
import gg.rsmod.plugins.content.scrolls.ClueGuardianProfiles

// The regression test builds this same definition, including the DSL HP conversion.
set_combat_def(Npcs.THE_MIMIC_14402, MimicCombat.definition())
on_npc_combat(Npcs.THE_MIMIC_14402) { npc.queue { MimicAttacks.combat(this) } }

MimicMinions.profiles.forEach { p ->
    val id=p.local
    val visual=ClueGuardianCombat.visuals(world,p.source)
    if(id!=null && visual!=null) {
        set_combat_def(id,ClueGuardianProfiles.definition(p,visual.attack,visual.block,visual.death))
        NpcAttacks.register(ClueGuardianCombat.row(p,id,visual))
        world.definitions.get(NpcDef::class.java,id).examine=p.examine
        // No drop table, kill-count award or automatic respawn: the next candy attack replaces dead minions.
    }
}
