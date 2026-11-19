package gg.rsmod.plugins.content.activity.toa.puzzles

import gg.rsmod.game.model.PlayerUID
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.combat.NpcCombatDef
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.entity.*
import gg.rsmod.game.model.timer.FROZEN_TIMER
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.activity.toa.*
import gg.rsmod.plugins.content.combat.isEnvenomed
import gg.rsmod.plugins.content.combat.isPoisoned
import gg.rsmod.plugins.content.combat.venom
import gg.rsmod.plugins.content.combat.combatRaycast
import gg.rsmod.plugins.content.activity.toa.puzzles.ApmekenPuzzle.Baboon

/** Eight-wave room with owned actors/tasks and evidence-labelled defaults for the remaining T22 observations. */
class ApmekenRoom(raid: ToaRaid) : ToaPuzzleRoom(raid) {
    val puzzle = ApmekenPuzzle<PlayerUID>(raid.roster.size)
    private var reference: ApmekenReferences.Profile? = null
    private data class Actor(val npc: Npc, val type: Baboon, var nextAttack: Int, val parent: Npc? = null,
                             var detonates: Int? = null, var previousTile: Tile? = null, var target: PlayerUID? = null)
    private val actors = linkedMapOf<Npc, Actor>()
    private val pools = linkedMapOf<Tile, Pair<GameObject, Int>>()
    private val viewers = linkedSetOf<Player>()
    private val viewerBases = hashMapOf<Player, gg.rsmod.game.model.Coordinate?>()
    private val pending = mutableListOf<Pair<Int, () -> Unit>>()

    override fun startPuzzle(): Boolean {
        reference = ApmekenReferences.profile ?: ApmekenDefaults.reference()
        if (ToaIds.item(POTION) == null) return missing("item:$POTION")
        if (Baboon.values().filter { it != Baboon.SPECIAL }.any { ToaIds.npc(it.osrs) == null })
            return missing("Apmeken NPC imports11709..11718 (large variants require their own imported scale)")
        //Room.start is synchronous immediately after the FIRST entrant teleports; world-list order next tick need not match entry order.
        players.forEach { puzzle.enter(it.uid); viewers += it }
        drawSight()
        return true
    }

    override fun debugState(player: Player) = "apmeken tick=$tick wave=${puzzle.wave} alive=${puzzle.remaining} " +
        "task=${puzzle.task?.type} sites=${puzzle.task?.remainingSites?.filterValues { it > 0 }?.keys} " +
        "pillars=${PILLARS.map { instance(it).let { t -> "${t.x},${t.z}" } }} vents=${VENTS.map { instance(it).let { t -> "${t.x},${t.z}" } }} " +
        "sight=${puzzle.sight == player.uid} complete=${puzzle.complete}"
    override fun enter(player: Player) { viewers += player; puzzle.enter(player.uid); drawSight() }
    override fun leave(player: Player) {
        val next = players.filter { it !== player }.takeIf { it.isNotEmpty() }?.let { reference!!.chooseSight.value(it).uid }
        puzzle.leave(player.uid, next); drawSight()
        reference?.drawSight?.value?.invoke(player, null, null); viewers -= player; viewerBases.remove(player)
    }

    /** Update:Tombs of Amascut: Balancing & Bugfixes (1 September 2022): "Completing the room will now delete the regular Hammer from
     *  your Inventory." */
    override fun cleared() {
        players.forEach { p -> p.inventory.getItemCount(Items.HAMMER).takeIf { it > 0 }?.let { p.inventory.remove(Items.HAMMER, it) } }
    }

    fun takeHammer(player: Player): Boolean {
        if (!act(player) || player.inventory.contains(Items.HAMMER)) return false
        //Strategies1292: this crate gives the existing ordinary hammer; completing the room deletes it again (cleared()).
        return player.inventory.add(Items.HAMMER).hasSucceeded()
    }
    fun takePotions(player: Player): Boolean {
        if (!act(player)) return false
        val id = ToaIds.item(POTION) ?: return false
        // Path_of_Apmeken changelog1Sep2022: crate gives100 instead of20.
        return player.inventory.add(id, 100).hasSucceeded()
    }
    private fun hasHammer(player: Player): Boolean = player.inventory.contains(Items.HAMMER) ||
        player.inventory.contains(Items.DRAGON_WARHAMMER) || player.equipment.contains(Items.DRAGON_WARHAMMER)

