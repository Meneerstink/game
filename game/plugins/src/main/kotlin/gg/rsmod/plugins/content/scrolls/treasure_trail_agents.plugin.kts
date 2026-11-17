package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.plugins.content.drops.DropTableFactory

TreasureTrailAgents.profiles.forEach { profile ->
    TreasureTrailAgents.actor(world, profile.source)?.let { id ->
        set_combat_def(id, TreasureTrailAgents.definition(profile))
        world.definitions.get(NpcDef::class.java, id).examine = profile.examine
        // Double agent Wiki: bones always. Owner N424/N561 removes the Wilderness looting bag.
        val drops = DropTableFactory.build { guaranteed { obj(Items.BONES) } }
        DropTableFactory.register(drops, id)
        on_npc_pre_death(id) { TreasureTrailAgents.killed(npc) }
        on_npc_death(id) {
            TreasureTrailAgents.dropOwner(npc)?.let { owner -> DropTableFactory.getDrop(world, owner, id, npc.tile) }
        }
    }
}
TreasureTrailStepCatalogue.current.rows.filter { TreasureTrailAgents.available(world, it) }.forEach {
    ClueScrollManager.registerStep(TreasureTrailSourceStep(it))
}
val guardianCycle = TimerKey()
on_world_init {
    world.plugins.bindCanAttack { attacker, target -> TreasureTrailAgents.canAttack(attacker, target) }
    world.timers[guardianCycle] = 1
}
on_timer(guardianCycle) { TreasureTrailGuardians.cycle(); world.timers[guardianCycle] = 1 }
on_logout { TreasureTrailGuardians.remove(player) }
on_player_death { TreasureTrailGuardians.remove(player) }
