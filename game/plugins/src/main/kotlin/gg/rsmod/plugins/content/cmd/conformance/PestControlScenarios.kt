package gg.rsmod.plugins.content.cmd.conformance

import gg.rsmod.game.model.entity.zoneTile
import gg.rsmod.game.message.impl.OpLoc1Message
import gg.rsmod.game.message.impl.OpLoc3Message
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.hit
import gg.rsmod.plugins.api.ext.isMulti
import gg.rsmod.plugins.content.activity.pest_control.PC_GAME_INTERFACE
import gg.rsmod.plugins.content.activity.pest_control.PC_HAMMER
import gg.rsmod.plugins.content.activity.pest_control.PC_KNIGHT_LIFEPOINTS
import gg.rsmod.plugins.content.activity.pest_control.PC_LANDER_INTERFACE
import gg.rsmod.plugins.content.activity.pest_control.PC_LOGS
import gg.rsmod.plugins.content.activity.pest_control.PC_REWARD_INTERFACE
import gg.rsmod.plugins.content.activity.pest_control.PEST_POINTS
import gg.rsmod.plugins.content.activity.pest_control.PestControlBarricades
import gg.rsmod.plugins.content.activity.pest_control.PestControlGame
import gg.rsmod.plugins.content.activity.pest_control.PestControlHandler
import gg.rsmod.plugins.content.activity.pest_control.PestControlRewards
import gg.rsmod.plugins.content.activity.pest_control.PestControlTier
import gg.rsmod.plugins.content.activity.pest_control.pestMonsterOf
import gg.rsmod.plugins.content.cmd.conformance.Conformance.Run
import gg.rsmod.plugins.content.mechanics.pvp.GuardedZones

/**
 * `conform systems pestcontrol` (owner 2026-10-02: "finish pest control fully"). Real bot input wherever the client has a
 * route (gangplank, ladder, Attack, Repair, Exchange, reward buttons); the departure is forced with the admin route
 * (`pcstart`, [PestControlHandler.forceStart]) instead of waiting out 500 ticks with 5 players.
 *
 * Checks: boarding and leaving the novice lander (deck, overlay 407, queue), the combat level gate, the departure into a
 * private instance (overlay 408, four shielded portals, the knight, barricades, safe zone), the first shield drop at tick
 * 50, monster waves that do not respawn, zeal from hitting a portal, a barricade repair (hammer + logs, +5 zeal), a safe
 * death back to the landing, the win (all portals down: points + coins for 50 zeal, nothing below it, instance released),
 * a loss (knight killed: no points), and the reward screen (Void Knight mace and Attack experience).
 */
object PestControlScenarios {
    private const val E = "pestcontrol"
    private val OUTPOST = PestControlTier.NOVICE.leaveTile

    private fun gangplank(bot: Client) = Bots.inject(bot, OpLoc1Message(0, 2658, PestControlTier.NOVICE.gangplankId, 2639))

    private fun ladder(bot: Client) = Bots.inject(bot, OpLoc1Message(0, 2660, PestControlTier.NOVICE.exitLadderId, 2639))

    private suspend fun QueueTask.boardAll(
        run: Run,
        bots: List<Client>,
    ) {
        for (b in bots) {
            b.moveTo(OUTPOST)
        }
        wait(2)
        for (b in bots) gangplank(b)
        repeat(8) { wait(1) }
    }

