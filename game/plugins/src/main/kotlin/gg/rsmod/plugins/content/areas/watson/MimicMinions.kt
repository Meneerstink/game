package gg.rsmod.plugins.content.areas.watson

import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.attr.DAMAGE_CREDIT_ATTR
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.scrolls.ClueGuardianProfiles
import gg.rsmod.plugins.content.scrolls.ClueGuardianCombat

/** Source minions, one living actor per colour; candy impacts own respawns. */
object MimicMinions {
    val profiles get()=ClueGuardianProfiles.all.filter { it.source in setOf(8635,8636,8637) }
    fun available(world: World): Boolean = profiles.size==3 && profiles.all { p ->
        p.local?.let { id -> world.definitions.getNullable(NpcDef::class.java,id)?.let {
            it.name==p.name && it.combatLevel==p.level && it.size==p.size && it.options.any { it.equals("Attack",true) }
        } }==true && ClueGuardianCombat.visuals(world,p.source)!=null
    }
    fun spawn(session: MimicEncounter.Session,colour: MimicCandies.Colour,tile: Tile): Npc? {
        val id=colour.minion ?: return null
        val p=profiles.singleOrNull { it.local==id } ?: return null
        if(session.finished || session.won || !session.map.area.contains(tile) || tile.height!=session.player.tile.height ||
            session.player.world.collision.isClipped(tile))return null
        val npc=Npc(id,tile,session.player.world)
        npc.respawns=false
        // The warrior can be blocked by the boss/other minions; no player-blocking custom rule.
        if(!npc.world.spawn(npc))return null
        return npc
    }
    /** Delayed candy publication is followed by the ordinary personal-combat permission check. */
    fun canAttack(attacker: Pawn,target: Pawn): Boolean {
        val actor=(attacker as? Npc)?.let { MimicEncounter.session(it) }
        if(actor!=null)return target===actor.player && !actor.finished && !actor.won && !actor.player.isDead()
        val defended=(target as? Npc)?.let { MimicEncounter.session(it) } ?: return true
        val credited=attacker as? Player ?: attacker.attr[DAMAGE_CREDIT_ATTR]?.get()
        return credited===defended.player && !defended.finished && !defended.won
    }
}
