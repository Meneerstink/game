package gg.rsmod.plugins.content.areas.watson

import com.google.gson.JsonParser
import gg.rsmod.game.RspsPaths
import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.TileGraphic
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Projectile
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.game.model.timer.FROZEN_TIMER
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.*
import gg.rsmod.plugins.content.combat.strategy.MeleeCombatStrategy
import java.io.File
import kotlin.random.Random

/** Attack plumbing behind owner Q1's source gate. No invented chance/scatter/ballistics defaults. */
object MimicAttacks {
    data class Flight(val startHeight: Int,val endHeight: Int,val slope: Int,val steepness: Int,val releaseDelay: Int)
    data class Plan(val offsets: List<Pair<Int,Int>>,val flight: Flight) {
        init { require(offsets.size==6 && offsets.distinct().size==6);require(flight.releaseDelay>=0) }
        fun tiles(centre: Tile)=offsets.map { centre.transform(it.first,it.second) }
    }
    val plan: Plan? by lazy {
        val row=JsonParser().parse(File(RspsPaths.data("cfg/clue/mimic-encounter.json")).readText()).asJsonObject
        val offsets=row["candyLandingOffsets"]?.takeUnless { it.isJsonNull }?.asJsonArray ?: return@lazy null
        val flight=row["candyFlight"]?.takeUnless { it.isJsonNull }?.asJsonObject ?: return@lazy null
        Plan(offsets.map { it.asJsonArray.let { p -> p[0].asInt to p[1].asInt } },
            Flight(flight["startHeight"].asInt,flight["endHeight"].asInt,flight["slope"].asInt,
                flight["steepness"].asInt,flight["releaseDelay"].asInt))
    }
    enum class Choice { MELEE,CANDY,FORWARD }
    /** Caller supplies measured probabilities. Their sum is validated; no guessed equal weighting. */
    fun choose(candy: Double,step: Double,random: Random): Choice {
        require(candy.isFinite() && step.isFinite() && candy>=0 && step>=0 && candy+step<=1.0)
        val roll=random.nextDouble()
        return when { roll<candy->Choice.CANDY;roll<candy+step->Choice.FORWARD;else->Choice.MELEE }
    }
    private val sweets=mapOf(1670 to 3012,1671 to 3013,1672 to 3014,1673 to 3015,
        1674 to 3016,1675 to 3017,1676 to 3018,1677 to 3019) // Existing ledger-owned source import.
    fun available(world: World): Boolean = plan!=null && MimicMinions.available(world) &&
        world.definitions.getNullable(gg.rsmod.game.fs.def.AnimDef::class.java,15405)!=null &&
        sweets.values.all { world.definitions.getNullable(gg.rsmod.game.fs.def.SpotAnimDef::class.java,it)!=null }

    fun candy(session: MimicEncounter.Session,assets: MimicEncounter.Assets,pattern: Plan): Boolean {
        val boss=session.boss ?: return false
        val ticks=assets.impactDelay ?: return false
        if(ticks*30<=pattern.flight.releaseDelay)return false
        val centre=Tile(session.player.tile)
        return MimicCandies.launch(session,pattern.tiles(centre),ticks,10,
            release={ candies ->
                boss.animate(15405)
                val start=Tile(boss.tile.x+boss.getSize()/2,boss.tile.z+boss.getSize()/2,boss.tile.height)
                candies.forEach { candy ->
                    val f=pattern.flight
                    boss.world.spawn(Projectile.Builder().setStart(start).setTarget(candy.tile)
                        .setGfx(sweets.getValue(candy.colour.sourceSpot)).setHeights(f.startHeight,f.endHeight)
                        .setSlope(f.slope,f.steepness).setTimes(f.releaseDelay,ticks*30).build())
                }
            },impact={ candy -> boss.world.spawn(TileGraphic(candy.tile,sweets.getValue(candy.colour.impactSpot),0)) },
            spawn={ colour,tile -> MimicMinions.spawn(session,colour,tile) })
    }
    /** One actual body step, respecting freeze and every footprint tile/edge. */
    fun forward(session: MimicEncounter.Session): Boolean {
        val boss=session.boss ?: return false
        if(session.finished || session.won || boss.isDead() || !boss.isSpawned() || boss.timers.has(FROZEN_TIMER))return false
        val centre=Tile(boss.tile.x+boss.getSize()/2,boss.tile.z+boss.getSize()/2,boss.tile.height)
        val direction=Direction.between(centre,session.player.tile)
        if(direction==Direction.NONE)return false
        val next=boss.tile.step(direction)
        val size=boss.getSize()
        if((0 until size).any { dx -> (0 until size).any { dz -> !session.map.area.contains(Tile(next.x+dx,next.z+dz,next.height)) } })return false
        if(!boss.world.collision.canBodyStep(boss.tile,size,size,direction,false))return false
        boss.moveTo(next)
        return true
    }
    suspend fun combat(task: QueueTask) {
        val npc=task.npc
        val session=MimicEncounter.session(npc) ?: return
        val assets=MimicEncounter.assets
        val pattern=plan ?: return
        if(!assets.ready || !available(npc.world))return
        val player=session.player
        while(task.awaitAttackDelay(npc) && !session.finished && !session.won && session.boss===npc &&
            npc.getCombatTarget()===player && player.isOnline && !player.isDead() && session.map.area.contains(player.tile) &&
            npc.canEngageCombat(player)) {
            npc.facePawn(player)
            when(choose(assets.candyChance!!,assets.stepChance!!,Random.Default)) {
                Choice.CANDY -> candy(session,assets,pattern)
                Choice.FORWARD -> forward(session)
                Choice.MELEE -> {
                    if(!npc.canAttackMelee(task,player,true)) { task.wait(1);continue }
                    if(session.finished || session.won || session.boss!==npc || !player.isOnline || player.isDead())break
                    MeleeCombatStrategy.attack(npc,player)
                }
            }
            npc.postAttackLogic(player)
            task.wait(npc.combatDef.attackSpeed)
        }
        npc.resetFacePawn()
        npc.removeCombatTarget()
    }
}