    private suspend fun QueueTask.monsters(
        run: Run,
        game: PestControlGame,
        bots: List<Client>,
        checked: MutableSet<gg.rsmod.game.model.entity.Npc>,
    ) {
        for (bot in bots) bot.moveTo(game.landingTile())
        wait(2)
        // N112: only the monsters these checks spawn ([checked]) stay; the caller's task clears the waves.
        game.knight.setCurrentLifepoints(PC_KNIGHT_LIFEPOINTS)
        // Spinner: heals its damaged portal by 10 % of its lifepoints when within 5 tiles.
        val index = game.portals.indexOfFirst { it != null && it.id in 6142..6145 && !it.isDead() }
        val portal = game.portals[index]!!
        portal.setCurrentLifepoints(portal.getMaximumLifepoints() / 2)
        val half = portal.getCurrentLifepoints()
        val spinner = game.spawnMonster(3747, portal.tile.transform(3, 0, 0), index)
        checked += spinner
        repeat(30) { wait(1) }
        run.expectTrue(E, "spinner heals its portal", portal.getCurrentLifepoints() > half, "lp $half -> ${portal.getCurrentLifepoints()}")
        // Portal death: its spinners explode, the knight regains 50 hitpoints. N112: the other pests go first - with hitpoints 1:1
        // (knight 200) the ~20 pests of the waves killed the half-health knight in these 5 ticks, the game was lost and every later
        // step (the safe death, the squire, the win) ran outside a game.
        game.knight.setCurrentLifepoints(PC_KNIGHT_LIFEPOINTS / 2)
        portal.hit(portal.getCurrentLifepoints())
        repeat(5) { wait(1) }
        // The pests keep hitting the knight meanwhile, so the +50 is allowed a few small hits. N112: hitpoints are 1:1 since the HP
        // unit migration (knight 200, heal 50; the scenario still expected 1000 -> 1400..1500).
        val healed = PC_KNIGHT_LIFEPOINTS / 2 + gg.rsmod.plugins.content.activity.pest_control.PC_KNIGHT_PORTAL_HEAL
        run.expectTrue(E, "portal destroyed: its spinner explodes, knight +50 hitpoints", !spinner.isSpawned() && game.knight.getCurrentLifepoints() in (healed - 15)..healed, "spinner=${spinner.isSpawned()} knight ${PC_KNIGHT_LIFEPOINTS / 2} -> ${game.knight.getCurrentLifepoints()}")
        // N112: the knight back to full, so the pests of the next checks cannot end the game before the death / squire / win steps.
        game.knight.setCurrentLifepoints(PC_KNIGHT_LIFEPOINTS)
        run.expectTrue(E, "one portal down: the game goes on", game.active, "active=${game.active} portals=${game.portals.map { "${it?.id}:${it?.isSpawned()}:${it?.isDead()}" }}")

        // Ravager: walks to an intact barricade within 5 tiles and smashes it.
        val wall = game.barricades.firstOrNull { it.id == 14224 }
        if (wall == null) {
            run.expectTrue(E, "an intact barricade for the ravager", false, "ids=${game.barricades.map { it.id }}")
            return
        }
        val spawn = wall.tile.transform(0, 2, 0)
        val near = game.barricades.filter { it.tile.isWithinRadius(spawn, 6) }.map { it.tile }.distinct()
        fun damageAround() = near.sumOf { t -> PestControlBarricades.objectsAt(run.world, t).filter { PestControlBarricades.isBarricadeOrGate(it.id) }.sumOf { it.id } }
        val sumBefore = damageAround()
        val ravager = game.spawnMonster(3742, spawn, 0)
        checked += ravager
        ravager.aggroCheck = { _, _ -> false }
        repeat(25) { wait(1) }
        // Every hit moves an id up the damage chain, so the id sum of the barricades around it grows.
        run.expectTrue(E, "ravager smashes the nearest barricade or gate", damageAround() > sumBefore, "id sum $sumBefore -> ${damageAround()} ravager at ${ravager.tile}")
        run.remove(ravager)

        // Splatter: explodes on a barricade next to it, damaging it, and is gone.
        val wall2 = game.barricades.firstOrNull { it.id in 14224..14229 && !it.tile.isWithinRadius(wall.tile, 3) }
        if (wall2 == null) {
            run.expectTrue(E, "a damageable barricade for the splatter", false, "ids=${game.barricades.map { it.id }}")
            return
        }
        val before2 = wall2.id
        val splatter = game.spawnMonster(3727, wall2.tile.transform(0, 1, 0), 0)
        checked += splatter
        splatter.aggroCheck = { _, _ -> false }
        repeat(6) { wait(1) }
        val atWall2 = PestControlBarricades.objectsAt(run.world, wall2.tile).map { it.id }
        run.expectTrue(E, "splatter explodes on a barricade", !splatter.isSpawned() && before2 !in atWall2 && atWall2.any { it == before2 + 3 }, "spawned=${splatter.isSpawned()} $before2 -> $atWall2")

        // Defiler: with nobody to fight it goes for the Void Knight. N112: spawned on the knight's far side (the bots stand on the
        // landing, z 49-54) - by Darkan's rule a pest picks a player within 10 tiles, and (33, 46) had five of them.
        val defiler = game.spawnMonster(3762, game.local(33, 38), 0)
        checked += defiler
        defiler.aggroCheck = { _, _ -> false }
        val knightLp = game.knight.getCurrentLifepoints()
        var targeted = false
        repeat(20) {
            wait(1)
            if (defiler.attr[gg.rsmod.game.model.attr.COMBAT_TARGET_FOCUS_ATTR]?.get() === game.knight) targeted = true
        }
        run.expectTrue(E, "defiler goes for the Void Knight", targeted, "target=${defiler.attr[gg.rsmod.game.model.attr.COMBAT_TARGET_FOCUS_ATTR]?.get()} at ${defiler.tile} knight lp $knightLp -> ${game.knight.getCurrentLifepoints()}")
        run.remove(defiler)

        // Shifter: teleports next to a target more than 5 tiles away.
        val shifter = game.spawnMonster(3732, game.knight.tile.transform(0, -12, 0), 0)
        checked += shifter
        shifter.aggroCheck = { _, _ -> false }
        wait(1)
        shifter.attack(game.knight)
        repeat(4) { wait(1) }
        run.expectTrue(E, "shifter teleports next to its distant target", shifter.tile.isWithinRadius(game.knight.tile, 3), "shifter at ${shifter.tile} knight ${game.knight.tile}")
        run.remove(shifter)
    }

