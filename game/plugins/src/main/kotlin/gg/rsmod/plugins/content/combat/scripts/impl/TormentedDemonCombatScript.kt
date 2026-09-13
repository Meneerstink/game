package gg.rsmod.plugins.content.combat.scripts.impl

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.combat.CombatScript
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.combat.WeaponStyle
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.ProjectileType
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.*
import gg.rsmod.plugins.content.combat.formula.MagicCombatFormula
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.formula.RangedCombatFormula
import gg.rsmod.plugins.content.combat.strategy.MagicCombatStrategy

/**
 * Tormented demons (npcs 8349-8351...).
 *
 * Attack ids ported from the Matrix 718 TormentedDemonCombat script (melee 10922 + gfx 1886,
 * magic 10918 + gfx 1883 + projectile 1884, ranged 10919 + gfx 1888 + projectile 1887); the
 * encounter rules follow the 2011 wiki:
 *  - the demon attacks with one style for ~5 attacks, then switches to another style;
 *  - it prays against the style that last damaged it (overhead icon), taking full damage
 *    only from the other styles;
 *  - its fire shield absorbs 75% of all damage until it is hit with Darklight/Silverlight,
 *    which lowers the shield for 60 seconds.
 * Max hits (2011): melee 18, magic 27, ranged 27.
 */
object TormentedDemonCombatScript : CombatScript() {
    override val ids = intArrayOf(Npcs.TORMENTED_DEMON, Npcs.TORMENTED_DEMON_8350, 8351, 8352, 8353, 8354, 8355, 8356, 8357, 8358, 8359, 8360, 8361, 8362, 8363, 8364, 8365, 8366, 8367, 8368, 8369)

    /** Current attack style: 0 melee, 1 magic, 2 ranged. */
    val STYLE = AttributeKey<Int>()
    val SHIELD_DOWN_UNTIL = AttributeKey<Int>()

    /** RCV-012 B7 decision 2 ("15"): Void `tds_change_attack` soft timer. */
    val STYLE_TIMER = gg.rsmod.game.model.timer.TimerKey()

    private const val MELEE_MAX = 18.0
    private const val MAGIC_MAX = 27.0
    private const val RANGED_MAX = 27.0

    /** Void TormentedDemon.kt: a demon spawns in the magic style and first switches after 14-29 ticks. */
    fun onSpawn(npc: Npc) {
        npc.attr[STYLE] = 1
        npc.timers[STYLE_TIMER] = 14 + npc.world.random(15)
    }

    /**
     * Void `tds_change_attack` tick: plays the change animation, rotates magic -> ranged -> melee -> magic, blocks attacks
     * for 6 ticks (`action_delay`) and re-arms for 26 ticks (~15.6 s).
     */
    fun switchStyle(npc: Npc) {
        if (!npc.isAlive()) return
        npc.animate(CHANGE_ANIM)
        npc.attr[STYLE] = nextStyle(npc.attr[STYLE] ?: 1)
        npc.timers[gg.rsmod.game.model.timer.ATTACK_DELAY] = 6
        npc.timers[STYLE_TIMER] = 26
    }

    fun nextStyle(style: Int): Int = (style + 1) % 3

    override suspend fun handleSpecialCombat(it: QueueTask) {
        val npc = it.npc
        var target = npc.getCombatTarget() ?: return
        val world = npc.world

        while (npc.canEngageCombat(target) && npc.isAttackDelayReady()) {
            npc.facePawn(target)
            val style = npc.attr[STYLE] ?: 1

            val distance = npc.getFrontFacingTile(target).getDistance(target.tile)
            // Void guthix_temple.combat.toml: the active style's section (chance 20) or the splash special (chance 1).
            if (world.random(SPECIAL_ROLL - 1) == 0) {
                special(npc, target)
            } else {
                when (style) {
                    0 -> {
                        if (distance <= 2 || npc.moveToAttackRange(it, target, distance = 2, projectile = false)) {
                            melee(npc, target)
                        } else {
                            magic(npc, target)
                        }
                    }
                    1 -> magic(npc, target)
                    else -> ranged(npc, target)
                }
            }

            npc.postAttackLogic(target)
            it.wait(npc.combatDef.attackSpeed)
            target = npc.getCombatTarget() ?: break
        }

        npc.resetFacePawn()
        npc.removeCombatTarget()
    }

