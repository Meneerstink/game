package gg.rsmod.plugins.content.activity.toa.puzzles

import gg.rsmod.game.model.PlayerUID
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.combat.NpcCombatDef
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.entity.*
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Anims
import gg.rsmod.game.message.impl.LocAnimMessage
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.activity.toa.*
import gg.rsmod.plugins.content.combat.formula.ElementalWeakness
import gg.rsmod.plugins.content.combat.Combat
import gg.rsmod.plugins.content.skills.farming.logic.handler.WaterHandler

/**
 * Crondis room adapter. Published rules are in CrondisPuzzle; unknown server schedules are supplied separately
 * by the evidence-labelled ToaPuzzleReferences.Crondis profile (N462 T18).
 */
class CrondisRoom(raid: ToaRaid) : ToaPuzzleRoom(raid) {
    val puzzle = CrondisPuzzle<PlayerUID>(raid.roster.size)
    private var reference: ToaPuzzleReferences.Crondis? = null
    private var palm: Npc? = null
    private val crocodiles = linkedMapOf<Npc, Int>()
    private val crocodileAttackers = hashMapOf<Npc, MutableSet<PlayerUID>>()
    private val items = mutableListOf<GroundItem>()
    private val dry = hashSetOf<Int>()
    private val held = hashMapOf<PlayerUID, Item>()

    override fun startPuzzle(): Boolean {
        reference = ToaPuzzleReferences.crondis ?: CrondisDefaults.reference()
        val container = ToaIds.item(CONTAINER) ?: return missing("item:$CONTAINER")
        if ((PALM..PALM + 4).any { ToaIds.npc(it) == null } || ToaIds.npc(CROCODILE) == null)
            return missing("Crondis palm variants/crocodile imports")
        if ((listOf(45398, 45399) + reference!!.trapEvents.value.flatMap { listOfNotNull(it.warningLoc, it.activeLoc) }).any { ToaIds.loc(it) == null })
            return missing("Crondis waterfall/trap locs")
        palm = spawn(PALM, PALM_TILE) ?: return false
        LIVE += this
        //The palm has a Water option and a growth meter, not a combat death/loot route (cache11700..11704).
        palm!!.hitModifier = { hit -> hit.hitmarks.forEach { it.damage = 0 } }
        reference!!.containerTiles.value.forEach { tile ->
            GroundItem(container, 1, instance(tile)).also { item ->
                item.despawnCycles = Int.MAX_VALUE
                scope.spawn(item); items += item
            }
        }
        return true
    }

    private fun syncContainer(player: Player): Boolean {
        val id = ToaIds.item(CONTAINER) ?: return false
        val item = player.inventory.rawItems.firstOrNull { it?.id == id }
        if (item == null) { held.remove(player.uid); puzzle.containerGone(player.uid); return false }
        held[player.uid] = item
        puzzle.container(player.uid, item)
        return true
    }

    override fun leave(player: Player) { held.remove(player.uid); puzzle.remove(player.uid) }

    /**
     * N606 OSRS HP HUD: the Palm of Resourcefulness in cyan, its water against the water it needs ("Palm of Resourcefulness 0 / 200"
     * -> "183 / 200", falling when a crocodile eats - Kaoz Entry guide 0:13-1:33; OSRS npc 11700-11704 param 510 = the HUD name).
     */
    fun hpHud(player: Player): gg.rsmod.plugins.content.mechanics.hud.BossHpHud.Bar? {
        val tree = palm ?: return null
        if (!eligible(player) || puzzle.closed || !tree.isSpawned() || tree.world !== player.world) return null
        return gg.rsmod.plugins.content.mechanics.hud.BossHpHud.Bar(tree, puzzle.growth.toInt(), puzzle.requiredWater,
            gg.rsmod.plugins.content.mechanics.hud.BossHpHud.Style.SHIELD, "Palm of Resourcefulness")
    }
    override fun debugState(player: Player) = "crondis tick=$tick growth=${"%.1f".format(puzzle.growth)}/${puzzle.requiredWater} " +
        "stage=${puzzle.palmStage} water=${"%.1f".format(puzzle.water(player.uid))} crocs=${crocodiles.keys.count { it.isSpawned() && !it.isDead() }}"