    /**
     * `conform systems pestdeparture`: the real lander timer - five players on the intermediate lander depart by themselves
     * within 500 ticks (2009scape PestControlActivityPlugin pulse), nobody forced.
     */
    suspend fun QueueTask.pestDeparture(run: Run) {
        val tier = PestControlTier.INTERMEDIATE
        val bots = (1..5).map { run.bot(tier.leaveTile, "PD$it") }
        repeat(3) { wait(1) }
        for (b in bots) Bots.inject(b, OpLoc1Message(0, 2643, tier.gangplankId, 2644))
        repeat(8) { wait(1) }
        run.expectTrue(E, "intermediate gangplank 25631: five queued", bots.all { PestControlHandler.lobbyOf(it) == tier }, bots.joinToString { "${it.username}=${PestControlHandler.lobbyOf(it)}@${it.tile}" })
        var waited = 0
        while (waited++ < 520 && PestControlHandler.gameOf(bots[0]) == null) wait(1)
        val game = PestControlHandler.gameOf(bots[0])
        run.expectTrue(E, "the lander departs by its own timer (<= 500 ticks)", game != null && bots.all { PestControlHandler.gameOf(it) === game }, "after $waited ticks game=${game != null}")
        if (game != null) {
            run.expectTrue(E, "intermediate portals: 6154-6157 shielded, ${tier.portalLifepoints} lifepoints", game.portals.map { it?.id } == listOf(6154, 6155, 6156, 6157) && game.portals.all { it?.getCurrentLifepoints() == tier.portalLifepoints }, "${game.portals.map { "${it?.id}:${it?.getCurrentLifepoints()}" }}")
            game.end(success = false, silent = true)
        }
    }

