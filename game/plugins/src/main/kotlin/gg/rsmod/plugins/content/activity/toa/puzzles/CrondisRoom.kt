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
                player.animate(ToaIds.seq(ref.fillSequence.value) ?: Anims.REACH_OUT_GRAB)
                dry += index; waterfall(index, true)
            }
        }
        //Strategies793: using the item saves exactly1 tick on each waterfall/palm interaction.
        if (itemUsed) fill() else scope.queue { wait(1); if (live) fill() }
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
        if (itemUsed) pour() else scope.queue { wait(1); if (live) pour() }
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

    private fun traps(ref: ToaPuzzleReferences.Crondis) {
        for (trap in ref.trapEvents.value) {
            if (tick < trap.cycle) continue
            val phase = (tick - trap.cycle) % ref.trapRepeatCycles.value
            if (trap.cueLoc != null && trap.cueTile != null && (phase == 0 || phase == trap.warningCycles)) {
                val obj = place(trap.cueLoc, trap.cueTile, 10, trap.cueRotation)
                val sequence = if (phase == 0) trap.warningSequence else trap.activeSequence
                if (obj != null && sequence != null) {
                    val animation = ToaIds.seq(sequence)
                    if (animation == null) missing("seq:$sequence (Crondis spear cue)")
                    else players.forEach { it.write(LocAnimMessage(obj, animation)) }
                }
            }
            if (phase == 0) trap.warningLoc?.let { loc -> trap.tiles.forEach { place(loc, it, 22) } }
            if (phase == trap.warningCycles) trap.activeLoc?.let { loc -> trap.tiles.forEach { place(loc, it, 22) } }
            if (trap.activeLoc != null && phase == trap.warningCycles + trap.duration) trap.tiles.forEach { unplace(it) }
            if (phase in trap.warningCycles until trap.warningCycles + trap.duration) {
                players.filter { source(it.tile) in trap.tiles }.forEach { player ->
                    //Water-container Wiki25: rare16% loss, otherwise50%; exact rare probability remains T18.
                    puzzle.obstacle(player.uid, raid.world.currentCycle,
                        if (raid.world.randomDouble() < ref.minorSpillChance.value) 0.84 else 0.5)
                    damage(player, raid.world.random(0..trap.maxHit))
                    // Strategies785: each actual trap hit drains Defence6, Agility3, Prayer2.
                    drain(player, Skills.DEFENCE, 6); drain(player, Skills.AGILITY, 3); drain(player, Skills.PRAYER, 2)
                }
            }
        }
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
        // Crocodile wiki infobox + cache2686 NPC11705 stats/params; opcode77 is HP, not Ranged.
        val CROCODILE_DEF = NpcCombatDef.DEFAULT.copy(lifepoints = 30, stats = listOf(150, 60, 100, 30, 1), attackSpeed = 7,
            attackStyleType = StyleType.CRUSH, poisonImmunity = true, venomImmunity = true,
            bonuses = listOf(0, 0, 0, 0, 0, 150, 350, 350, 5, 150, 0, 100, 0, 0))
    }
}