    /** Engine option1 uses a pickup condition, bypassing on_ground_item_option entirely. */
    fun canTake(player: Player, item: GroundItem): Boolean {
        if (!eligible(player) || item !in items || !item.isSpawned(raid.world)) return false
        val id = ToaIds.item(CONTAINER) ?: return false
        return !player.inventory.contains(id) //The engine atomically checks capacity, inserts and removes the ground item.
    }

    fun fill(player: Player, obj: GameObject, itemUsed: Boolean = false): Boolean {
        val ref = reference ?: return false
        if (!eligible(player) || !syncContainer(player)) return false
        val index = WATERFALLS.indexOfFirst { instance(it) == obj.tile }
        if (index < 0 || !act(player)) return false
        val container = held[player.uid] ?: return false
        val tile = Tile(player.tile)
        val fill = {
            if (eligible(player) && player.tile == tile && player.inventory.rawItems.any { it === container } &&
                puzzle.fill(player.uid, index, raid.world.currentCycle, ref.refillCycles.value[raid.roster.size - 1])) {
                // OSRS gameplay: Kaoz OSRS DQ6FzzXdA4E, 08:30; only a successful refill announces it.
                player.message("You fill your container.")
                player.animate(ToaIds.seq(ref.fillSequence.value) ?: Anims.REACH_OUT_GRAB)
                dry += index; waterfall(index, true)
            }
        }
        //Strategies793: using the item saves exactly1 tick on each waterfall/palm interaction.
        //WorldQueueTaskSet cycles a new queue's first wait in the same tick, so wait(n+1) = n ticks (ultrareview #6: was 1 tick early).
        if (itemUsed) fill() else scope.queue { wait(2); if (live) fill() }
        return true
    }

    fun water(player: Player, target: Npc, itemUsed: Boolean = false): Boolean {
        if (target !== palm || !eligible(player) || !syncContainer(player) || !act(player)) return false
        val container = held[player.uid] ?: return false
        val tile = Tile(player.tile)
        val pour = {
            if (eligible(player) && player.tile == tile && player.inventory.rawItems.any { it === container }) {
                val amount = puzzle.waterPalm(player.uid, raid.world.currentCycle)
                if (amount > 0.0) {
                    //Wiki Module:Tombs_of_Amascut_loot:2 points per palm HP; per-action floor follows existing ledger (T06).
                    award(player.uid, (2 * amount).toInt())
                    player.animate(reference?.pourSequence?.value?.let(ToaIds::seq) ?: Anims.WATER_PATCH)
                    updatePalm(); clearIf(puzzle.complete)
                }
            }
        }
        //WorldQueueTaskSet cycles a new queue's first wait in the same tick, so wait(n+1) = n ticks (ultrareview #6: was 1 tick early).
        if (itemUsed) pour() else scope.queue { wait(2); if (live) pour() }
        return true
    }

    fun waterCan(player: Player, target: Npc, slot: Int): Boolean {
        if (target !== palm || !eligible(player) || !act(player)) return false
        val item = player.inventory[slot] ?: return false
        val regular = WaterHandler.wateringCans.indexOf(item.id)
        val gricoller = ToaIds.item(GRICOLLER) == item.id
        val success = puzzle.waterFromCan(player.uid, raid.world.currentCycle) consume@ {
            if (player.inventory[slot] !== item) return@consume false
            if (regular > 0) player.inventory[slot] = Item(WaterHandler.wateringCans[regular - 1])
            else if (gricoller) {
                val charges = item.getAttr(ItemAttribute.CHARGES) ?: 0
                if (charges < 10) return@consume false
                item.putAttr(ItemAttribute.CHARGES, charges - 10)
                player.inventory[slot] = item
            } else return@consume false
            true
        }
        if (success) { award(player.uid, 2); updatePalm(); clearIf(puzzle.complete) }
        return success
    }

    fun empty(player: Player): Boolean = eligible(player) && syncContainer(player) && act(player) && puzzle.empty(player.uid)
    fun check(player: Player): Boolean {
        val ref = reference ?: return false
        if (!eligible(player) || !syncContainer(player)) return false
        ref.showWaterPercent.value(player, puzzle.displayedWater(player.uid))
        return true
    }