    /**
     * `conform systems pestsolo` (N232): one player boards the novice lander and stands idle at the landing; the tick the Void Knight
     * dies is the measure of how much time a solo player has. 5 idle bots lost the knight by tick ~85 (N112); solo it takes a third
     * of the pests' damage (PestControlHandler.knightDamage).
     */
    suspend fun QueueTask.pestSolo(run: Run) {
        val bot = run.bot(OUTPOST, "PS1")
        repeat(3) { wait(1) }
        boardAll(run, listOf(bot))
        PestControlHandler.forceStart(run.world, PestControlTier.NOVICE)
        repeat(3) { wait(1) }
        val game = PestControlHandler.gameOf(bot)
        if (game == null) {
            run.expectTrue(E, "solo departure: a game", false, "no game for PS1 at ${bot.tile}")
            return
        }
        bot.moveTo(game.landingTile())
        while (game.active && game.knight.isSpawned() && !game.knight.isDead() && game.ticks < 700) wait(1)
        val died = game.ticks
        val lp = game.knight.getCurrentLifepoints()
        run.expectTrue(E, "solo idle: the Void Knight lives 200+ ticks (before N232: 101; 5 idle bots: ~85)", died >= 200, "knight died at tick $died (lp $lp, active ${game.active})")
        if (game.active) game.end(success = false, silent = true)
    }