    /**
     * RCV-005: the attack visuals, projectiles, sounds and hits come from Void guthix_temple tormented_demon
     * sections (the shared data-driven model). Void only defines ids 8352-8354, so every tormented demon id
     * uses that sourced row; this script keeps the style rotation and the damage/shield rules.
     */
    private const val VOID_ROW_ID = 8352

    private fun melee(
        npc: Npc,
        target: Pawn,
    ) {
        if (gg.rsmod.plugins.content.combat.attack.NpcAttacks.attackWith(npc, target, "melee", VOID_ROW_ID)) return
        npc.prepareAttack(CombatClass.MELEE, StyleType.SLASH, WeaponStyle.AGGRESSIVE)
        npc.animate(10922)
        npc.graphic(1886)
        val landHit = MeleeCombatFormula.getAccuracy(npc, target) >= npc.world.randomDouble()
        npc.dealHit(target = target, maxHit = MELEE_MAX, landHit = landHit, delay = 1, hitType = HitType.MELEE)
    }

    private fun magic(
        npc: Npc,
        target: Pawn,
    ) {
        if (gg.rsmod.plugins.content.combat.attack.NpcAttacks.attackWith(npc, target, "magic", VOID_ROW_ID)) return
        npc.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.ACCURATE)
        npc.animate(10918)
        npc.graphic(1883, 96)
        npc.world.spawn(npc.createProjectile(target, 1884, ProjectileType.MAGIC))
        val delay = MagicCombatStrategy.getHitDelay(npc.getFrontFacingTile(target), target.getCentreTile())
        val landHit = MagicCombatFormula.getAccuracy(npc, target) >= npc.world.randomDouble()
        npc.dealHit(target = target, maxHit = MAGIC_MAX, landHit = landHit, delay = delay, hitType = HitType.MAGIC)
    }

    private fun ranged(
        npc: Npc,
        target: Pawn,
    ) {
        if (gg.rsmod.plugins.content.combat.attack.NpcAttacks.attackWith(npc, target, "range", VOID_ROW_ID)) return
        npc.prepareAttack(CombatClass.RANGED, StyleType.RANGED, WeaponStyle.ACCURATE)
        npc.animate(10919)
        npc.graphic(1888)
        npc.world.spawn(npc.createProjectile(target, 1887, ProjectileType.ARROW))
        val delay = MagicCombatStrategy.getHitDelay(npc.getFrontFacingTile(target), target.getCentreTile())
        val landHit = RangedCombatFormula.getAccuracy(npc, target) >= npc.world.randomDouble()
        npc.dealHit(target = target, maxHit = RANGED_MAX, landHit = landHit, delay = delay, hitType = HitType.RANGE)
    }

    /**
     * Void TormentedDemon `npcAttack("tormented_demon", "special")`: magic projectile to a random tile within 4 of the
     * target; on impact the impact gfx lands there and a target still within 1 tile takes 281 (x10) = 28 magic damage.
     */
    private fun special(
        npc: Npc,
        target: Pawn,
    ) {
        val world = npc.world
        npc.animate(10918)
        val tile = target.tile.transform(world.random(8) - 4, world.random(8) - 4)
        world.spawn(npc.createProjectile(tile, 1884, ProjectileType.MAGIC))
        val delay = MagicCombatStrategy.getHitDelay(npc.getCentreTile(), tile).coerceAtLeast(1)
        world.queue {
            wait(delay)
            world.spawn(gg.rsmod.game.model.TileGraphic(tile, id = 1883, height = 0))
            if (target.isAlive() && target.tile.height == tile.height && target.tile.isWithinRadius(tile, 1)) {
                (target as? Player)?.message("The demon's magical attack splashes on you.")
                target.hit(damage = SPLASH_DAMAGE, type = HitType.MAGIC, delay = 0)
            }
        }
    }

    /** Void combat.toml: the active style section weighs 20, the special 1. */
    const val SPECIAL_ROLL = 21
    const val SPLASH_DAMAGE = 28

    /**
     * Void guthix_temple.anims.toml: 10917 = style change, 10924 = death (Novite's 667 definitions use 10917 as death).
     * 667 cache lengths (TormentedDemonDropTablesTests): 10917 = 5 ticks, covered by Void's 6-tick action delay after a
     * change; 10924 = 9 ticks. Owner decision "you choose" (2026-09-13): Void, consistent with the other B7 choices.
     */
    const val CHANGE_ANIM = 10917
    const val DEATH_ANIM = 10924

    /**
     * Incoming-damage rule, applied from the damage pipeline: the demon's shield absorbs 75% of
     * damage while up, and it prays against the last style that hurt it.
     */
    fun modifyIncomingDamage(
        npc: Npc,
        attacker: Pawn,
        style: CombatClass,
        damage: Int,
        weaponId: Int,
    ): Int {
        val world = npc.world
        // RCV-005 root cause (owner: "tormented demon lijkt niet goed te prayen tegen mijn attack style"): the demon
        // switched its protection on every single hit. Void TormentedDemon.kt + Novite TormentedDemon.java: it only
        // starts praying against a style after taking 31 damage from it (x10 source: 310, each hit - a miss included -
        // counted as at least 2 / x10: 20), by transforming into the variant that shows that protection prayer.
        // RCV-012 B7: the variant is the demon's own cache group (see [variantFor]); the old hard-coded Void 634 ids
        // 8352-8354 ignored that id 8349 already shows the protect-melee icon at spawn, so melee did full damage under a
        // visible melee prayer.
        val shown = displayedId(npc)
        var result = if (prayedStyle(shown) == style) 0 else damage
        val shieldDown = (npc.attr[SHIELD_DOWN_UNTIL] ?: 0) > world.currentCycle
        // Owner decision 3 ("you choose"): Void - a damaging Darklight or holy water hit lowers the shield for 60 s.
        if (damage > 0 && (weaponId == gg.rsmod.plugins.api.cfg.Items.DARKLIGHT || weaponId == gg.rsmod.plugins.api.cfg.Items.HOLY_WATER)) {
            npc.attr[SHIELD_DOWN_UNTIL] = world.currentCycle + 100
            if (attacker is Player) attacker.message("The demon is temporarily weakened by your weapon.")
        } else if (!shieldDown) {
            npc.graphic(SHIELD_GFX)
            result = result / 4
        }
        if (prayedStyle(shown) != style) {
            val counted = (npc.attr[STYLE_DAMAGE]?.get(style) ?: 0) + result.coerceAtLeast(2)
            if (counted >= PRAYER_SWITCH_DAMAGE) {
                npc.setTransmogId(variantFor(npc.id, style))
                npc.attr[STYLE_DAMAGE] = HashMap()
                if (attacker is Player) attacker.message("The Tormented demon regains its strength against your weapon.")
            } else {
                npc.attr[STYLE_DAMAGE] = HashMap(npc.attr[STYLE_DAMAGE] ?: emptyMap()).also { it[style] = counted }
            }
        }
        return result
    }

    /** First and last cache id of the tormented demon definitions (pristine 667 NpcDefProbeTool 8349..8369). */
    const val FIRST_ID = 8349
    const val LAST_ID = 8369

    /**
     * The 667 cache stores each demon as a group of three definitions whose head icons repeat 0, 2, 1 (protect melee,
     * protect magic, protect missiles); Novite `TormentedDemon.switchPrayers` transforms into exactly that offset.
     */
    private val OFFSET_STYLE = listOf(CombatClass.MELEE, CombatClass.MAGIC, CombatClass.RANGED)

    fun displayedId(npc: Npc): Int = npc.getTransmogId().takeIf { it in FIRST_ID..LAST_ID } ?: npc.id

    fun prayedStyle(displayedId: Int): CombatClass? =
        if (displayedId in FIRST_ID..LAST_ID) OFFSET_STYLE[(displayedId - FIRST_ID) % 3] else null

    fun variantFor(
        npcId: Int,
        style: CombatClass,
    ): Int = FIRST_ID + (npcId - FIRST_ID) / 3 * 3 + OFFSET_STYLE.indexOf(style)

    const val PRAYER_SWITCH_DAMAGE = 31
    private const val SHIELD_GFX = 1885 // Void tormented_demon_shield
    private val STYLE_DAMAGE = AttributeKey<HashMap<CombatClass, Int>>()
}
