package gg.rsmod.plugins.content.activity.toa.puzzles

import gg.rsmod.game.model.PlayerUID
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.combat.NpcCombatDef
import gg.rsmod.game.model.entity.*
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.activity.toa.*
import gg.rsmod.plugins.content.activity.toa.puzzles.ScabarasReferences.Kind
import gg.rsmod.plugins.content.activity.toa.puzzles.ScabarasPuzzle.Result
import gg.rsmod.plugins.content.combat.setCombatTarget
import gg.rsmod.plugins.content.combat.combatRaycast
import gg.rsmod.plugins.content.combat.formula.ElementalWeakness
import gg.rsmod.plugins.api.cfg.Anims

/** Five puzzle kinds sharing a generation, branched gates and a scope; inferred layout details are labelled in the profile. */
class ScabarasRoom(raid: ToaRaid) : ToaPuzzleRoom(raid) {
    private var reference: ScabarasReferences.Profile? = null
    private lateinit var layout: ScabarasReferences.Layout
    private lateinit var initial: ScabarasReferences.Initial
    private lateinit var memory: ScabarasPuzzle.Memory
    private lateinit var obelisks: ScabarasPuzzle.Obelisks
    private lateinit var lights: ScabarasPuzzle.Lights
    private lateinit var addition: ScabarasPuzzle.Addition
    private lateinit var matching: ScabarasPuzzle.Matching
    private lateinit var progression: ScabarasGateState
    private val finished get() = progression.finished
    private val skipped get() = progression.skipped
    private val obeliskNpcs = mutableListOf<Npc>()
    private val scarabs = linkedMapOf<Npc, Int>()
    private val pending = mutableListOf<Pair<Int, () -> Unit>>()
    private val contacts = ScabarasPuzzle.Contacts<Player, Tile>()
    private val lastContactCycle = hashMapOf<Pair<Kind, Tile>, Int>()
    private val revealed = hashMapOf<Int, Int>()
    private var mismatch = false
    private var revealGeneration = 0
    private val gateStates = hashMapOf<Int, Boolean>()

    override fun startPuzzle(): Boolean {
        val ref = ScabarasReferences.profile ?: ScabarasDefaults.reference()
        reference = ref
        layout = ref.layout.value(raid.roster.size)
        progression = ScabarasGateState(raid.roster.size, layout.soloSkipsOnClear, layout.numberPrerequisites)
        initial = ref.initial.value(raid.roster.size, scope.run.generation)
        memory = ScabarasPuzzle.Memory(layout.memoryTiles.size, ref.memoryResetOnWrong.value)
        obelisks = ScabarasPuzzle.Obelisks(initial.obeliskOrder, ref.obelisksResetOnWrong.value)
        lights = ScabarasPuzzle.Lights(initial.lights)
        addition = ScabarasPuzzle.Addition(initial.additionTarget)
        matching = ScabarasPuzzle.Matching(initial.leftSymbols, initial.rightSymbols, raid.roster.size,
            if (raid.roster.size == 1) initial.soloPreSolved else emptySet())
        if (raid.roster.size == 1) {
            //Wiki939 moves the statue forward one tile solo;40-loc45205 fixes its original anchor. Scope restores it on wipe.
            place(45205, layout.soloStatueFrom, 10, 3); unplace(layout.soloStatueFrom, 10)
            place(45205, layout.soloStatueTo, 10, 3)
        }
        //50-loc-defs45338/39/55: memory button, number tablet, final button. Locations are source-supplied dynamic placements.
        place(45338, layout.memoryButton); place(45339, layout.tablet)
        if (ref.requiresFinalButton.value) place(45355, layout.finalButton)
        layout.memoryTiles.forEach { place(ref.memoryNeutralLoc.value, it, 22) }
        layout.obelisks.forEachIndexed { index, tile ->
            //Cache2/9/11698:120 HP and Hit option. It is an interaction target, not a damage/XP source.
            val npc = spawn(OBELISK, tile, NpcCombatDef.DEFAULT.copy(lifepoints = 120, xpMultiplier = 0.0)) ?: return false
            npc.hitModifier = { hit ->
                val player = hit.source as? Player
                val landed = hit.hitmarks.any { it.damage > 0 }
                hit.hitmarks.forEach { it.damage = 0 }
                if (landed && player != null && eligible(player)) strike(player, index)
            }
            obeliskNpcs += npc
        }
        drawLights(); drawAddition(); drawMatching(); drawGates()
        if (lights.complete) finish(Kind.LIGHTS)
        return true
    }

