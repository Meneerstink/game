package gg.rsmod.plugins.content.areas.wilderness.bosses

import gg.rsmod.game.action.EquipAction
import gg.rsmod.game.model.Direction
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.combat.CombatScript
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.combat.WeaponStyle
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.cfg.Sfx
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.*
import gg.rsmod.plugins.content.combat.formula.MagicCombatFormula
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.formula.RangedCombatFormula
import gg.rsmod.plugins.content.combat.strategy.MagicCombatStrategy

/**
 * N522 nachtrun deel 1 (owner 2026-11-02 15:4x "Chaos Elemental VOLLEDIG OSRS (drops + mechanics)"): the OSRS Chaos Elemental (OSRS npc
 * 2054, imported as [Npcs.CHAOS_ELEMENTAL] with OsrsNpcImportTool batch chaos-elemental, tx-20261104-220735). The 667 npc 3200 is the later
 * HD remodel and no longer spawns.
 *
 * Mechanics - OSRS Wiki "Chaos Elemental" + "Chaos Elemental/Strategies" (2026-11-04):
 * - Discord: "Deals either Magic, Ranged, or Melee damage", magic "most frequently", "hit into the 20s" (max 28), follows the player and
 *   travels through walls. Style split (no Jagex number): Kronos-184 bosses/ChaosElemental.triAttack - magic 6/10, else ranged / melee 1/2.
 * - Confusion: "Teleports the player somewhere nearby, away from it" - Kronos teleportAttack: 1 to 4 tiles straight away from the
 *   elemental's footprint while each step is free, 1 in 12 attacks.
 * - Madness: "Unequips up to four equipped items", "The weapon of the player is always prioritised", "it cannot unequip the player's items
 *   if there is no room in the inventory", "does not deal any damage" (Strategies). Kronos disarmAttack: chosen 1 in 10 while the target has
 *   a free inventory slot; slots weapon, shield, body, head, legs, hands, feet.
 * - Range 8 (Kronos withinDistance(8) / follow(8)); attack speed 4, max 28 (infobox).
 * Ids: RuneLite gameval AnimationID CHAOSELEMENTAL_ATTACK 3146 / _DEATH 3147 and SpotanimID CHAOSELEMENTAL_SPOTANIM_MADNESS_CASTING 550 ..
 * _DISCORD_IMPACT 558, all imported from the OSRS cache (the 667 spotanims with these ids are the HD remakes). Projectile shape: Kronos
 * Projectile(gfx, 80, 32, 40, 60, 6, 16, 64) = start height 80, end height 32, delay 40, curve 16.
 * Sounds: OSRS synths 344-353 (byte-size identical in both caches; names from the OSRS rsmod donor Sfx: CONFUSION_CAST 344, CONFUSION_IMPACT
 * 346, DISCORD 348, DISCORD_IMPACT 350, MADNESS_CAST 352, MADNESS_IMPACT 353, HIT 351, DEATH 347). Messages: Kronos (the Wiki lists none).
 */
object ChaosElementalCombat : CombatScript() {
    override val ids = intArrayOf(Npcs.CHAOS_ELEMENTAL)

    const val ANIM_ATTACK = 16233 // OSRS 3146 CHAOSELEMENTAL_ATTACK
    const val ANIM_DEATH = 16234 // OSRS 3147 CHAOSELEMENTAL_DEATH

    const val GFX_MADNESS_CAST = 3220 // OSRS 550
    const val GFX_MADNESS_TRAVEL = 3192 // OSRS 551 (wildy-bosses-1)
    const val GFX_MADNESS_IMPACT = 3198 // OSRS 552 (wildy-bosses-1)
    const val GFX_CONFUSION_CAST = 3193 // OSRS 553 (wildy-bosses-1)
    const val GFX_CONFUSION_TRAVEL = 3194 // OSRS 554 (wildy-bosses-1)
    const val GFX_CONFUSION_IMPACT = 3221 // OSRS 555
    const val GFX_DISCORD_CAST = 3222 // OSRS 556
    const val GFX_DISCORD_TRAVEL = 3223 // OSRS 557
    const val GFX_DISCORD_IMPACT = 3224 // OSRS 558