    fun repair(player: Player, obj: GameObject): Boolean {
        if (!eligible(player) || !hasHammer(player)) return false
        val index = PILLARS.indexOfFirst { instance(it) == obj.tile }
        if (index < 0 || !act(player)) return false
        val result = puzzle.fix(player.uid, ApmekenPuzzle.Action.PILLAR, index, tick)
        return actionResult(player, result)
    }

    fun pour(player: Player, target: Player? = null): Boolean {
        val ref = reference ?: return false
        if (!eligible(player)) return false
        val id = ToaIds.item(POTION) ?: return false
        if (!player.inventory.contains(id) || !act(player)) return false
        val task = puzzle.task
        val before = task?.performed ?: 0
        val vent = VENTS.indexOfFirst { instance(it) == player.tile }
        val result = if (target != null) {
            if (!eligible(target) || player.tile.getDistance(target.tile) > 1) return false
            puzzle.cleanse(player.uid, setOf(target.uid), tick)
        } else if (vent >= 0 && puzzle.task?.type != ApmekenPuzzle.Action.CORRUPTION) {
            puzzle.fix(player.uid, ApmekenPuzzle.Action.VENT, vent, tick)
        } else {
            puzzle.cleanse(player.uid, players.filter { it.tile == player.tile }.map { it.uid }.toSet(), tick)
        }
        if (result == ApmekenPuzzle.Result.REFUSED) return false
        player.inventory.remove(id, 1)
        if (result == ApmekenPuzzle.Result.WRONG) damage(player, ref.wrongActionDamage.value)
        if (result == ApmekenPuzzle.Result.PERFORMED || result == ApmekenPuzzle.Result.COMPLETE)
            award(player.uid, 75 * ((task?.performed ?: before) - before)) //ONZEKER T22: Wiki loot module75 points per trap action.
        if (vent >= 0 && target == null && task?.type == ApmekenPuzzle.Action.VENT &&
            (result == ApmekenPuzzle.Result.PERFORMED || result == ApmekenPuzzle.Result.COMPLETE)) player.message(FIX_FUMES)
        drawSight()
        return result == ApmekenPuzzle.Result.PERFORMED || result == ApmekenPuzzle.Result.COMPLETE
    }
    private fun actionResult(player: Player, result: ApmekenPuzzle.Result): Boolean {
        if (result == ApmekenPuzzle.Result.WRONG) damage(player, reference!!.wrongActionDamage.value)
        if (result == ApmekenPuzzle.Result.PERFORMED || result == ApmekenPuzzle.Result.COMPLETE) player.message(FIX_ROOF_SUPPORT)
        if (result == ApmekenPuzzle.Result.PERFORMED || result == ApmekenPuzzle.Result.COMPLETE) award(player.uid, 75)
        drawSight()
        return result == ApmekenPuzzle.Result.PERFORMED || result == ApmekenPuzzle.Result.COMPLETE
    }

    private fun drawSight() {
        val draw = reference?.drawSight?.value ?: return
        val holder = players.firstOrNull { it.uid == puzzle.sight }
        players.forEach { draw(it, holder, puzzle.task); viewerBases[it] = it.lastKnownRegionBase }
    }