    fun button(player: Player, obj: GameObject): Boolean {
        if (!eligible(player)) return false
        val tile = source(obj.tile) ?: return false
        if (tile == layout.memoryButton && Kind.MEMORY !in finished && Kind.MEMORY !in skipped && act(player)) {
            val ref = reference!!
            val sequence = ref.memoryOrder.value(memory.generation + 1)
            val token = memory.show(sequence) ?: return false
            layout.memoryTiles.forEach { unplace(it, 10) }
            scope.queue {
                //WorldQueueTaskSet cycles a new coroutine's first wait immediately. Enter playback after that startup step,
                //otherwise flashCycles=1 adds AND removes the first plate in one tick: players see only4 of the5 flashes.
                wait(1)
                for (cell in sequence) {
                    if (!live || memory.generation != token) return@queue
                    place(ref.memoryShownLoc.value, layout.memoryTiles[cell], 10)
                    wait(ref.flashCycles.value)
                    if (!live || memory.generation != token) return@queue
                    unplace(layout.memoryTiles[cell], 10)
                    if (ref.betweenFlashesCycles.value > 0) wait(ref.betweenFlashesCycles.value)
                }
                memory.finishPlayback(token)
            }
            return true
        }
        if (tile == layout.finalButton && act(player) && allFinished()) {
            clearIf(true); return true
        }
        return false
    }

    fun inspect(player: Player, obj: GameObject): Boolean {
        if (!eligible(player) || source(obj.tile) != layout.tablet || !addition.unlock(previousFinished())) return false
        reference!!.showTablet.value(player, addition.target)
        return true
    }

    fun flip(player: Player, obj: GameObject): Boolean {
        if (!eligible(player) || Kind.LIGHTS in skipped) return false
        val index = layout.lights.indexOf(source(obj.tile))
        if (index < 0 || !act(player)) return false
        if (!claim(Kind.LIGHTS, layout.lights[index])) return false
        val result = lights.flip(index)
        if (result == Result.IGNORED) return false
        damage(player, 20) //Strategies920: exactly20 unavoidable damage for a single-plate Flip.
        drawLights()
        if (result == Result.SOLVED) finish(Kind.LIGHTS)
        return true
    }

    fun activate(player: Player, obj: GameObject): Boolean {
        if (!eligible(player) || !progression.canMatch || !act(player) || Kind.MATCHING in skipped) return false
        val tile = source(obj.tile) ?: return false
        for (board in 0..1) {
            val index = layout.matchingButtons[board].indexOf(tile)
            if (index >= 0 && claim(Kind.MATCHING, tile)) { match(player, board, index); return true }
        }
        return false
    }

    private fun match(player: Player, board: Int, index: Int) {
        if (mismatch) { revealed.clear(); mismatch = false }
        val generation = ++revealGeneration
        val result = matching.activate(board, index)
        if (result == Result.IGNORED) { drawMatching(); return }
        revealed[board] = index
        if (result == Result.WRONG) {
            mismatch = true
            //Perfect_Scabaras Wiki: final matching puzzle cannot damage a player, even on a mismatch.
            pending += (tick + reference!!.matchingMismatchCycles.value) to {
                if (revealGeneration == generation) { revealed.clear(); mismatch = false; drawMatching() }
            }
        }
        drawMatching()
        if (result == Result.SOLVED) finish(Kind.MATCHING)
    }

    fun hitObelisk(player: Player, npc: Npc): Boolean {
        if (!eligible(player) || npc !in obeliskNpcs || obelisks.complete || Kind.OBELISKS in skipped) return false
        player.setCombatTarget(npc) //A real weapon attack must reach/land; merely clicking a distant NPC cannot solve it.
        return true
    }

    private fun strike(player: Player, index: Int) {
        val ref = reference ?: return
        if (Kind.OBELISKS in skipped || Kind.OBELISKS in finished || obelisks.complete) return
        val tile = layout.obelisks[index]
        if (!claim(Kind.OBELISKS, tile)) return
        val result = obelisks.strike(index)
        if (result == Result.WRONG) {
            val target = source(player.tile) ?: return
            if (target !in ref.rockSafeTiles.value) {
                players.forEach { ToaPuzzleVisuals.spot(it, ref.rockWarningSpot.value, instance(target)) }
                pending += (tick + ref.rockDelay.value) to {
                    players.filter { source(it.tile) == target }.forEach { damage(it, raid.world.random(0..ref.rockMaxHit.value)) }
                }
            }
        }
        //NPC11699 is the lit/struck counterpart of11698, not an independently spawned actor.
        obeliskNpcs.forEachIndexed { i, npc ->
            val lit = initial.obeliskOrder.indexOf(i) < obelisks.cursor
            ToaIds.npc(if (lit) OBELISK_LIT else OBELISK)?.let(npc::setTransmogId)
        }
        if (result == Result.SOLVED) finish(Kind.OBELISKS)
    }

