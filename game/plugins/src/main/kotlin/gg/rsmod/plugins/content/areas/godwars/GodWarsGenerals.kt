package gg.rsmod.plugins.content.areas.godwars

import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.combat.CombatScript
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.combat.WeaponStyle
import gg.rsmod.game.model.entity.AreaSound
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.PrayerIcon
import gg.rsmod.plugins.api.ProjectileType
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.hasPrayerIcon
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.npc
import gg.rsmod.plugins.api.ext.prepareAttack
import gg.rsmod.plugins.content.combat.canEngageCombat
import gg.rsmod.plugins.content.combat.createProjectile
import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.formula.MagicCombatFormula
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.formula.RangedCombatFormula
import gg.rsmod.plugins.content.combat.getCombatTarget
import gg.rsmod.plugins.content.combat.isAttackDelayReady
import gg.rsmod.plugins.content.combat.moveToAttackRange
import gg.rsmod.plugins.content.combat.postAttackLogic
import gg.rsmod.plugins.content.combat.removeCombatTarget
import gg.rsmod.plugins.content.combat.strategy.MagicCombatStrategy
import gg.rsmod.plugins.content.combat.strategy.RangedCombatStrategy
import gg.rsmod.plugins.content.mechanics.poison.Poison

/**
 * Combat scripts for the four God Wars Dungeon generals.
 *
 * Mechanics (attack mix, area-of-effect targets, projectiles, gfx, force-chat lines and their
 * sound ids) are ported from the original 2012 Matrix 718 boss scripts kept at
 * `C:\RSPS\import-source\donors\matrix-data\matrix-scripts\` (pre-2012 ids, so every animation,
 * projectile and gfx exists in the 667 cache). Fixed max hits are the OSRS Wiki / 2007-era values
 * the bosses kept through 2011 (Graardor 60 melee / 35 ranged, Zilyana 27, K'ril 46 melee / 49
 * through Protect from Melee / 30 magic, Kree'arra 26 melee / 69 ranged / 21 magic); accuracy is
 * rolled through this codebase's own formulas against the general's sourced stats.
 */
object GodWarsGenerals {
    /** Everyone a general's area attack reaches: players on the same plane inside the chamber. */
    private const val CHAMBER_RADIUS = 12

    fun chamberPlayers(npc: Npc): List<Player> {
        val players = ArrayList<Player>()
        npc.world.players.forEach { p ->
            if (p.tile.height == npc.tile.height && p.tile.isWithinRadius(npc.tile, CHAMBER_RADIUS) && npc.canEngageCombat(p)) {
                players += p
            }
        }
        return players
    }

    fun shout(
        npc: Npc,
        line: String,
        sound: Int = -1,
    ) {
        npc.forceChat(line)
        if (sound > -1) {
            npc.world.spawn(AreaSound(tile = npc.tile, id = sound, radius = 10, volume = 1))
        }
    }

    fun rollAccuracy(
        npc: Npc,
        target: Pawn,
        formula: gg.rsmod.plugins.content.combat.formula.CombatFormula,
    ): Boolean = formula.getAccuracy(npc, target) >= npc.world.randomDouble()

    /**
     * Shared loop: every general keeps fighting its focused target while any chamber player is
     * attackable, performing one [attack] per attack delay.
     */
    suspend fun fight(
        it: QueueTask,
        meleeRange: Int,
        attack: suspend (npc: Npc, target: Pawn) -> Unit,
    ) {
        val npc = it.npc
        var target = npc.getCombatTarget() ?: return
        while (npc.canEngageCombat(target)) {
            npc.facePawn(target)
            if (npc.moveToAttackRange(it, target, distance = meleeRange, projectile = meleeRange > 1) && npc.isAttackDelayReady()) {
                attack(npc, target)
                npc.postAttackLogic(target)
            }
            it.wait(1)
            target = npc.getCombatTarget() ?: break
        }
        npc.resetFacePawn()
        npc.removeCombatTarget()
    }

    /** General Graardor: melee (7060) or, one attack in three, a ranged volley (7063, proj 1200) at everyone in the chamber. */
    object Graardor : CombatScript() {
        override val ids = intArrayOf(Npcs.GENERAL_GRAARDOR)

        private val lines =
            listOf(
                "Death to our enemies!" to 3219,
                "Brargh!" to 3209,
                "Break their bones!" to -1,
                "For the glory of Bandos!" to -1,
                "Split their skulls!" to 3229,
                "We feast on the bones of our enemies tonight!" to 3206,
                "CHAAARGE!" to 3220,
                "Crush them underfoot!" to 3224,
                "All glory to Bandos!" to 3205,
                "GRAAAAAAAAAR!" to 3207,
                "FOR THE GLORY OF THE BIG HIGH WAR GOD!" to -1,
            )