    override fun cycle() {
        val ref = reference ?: return
        if (players.any { !viewerBases.containsKey(it) || viewerBases[it] != it.lastKnownRegionBase }) drawSight()
        actors.values.filter { !it.npc.isSpawned() || it.npc.isDead() }.toList().forEach {
            puzzle.killed(it.npc)
            if (!it.npc.isSpawned()) actors.remove(it.npc)
        }
        if (puzzle.wave < ApmekenPuzzle.WAVES.size && puzzle.remaining <= ref.remainingThresholds.value[puzzle.wave]) {
            val plan = ref.waves.value(raid.roster.size, puzzle.wave + 1)
            val spawned = mutableListOf<Npc>()
            val advanced = puzzle.nextWave(ref.remainingThresholds.value[puzzle.wave]) {
                plan.mapNotNull { s -> spawnActor(s.type, s.tile)?.also { spawned += it }?.let { it to s.type } }
            }
            if (!advanced) {
                spawned.forEach { actors.remove(it); raid.world.remove(it) }
                missing("T22 invalid or incomplete source wave${puzzle.wave + 1}")
            }
        }
        val expiring = puzzle.task?.type
        val missed = puzzle.expireTask(tick)
        if (missed > 0) {
            // ToaMistakeTracker c73766d0 ApmekenPuzzleDetector:62-64,120-128: exact team-wide OSRS game messages.
            val text = when (checkNotNull(expiring)) {
                ApmekenPuzzle.Action.VENT -> "The fumes filling the room suddenly ignite!"
                ApmekenPuzzle.Action.PILLAR -> "Damaged roof supports cause some debris to fall on you!"
                ApmekenPuzzle.Action.CORRUPTION -> "Your group is overwhelmed by Amascut's corruption!"
            }
            // N609 14-11 census: the failure graphics spawn on the players' tiles (toa-mistake-tracker ApmekenPuzzleDetector VENT 2138
            // TOA_PATH_APMEKEN_GAS / PILLAR 2139 TOA_PATH_APMEKEN_RUBBLE; its comment: 'two players on the same tile as the graphics
            // spawn ... only one takes damage'). Corruption: no graphic named in any source.
            val failGfx = when (expiring) { ApmekenPuzzle.Action.VENT -> 2138; ApmekenPuzzle.Action.PILLAR -> 2139; else -> null }
            failGfx?.let { g -> players.forEach { p -> players.forEach { viewer -> ToaPuzzleVisuals.spot(viewer, g, p.tile) } } }
            players.forEach { it.message(text); damage(it, ref.missedActionDamage.value(raid.roster.size, missed)) }; drawSight()
        }
        if (tick > 0 && tick % ref.sightCycle.value == 0 && players.isNotEmpty()) {
            puzzle.transferSight(ref.chooseSight.value(players).uid); drawSight()
        }
        for (task in ref.tasks.value) {
            if (tick < task.cycle || (tick - task.cycle) % ref.taskRepeatCycles.value != 0) continue
            if (puzzle.beginTask(task.type, task.requiredByPartySize[raid.roster.size - 1],
                    task.usesByPartySize[raid.roster.size - 1], tick + task.duration)) {
                // Strategies1291 literal for the party; N610 14-11: the holder of Apmeken's sight gets what the issue is (the exact OSRS
                // game messages RuneLite ToA Extended Apmeken MESSAGE_SENSE_ROOF_SUPPORTS / MESSAGE_SENSE_FUMES reads). Corruption: no source.
                players.forEach { it.message(senseMessage(task.type, it.uid == puzzle.sight)) }
                drawSight()
            }
        }
        pending.filter { it.first <= tick }.toList().forEach { pending.remove(it); if (live) it.second() }
        pools.filterValues { it.second <= tick }.keys.toList().forEach { tile -> pools.remove(tile)?.let { raid.world.remove(it.first) } }
        actors.values.toList().forEach(::actorCycle)
        players.filter { source(it.tile) in pools }.forEach { player ->
            damage(player, raid.world.random(6..8)); player.venom() // Strategies1285:6-8/tick plus venom, immunity handled by engine.
        }
        clearIf(puzzle.complete && actors.isEmpty()) //Allow the final owned death animations to finish before closing the scope.
    }

    private fun spawnActor(type: Baboon, tile: Tile, parent: Npc? = null): Npc? {
        val ref = reference ?: return null
        val visual = ref.actors.value[type] ?: return null
        val death = ToaIds.npcSeq(type.osrs, visual.deathSequence) ?: run {
            missing("npcseq:${type.osrs}:${visual.deathSequence}"); -1
        }
        val npc = spawn(type.osrs, tile, definition(type, raid.settings.raidLevel).copy(deathAnimation = listOf(death), waitFullDeathAnimation = true))
            ?: return null
        actors[npc] = Actor(npc, type, tick, parent)
        // N606 (animatiedekking 12-11: NPC_MANDRILL_SPAWN01/02 nooit gespeeld; toagfx: baboons appear in the wall alcove = the OSRS spawn
        // points): the baboon climbs out of its hole with the cache spawn clip (seq9752, 120 cycles = 4 ticks).
        // ONZEKER T01: which of SPAWN01/SPAWN02 OSRS uses per hole is unpublished -> SPAWN01; check against a video.
        if (parent == null) ToaIds.npcSeq(type.osrs, 9752)?.let { npc.animate(it) } ?: missing("npcseq:${type.osrs}:9752")
        return npc
    }

