package gg.rsmod.plugins.content.areas.wilderness

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.combat.WeaponStyle
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.plugins.content.combat.*
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.formula.RangedCombatFormula
import gg.rsmod.plugins.content.combat.strategy.RangedCombatStrategy
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.plugins.content.drops.global.Rare
import java.util.concurrent.TimeUnit

/**
 * Bork (Chaos Tunnels, once per day). Entering the portal at 3142,5545 while the daily cooldown is
 * clear moves the player into Bork's cavern and spawns Bork with a Dagon'hai elite. At 60% health
 * Bork shouts "Come to my aid, brothers!" and summons 1-3 Ork legions. On Bork's death the Dagon'hai
 * elite teleports away, the cavern starts collapsing (falling rocks every 20 seconds) and the
 * player is pushed out after five hits. Drops: big bones, 2,000-10,000 coins, 5 blue / 7 crimson /
 * 2 green charms, uncut gems (extra with a Ring of wealth) and the rare drop table.
 *
 * Sources: Void donor Bork.kt/HuntForSurok.kt and chaos_tunnels data (ids), Novite Bork/BorkCombat.
 */
val BORK_COOLDOWN = AttributeKey<Long>(persistenceKey = "bork_cooldown_epoch_day")
val BORK_KILLS = AttributeKey<Int>(persistenceKey = "bork_kill_count")
val BORK_LEGION_SUMMONED = AttributeKey<Boolean>()
val BORK_LEGIONS = AttributeKey<MutableList<Npc>>()
val BORK_ELITE = AttributeKey<java.lang.ref.WeakReference<Npc>>()
val BORK_OWNER = AttributeKey<java.lang.ref.WeakReference<Player>>()
val CAVERN_COLLAPSE = AttributeKey<Int>()

val PORTAL_TILE = Tile(3142, 5545)
val CAVERN_ENTRY = Tile(3107, 5537)
val BORK_SPAWN = Tile(3095, 5533)
val ELITE_SPAWN = Tile(3107, 5547)
val CAVERN_X = 3072..3135
val CAVERN_Z = 5504..5567
val LEGION_X = 3093..3106
val LEGION_Z = 5531..5540

val ANIM_BORK_SUMMON = 8757
val ANIM_LEGION_SPAWN = 8765
val ANIM_LEGION_RANGED = 8751
val ANIM_ELITE_CAST = 1084
val GFX_MINION_AID = 1551
val GFX_MINION_SPAWN = 1552
val GFX_LEGION_SPAWN = 1314
val PROJ_LEGION_SWORD = 1550
val GFX_CURSE_IMPACT = 110
val DAGONHAI_ELITE = 7137
val COLLAPSE_TIMER = TimerKey()

val table = DropTableFactory

fun inCavern(tile: Tile) = tile.height == 0 && tile.x in CAVERN_X && tile.z in CAVERN_Z

fun currentDay(): Long = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis())

on_obj_option(obj = Objs.PORTAL_28779, option = "enter") {
    val obj = player.getInteractingGameObj()
    if (obj.tile != PORTAL_TILE) {
        // The other 108 Chaos Tunnels portals share this id; their pairs are sourced table data.
        gg.rsmod.plugins.content.mechanics.objteleports.ObjectTeleports.fallback(player)
        return@on_obj_option
    }
    val day = currentDay()
    if ((player.attr[BORK_COOLDOWN] ?: -1L) >= day) {
        player.message("The portal appears to have stopped working for now.")
        val hours = 24 - TimeUnit.MILLISECONDS.toHours(System.currentTimeMillis() % TimeUnit.DAYS.toMillis(1))
        player.message("Perhaps you should return in ${if (hours <= 0L) "a little while" else "$hours hour${if (hours == 1L) "" else "s"}"}?")
        return@on_obj_option
    }
    if (world.npcs.any { it.id == Npcs.BORK_7134 && inCavern(it.tile) && !it.isDead() }) {
        player.message("Someone else is already fighting Bork. Try again in a moment.")
        return@on_obj_option
    }
    player.queue {
        player.graphic(GFX_CURSE_IMPACT)
        player.lock()
        wait(2)
        player.moveTo(CAVERN_ENTRY)
        val elite = Npc(DAGONHAI_ELITE, Tile(ELITE_SPAWN), world)
        elite.respawns = false
        elite.walkRadius = 0
        world.spawn(elite)
        val bork = Npc(Npcs.BORK_7134, Tile(BORK_SPAWN), world)
        bork.respawns = false
        bork.walkRadius = 5
        bork.attr[BORK_OWNER] = java.lang.ref.WeakReference(player)
        bork.attr[BORK_ELITE] = java.lang.ref.WeakReference(elite)
        world.spawn(bork)
        elite.facePawn(player)
        wait(1)
        player.unlock()
        val name = player.username
        chatNpc("Our Lord Zamorak has power over life and death, $name! He has seen fit to resurrect Bork", "to continue his great work... and now you will fall before him!", npc = DAGONHAI_ELITE, facialExpression = FacialExpression.ANGRY)
        bork.attack(player)
        elite.attack(player)
    }
}

