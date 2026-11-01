package gg.rsmod.plugins.content.areas.wilderness.bosses

import gg.rsmod.game.action.EquipAction
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.TileGraphic
import gg.rsmod.game.model.attr.AttributeKey
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
import gg.rsmod.plugins.api.ProjectileType
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.cfg.Sfx
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.*
import gg.rsmod.plugins.content.combat.formula.MagicCombatFormula
import gg.rsmod.plugins.content.combat.strategy.MagicCombatStrategy

/**
 * N522 bundle 1 (owner 2026-11-01 "alle osrs wilderness bosses ... volledig"): the OSRS Chaos Fanatic (OSRS npc 6619, imported as
 * [Npcs.CHAOS_FANATIC]).
 *
 * Mechanics - OSRS Wiki "Chaos Fanatic" (2026-11-01): magic only, attack speed 2; the standard red magic attack (max 21, Protect from
 * Magic negates it); an unavoidable green attack that removes a worn item into the inventory (a full inventory prevents it; "similar
 * to the chaos elemental, he can unequip your items", Strategies); three slow green projectiles that explode on the ground, one on the
 * target's tile (max 31), only on the last player who engaged him (Poll 83, 2 April 2025). Pattern: at the start and after each
 * cooldown 10/15 red, 4/15 explosion, 1/15 disarm; an explosion is followed by 12 red-only attacks.
 * Ids (no Jagex documentation): Kronos-184 data/npcs/combat/Chaos_Fanatic.json (attack ZAROS_VERTICAL_CASTING 1979, defend 425,
 * death 2304) and bosses/ChaosFanatic.java (red projectile 554 + hit spotanim 305, ground projectile 551, explosion 552 on the target
 * tile and 157 on the others, disarm hit 0-5); RuneLite aoewarnings CHAOS_FANATIC_AOE = 551. OSRS spotanims imported with
 * OsrsFxImportTool batch wildy-bosses-1 (tx-20261101-134855): 551 -> 3192, 554 -> 3194, 157 -> 3127, 305 -> 3195 (OSRS 305 is 157
 * recoloured green), 552 -> 3198 (tx-20261101-192108; the 667 copy of 552 uses seq 3152, which shows nothing). Shouts: OSRS Wiki "Transcript:Chaos Fanatic", one on every attack (Kronos, tarnish, Elderscape).
 * Sounds ADAPTED (OSRS Wiki lists no Chaos Fanatic sound): his red and green projectiles are the Chaos Elemental's confusion and
 * madness projectiles, so their cast / impact synths (OSRS ids 344/346 and 352/353, shared by the 667 cache) are used; the ground
 * explosion plays the OSRS "explosion" synth 1487.
 */
object ChaosFanaticCombat : CombatScript() {
    override val ids = intArrayOf(Npcs.CHAOS_FANATIC)

    const val ANIM_ATTACK = 16112 // OSRS 1979 ZAROS_VERTICAL_CASTING
    const val ANIM_DEFEND = 16113 // OSRS 425 HUMAN_UNARMED_DEF
    const val ANIM_DEATH = 16114 // OSRS 2304

    const val GFX_RED_PROJECTILE = 3194 // OSRS 554
    const val GFX_RED_HIT = 3195 // OSRS 305
    const val GFX_GREEN_PROJECTILE = 3192 // OSRS 551
    const val GFX_EXPLOSION_CENTRE = 3198 // OSRS 552 (667 552 uses seq 3152 that shows nothing)
    const val GFX_EXPLOSION = 3127 // OSRS 157

    const val MAX_RED = 21
    const val MAX_EXPLOSION = 31
    const val MAX_DISARM = 5
    const val ATTACK_RANGE = 8

    /** Red-only attacks after an explosion (OSRS Wiki: "a 12-attack cooldown"). */
    const val COOLDOWN_ATTACKS = 12

    /** Ground projectile flight: lands after 3 ticks (Kronos explosion delay 3). */
    private const val GROUND_DELAY = 30
    private const val GROUND_LIFESPAN = 90
    private const val EXPLOSION_TICKS = 3

    val SHOUTS =
        listOf(
            "BURN!",
            "WEUGH!",
            "Devilish Oxen Roll!!",
            "All your wilderness are belong to them!",
            "AhehHeheuhHhahueHuUEehEahAH!",
            "I shall call him squidgy and he shall be my squidgy!!",
        )

    /** Disarmable slots (Kronos DISARM_SLOTS). */
    val DISARM_SLOTS =
        listOf(EquipmentType.WEAPON, EquipmentType.SHIELD, EquipmentType.CHEST, EquipmentType.HEAD, EquipmentType.LEGS, EquipmentType.GLOVES, EquipmentType.BOOTS)
            .map { it.id }

    /** Red-only attacks still to come after the last explosion. */
    val COOLDOWN_LEFT = AttributeKey<Int>()

    enum class Attack { RED, EXPLOSION, DISARM }

    /** The OSRS Wiki pattern: 12 red attacks after an explosion, otherwise 10/15 red, 4/15 explosion, 1/15 disarm ([roll] 0..14). */
    fun choose(cooldownLeft: Int, roll: Int): Attack =
        when {
            cooldownLeft > 0 -> Attack.RED
            roll < 10 -> Attack.RED
            roll < 14 -> Attack.EXPLOSION
            else -> Attack.DISARM
        }