    override fun cycle() {
        val ref = reference ?: return
        players.forEach(::syncContainer)
        dry.filter { puzzle.waterfallAvailable(it, raid.world.currentCycle) }.toList().forEach {
            dry -= it; waterfall(it, false)
        }
        // Recreate missing source items after pickup/destroy without ever granting a filled container.
        items.forEach { item -> if (!item.isSpawned(raid.world)) { item.currentCycle = 0; scope.spawn(item) } }
        for (spawn in ref.crocodileSpawns.value) {
            if (raid.roster.size >= spawn.minimumPartySize && tick >= spawn.cycle &&
                (tick - spawn.cycle) % ref.crocodileRepeatCycles.value == 0 &&
                crocodiles.keys.count { it.isSpawned() && !it.isDead() } < 8) { // Crocodile wiki: max8 alive.
                val death = ToaIds.npcSeq(CROCODILE, ref.crocodileDeathSequence.value) ?: Anims.ANIM_2038
                spawn(CROCODILE, spawn.tile, CROCODILE_DEF.copy(deathAnimation = listOf(death), waitFullDeathAnimation = true))?.let { npc ->
                    //Crocodile specific article,25June2025 changelog:40% earth weakness, independent of ordinary crocodiles.
                    npc.attr[ElementalWeakness.INSTANCE] = ElementalWeakness.Weakness(ElementalWeakness.Element.EARTH, 40)
                    crocodiles[npc] = tick
                    val oldModifier = npc.hitModifier
                    npc.hitModifier = { hit ->
                        oldModifier?.invoke(hit)
                        (hit.source as? Player)?.takeIf(::eligible)?.let {
                            crocodileAttackers.getOrPut(npc) { mutableSetOf() }.add(it.uid)
                        }
                    }
                }
            }
        }
        traps(ref)
        crocodiles.entries.toList().forEach { (npc, nextAttack) ->
            if (!npc.isSpawned() || npc.isDead()) { crocodiles.remove(npc); crocodileAttackers.remove(npc); return@forEach }
            val nearby = players.filter { npc.tile.isWithinRadius(it.tile, ref.crocodileAggroRange.value) }
            val attackers = crocodileAttackers[npc].orEmpty()
            when (val target = puzzle.target(nearby.map { it.uid }, attackers)) {
                CrondisPuzzle.Target.Palm -> {
                    val tree = palm ?: return@forEach
                    val reaches = Combat.inMeleeReach(npc, tree, 1)
                    if (reaches) npc.stopMovement() else follow(npc, tree.tile)
                    if (tick >= nextAttack && reaches) {
                        npc.animate(ToaIds.npcSeq(CROCODILE, ref.crocodileAttackSequence.value) ?: Anims.ANIM_2039)
                        puzzle.eatPalm(ref.palmBiteWater.value); updatePalm(); crocodiles[npc] = tick + 7
                    }
                }
                is CrondisPuzzle.Target.Player -> {
                    val player = nearby.firstOrNull { it.uid == target.player } ?: return@forEach
                    follow(npc, player.tile)
                    if (tick >= nextAttack && inMeleeRange(npc, player.tile)) {
                        crocodiles[npc] = tick + 7 // Crocodile wiki attack speed7; no RL scaling.
                        val protected = player.isProtectedFrom(CombatClass.MELEE)
                        npc.animate(ToaIds.npcSeq(CROCODILE, ref.crocodileAttackSequence.value) ?: Anims.ANIM_2039)
                        attack(npc, player, puzzle.crocodileDamage(player.uid, raid.world.currentCycle, false), CombatClass.MELEE,
                            fixed = true, protectedNumerator = 1, protectedDenominator = 3) { lands, _, _ ->
                            if (lands) {
                                if (protected) drain(player, Skills.PRAYER, 12)
                                puzzle.crocodileBite(player.uid)
                            }
                        }
                    }
                }
                null -> npc.stopMovement()
            }
        }
    }

