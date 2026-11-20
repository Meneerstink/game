package gg.rsmod.plugins.content.areas.watson

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.hit

/** The known candy mechanics, independent of the still-unproven attack choice/landing generator.
 * The caller must supply six actual landing tiles and their verified visual callbacks.
 * Wiki The Mimic: three purple plus one red/blue/green; colour summons the corresponding third-age actor.
 */
object MimicCandies {
    enum class Colour(val sourceSpot: Int, val impactSpot: Int, val minion: Int?) {
        PURPLE(1670,1674,null), RED(1672,1676,Npcs.THIRD_AGE_WARRIOR),
        BLUE(1673,1677,Npcs.THIRD_AGE_MAGE), GREEN(1671,1675,Npcs.THIRD_AGE_RANGER),
    }
    data class Candy(val colour: Colour, val tile: Tile)
    private class Volley(val session: MimicEncounter.Session, val boss: Npc, val due: Int,
                         val candies: List<Candy>, val impact: (Candy) -> Unit,
                         val spawn: (Colour,Tile) -> Npc?, val damage: Int)
    private val pending=ArrayList<Volley>()
    private val colours=listOf(Colour.PURPLE,Colour.PURPLE,Colour.PURPLE,Colour.RED,Colour.BLUE,Colour.GREEN)

    private fun current(session: MimicEncounter.Session, boss: Npc): Boolean =
        !session.finished && !session.won && session.boss===boss && boss.isSpawned() && !boss.isDead() &&
            boss.world===session.player.world && session.player.tile.height==boss.tile.height &&
            session.player.isOnline && !session.player.isDead() && session.map.area.contains(session.player.tile) &&
            session.player.inventory.any { it===session.casket } && MimicChallenge.active(session.casket) &&
            session.casket.getAttr(gg.rsmod.game.model.item.ItemAttribute.MIMIC_ATTEMPT_SERIAL)==session.token &&
            MimicEncounter.session(session.player)===session

    /** No hidden radius or attack probability is guessed. Multiple in-flight volleys are allowed. */
    fun launch(session: MimicEncounter.Session, tiles: List<Tile>, delayTicks: Int, damage: Int,
               release: (List<Candy>) -> Unit, impact: (Candy) -> Unit, spawn: (Colour,Tile) -> Npc?): Boolean {
        val boss=session.boss ?: return false
        if(delayTicks<=0 || damage<0 || !current(session,boss) || tiles.size!=6 || tiles.distinct().size!=6 ||
            tiles.any { it.height!=boss.tile.height || !session.map.area.contains(it) }) return false
        val candies=colours.zip(tiles).map { (colour,tile)->Candy(colour,tile) }
        // Publish only after validation; the same exact boss/session/token owns every delayed effect.
        release(candies)
        if(!current(session,boss))return false
        pending+=Volley(session,boss,boss.world.currentCycle+delayTicks,candies,impact,spawn,damage)
        return true
    }

    fun cycle(world: World) {
        val due=pending.filter { it.boss.world===world && (!current(it.session,it.boss) || world.currentCycle>=it.due) }
        // Remove before callbacks, including reentrant world ticks or death/exit callbacks.
        pending.removeAll(due.toSet())
        due.forEach { volley ->
            val s=volley.session
            if(!current(s,volley.boss) || world.currentCycle<volley.due)return@forEach
            for (candy in volley.candies) {
                if (!current(s,volley.boss)) break
                volley.impact(candy)
            }
            if(!current(s,volley.boss))return@forEach
            if(volley.candies.any { it.tile==s.player.tile } && volley.damage>0) s.player.hit(volley.damage, source = volley.boss)
            if(!current(s,volley.boss))return@forEach
            s.minions.removeAll { it.isDead() || !it.isSpawned() }
            for(candy in volley.candies) {
                if(!current(s,volley.boss))break
                val id=candy.colour.minion ?: continue
                if(s.minions.any { it.id==id && !it.isDead() && it.isSpawned() })continue
                val npc=volley.spawn(candy.colour,candy.tile) ?: continue
                if(npc.id==id && npc.world===world && npc.isSpawned() && !npc.isDead() && npc.tile==candy.tile && current(s,volley.boss))
                    { s.minions+=npc; npc.attack(s.player) }
                else if(npc.world===world && npc.isSpawned()) world.remove(npc)
            }
        }
    }
    fun cancel(session: MimicEncounter.Session) { pending.removeAll { it.session===session } }
}
