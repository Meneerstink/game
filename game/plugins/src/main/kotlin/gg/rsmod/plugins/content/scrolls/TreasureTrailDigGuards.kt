package gg.rsmod.plugins.content.scrolls

import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.fs.def.NpcDef
import gg.rsmod.plugins.api.ext.*

/** Complete personal dig transaction, independent of unproven visual/selection constants.
 * A combat adapter must register a verified source identity before it can enter the step pool.
 */
object TreasureTrailDigGuards {
    private data class Installed(val world: World,val local: Int)
    private val installed=HashMap<Int,Installed>()
    private val sources=setOf(6587,6588,7307,7308,7309,7310)
    fun register(world: World, source: Int, local: Int): Boolean {
        val p=ClueGuardianProfiles.all.singleOrNull { it.source==source && source in sources } ?: return false
        val def=world.definitions.getNullable(NpcDef::class.java,local) ?: return false
        if(def.name!=p.name || def.combatLevel!=p.level || def.size!=p.size || !def.options.any { it.equals("Attack",true) } ||
            !ClueGuardianDropCatalogue.current.available(world,source))return false
        installed[source]=Installed(world,local)
        return true
    }
    fun available(world: World,row: TreasureTrailStepCatalogue.Step): Boolean = when {
        row.action==TreasureTrailStepCatalogue.Action.HOT_COLD ->
            TreasureTrailHotCold.current.available(world,row) { installed[it]?.world===world }
        row.action==TreasureTrailStepCatalogue.Action.DIG && row.challenge==TreasureTrailStepCatalogue.Challenge.GUARD ->
            !row.chooseGuard && row.guards.isNotEmpty() && row.guards.all { installed[it]?.world===world }
        else -> false
    }
    /** An elite alternative needs a caller-supplied sourced choice; no 50/50 probability is invented. */
    fun dig(player: Player, choose: (List<Int>) -> Int? = { it.singleOrNull() },
            spawn: (Player,List<Int>) -> List<Npc>? = ::spawn): Boolean {
        for(tier in ClueScrollTier.values()) {
            val step=ClueScrollManager.activeStep(player,tier) as? TreasureTrailSourceStep ?: continue
            val row=step.source
            val target=if(row.action==TreasureTrailStepCatalogue.Action.HOT_COLD) TreasureTrailHotCold.current.site(player,step) else null
            val guards=if(target!=null) target.guards else {
                if(row.action!=TreasureTrailStepCatalogue.Action.DIG || row.challenge!=TreasureTrailStepCatalogue.Challenge.GUARD ||
                    !row.at(player.tile) || row.guards.any { it !in sources })continue
                if(row.chooseGuard) listOf(choose(row.guards) ?: return true) else row.guards
            }
            when(TreasureTrailGuardians.attempt(player,step,guards,spawn=spawn)) {
                TreasureTrailGuardians.Result.READY -> TreasureTrailGuardians.dig(player,step)
                TreasureTrailGuardians.Result.FIGHTING -> TreasureTrailGuardians.session(player)?.actors?.forEach { if(!it.isDead())it.attack(player) }
                TreasureTrailGuardians.Result.UNAVAILABLE -> println("PROBLEM CLUE dig guardian assets/spawn unavailable at${player.tile}; clue retained")
                TreasureTrailGuardians.Result.UNHANDLED -> return false
            }
            return true
        }
        return false
    }
    /** Atomic placement: no partial Ancient trio remains after a failed later spawn. */
    private fun spawn(player: Player, ids: List<Int>): List<Npc>? {
        if(ids.isEmpty() || ids.any { installed[it]?.world!==player.world })return null
        val actors=ArrayList<Npc>()
        val used=HashSet<Tile>()
        for(source in ids) {
            val p=ClueGuardianProfiles.all.single { it.source==source }
            val tile=Direction.values().asSequence().filter { it!=Direction.NONE && player.world.collision.canStep(player.tile,it) }
                .map { player.tile.step(it) }.firstOrNull { tile ->
                    (0 until p.size).all { dx -> (0 until p.size).all { dz ->
                        val point=Tile(tile.x+dx,tile.z+dz,tile.height)
                        point !in used && point!=player.tile && !player.world.collision.isClipped(point)
                    } }
                }
            if(tile==null) { actors.forEach { player.world.remove(it) };return null }
            val npc=Npc(installed.getValue(source).local,tile,player.world)
            npc.respawns=false
            TreasureTrailAgents.claim(player,npc)
            if(!player.world.spawn(npc)) { actors.forEach { player.world.remove(it) };return null }
            actors+=npc
            for(dx in 0 until p.size)for(dz in 0 until p.size) used+=Tile(tile.x+dx,tile.z+dz,tile.height)
        }
        return actors
    }
}