on_obj_option(obj = Objs.PORTAL_29537, option = "enter") {
    leaveCavern(player)
}

fun leaveCavern(player: Player) {
    player.timers.remove(COLLAPSE_TIMER)
    player.attr.remove(CAVERN_COLLAPSE)
    player.moveTo(PORTAL_TILE)
}

/**
 * Cleans up Bork, his legions and the elite when the challenger leaves without finishing.
 */
val CAVERN_WATCH = TimerKey()

on_world_init {
    world.timers[CAVERN_WATCH] = 10
}

on_timer(CAVERN_WATCH) {
    world.npcs.forEach { npc ->
        if (npc.id == Npcs.BORK_7134 && !npc.isDead()) {
            val owner = npc.attr[BORK_OWNER]?.get()
            if (owner == null || !owner.isOnline || !inCavern(owner.tile)) {
                despawnEncounter(npc)
            }
        }
    }
    world.timers[CAVERN_WATCH] = 10
}

fun despawnEncounter(bork: Npc) {
    bork.attr[BORK_LEGIONS]?.forEach { if (it.isSpawned()) world.remove(it) }
    bork.attr[BORK_ELITE]?.get()?.let { if (it.isSpawned()) world.remove(it) }
    if (bork.isSpawned()) world.remove(bork)
}

on_npc_pre_death(Npcs.BORK_7134) {
    val bork = npc
    bork.attr[BORK_LEGIONS]?.forEach { if (it.isSpawned()) world.remove(it) }
    val killer = bork.attr[BORK_OWNER]?.get() ?: (bork.damageMap.getMostDamage() as? Player)
    val elite = bork.attr[BORK_ELITE]?.get()
    table.getDrop(world, killer ?: return@on_npc_pre_death, bork.id, bork.tile)
    killer.attr[BORK_COOLDOWN] = currentDay()
    killer.attr[BORK_KILLS] = (killer.attr[BORK_KILLS] ?: 0) + 1
    killer.addXp(Skills.SLAYER, 1500.0)
    killer.queue {
        wait(2)
        if (elite != null && elite.isSpawned()) {
            elite.stopMovement()
            elite.removeCombatTarget()
            elite.forceChat("Zamorak! Avenge me!")
            wait(2)
            elite.animate(Anims.MODERN_TELEPORT_START)
            elite.graphic(Gfx.MODERN_TELEPORT_START)
            wait(3)
            world.remove(elite)
        }
        chatPlayer("That monk - he called to Zamorak for revenge!", facialExpression = FacialExpression.ANGRY)
        killer.message("Something is shaking the whole cavern! You should get out of here quick!")
        chatPlayer("What th-? This power! It must be Zamorak! I can't fight something this strong!", "I'd better loot what I can and get out of here!", facialExpression = FacialExpression.AFRAID)
        killer.attr[CAVERN_COLLAPSE] = 0
        killer.timers[COLLAPSE_TIMER] = 13
    }
}

on_timer(COLLAPSE_TIMER) {
    if (!inCavern(player.tile)) {
        player.attr.remove(CAVERN_COLLAPSE)
        return@on_timer
    }
    val count = (player.attr[CAVERN_COLLAPSE] ?: 0) + 1
    player.attr[CAVERN_COLLAPSE] = count
    if (count >= 5) {
        player.message("You quickly make your escape as the cavern collapses behind you!")
        leaveCavern(player)
        return@on_timer
    }
    player.hit(20, HitType.REGULAR_HIT)
    player.message("You are hit by falling rocks! Look out!")
    player.timers[COLLAPSE_TIMER] = 33
}