    fun shortcut(player: Player, obj: GameObject): Boolean {
        if (!eligible(player)) return false
        val objectSource = source(obj.tile) ?: return false
        val playerSource = source(player.tile) ?: return false
        val route = layout.shortcuts.firstOrNull { it.objectTile == objectSource && it.from.contains(playerSource) &&
            (finished + skipped).containsAll(it.requires) } ?: return false
        if (!act(player)) return false
        val animation = ToaIds.seq(route.sequence) ?: Anims.REACH_OUT_GRAB //ONZEKER T02 legacy reach if stepping-stone seq769 not yet mapped.
        player.animate(animation)
        scope.queue {
            wait(route.duration)
            if (eligible(player) && source(player.tile)?.let(route.from::contains) == true) player.teleportTo(instance(route.destination))
        }
        return true
    }

    override fun cycle() {
        pending.filter { it.first <= tick }.toList().forEach { pending.remove(it); if (live) it.second() }
        for ((player, tile) in contacts.update(players.mapNotNull { player -> source(player.tile)?.let { player to it } })) {
            if (!live) return
            step(player, tile)
        }
        if (live) scarabCycle()
    }

    private fun step(player: Player, tile: Tile) {
        val ref = reference ?: return
        val memoryIndex = layout.memoryTiles.indexOf(tile)
        if (memoryIndex >= 0 && Kind.MEMORY !in skipped && claim(Kind.MEMORY, tile)) when (memory.step(memoryIndex)) {
            Result.WRONG -> {
                layout.memoryTiles.forEach { unplace(it, 10) }
                players.forEach { ToaPuzzleVisuals.spot(it, ref.rockWarningSpot.value, player.tile) }
                damage(player, ref.memoryDamage.value)
            }
            Result.PROGRESS -> place(ref.memoryPressedLoc.value, tile, 10)
            Result.SOLVED -> {
                //SequencePuzzleSolver observes simultaneous DOWN spawns when the sequence is complete.
                layout.memoryTiles.forEach { place(ref.memoryShownLoc.value, it, 10) }
                finish(Kind.MEMORY)
            }
            else -> Unit
        }
        val lightIndex = layout.lights.indexOf(tile)
        if (lightIndex >= 0 && Kind.LIGHTS !in skipped && claim(Kind.LIGHTS, tile)) {
            val result = lights.step(lightIndex); drawLights()
            if (result == Result.SOLVED) finish(Kind.LIGHTS)
        }
        val number = layout.additionTiles.indexOf(tile)
        if (number >= 0 && Kind.ADDITION !in skipped && claim(Kind.ADDITION, tile)) {
            addition.unlock(previousFinished())
            val result = addition.step(number)
            if (result == Result.WRONG) damage(player, ref.additionDamage.value)
            if (result != Result.IGNORED) drawAddition()
            if (result == Result.SOLVED) finish(Kind.ADDITION)
        }
        if (progression.canMatch && Kind.MATCHING !in skipped) for (board in 0..1) {
            val index = layout.matchingButtons[board].indexOf(tile)
            if (index >= 0 && claim(Kind.MATCHING, tile)) match(player, board, index)
        }
    }

    private fun previousFinished(): Boolean = progression.canReadNumber
    private fun claim(kind: Kind, tile: Tile): Boolean {
        val key = kind to tile
        if (lastContactCycle[key] == raid.world.currentCycle) return false
        lastContactCycle[key] = raid.world.currentCycle
        return true
    }
    private fun allFinished(): Boolean = progression.complete
    private fun finish(kind: Kind) {
        if (!progression.finish(kind)) return
        drawGates()
        //ONZEKER T06: Wiki loot module estimates300 puzzle points/member (3x100); exact per-action allocation unprovided.
        if (progression.complete) players.forEach { award(it.uid, 300) }
        if (allFinished() && reference?.requiresFinalButton?.value == false) clearIf(true)
    }
    private fun drawGates() {
        layout.gates.forEachIndexed { index, gate ->
            val open = (finished + skipped).containsAll(gate.requires)
            if (gateStates[index] != open) {
                gateStates[index] = open
                //Cache45335 is unnamed barrier FX, not a proven nonblocking open-door definition.
                //Removing the owned closed barrier opens its collision as well as its appearance.
                if (open) unplace(gate.tile, gate.shape)
                else place(gate.closedLoc, gate.tile, gate.shape, gate.rotation)
            }
        }
    }
    private fun drawLights() {
        val ref = reference ?: return
        layout.lights.forEachIndexed { cell, tile ->
            place(ref.lightsUnlitLoc.value, tile, 22)
            if (lights.lit and (1 shl cell) != 0) place(ref.lightsLitLoc.value, tile, 10) else unplace(tile, 10)
        }
    }
    private fun drawAddition() {
        val ref = reference ?: return
        layout.additionTiles.forEachIndexed { cell, tile ->
            val value = ScabarasPuzzle.NUMBER_GRID[cell]
            place(ref.numberLocs.value.getValue(value), tile, 22)
            if (cell in addition.selectedCells) place(ref.numberSelectedLocs.value.getValue(value), tile, 10) else unplace(tile, 10)
        }
    }
    private fun drawMatching() {
        val ref = reference ?: return
        for (board in 0..1) for (cell in 0..8) {
            val symbol = (if (board == 0) initial.leftSymbols else initial.rightSymbols)[cell]
            val loc = if (matching.matched(symbol) || revealed[board] == cell) ref.matchingSymbolLocs.value.getValue(symbol)
                else ref.matchingButtonLocs.value.getValue(symbol)
            place(loc, layout.matchingButtons[board][cell], 22)
            if (matching.matched(symbol) || revealed[board] == cell)
                place(ref.matchingSymbolFx.value.getValue(symbol), layout.matchingButtons[board][cell], 10)
            else unplace(layout.matchingButtons[board][cell], 10)
        }
    }