        override suspend fun handleSpecialCombat(it: QueueTask) =
            fight(it, meleeRange = 1) { npc, target ->
                val world = npc.world
                if (world.random(4) == 0) {
                    val (line, sound) = lines.random()
                    shout(npc, line, sound)
                }
                if (world.random(2) == 0) {
                    npc.prepareAttack(CombatClass.RANGED, StyleType.RANGED, WeaponStyle.NONE)
                    npc.animate(7063)
                    chamberPlayers(npc).forEach { p ->
                        val projectile = npc.createProjectile(p, gfx = 1200, type = ProjectileType.ARROW)
                        world.spawn(projectile)
                        val delay = RangedCombatStrategy.getHitDelay(npc.getCentreTile(), p.tile)
                        npc.dealHit(target = p, maxHit = 35.0, landHit = rollAccuracy(npc, p, RangedCombatFormula), delay = delay, hitType = HitType.RANGE)
                    }
                } else {
                    npc.prepareAttack(CombatClass.MELEE, StyleType.CRUSH, WeaponStyle.NONE)
                    npc.animate(npc.combatDef.attackAnimation)
                    npc.dealHit(target = target, maxHit = 60.0, landHit = rollAccuracy(npc, target, MeleeCombatFormula), delay = 1, hitType = HitType.MELEE)
                }
            }
    }

    /** Commander Zilyana: melee (6964) or, half the time, a lightning strike (6967, gfx 1194) on everyone within 3 tiles. */
    object Zilyana : CombatScript() {
        override val ids = intArrayOf(Npcs.COMMANDER_ZILYANA)

        private val lines =
            listOf(
                "Death to the enemies of the light!" to 3247,
                "Slay the evil ones!" to 3242,
                "Saradomin lend me strength!" to 3263,
                "By the power of Saradomin!" to 3262,
                "May Saradomin be my sword." to 3251,
                "Good will always triumph!" to 3260,
                "Forward! Our allies are with us!" to 3245,
                "Saradomin is with us!" to 3266,
                "In the name of Saradomin!" to 3250,
                "Attack! Find the Godsword!" to 3258,
            )