    const val MAX_HIT = 28
    const val ATTACK_RANGE = 8
    const val MADNESS_MAX_ITEMS = 4
    const val CONFUSION_MAX_STEPS = 4

    const val MSG_MADNESS = "The Chaos Elemental unequips one of your items!"
    const val MSG_CONFUSION = "The Chaos Elemental teleports you!"

    /** Kronos DISARM_SLOTS; the weapon always first (OSRS Wiki Strategies). */
    val MADNESS_SLOTS =
        listOf(EquipmentType.WEAPON, EquipmentType.SHIELD, EquipmentType.CHEST, EquipmentType.HEAD, EquipmentType.LEGS, EquipmentType.GLOVES, EquipmentType.BOOTS)
            .map { it.id }

    enum class Attack { DISCORD, CONFUSION, MADNESS }

    enum class Style { MAGIC, RANGED, MELEE }

    /** Kronos attack(): madness 1/10 while the target has a free slot ([madnessRoll] 0..9), else confusion 1/12 ([confusionRoll] 0..11). */
    fun choose(freeSlot: Boolean, madnessRoll: Int, confusionRoll: Int): Attack =
        when {
            freeSlot && madnessRoll == 0 -> Attack.MADNESS
            confusionRoll == 0 -> Attack.CONFUSION
            else -> Attack.DISCORD
        }

    /** Kronos triAttack: magic 6/10 ([roll10] 0..5), else ranged or melee half each ([roll2] 0/1). */
    fun discordStyle(roll10: Int, roll2: Int): Style =
        when {
            roll10 < 6 -> Style.MAGIC
            roll2 == 0 -> Style.RANGED
            else -> Style.MELEE
        }

    /** The slots Madness takes, in order: the weapon first, then the other worn slots shuffled by [shuffle]; at most 4, one per free slot. */
    fun madnessSlots(player: Player, shuffle: (List<Int>) -> List<Int> = { it.shuffled() }): List<Int> {
        val worn = MADNESS_SLOTS.filter { player.equipment[it] != null }
        val weapon = worn.filter { it == EquipmentType.WEAPON.id }
        val others = shuffle(worn - EquipmentType.WEAPON.id)
        return (weapon + others).take(minOf(MADNESS_MAX_ITEMS, player.inventory.freeSlotCount))
    }

    override suspend fun handleSpecialCombat(it: QueueTask) {
        val npc = it.npc
        var target = npc.getCombatTarget() ?: return
        val world = npc.world
        while (it.awaitAttackDelay(npc) && npc.canEngageCombat(target)) {
            npc.facePawn(target)
            if (npc.moveToAttackRange(it, target, distance = ATTACK_RANGE, projectile = true)) {
                val freeSlot = target is Player && target.inventory.freeSlotCount > 0
                when (choose(freeSlot, world.random(9), world.random(11))) {
                    Attack.DISCORD -> discord(npc, target, discordStyle(world.random(9), world.random(1)))
                    Attack.CONFUSION -> confusion(npc, target)
                    Attack.MADNESS -> madness(npc, target as Player)
                }
            }
            npc.postAttackLogic(target)
            it.wait(npc.combatDef.attackSpeed)
            target = npc.getCombatTarget() ?: break
        }
        npc.resetFacePawn()
        npc.removeCombatTarget()
    }

    /** Cast animation + cast graphic + the travelling projectile; returns the hit delay in ticks. */
    private fun cast(npc: Npc, target: Pawn, castGfx: Int, travel: Int, castSound: Int): Int {
        npc.animate(ANIM_ATTACK)
        npc.graphic(castGfx)
        npc.world.spawn(npc.createProjectile(target, travel, startHeight = 80, endHeight = 32, delay = 40, angle = 16))
        if (target is Player) target.playSound(castSound)
        return MagicCombatStrategy.getHitDelay(npc.getFrontFacingTile(target), target.getCentreTile())
    }