    suspend fun QueueTask.pestControl(run: Run) {
        val bots = (1..5).map { run.bot(OUTPOST, "PC$it") }
        repeat(3) { wait(1) }
        val a = bots[0]
        val b = bots[1]

        // 1. Lander: the combat gate, boarding by the gangplank, leaving by the ladder.
        val low = run.bot(OUTPOST, "PCLOW")
        // Only the gate is under test: the combat level the lander compares is set directly.
        low.combatLevel = 30
        wait(1)
        gangplank(low)
        repeat(6) { wait(1) }
        run.expectTrue(E, "combat level ${low.combatLevel} < 40: not boarded", PestControlHandler.lobbyOf(low) == null, "lobby=${PestControlHandler.lobbyOf(low)}")

        gangplank(a)
        repeat(6) { wait(1) }
        run.expectTrue(
            E, "gangplank 14315: on the novice deck, queued, lander overlay 407",
            PestControlHandler.lobbyOf(a) == PestControlTier.NOVICE && PestControlTier.NOVICE.lander.contains(a.tile) && a.interfaces.isVisible(PC_LANDER_INTERFACE),
            "lobby=${PestControlHandler.lobbyOf(a)} tile=${a.tile} 407=${a.interfaces.isVisible(PC_LANDER_INTERFACE)}",
        )
        run.expectTrue(E, "the deck is a safe zone", GuardedZones.contains(a.zoneTile()), "tile=${a.tile}")
        ladder(a)
        repeat(6) { wait(1) }
        run.expectTrue(
            E, "ladder 14314: off the lander, not queued, overlay closed",
            PestControlHandler.lobbyOf(a) == null && a.tile.sameAs(PestControlTier.NOVICE.leaveTile) && !a.interfaces.isVisible(PC_LANDER_INTERFACE),
            "lobby=${PestControlHandler.lobbyOf(a)} tile=${a.tile}",
        )

        // 2. Departure into a private copy of the island.
        boardAll(run, bots)
        run.expectTrue(E, "five players queued", bots.all { PestControlHandler.lobbyOf(it) == PestControlTier.NOVICE }, bots.joinToString { "${it.username}=${PestControlHandler.lobbyOf(it)}" })
        PestControlHandler.forceStart(run.world, PestControlTier.NOVICE)
        repeat(3) { wait(1) }
        val game = PestControlHandler.gameOf(a)
        if (game == null) {
            run.expectTrue(E, "departure: a game", false, "no game for PC1 at ${a.tile}")
            return
        }
        run.expectTrue(E, "all five in the instance, overlay 408", bots.all { PestControlHandler.gameOf(it) === game && it.interfaces.isVisible(PC_GAME_INTERFACE) }, "tiles=${bots.map { it.tile }}")
        run.expectTrue(E, "the instance is not the real island", game.base != gg.rsmod.plugins.content.activity.pest_control.PC_REGION_BASE, "base=${game.base}")
        run.expectTrue(E, "inside the game is a safe zone (no PvP)", GuardedZones.contains(a.zoneTile()), "tile=${a.tile}")
        // ROOT_CAUSES #142: the island is multi-combat (multi.yml region 10536); its instance copy must be too.
        run.expectTrue(
            E, "the island instance is multi-combat (multi.yml region 10536)",
            a.tile.isMulti(run.world) && game.local(32, 32).isMulti(run.world) && game.local(4, 31).isMulti(run.world),
            "instance=${a.tile.isMulti(run.world)} real island=${gg.rsmod.plugins.content.activity.pest_control.PC_REGION_BASE.transform(32, 32, 0).isMulti(run.world)}",
        )
        run.expectTrue(E, "four shielded portals 6146-6149", game.portals.map { it?.id } == listOf(6146, 6147, 6148, 6149), "${game.portals.map { it?.id }}")
        run.expectTrue(E, "the Void Knight with ${PC_KNIGHT_LIFEPOINTS} lifepoints", game.knight.isSpawned() && game.knight.getCurrentLifepoints() == PC_KNIGHT_LIFEPOINTS, "${game.knight.id} lp=${game.knight.getCurrentLifepoints()}")
        run.expectTrue(E, "all 38 barricades and gates of the island found in the instance", game.barricades.size == 38, "count=${game.barricades.size} ids=${game.barricades.map { it.id }.distinct().sorted()}")

        // 3. Shields, waves.
        while (game.active && game.ticks < 52) wait(1)
        val open = game.portals.count { it != null && it.id in 6142..6145 }
        run.expectTrue(E, "tick 50: one shield dropped, that portal is attackable", open == 1, "${game.portals.map { it?.id }}")
        val monsters = game.monsters.size
        run.expectTrue(E, "monster waves spawned by the portals", monsters > 0, "monsters=$monsters")
        val respawning = game.monsters.filter { it.respawns }
        run.expectTrue(E, "no pest monster respawns", respawning.isEmpty(), respawning.joinToString { "${it.id}" })

        // N112: from here on only the monsters a check spawns itself stay. With hitpoints 1:1 (knight 200) the waves (every 35
        // ticks per portal) and the knight shifters killed the knight of five idle bots by tick ~85, in the middle of these steps:
        // the game was lost and the safe death, the squire and the win ran outside a game. A world task clears the rest each tick.
        val checked = HashSet<gg.rsmod.game.model.entity.Npc>()
        var clearing = true
        run.world.queue {
            while (clearing && game.active) {
                game.monsters.filter { it !in checked }.forEach { run.world.remove(it) }
                wait(1)
            }
        }

        // 4. Zeal from hitting a portal (real Attack).
        val portal = game.portals.first { it != null && it.id in 6142..6145 }!!
        // West of the 3x3 portal, not on its diagonal corner (unarmed melee cannot hit diagonally).
        for (bot in bots) bot.moveTo(portal.tile.transform(-1, 1, 0))
        wait(2)
        val before = portal.getCurrentLifepoints()
        Bots.npcOption(a, portal, 2)
        var waited = 0
        while (waited++ < 20 && portal.damageMap.getDamageFrom(a) == 0) {
            wait(1)
            // The pests attack the bots; auto-retaliate may switch the bot's target, so the Attack is repeated like a player would.
            if (waited % 5 == 0) Bots.npcOption(a, portal, 2)
        }
        val zealA = PestControlHandler.zealOf(a)
        // Wave spinners may heal the portal meanwhile, so the damage is read from the portal's damage record.
        val dealt = portal.damageMap.getDamageFrom(a)
        run.expectTrue(E, "attacking the portal: damage and zeal (1 damage = 1 zeal)", dealt > 0 && zealA >= dealt, "dealt=$dealt lp $before -> ${portal.getCurrentLifepoints()} zeal=$zealA")

        // 5. Repair a damaged barricade (hammer + logs).
        val target = game.barricades.firstOrNull { it.id == 14227 || it.id == 14228 || it.id == 14229 }
        if (target == null) {
            run.expectTrue(E, "an undamaged barricade to test repair", false, "ids=${game.barricades.map { it.id }.distinct()}")
        } else {
            val damaged = PestControlBarricades.damage(game, target)!!
            b.inventory.add(PC_HAMMER)
            b.inventory.add(PC_LOGS)
            b.moveTo(damaged.tile.transform(0, 1, 0))
            wait(2)
            val zealBefore = PestControlHandler.zealOf(b)
            Bots.inject(b, OpLoc3Message(damaged.id, damaged.tile.x, damaged.tile.z, 0))
            repeat(6) { wait(1) }
            val now = PestControlBarricades.objectsAt(run.world, damaged.tile).map { it.id }
            run.expectTrue(
                E, "repair ${damaged.id}: back to ${target.id}, logs used, +5 zeal",
                target.id in now && !b.inventory.contains(PC_LOGS) && PestControlHandler.zealOf(b) == zealBefore + 5,
                "ids=$now logs=${b.inventory.contains(PC_LOGS)} zeal=${PestControlHandler.zealOf(b)}",
            )
        }

        // 5a. A gate pair opens and closes (real Open / Close), and a tower ladder steps over the wall.
        val gate = game.barricades.firstOrNull { it.id == 14233 && it.tile.x - game.base.x == 19 }
        if (gate == null) {
            run.expectTrue(E, "the west gate 14233 at local (19, 33)", false, "ids=${game.barricades.filter { it.id in 14233..14248 }.map { "${it.id}@${it.tile}" }}")
        } else {
            b.moveTo(gate.tile.transform(1, 0, 0))
            wait(2)
            Bots.inject(b, OpLoc1Message(0, gate.tile.x, gate.id, gate.tile.z))
            repeat(4) { wait(1) }
            val opened = game.barricades.filter { it.id == 14234 || it.id == 14236 }
            run.expectTrue(E, "gate opened: both leaves 14234 / 14236", opened.size == 2, "open leaves=${opened.map { "${it.id}@${it.tile}" }}")
            val leaf = opened.firstOrNull { it.id == 14234 }
            if (leaf != null) {
                Bots.inject(b, OpLoc1Message(0, leaf.tile.x, leaf.id, leaf.tile.z))
                repeat(4) { wait(1) }
                val closed = game.barricades.filter { (it.id == 14233 || it.id == 14235) && it.tile.x - game.base.x == 19 }
                run.expectTrue(E, "gate closed again on its own tiles", closed.size == 2 && closed.map { it.tile.z - game.base.z }.sorted() == listOf(32, 33), "closed=${closed.map { "${it.id}@${it.tile}" }}")
            }
        }
        b.moveTo(game.local(46, 41))
        wait(2)
        Bots.inject(b, OpLoc1Message(0, game.base.x + 45, gg.rsmod.plugins.content.activity.pest_control.PC_TOWER_LADDER, game.base.z + 41))
        repeat(5) { wait(1) }
        run.expectTrue(E, "tower ladder (45, 41): over to (44, 41)", b.tile.sameAs(game.local(44, 41)), "at ${b.tile.x - game.base.x},${b.tile.z - game.base.z}")

        // 5b. Monster behaviour (2009scape PC*NPC), each spawned on purpose away from the players.
        monsters(run, game, bots, checked)
        clearing = false
        run.expectTrue(E, "after the monster checks the game is still on", game.active, "active=${game.active} knight=${game.knight.getCurrentLifepoints()} portals=${game.portals.map { "${it?.id}:${it?.isSpawned()}:${it?.isDead()}" }}")

        // 6. A safe death: back at the landing, items kept.
        b.inventory.add(gg.rsmod.plugins.api.cfg.Items.ABYSSAL_WHIP)
        b.hit(b.getCurrentLifepoints())
        repeat(14) { wait(1) }
        run.expectTrue(
            E, "death in the game: back at the landing, whip kept",
            PestControlHandler.gameOf(b) === game && b.inventory.contains(gg.rsmod.plugins.api.cfg.Items.ABYSSAL_WHIP) &&
                (b.tile.x - game.base.x) in 32..35 && (b.tile.z - game.base.z) in 49..54,
            "game=${PestControlHandler.gameOf(b) === game} tile=${b.tile} whip=${b.inventory.contains(gg.rsmod.plugins.api.cfg.Items.ABYSSAL_WHIP)}",
        )

        // 6b. The squire by the landing: "Leave" takes a player off the island.
        val c = bots[2]
        val squire = run.world.npcs.firstOrNull { it.id == gg.rsmod.plugins.content.activity.pest_control.PC_LEAVE_SQUIRE && game.map.area.contains(it.tile) }
        if (squire == null) {
            run.expectTrue(E, "the leave squire 3781 in the instance", false, "none")
        } else {
            c.moveTo(gg.rsmod.plugins.content.mechanics.pvp.CityGuards.nearestWalkable(run.world, squire.tile.transform(0, 1, 0), radius = 3) ?: squire.tile.transform(0, 1, 0))
            wait(2)
            Bots.npcOption(c, squire, 3)
            var w = 0
            while (w++ < 10 && PestControlHandler.gameOf(c) != null) wait(1)
            run.expectTrue(E, "squire 3781 Leave: off the island, overlay closed", PestControlHandler.gameOf(c) == null && c.tile.sameAs(PestControlTier.NOVICE.leaveTile) && !c.interfaces.isVisible(PC_GAME_INTERFACE), "tile=${c.tile}")
        }

        // 7. Win: all portals destroyed. PC1 has the zeal for a reward, PC2 is held under 50.
        PestControlHandler.addZeal(a, 60)
        b.attr[PestControlHandler.ZEAL_ATTR] = 10
        while (game.active && game.ticks < 202) wait(1)
        val pointsBefore = a.attr[PEST_POINTS] ?: 0
        val bPoints = b.attr[PEST_POINTS] ?: 0
        val coinsBefore = a.inventory.getItemCount(995)
        for (p in game.portals.filterNotNull()) {
            if (!p.isDead()) p.hit(p.getCurrentLifepoints())
        }
        repeat(10) { wait(1) }
        run.expectTrue(E, "all portals down: the game ends", !game.active, "active=${game.active}")
        run.expectTrue(
            E, "won with 60+ zeal: +${game.tier.pointsPerWin} points and combat x 10 coins",
            (a.attr[PEST_POINTS] ?: 0) == pointsBefore + game.tier.pointsPerWin && a.inventory.getItemCount(995) == coinsBefore + a.combatLevel * 10,
            "points $pointsBefore -> ${a.attr[PEST_POINTS]} coins $coinsBefore -> ${a.inventory.getItemCount(995)}",
        )
        run.expectTrue(E, "won with 10 zeal: no points", (b.attr[PEST_POINTS] ?: 0) == bPoints, "points=${b.attr[PEST_POINTS]}")
        run.expectTrue(E, "everyone back at the novice lander, overlay closed", bots.all { it.tile.sameAs(PestControlTier.NOVICE.leaveTile) && !it.interfaces.isVisible(PC_GAME_INTERFACE) }, "tiles=${bots.map { it.tile }}")
        run.expectTrue(E, "the instance is released", run.world.instanceAllocator.getMap(game.base) == null, "base=${game.base}")

        // 8. Loss: the knight is killed.
        boardAll(run, bots)
        PestControlHandler.forceStart(run.world, PestControlTier.NOVICE)
        repeat(3) { wait(1) }
        val second = PestControlHandler.gameOf(a)
        if (second == null) {
            run.expectTrue(E, "second departure: a game", false, "no game")
        } else {
            val p0 = a.attr[PEST_POINTS] ?: 0
            PestControlHandler.addZeal(a, 80)
            second.knight.hit(second.knight.getCurrentLifepoints())
            repeat(10) { wait(1) }
            run.expectTrue(E, "knight killed: game lost, no points, players out", !second.active && (a.attr[PEST_POINTS] ?: 0) == p0 && a.tile.sameAs(PestControlTier.NOVICE.leaveTile), "active=${second.active} points=${a.attr[PEST_POINTS]} tile=${a.tile}")
        }

        // 9. Rewards: Exchange on a Void Knight, the mace (250) and Attack experience (1 point).
        gg.rsmod.game.model.attr.PointBalance.set(a.attr, PEST_POINTS, 251)
        a.inventory.removeAll()
        val knight: Npc? = run.world.npcs.firstOrNull { it.id == gg.rsmod.plugins.api.cfg.Npcs.VOID_KNIGHT_3788 }
        if (knight == null) {
            run.expectTrue(E, "a Void Knight 3788 at the outpost", false, "none spawned")
            return
        }
        a.moveTo(gg.rsmod.plugins.content.mechanics.pvp.CityGuards.nearestWalkable(run.world, knight.tile.transform(1, 0, 0), radius = 3) ?: knight.tile.transform(1, 0, 0))
        wait(2)
        Bots.npcOption(a, knight, 3)
        // The bot walks up to the (wandering) knight first.
        var waitedOpen = 0
        while (waitedOpen++ < 10 && !a.interfaces.isVisible(PC_REWARD_INTERFACE)) wait(1)
        run.expectTrue(E, "Exchange opens the reward screen 267", a.interfaces.isVisible(PC_REWARD_INTERFACE), "visible=${a.interfaces.isVisible(PC_REWARD_INTERFACE)}")
        Bots.button(a, PC_REWARD_INTERFACE, 41)
        wait(1)
        Bots.button(a, PC_REWARD_INTERFACE, PestControlRewards.CONFIRM)
        repeat(2) { wait(1) }
        run.expectTrue(E, "Void Knight mace bought for 250 points", a.inventory.contains(8841) && (a.attr[PEST_POINTS] ?: 0) == 1, "mace=${a.inventory.contains(8841)} points=${a.attr[PEST_POINTS]}")
        val xp = a.skills.getCurrentXp(Skills.ATTACK)
        PestControlRewards.open(a)
        wait(1)
        Bots.button(a, PC_REWARD_INTERFACE, 34)
        wait(1)
        Bots.button(a, PC_REWARD_INTERFACE, PestControlRewards.CONFIRM)
        repeat(2) { wait(1) }
        val expected = PestControlRewards.experience(99, Skills.ATTACK, 1)
        // The base is floor(99^2/600) x 35 = 560; the server's experience rate / bonus experience multiply it like any experience.
        run.expectTrue(E, "1 point of Attack: base $expected xp, at least that gained", a.skills.getCurrentXp(Skills.ATTACK) - xp >= expected - 0.5 && (a.attr[PEST_POINTS] ?: 0) == 0, "xp +${a.skills.getCurrentXp(Skills.ATTACK) - xp} points=${a.attr[PEST_POINTS]}")
        val types = game.monsters.mapNotNull { pestMonsterOf(it.id) }.distinct()
        run.note("pestcontrol: monster types seen in the first game: $types")
    }
}