        override suspend fun handleSpecialCombat(it: QueueTask) =
            fight(it, meleeRange = 1) { npc, target ->
                val world = npc.world
                if (world.random(4) == 0) {
                    val (line, sound) = lines.random()
                    shout(npc, line, sound)
                }
                if (world.random(1) == 0) {
                    npc.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.NONE)
                    npc.animate(6967)
                    chamberPlayers(npc).filter { it.tile.isWithinRadius(npc.tile, 3) }.forEach { p ->
                        val hit = npc.dealHit(target = p, maxHit = 27.0, landHit = rollAccuracy(npc, p, MagicCombatFormula), delay = 1, hitType = HitType.MAGIC)
                        if (hit.hit.hitmarks.sumOf { h -> h.damage } > 0) {
                            p.graphic(1194)
                        }
                    }
                } else {
                    npc.prepareAttack(CombatClass.MELEE, StyleType.CRUSH, WeaponStyle.NONE)
                    npc.animate(npc.combatDef.attackAnimation)
                    npc.dealHit(target = target, maxHit = 27.0, landHit = rollAccuracy(npc, target, MeleeCombatFormula), delay = 1, hitType = HitType.MELEE)
                }
            }
    }

    /**
     * K'ril Tsutsaroth: one attack in three is a flame volley (14962, gfx 1210, proj 1211, may poison)
     * at everyone in the chamber; otherwise melee (14963), and against a target praying Protect from /
     * Deflect Melee the blow goes through for up to 49 (14968), draining half the damage in prayer.
     */
    object Kril : CombatScript() {
        override val ids = intArrayOf(Npcs.KRIL_TSUTSAROTH)

        private val lines =
            listOf(
                "Attack them, you dogs!" to -1,
                "Forward!" to -1,
                "Death to Saradomin's dogs!" to -1,
                "Kill them, you cowards!" to -1,
                "The Dark One will have their souls!" to 3229,
                "Zamorak curse them!" to -1,
                "Rend them limb from limb!" to -1,
                "No retreat!" to -1,
                "Flay them all!" to -1,
            )

        override suspend fun handleSpecialCombat(it: QueueTask) =
            fight(it, meleeRange = 1) { npc, target ->
                val world = npc.world
                if (world.random(4) == 0) {
                    val (line, sound) = lines.random()
                    shout(npc, line, sound)
                }
                if (world.random(2) == 0) {
                    npc.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.NONE)
                    npc.animate(14962)
                    npc.graphic(1210)
                    chamberPlayers(npc).forEach { p ->
                        world.spawn(npc.createProjectile(p, gfx = 1211, type = ProjectileType.MAGIC))
                        val delay = MagicCombatStrategy.getHitDelay(npc.getCentreTile(), p.tile)
                        npc.dealHit(target = p, maxHit = 30.0, landHit = rollAccuracy(npc, p, MagicCombatFormula), delay = delay, hitType = HitType.MAGIC, onHit = {
                            if (world.random(4) == 0) {
                                Poison.poison(p, initialDamage = 16)
                            }
                        })
                    }
                } else {
                    npc.prepareAttack(CombatClass.MELEE, StyleType.SLASH, WeaponStyle.NONE)
                    val praying =
                        target is Player &&
                            (target.hasPrayerIcon(PrayerIcon.PROTECT_FROM_MELEE) || target.hasPrayerIcon(PrayerIcon.DEFLECT_MELEE))
                    if (praying) {
                        val player = target as Player
                        shout(npc, "YARRRRRRR!")
                        npc.animate(14968)
                        player.message("K'ril Tsutsaroth slams through your protection prayer, leaving you feeling drained.")
                        // Protection prayers make npc melee accuracy 0, so this blow always lands.
                        npc.dealHit(target = player, maxHit = 49.0, landHit = true, delay = 1, hitType = HitType.MELEE, onHit = { hit ->
                            val dealt = hit.hit.hitmarks.sumOf { h -> h.damage } / 10
                            val drain = (dealt / 2) * 10
                            player.setCurrentPrayerPoints((player.getCurrentPrayerPoints() - drain).coerceAtLeast(0))
                        })
                    } else {
                        npc.animate(14963)
                        npc.dealHit(target = target, maxHit = 46.0, landHit = rollAccuracy(npc, target, MeleeCombatFormula), delay = 1, hitType = HitType.MELEE)
                    }
                }
            }
    }

    /**
     * Kree'arra: only meleés (6997) while nobody in the chamber is fighting her; otherwise every
     * attack (6976) hits everyone in the chamber, each with either a magic blast (proj 1198, gfx
     * 1196) or a ranged blast (proj 1197) that knocks the player up to two tiles away.
     */
    object Kreearra : CombatScript() {
        override val ids = intArrayOf(Npcs.KREEARRA)

        override suspend fun handleSpecialCombat(it: QueueTask) =
            fight(it, meleeRange = 8) { npc, target ->
                val world = npc.world
                val players = chamberPlayers(npc)
                val underAttack = players.any { p -> p.getCombatTarget() == npc }
                if (!underAttack && target.tile.isWithinRadius(npc.tile, 1 + npc.getSize())) {
                    npc.prepareAttack(CombatClass.MELEE, StyleType.CRUSH, WeaponStyle.NONE)
                    npc.animate(6997)
                    npc.dealHit(target = target, maxHit = 26.0, landHit = rollAccuracy(npc, target, MeleeCombatFormula), delay = 1, hitType = HitType.MELEE)
                    return@fight
                }
                npc.animate(6976)
                players.forEach { p ->
                    if (world.random(2) == 0) {
                        npc.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.NONE)
                        val projectile = npc.createProjectile(p, gfx = 1198, type = ProjectileType.MAGIC)
                        world.spawn(projectile)
                        val delay = MagicCombatStrategy.getHitDelay(npc.getCentreTile(), p.tile)
                        p.graphic(1196, 0, projectile.lifespan)
                        npc.dealHit(target = p, maxHit = 21.0, landHit = rollAccuracy(npc, p, MagicCombatFormula), delay = delay, hitType = HitType.MAGIC)
                    } else {
                        npc.prepareAttack(CombatClass.RANGED, StyleType.RANGED, WeaponStyle.NONE)
                        world.spawn(npc.createProjectile(p, gfx = 1197, type = ProjectileType.ARROW))
                        val delay = RangedCombatStrategy.getHitDelay(npc.getCentreTile(), p.tile)
                        npc.dealHit(target = p, maxHit = 69.0, landHit = rollAccuracy(npc, p, RangedCombatFormula), delay = delay, hitType = HitType.RANGE)
                        knockBack(p)
                    }
                }
            }

        private fun knockBack(player: Player) {
            val world = player.world
            repeat(10) {
                val tile = Tile(player.tile.x + world.random(-2..2), player.tile.z + world.random(-2..2), player.tile.height)
                if (tile != player.tile && !world.collision.isClipped(tile)) {
                    player.moveTo(tile)
                    return
                }
            }
        }
    }
}