on_logout {
    player.timers.remove(COLLAPSE_TIMER)
}

on_login {
    if (inCavern(player.tile)) {
        player.moveTo(PORTAL_TILE)
    }
}

/* ------------------------------------------------------------------------------------------
 * Drops and definitions
 * ---------------------------------------------------------------------------------------- */

val borkDrops = table.build {
    guaranteed {
        obj(Items.BIG_BONES)
        obj(Items.COINS_995, quantityRange = 2000..10000)
        obj(Items.BLUE_CHARM, quantity = 5)
        obj(Items.CRIMSON_CHARM, quantity = 7)
        obj(Items.GREEN_CHARM, quantity = 2)
        obj(Items.UNCUT_RUBY)
        obj(Items.UNCUT_EMERALD)
        obj(Items.UNCUT_SAPPHIRE)
    }
    main {
        total(512)
        table(Rare.rareTable, slots = 1)
        nothing(511)
    }
}
table.register(borkDrops, Npcs.BORK_7134)

set_combat_def(npc = Npcs.BORK_7134) {
    configs {
        attackSpeed = 8
        attackStyle = StyleType.CRUSH
        respawnDelay = 0
    }
    stats {
        hitpoints = 3000
        attack = 195
        strength = 245
        defence = 195
        magic = 1
        ranged = 1
    }
    anims {
        attack = 8754
        block = 8755
        death = 8756
    }
    aggro {
        radius = 12
    }
}

set_combat_def(npc = Npcs.ORK_LEGION) {
    configs {
        attackSpeed = 4
        attackStyle = StyleType.CRUSH
        respawnDelay = 0
    }
    stats {
        hitpoints = 500
        attack = 60
        strength = 60
        defence = 102
        magic = 40
        ranged = 100
    }
    anims {
        attack = 8760
        block = 8762
        death = 8761
    }
    aggro {
        radius = 12
    }
}

set_combat_def(npc = DAGONHAI_ELITE) {
    configs {
        attackSpeed = 36
        attackStyle = StyleType.MAGIC
        respawnDelay = 0
    }
    stats {
        hitpoints = 1000
        attack = 1
        strength = 1
        defence = 100
        magic = 120
        ranged = 1
    }
    anims {
        attack = 1084
        block = 424
        death = 836
    }
    aggro {
        radius = 12
    }
}

/* ------------------------------------------------------------------------------------------
 * Combat
 * ---------------------------------------------------------------------------------------- */

on_npc_combat(npc = Npcs.BORK_7134) {
    npc.queue {
        val bork = npc
        var target = bork.getCombatTarget() ?: return@queue
        while (bork.canEngageCombat(target) && bork.isAttackDelayReady()) {
            bork.facePawn(target)
            if (bork.getCurrentLifepoints() <= bork.getMaximumLifepoints() * 0.6 && bork.attr[BORK_LEGION_SUMMONED] != true) {
                bork.attr[BORK_LEGION_SUMMONED] = true
                summonLegions(bork, target as? Player)
                wait(9)
            }
            if (bork.moveToAttackRange(this, target, distance = 1, projectile = false)) {
                bork.prepareAttack(CombatClass.MELEE, StyleType.CRUSH, WeaponStyle.AGGRESSIVE)
                bork.animate(bork.combatDef.attackAnimation)
                val land = MeleeCombatFormula.getAccuracy(bork, target) >= world.randomDouble()
                bork.dealHit(target = target, maxHit = 25.4, landHit = land, delay = 1, hitType = HitType.MELEE)
            }
            bork.postAttackLogic(target)
            wait(bork.combatDef.attackSpeed)
            target = bork.getCombatTarget() ?: break
        }
        bork.resetFacePawn()
        bork.removeCombatTarget()
    }
}