    /**
     * TOA-1911 (eigenaar 19-11 punt 20: 'bewegende speren in het zuiden visueel niet goed (gaten tijdens bewegen, tegel klopt niet met
     * OSRS)'). The old schedule fired ONE spear row every 2 ticks (a 20-tick crawl, always 1-2 spears out), cut every thrust off after
     * 1 tick with the second sequence 9564, ran the south wave away from the waterfall, and drew the poison as the red vine FLOOR loc
     * 45418/45419 (toa_crondis_poison_tile) - a brown band over the whole width. OSRS (Kaoz Entry guide 7:37-7:52, 60 fps at 1x by the
     * stream clock; cache2686 dump.seq/dump.spot):
     * - spears: every [SPEAR_PERIOD] ticks all ten spears of a corridor thrust in one wave from the exit end towards the waterfall within
     *   ~2 ticks (first 7:49.15, last 7:50.25; 7:37 / 7:43.3 / 7:49.15 = ~6 s apart). One sequence per thrust: crondis_spear_trap_activate
     *   9563 (thrust + retract 1.8 s, rest, wind-up = 5.9 s = the period); the spear is out ~1.4 s -> it hits on the tick after the trigger.
     * - poison: the waterfall statue sends crondis_column_trap_anim 2129/2130 (model 46216, crondis_poison_spotanim: 16 x 5 + 15 cycles,
     *   the light warning cloud then the dark one) down 2-3 of the 5 columns, each next tile 10 client cycles later (the same 10-cycle
     *   stagger as crondis_poison_tile_activate_01..10), every [POISON_PERIOD] ticks (7:43.0 / 7:46.6 / 7:50.6). Only the dark cloud
     *   hurts (Strategies 785): tile k from the waterfall is dark from ~0.6 s to ~1.6 s after its start.
     * ONZEKER: the exact damage ticks, the column choice (video: columns 1+3, 0+2+3) and the offset between corridors are measured or
     * inferred, not published.
     */
    private fun traps(ref: ToaPuzzleReferences.Crondis) {
        spears(ref)
        poison(ref)
    }

    private fun spears(ref: ToaPuzzleReferences.Crondis) {
        for (trap in ref.trapEvents.value) {
            val cueLoc = trap.cueLoc ?: continue
            val cue = trap.cueTile ?: continue
            // Rows from the exit end (0) to the waterfall (9): south corridors run 5267 -> 5258, north ones 5293 -> 5302.
            val row = if (cue.z > PALM_TILE.z) cue.z - NORTH_SPEARS_FIRST else SOUTH_SPEARS_LAST - cue.z
            val offset = trap.cycle + row / 4 // trap.cycle = the schedule's start (tests postpone it)
            if (tick < offset) continue
            val phase = (tick - offset) % SPEAR_PERIOD
            if (phase == 0) {
                val obj = place(cueLoc, cue, 10, trap.cueRotation)
                val animation = trap.activeSequence?.let { ToaIds.seq(it) }
                if (obj != null && animation != null) players.forEach { it.write(LocAnimMessage(obj, animation)) }
                else if (animation == null) missing("seq:${trap.activeSequence} (Crondis spear)")
            }
            if (phase == 1) players.filter { source(it.tile) in trap.tiles }.forEach { trapHit(ref, it, trap.maxHit) }
        }
    }

    private class PoisonWave(val start: Int, val lane: Int, val north: Boolean, val columns: List<Int>)
    private val poisonWaves = mutableListOf<PoisonWave>()

    private fun poison(ref: ToaPuzzleReferences.Crondis) {
        val start = ref.trapEvents.value.minOfOrNull { it.cycle } ?: return
        if (tick >= start && (tick - start) % POISON_PERIOD == 0) {
            for (lane in LANES) for (north in listOf(false, true)) {
                val columns = (0..4).shuffled(java.util.Random(raid.world.random.nextLong())).take(if (raid.world.random(1) == 0) 2 else 3).sorted()
                val wave = PoisonWave(tick, lane, north, columns)
                poisonWaves += wave
                players.forEach { viewer ->
                    for (column in columns) for (k in 0 until POISON_LENGTH) {
                        // 2129 carries the cloud's synth 6509/6495 on frames 1/6 - once per column, 2130 is the same cloud without sound.
                        ToaPuzzleVisuals.spotDelayed(viewer, if (k == 0) 2129 else 2130, instance(poisonTile(wave, column, k)), k * 10)
                    }
                }
            }
        }
        poisonWaves.removeAll { tick - it.start > 8 }
        val hit = hashSetOf<PlayerUID>()
        for (wave in poisonWaves) {
            val t = tick - wave.start
            if (t < 1) continue
            // Dark on tile k while k*0.2 s + 0.6 <= t*0.6 s <= k*0.2 s + 1.6  ->  3t-8 <= k <= 3t-3.
            val dark = (3 * t - 8).coerceAtLeast(0)..(3 * t - 3).coerceAtMost(POISON_LENGTH - 1)
            if (dark.isEmpty()) continue
            val tiles = wave.columns.flatMap { column -> dark.map { poisonTile(wave, column, it) } }.toSet()
            players.filter { it.uid !in hit && source(it.tile) in tiles }.forEach { hit += it.uid; trapHit(ref, it, POISON_MAX_HIT) }
        }
    }