    private fun actorCycle(actor: Actor) {
        val ref = reference ?: return
        val npc = actor.npc
        if (!npc.isSpawned() || npc.isDead()) return
        if (actor.type == Baboon.CURSED) {
            if (tick % ref.cursedStepCycles.value == 0) {
                val tile = source(npc.tile) ?: return
                if (actor.previousTile != tile) {
                    if (tile !in pools) place(VENOM_POOL, tile, 22)?.let { pools[tile] = it to (tick + ref.venomPoolCycles.value) }
                    actor.previousTile = tile
                }
                //Strategies1285: avoid vents and pillar fronts along the walk, not just at its eventual destination.
                val candidates = ref.cursedWalkTiles.value.filter { it != tile && it.isWithinRadius(tile, 1) &&
                    it !in VENTS && it !in PILLAR_FRONTS && players.none { p -> source(p.tile) == it } }
                if (candidates.isNotEmpty() && !npc.timers.has(FROZEN_TIMER)) npc.walkTo(instance(candidates[raid.world.random(0 until candidates.size)]))
            }
            return
        }
        val available = players
        if (available.isEmpty()) return
        val player = available.firstOrNull { it.uid == actor.target } ?: ref.chooseTarget.value(npc, available, actor.type)
            .takeIf { it in available }?.also { actor.target = it.uid } ?: return
        if (actor.type == Baboon.VOLATILE) {
            val detonation = actor.detonates
            if (detonation != null) {
                if (tick >= detonation) {
                    actor.detonates = Int.MAX_VALUE //One blast even if the lethal hit/death queue is delayed to a later world phase.
                    // N606: the blast itself (cache2686 spotanim 2249 SPOTANIM_MANDRILL_EXPLODE) was never sent - only the death clip.
                    players.forEach { ToaPuzzleVisuals.spot(it, 2249, npc.tile) }
                    // N609 14-11 census: OSRS spawns graphics object 131 FIREBLAST_IMPACT on the explosion centre (RuneLite toa-mistake-
                    // tracker ApmekenPuzzleDetector VOLATILE_GRAPHICS_ID, 3x3 hit around it; raid-recorder GO_APMEKEN_VOLATILE_EXPLOSION).
                    players.forEach { ToaPuzzleVisuals.spot(it, 131, npc.tile) }
                    players.filter { it.tile.isWithinRadius(npc.tile, 1) }.forEach { damage(it, ref.volatileDamage.value(npc.getCurrentLifepoints())) }
                    actors.values.filter { it.npc !== npc && it.npc.tile.isWithinRadius(npc.tile, 1) }.toList().forEach {
                        // Strategies1279: kills nearby baboons regardless of HP. Pets are never in the owned actor collection.
                        it.npc.hit(it.npc.getCurrentLifepoints())
                    }
                    ToaIds.npcSeq(actor.type.osrs, VOLATILE_EXPLODE)?.let {
                        npc.combatDef = npc.combatDef.copy(deathAnimation = listOf(it))
                    }
                    npc.hit(npc.getCurrentLifepoints())
                }
            } else {
                follow(npc, player.tile)
                if (inMeleeRange(npc, player.tile)) {
                    actor.detonates = tick + ref.volatileChargeCycles.value
                    animate(npc, ref.actors.value.getValue(actor.type).attackSequence)
                }
            }
            return
        }
        val visual = ref.actors.value.getValue(actor.type)
        val style = when (actor.type.family) { 0, 6 -> CombatClass.MELEE; 1 -> CombatClass.RANGED; else -> CombatClass.MAGIC }
        val reaches = if (style == CombatClass.MELEE) inMeleeRange(npc, player.tile)
            else npc.combatRaycast(player, visual.range, projectile = true)
        if (!reaches) { follow(npc, player.tile); return }
        npc.stopMovement()
        if (tick < actor.nextAttack) return
        if (actor.type == Baboon.SHAMAN && !npc.isPoisoned() && !npc.isEnvenomed() &&
            actors.values.count { it.parent === npc && it.npc.isSpawned() && !it.npc.isDead() } < ref.thrallCap.value) {
            spawnActor(Baboon.THRALL, ref.thrallTile.value(npc), npc)?.let {
                puzzle.child(it, Baboon.THRALL)
                animate(npc, visual.attackSequence) //ONZEKER T01: nearest same-shaman magic-cast clip for its summon.
            }
            actor.nextAttack = tick + ref.summonCycles.value
            return
        }
        actor.nextAttack = tick + npc.combatDef.attackSpeed
        animate(npc, visual.attackSequence); visual.launch(npc, player)
        pending += (tick + visual.hitDelay) to {
            if (eligible(player) && npc.isSpawned() && !npc.isDead()) {
                val base = if (actor.type.combat && actor.type.osrs <= 11711 || actor.type == Baboon.THRALL) 5 else 7
                val maximum = base * (250 + minOf(raid.settings.raidLevel, 375)) / 250 //10-schaal: damage+.4%/RL, cap375.
                val protected = player.isProtectedFrom(style)
                attack(npc, player, maximum, style) { _, amount, raw ->
                    if (actor.type == Baboon.SHAMAN && amount > 0) drain(player, Skills.PRAYER, amount)
                    if (actor.type == Baboon.THRALL) drain(player, Skills.PRAYER, ref.thrallPrayerDrain.value(raw, protected))
                }
            }
        }
    }