    private fun scarabCycle() {
        val ref = reference ?: return
        for (spawn in ref.scarabSpawns.value) {
            if (raid.roster.size >= spawn.minimumPartySize && tick >= spawn.cycle &&
                (tick - spawn.cycle) % ref.scarabRepeatCycles.value == 0) {
                val death = ToaIds.npcSeq(SCARAB, ref.scarabDeathSequence.value) ?: -1
                val def = definition(raid.settings.raidLevel, raid.roster.size).copy(deathAnimation = listOf(death), waitFullDeathAnimation = true)
                spawn(SCARAB, spawn.tile, def)?.let {
                    //Scarab wiki25June2025 changelog:40% Fire, per instance to avoid by-name collisions with other scarabs.
                    it.attr[ElementalWeakness.INSTANCE] = ElementalWeakness.Weakness(ElementalWeakness.Element.FIRE, 40)
                    scarabs[it] = tick
                }
            }
        }
        scarabs.entries.toList().forEach { (npc, next) ->
            if (!npc.isSpawned() || npc.isDead()) { scarabs.remove(npc); return@forEach }
            val target = players.minByOrNull { npc.tile.getDistance(it.tile) } ?: return@forEach
            if (!npc.combatRaycast(target, ref.scarabRange.value, projectile = true)) { follow(npc, target.tile); return@forEach }
            npc.stopMovement()
            if (tick < next) return@forEach
            scarabs[npc] = tick + 4 //Scarab wiki attack speed4; overhead Missiles completely blocks it (Strategies905).
            animate(npc, ref.scarabAttackSequence.value); ref.scarabLaunch.value(npc, target)
            pending += (tick + ref.scarabHitDelay.value) to {
                if (eligible(target) && npc.isSpawned() && !npc.isDead()) attack(npc, target,
                    6 * (250 + minOf(raid.settings.raidLevel, 375)) / 250, CombatClass.RANGED)
            }
        }
    }

    override fun closePuzzle() {
        if (::memory.isInitialized) memory.close()
        if (::obelisks.isInitialized) obelisks.close()
        if (::lights.isInitialized) lights.close()
        if (::addition.isInitialized) addition.close()
        if (::matching.isInitialized) matching.close()
        pending.clear(); contacts.clear(); lastContactCycle.clear(); revealed.clear(); scarabs.clear(); obeliskNpcs.clear()
    }
    companion object {
        //Cache2686 NPC11697-11699,07-scabaras-brontekst,50-npc-defs. Local IDs exclusively through ToaIds.
        const val SCARAB = 11697
        const val OBELISK = 11698
        const val OBELISK_LIT = 11699

        /** Scarab infobox,07-scabaras +10-schaal: normal NPC HP/group and HP/defence/accuracy scaling; no path levels. */
        fun definition(raidLevel: Int, partySize: Int): NpcCombatDef {
            require(raidLevel in 0..600 && partySize in 1..8)
            val group = 10 + 9 * minOf(partySize - 1, 2) + 6 * maxOf(partySize - 3, 0)
            fun scaled(level: Int) = level * (250 + raidLevel) / 250
            //ONZEKER T04: floor once after the combined HP factors, consistent with the raid's other directors.
            return NpcCombatDef.DEFAULT.copy(lifepoints = 12 * (250 + raidLevel) * group / 2500,
                stats = listOf(scaled(20), 32, scaled(28), scaled(1), scaled(50)), attackSpeed = 4,
                bonuses = listOf(0, 0, 0, 0, 150, 0, 0, 0, 0, 0, 0, 0, 0, 0),
                poisonImmunity = true, venomImmunity = true)
        }
    }
}