    /** Column 0..4 across the corridor (x lane..lane+4), tile k counted from the waterfall: south 5255 -> 5270, north 5305 -> 5290. */
    private fun poisonTile(wave: PoisonWave, column: Int, k: Int) =
        Tile(wave.lane + column, if (wave.north) 5305 - k else 5255 + k)

    private fun trapHit(ref: ToaPuzzleReferences.Crondis, player: Player, maxHit: Int) {
        //Water-container Wiki25: rare16% loss, otherwise50%; exact rare probability remains T18.
        puzzle.obstacle(player.uid, raid.world.currentCycle,
            if (raid.world.randomDouble() < ref.minorSpillChance.value) 0.84 else 0.5)
        damage(player, raid.world.random(0..maxHit))
        // Strategies785: each actual trap hit drains Defence6, Agility3, Prayer2.
        drain(player, Skills.DEFENCE, 6); drain(player, Skills.AGILITY, 3); drain(player, Skills.PRAYER, 2)
    }

    private fun waterfall(index: Int, isDry: Boolean) {
        // 40-loc-placements45398: southern rot0, northern rot2. 50-loc-defs45399 is its dry replacement.
        place(if (isDry) 45399 else 45398, WATERFALLS[index], rotation = if (index % 2 == 0) 0 else 2)
    }
    private fun updatePalm() {
        val tree = palm ?: return
        ToaIds.npc(PALM + puzzle.palmStage)?.let { tree.setTransmogId(it) }
    }
    override fun closePuzzle() { puzzle.close(); held.clear(); crocodiles.clear(); crocodileAttackers.clear(); items.clear(); dry.clear(); palm = null }

    companion object {
        /** N606: rooms with a palm, for the HP HUD provider (weak: a finished raid's room goes with it). */
        val LIVE: MutableSet<CrondisRoom> = java.util.Collections.newSetFromMap(java.util.WeakHashMap<CrondisRoom, Boolean>())
        // 09b-crondis-brontekst Wiki item/palm/crocodile infoboxes; local IDs ONLY via ToaIds.
        const val CONTAINER = 27295
        //RuneLite gameval ItemID.ZEAH_WATERINGCAN=13353; Palm wiki: ten charges per water point.
        const val GRICOLLER = 13353
        const val PALM = 11700
        const val CROCODILE = 11705
        // Palm wiki map centre3936,5280, size5 from cache2/9/11700 => southwest3934,5278.
        val PALM_TILE = Tile(3934, 5278)
        // Cache2686 5/15698/1, 40-loc-placements.tsv: every45398, preserving north/south order.
        val WATERFALLS = listOf(Tile(3926, 5250), Tile(3926, 5306), Tile(3940, 5250), Tile(3940, 5306))
        // TOA-1911 trap schedule (see traps()): corridors x LANES..+4, spear rows 5258..5267 (south) / 5293..5302 (north).
        val LANES = listOf(3927, 3941)
        const val SOUTH_SPEARS_LAST = 5267
        const val NORTH_SPEARS_FIRST = 5293
        const val SPEAR_PERIOD = 10
        const val POISON_PERIOD = 6
        const val POISON_LENGTH = 16
        const val POISON_MAX_HIT = 6
        // Crocodile wiki infobox + cache2686 NPC11705 stats/params; opcode77 is HP, not Ranged.
        val CROCODILE_DEF = NpcCombatDef.DEFAULT.copy(lifepoints = 30, stats = listOf(150, 60, 100, 30, 1), attackSpeed = 7,
            attackStyleType = StyleType.CRUSH, poisonImmunity = true, venomImmunity = true,
            bonuses = listOf(0, 0, 0, 0, 0, 150, 350, 350, 5, 150, 0, 100, 0, 0))
    }
}