    override suspend fun handleSpecialCombat(it: QueueTask) {
        val npc = it.npc
        var target = npc.getCombatTarget() ?: return
        while (it.awaitAttackDelay(npc) && npc.canEngageCombat(target)) {
            npc.facePawn(target)
            if (npc.moveToAttackRange(it, target, distance = ATTACK_RANGE, projectile = true)) {
                val left = npc.attr[COOLDOWN_LEFT] ?: 0
                when (choose(left, npc.world.random(14))) {
                    Attack.RED -> red(npc, target)
                    Attack.EXPLOSION -> explosion(npc, target)
                    Attack.DISARM -> disarm(npc, target)
                }
                if (left > 0) npc.attr[COOLDOWN_LEFT] = left - 1
                npc.forceChat(SHOUTS.random())
            }
            npc.postAttackLogic(target)
            it.wait(npc.combatDef.attackSpeed)
            target = npc.getCombatTarget() ?: break
        }
        npc.resetFacePawn()
        npc.removeCombatTarget()
    }

    private fun cast(npc: Npc, target: Pawn, projectile: Int): Int {
        npc.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.ACCURATE)
        npc.animate(ANIM_ATTACK)
        npc.world.spawn(npc.createProjectile(target, projectile, ProjectileType.MAGIC))
        return MagicCombatStrategy.getHitDelay(npc.getFrontFacingTile(target), target.getCentreTile())
    }

    private fun red(npc: Npc, target: Pawn) {
        val delay = cast(npc, target, GFX_RED_PROJECTILE)
        if (target is Player) {
            target.playSound(Sfx.CHAOS_ELEMENTAL_CONFUSION_CAST)
            target.playSound(Sfx.CHAOS_ELEMENTAL_CONFUSION_IMPACT, delay = delay * 30)
        }
        val land = MagicCombatFormula.getAccuracy(npc, target) >= npc.world.randomDouble()
        npc.dealHit(target = target, maxHit = MAX_RED.toDouble(), landHit = land, delay = delay, hitType = HitType.MAGIC, onHit = {
            if (land) target.graphic(GFX_RED_HIT)
        })
    }

    /** Unavoidable: no accuracy roll, a worn item goes to the inventory while it has room. */
    private fun disarm(npc: Npc, target: Pawn) {
        val delay = cast(npc, target, GFX_GREEN_PROJECTILE)
        if (target !is Player) return
        target.playSound(Sfx.CHAOS_ELEMENTAL_MADNESS_CAST)
        target.playSound(Sfx.CHAOS_ELEMENTAL_MADNESS_IMPACT, delay = delay * 30)
        npc.dealHit(target = target, maxHit = MAX_DISARM.toDouble(), landHit = true, delay = delay, hitType = HitType.MAGIC, onHit = {
            if (!target.isDead() && target.isOnline) disarmOne(target)
        })
    }

    /** One random worn item of [DISARM_SLOTS] into the inventory; nothing with a full inventory (OSRS Wiki). */
    fun disarmOne(player: Player): Boolean {
        if (player.inventory.freeSlotCount == 0) return false
        val worn = DISARM_SLOTS.filter { player.equipment[it] != null }
        if (worn.isEmpty()) return false
        val removed = EquipAction.unequip(player, worn.random()) == EquipAction.Result.SUCCESS
        if (removed) player.message("The Chaos Fanatic has removed some of your worn equipment.")
        return removed
    }

    /** Three tiles: the target's own and two others within 2 (not the fanatic's own tile; Kronos magicAttack). */
    fun explosionTiles(npc: Npc, target: Pawn, pick: (List<Tile>) -> List<Tile>): List<Tile> {
        val centre = Tile(target.tile)
        val around =
            (-2..2).flatMap { dx -> (-2..2).map { dz -> centre.transform(dx, dz) } }
                .filter { it != centre && it != npc.tile }
        return listOf(centre) + pick(around).take(2)
    }

    private fun explosion(npc: Npc, target: Pawn) {
        npc.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.ACCURATE)
        npc.animate(ANIM_ATTACK)
        npc.attr[COOLDOWN_LEFT] = COOLDOWN_ATTACKS + 1 // this attack itself is counted down at the end of the swing
        val world = npc.world
        val tiles = explosionTiles(npc, target) { it.shuffled() }
        tiles.forEach { tile ->
            world.spawn(npc.createProjectile(tile, GFX_GREEN_PROJECTILE, startHeight = 80, endHeight = 0, delay = GROUND_DELAY, angle = 16, lifespan = GROUND_LIFESPAN))
        }
        world.queue {
            wait(EXPLOSION_TICKS)
            tiles.forEachIndexed { i, tile -> world.spawn(TileGraphic(tile, if (i == 0) GFX_EXPLOSION_CENTRE else GFX_EXPLOSION, 0)) }
            if (target is Player && target.isOnline) target.playSound(Sfx.EXPLOSION)
            // Poll 83 (2 April 2025): only the last player who engaged in combat is hit.
            if (npc.isDead() || !npc.isSpawned() || target.isDead()) return@queue
            if (target is Player && !target.isOnline) return@queue
            if (tiles.any { it.sameAs(target.tile) }) {
                npc.dealHit(target = target, maxHit = MAX_EXPLOSION.toDouble(), landHit = true, delay = 0, hitType = HitType.REGULAR_HIT)
            }
        }
    }
}
