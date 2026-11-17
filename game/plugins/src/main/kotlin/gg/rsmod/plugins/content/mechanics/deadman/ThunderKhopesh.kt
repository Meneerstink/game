package gg.rsmod.plugins.content.mechanics.deadman

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.ext.isProtectedFrom
import gg.rsmod.plugins.api.ext.isMulti
import gg.rsmod.plugins.content.combat.Combat
import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks
import gg.rsmod.plugins.content.mechanics.prayer.AncientCurses

/** DMM source33041, NEVER Leagues30388. Pinned wiki rev15262511. Delayed bolts hit a snapshot tile, grant no XP. */
object ThunderKhopesh {
    const val SPECIAL_ENERGY = 60
    val PVP_BOLT_TICKS = listOf(7, 9, 11, 13)
    // Phase3 advice: use the existing melee launch delay, including dealHit's player-PID adjustment.
    // The wiki establishes one initial PvP hit; it does not establish its animation/impact offset.
    const val STANDARD_MELEE_DELAY = 1
    interface Appearance {
        val passiveDelay: Int
        val pvmHitDelays: List<Int>
        /** Override after OSRS measurement; null explicitly disables the PvP special before energy is spent. */
        val pvpInitialDelay: Int? get() = STANDARD_MELEE_DELAY
        fun swing(player: Player, special: Boolean)
        fun bolt(player: Player, tile: Tile)
    }
    @Volatile var appearance: Appearance? = null
    fun withinBolt(tile: Tile, target: Tile, size: Int = 1): Boolean = tile.height == target.height &&
        tile.x + 1 >= target.x && tile.x - 1 <= target.x + size - 1 && tile.z + 1 >= target.z && tile.z - 1 <= target.z + size - 1
    fun passive(player: Player, target: Pawn, landed: Boolean) {
        val look = appearance ?: return
        if (!Dmm2Ids.ItemId.KHOPESH.matches(player.equipment[3]?.id) || !landed || player.world.randomDouble() >= 0.2) return
        // The bolt is magic damage; Protect from Melee only reduces the triggering swing.
        val max = MeleeCombatFormula.getMaxHitIgnoringMeleeProtection(player, target).toInt()
        bolt(player, target, Tile(target.getCentreTile()), look.passiveDelay, 0, max / 2)
    }
    /** Register only with a complete proven presentation, so no energy is spent on a missing lightning asset. */
    fun register() {
        val id = Dmm2Ids.ItemId.KHOPESH.local ?: return
        if (appearance == null) return
        SpecialAttacks.register(SPECIAL_ENERGY, id) {
            val look = appearance ?: run { specialFailed(); return@register }
            val tile = Tile(target.getCentreTile())
            val max = MeleeCombatFormula.getMaxHit(player, target).toInt()
            val pvp = target is Player
            val initialDelays = if (pvp) listOf(look.pvpInitialDelay ?: run { specialFailed(); return@register }) else look.pvmHitDelays
            require(initialDelays.size == (if (pvp) 1 else 2) && initialDelays.all { it >= 0 })
            var landed = false
            var dealt = 0
            look.swing(player, true)
            repeat(if (pvp) 1 else 2) { index ->
                val success = MeleeCombatFormula.getAccuracy(player, target) >= world.randomDouble()
                landed = landed || success
                val hit = player.dealHit(target = target, maxHit = if (pvp) kotlin.math.floor(max * 1.3) else max.toDouble(), landHit = success, delay = initialDelays[index], hitType = HitType.MELEE)
                dealt += hit.hit.hitmarks.sumOf { it.damage }
            }
            if (landed) {
                if (pvp) PVP_BOLT_TICKS.forEach { delay -> bolt(player, target, tile, delay, dealt * 30 / 100, dealt * 45 / 100) }
                else bolt(player, target, tile, look.passiveDelay, 0, max)
            }
        }
    }
    private fun bolt(player: Player, original: Pawn, tile: Tile, delay: Int, minimum: Int, maximum: Int) {
        require(delay >= 0)
        player.world.queue {
            wait(delay)
            if (!player.isOnline || player.isDead() || player.tile.height != tile.height) return@queue
            appearance?.bolt(player, tile)
            // Candidate discovery never bypasses single/multi, guarded zones, combat-level restrictions or target ownership.
            val candidates = mutableListOf<Pawn>(original)
            player.world.npcs.forEach { if (it !== original && withinBolt(tile, it.tile, it.getSize())) candidates += it }
            player.world.players.forEach { if (it !== player && it !== original && withinBolt(tile, it.tile)) candidates += it }
            for (target in candidates.distinct()) {
                if (target is gg.rsmod.game.model.entity.Npc && !target.isSpawned()) continue
                if (target is Player && !target.isOnline) continue
                if (target !== original && (!player.tile.isMulti(player.world) || !target.tile.isMulti(player.world))) continue
                if (target.isDead() || !withinBolt(tile, target.tile, target.getSize()) || !Combat.canEngage(player, target)) continue
                val prayer = if (target is Player && target.isProtectedFrom(CombatClass.MAGIC) && !AncientCurses.deflects(target, CombatClass.MAGIC)) 0.6 else 1.0
                val low = kotlin.math.floor(minimum * prayer).toInt()
                val high = kotlin.math.floor(maximum * prayer).toInt().coerceAtLeast(low)
                val rolled = low + player.world.random.nextInt(high - low + 1)
                player.dealHit(target = target, minHit = rolled - 0.1, maxHit = rolled.toDouble(), landHit = true, delay = 0, hitType = HitType.MAGIC)
            }
        }
    }
}