    private fun discord(npc: Npc, target: Pawn, style: Style) {
        when (style) {
            Style.MAGIC -> npc.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.ACCURATE)
            Style.RANGED -> npc.prepareAttack(CombatClass.RANGED, StyleType.RANGED, WeaponStyle.ACCURATE)
            Style.MELEE -> npc.prepareAttack(CombatClass.MELEE, StyleType.CRUSH, WeaponStyle.ACCURATE)
        }
        val delay = cast(npc, target, GFX_DISCORD_CAST, GFX_DISCORD_TRAVEL, Sfx.CHAOS_ELEMENTAL_DISCORD)
        val accuracy =
            when (style) {
                Style.MAGIC -> MagicCombatFormula.getAccuracy(npc, target)
                Style.RANGED -> RangedCombatFormula.getAccuracy(npc, target)
                Style.MELEE -> MeleeCombatFormula.getAccuracy(npc, target)
            }
        val land = accuracy >= npc.world.randomDouble()
        val hitType = when (style) { Style.MAGIC -> HitType.MAGIC; Style.RANGED -> HitType.RANGE; Style.MELEE -> HitType.MELEE }
        if (target is Player) target.playSound(Sfx.CHAOS_ELEMENTAL_DISCORD_IMPACT, delay = delay * 30)
        npc.dealHit(target = target, maxHit = MAX_HIT.toDouble(), landHit = land, delay = delay, hitType = hitType, onHit = {
            target.graphic(GFX_DISCORD_IMPACT, height = 100)
        })
    }

    private fun confusion(npc: Npc, target: Pawn) {
        npc.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.ACCURATE)
        val delay = cast(npc, target, GFX_CONFUSION_CAST, GFX_CONFUSION_TRAVEL, Sfx.CHAOS_ELEMENTAL_CONFUSION_CAST)
        val steps = npc.world.random(1..CONFUSION_MAX_STEPS)
        val life = WildernessBossLife.token(npc)
        npc.world.queue {
            wait(delay)
            if (!WildernessBossLife.active(npc, life)) return@queue
            val p = target as? Player ?: return@queue
            if (!p.isOnline || p.isDead() || p.tile.height != npc.tile.height) return@queue
            val dest = teleportDestination(npc, p.tile, steps)
            p.graphic(GFX_CONFUSION_IMPACT, height = 100)
            p.playSound(Sfx.CHAOS_ELEMENTAL_CONFUSION_IMPACT)
            if (!dest.sameAs(p.tile)) {
                p.stopMovement()
                p.moveTo(dest.x, dest.z, dest.height)
            }
            p.message(MSG_CONFUSION)
        }
    }

    /** Kronos teleportAttack: [steps] tiles straight away from the footprint ([OldWildernessBosses.pushVector]) while each step is free. */
    fun teleportDestination(npc: Npc, start: Tile, steps: Int): Tile {
        val (vx, vz) = OldWildernessBosses.pushVector(npc, start)
        var end = Tile(start)
        if (vx == 0 && vz == 0) return end
        repeat(steps) {
            val next = end.transform(vx, vz)
            if (!npc.world.collision.canStep(end, Direction.between(end, next))) return end
            end = next
        }
        return end
    }

    private fun madness(npc: Npc, target: Player) {
        npc.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.ACCURATE)
        val delay = cast(npc, target, GFX_MADNESS_CAST, GFX_MADNESS_TRAVEL, Sfx.CHAOS_ELEMENTAL_MADNESS_CAST)
        val life = WildernessBossLife.token(npc)
        npc.world.queue {
            wait(delay)
            if (!WildernessBossLife.active(npc, life)) return@queue
            if (!target.isOnline || target.isDead()) return@queue
            target.graphic(GFX_MADNESS_IMPACT, height = 100)
            target.playSound(Sfx.CHAOS_ELEMENTAL_MADNESS_IMPACT)
            unequip(target)
        }
    }

    /** Madness on impact: the weapon first, up to 4 items, never with a full inventory (OSRS Wiki Strategies); returns how many came off. */
    fun unequip(player: Player): Int {
        var removed = 0
        for (slot in madnessSlots(player)) {
            if (player.inventory.freeSlotCount == 0) break
            if (player.equipment[slot] == null) continue
            if (EquipAction.unequip(player, slot) == EquipAction.Result.SUCCESS) {
                removed++
                player.message(MSG_MADNESS)
            }
        }
        return removed
    }
}