    fun maximumHit(npc: Npc, style: CombatClass): Boolean = live && actors[npc]?.type?.maximumHit(style) == true

    override fun closePuzzle() {
        reference?.drawSight?.value?.let { draw -> viewers.forEach { draw(it, null, null) } }
        viewers.clear(); viewerBases.clear(); actors.clear(); pools.clear(); pending.clear(); puzzle.close()
    }

    /** RuneLite ToA Extended Apmeken MESSAGE_SENSE_* / MESSAGE_FIX_* (Text.standardize = lower case of the OSRS game message). */
    companion object {
        const val SENSE_ANY = "You sense an issue somewhere in the room."
        const val SENSE_ROOF = "You sense an issue with the roof supports."
        const val SENSE_FUMES = "You sense some strange fumes coming from holes in the floor."
        const val FIX_ROOF_SUPPORT = "You repair the damaged roof support."
        const val FIX_FUMES = "You neutralise the fumes coming from the hole."
        fun senseMessage(type: ApmekenPuzzle.Action, sightHolder: Boolean): String = if (!sightHolder) SENSE_ANY else when (type) {
            ApmekenPuzzle.Action.PILLAR -> SENSE_ROOF
            ApmekenPuzzle.Action.VENT -> SENSE_FUMES
            ApmekenPuzzle.Action.CORRUPTION -> SENSE_ANY
        }

        //20-item-manifest/cache2/10/27297, 50-loc-defs45493.
        const val POTION = 27297
        const val VENOM_POOL = 45493
        //RuneLite AnimationID.NPC_MANDRILL_EXPLODE; normal ranged kills instead use the profile's DESPAWN9754.
        const val VOLATILE_EXPLODE = 9756
        //40-loc-placements cache2686 5/15186/1; loc45494 and45499, respectively.
        val PILLARS = listOf(Tile(3803, 5269), Tile(3803, 5289), Tile(3811, 5269), Tile(3811, 5289))
        val VENTS = listOf(Tile(3800, 5276), Tile(3800, 5284), Tile(3816, 5276), Tile(3816, 5284))
        //3x3 pillars face room centre: adjacent interaction tile in front, derived from their bounding boxes.
        val PILLAR_FRONTS = listOf(Tile(3804, 5272), Tile(3804, 5288), Tile(3812, 5272), Tile(3812, 5288))

        /** Wiki minion infoboxes and cache2686 NPC11709..11718 (including ranged stat opcode78, HP opcode77). */
        fun definition(type: Baboon, raidLevel: Int): NpcCombatDef {
            require(type != Baboon.SPECIAL && raidLevel in 0..600)
            val small = type.osrs in 11709..11711 || type == Baboon.THRALL
            val level = if (small) 40 else 60
            val defence = if (small) 12 else 20
            val offensive = if (small) 20 else 25
            val meleeDef = if (type == Baboon.THRALL) 0 else if (type.family == 1) -50 else 900
            val magicDef = if (type == Baboon.THRALL) 0 else if (type.family in listOf(0, 4, 5)) -60 else 900
            val rangeDef = if (type == Baboon.THRALL) 0 else if (type.family in listOf(2, 3, 4, 5)) -50 else 900
            val bonuses = listOf(offensive, offensive, offensive, offensive, offensive,
                meleeDef, meleeDef, meleeDef, magicDef, rangeDef, offensive, 0, 0, 0)
            val scaled = level * (250 + raidLevel) / 250
            // TOA-1911 punt 17: the Brawler/Thrower/Mage infoboxes (wiki 19-11) publish 'range = 40' for BOTH sizes (the large ones only
            // raise att/str/mage to 60); Shaman/Volatile/Cursed range 60, Thrall 40.
            val ranged = (if (type.combat) 40 else level) * (250 + raidLevel) / 250
            return NpcCombatDef.DEFAULT.copy(lifepoints = type.hp, stats = listOf(scaled, level, defence * (250 + raidLevel) / 250, scaled, ranged),
                attackSpeed = if (type == Baboon.SHAMAN) 8 else 4, bonuses = bonuses, attackStyleType = StyleType.CRUSH,
                poisonImmunity = type != Baboon.SHAMAN, venomImmunity = type != Baboon.SHAMAN)
        }
    }
}