suspend fun QueueTask.summonLegions(bork: Npc, player: Player?) {
    bork.forceChat("Come to my aid, brothers!")
    bork.animate(ANIM_BORK_SUMMON)
    bork.graphic(GFX_MINION_AID)
    player?.message("Bork strikes the ground with his axe.")
    wait(5)
    val legions = ArrayList<Npc>()
    repeat(1 + world.random(2)) {
        val tile = Tile(world.random(LEGION_X), world.random(LEGION_Z), 0)
        if (world.collision.isClipped(tile)) return@repeat
        world.spawn(gg.rsmod.game.model.TileGraphic(tile, GFX_MINION_SPAWN, 0))
        val legion = Npc(Npcs.ORK_LEGION, tile, world)
        legion.respawns = false
        legion.walkRadius = 6
        if (world.spawn(legion)) {
            legions.add(legion)
            legion.animate(ANIM_LEGION_SPAWN)
            legion.graphic(GFX_LEGION_SPAWN)
            legion.forceChat(if (world.random(1) == 0) "Die, human!" else "For Bork!")
            if (player != null) legion.attack(player)
        }
    }
    bork.attr[BORK_LEGIONS] = legions
    bork.forceChat("Destroy the intruder, my legions!")
}

on_npc_combat(npc = Npcs.ORK_LEGION) {
    npc.queue {
        val legion = npc
        var target = legion.getCombatTarget() ?: return@queue
        var cry = 0
        while (legion.canEngageCombat(target) && legion.isAttackDelayReady()) {
            legion.facePawn(target)
            if (++cry % 6 == 0) {
                legion.forceChat(listOf("We are the collective!", "Form a triangle!!", "Steady lads!", "Hup! 2... 3... 4!!", "To the attack!", "Resistance is futile!").random())
            }
            val distance = legion.getFrontFacingTile(target).getDistance(target.tile)
            if (distance <= 1) {
                legion.prepareAttack(CombatClass.MELEE, StyleType.CRUSH, WeaponStyle.AGGRESSIVE)
                legion.animate(legion.combatDef.attackAnimation)
                val land = MeleeCombatFormula.getAccuracy(legion, target) >= world.randomDouble()
                legion.dealHit(target = target, maxHit = 8.2, landHit = land, delay = 1, hitType = HitType.MELEE)
            } else if (legion.moveToAttackRange(this, target, distance = 8, projectile = true)) {
                legion.prepareAttack(CombatClass.RANGED, StyleType.RANGED, WeaponStyle.ACCURATE)
                legion.animate(ANIM_LEGION_RANGED)
                world.spawn(legion.createProjectile(target, PROJ_LEGION_SWORD, ProjectileType.THROWN))
                val delay = RangedCombatStrategy.getHitDelay(legion.getFrontFacingTile(target), target.getCentreTile())
                val land = RangedCombatFormula.getAccuracy(legion, target) >= world.randomDouble()
                legion.dealHit(target = target, maxHit = 8.2, landHit = land, delay = delay, hitType = HitType.RANGE)
            }
            legion.postAttackLogic(target)
            wait(legion.combatDef.attackSpeed)
            target = legion.getCombatTarget() ?: break
        }
        legion.resetFacePawn()
        legion.removeCombatTarget()
    }
}

/**
 * The Dagon'hai elite casts a slow, unblockable curse (10-25 damage) every 36 ticks.
 */
on_npc_combat(npc = DAGONHAI_ELITE) {
    npc.queue {
        val elite = npc
        var target = elite.getCombatTarget() ?: return@queue
        while (elite.canEngageCombat(target) && elite.isAttackDelayReady()) {
            elite.facePawn(target)
            elite.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.ACCURATE)
            elite.animate(ANIM_ELITE_CAST)
            elite.graphic(Gfx.CURSE_SPELL)
            world.spawn(elite.createProjectile(target, Gfx.CURSE_SPELL_PROJ, ProjectileType.MAGIC))
            val victim = target
            world.queue {
                wait(3)
                if (victim is Player && victim.isOnline && !victim.isDead()) {
                    victim.graphic(GFX_CURSE_IMPACT)
                    victim.hit(10 + world.random(15), HitType.MAGIC)
                }
            }
            elite.postAttackLogic(target)
            wait(elite.combatDef.attackSpeed)
            target = elite.getCombatTarget() ?: break
        }
        elite.resetFacePawn()
        elite.removeCombatTarget()
    }
}
