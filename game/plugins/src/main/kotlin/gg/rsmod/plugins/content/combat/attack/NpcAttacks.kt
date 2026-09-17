package gg.rsmod.plugins.content.combat.attack

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import gg.rsmod.game.model.Tile
import gg.rsmod.game.model.TileGraphic
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.combat.PawnHit
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.combat.WeaponStyle
import gg.rsmod.game.model.entity.AreaSound
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.entity.Projectile
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.freeze
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.playSound
import gg.rsmod.plugins.api.ext.prepareAttack
import gg.rsmod.plugins.content.combat.Combat
import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.formula.CombatFormula
import gg.rsmod.plugins.content.combat.formula.DragonfireFormula
import gg.rsmod.plugins.content.combat.formula.DragonfireTable
import gg.rsmod.plugins.content.combat.formula.MagicCombatFormula
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.formula.RangedCombatFormula
import gg.rsmod.plugins.content.combat.formula.WyvernIcyBreath
import gg.rsmod.plugins.content.combat.poison
import java.io.File
import java.io.FileReader
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

/**
 * RCV-005 root cause (owner 2026-09-13: "de kalpite queen buggs gelden voor alle bosses" - no attack
 * animations per style, gfx, projectiles, sounds or mechanics): npcs had no per-attack layer. Hand-written
 * scripts played one placeholder animation and a single hit; nothing else was ever issued (AV trace).
 *
 * This is the one shared attack layer for every npc, ported from Void (rev-634, ids validated against the
 * rev-667 cache by NpcAttacksTests): the data is `data/cfg/npcs/npc-attacks.json`, generated from Void's
 * `*.combat.toml` by `C:\RSPS\tools\npc-attacks\generate.js`, and [attack] is Void
 * `content/entity/npc/combat/Attack.kt` `npcCombatSwing` + `npcCombatAttack`:
 * select a weighted valid attack -> attacker anim/gfx/sounds/say -> for every target: target anim/gfx/sounds,
 * projectiles (Void ShootProjectile timing), hits, impact or miss gfx/sounds, attack hook, impact hook + effects.
 *
 * Boss mechanics the data cannot express are registered by content plugins exactly like Void's script DSL:
 * [condition] (`npcCondition`), [onAttack] (`npcAttack`, per target after the hits), [onImpact] (`npcImpact`,
 * when the hit lands; false skips the impact effects) and [onSwing] (once per swing, e.g. boss shouts).
 * Hook keys are Void combat definition names (`Row.combatDef`), e.g. `kril_tsutsaroth` / `melee_slam`.
 *
 * Units: table hit min/max are x10 life points (Void) and are divided by 10 here (this server is 1:1);
 * projectile delay/curve/heights and gfx/sound delays are client cycles; hit delay converts client cycles to
 * ticks exactly like Void `Hit.kt` (`delay / 30 + 1`, 0 = this tick).
 */
object NpcAttacks {
    const val DEFAULT_PATH = "./data/cfg/npcs/npc-attacks.json"

    class Gfx(
        val id: Int = -1,
        val delay: Int? = null,
        val height: Int? = null,
        val area: Boolean = false,
        @SerializedName("offset_x") val offsetX: Int = 0,
        @SerializedName("offset_y") val offsetY: Int = 0,
        @SerializedName("def_height") val defHeight: Int? = null,
        @SerializedName("def_delay") val defDelay: Int = 0,
    )

    class Sound(
        val id: Int = -1,
        val delay: Int = 0,
        val radius: Int = 0,
        @SerializedName("offset_x") val offsetX: Int = 0,
        @SerializedName("offset_y") val offsetY: Int = 0,
    )

    class Proj(
        val id: Int = -1,
        val delay: Int? = null,
        @SerializedName("curve_min") val curveMin: Int? = null,
        @SerializedName("curve_max") val curveMax: Int? = null,
        @SerializedName("end_height") val endHeight: Int? = null,
        @SerializedName("def_height") val defHeight: Int = 0,
        @SerializedName("def_end_height") val defEndHeight: Int = 0,
        @SerializedName("def_delay") val defDelay: Int = 0,
        @SerializedName("def_curve") val defCurve: Int = 0,
        @SerializedName("time_offset") val timeOffset: Int = 0,
        val multiplier: Int = 5,
        @SerializedName("size_offset") val sizeOffset: Int = 0,
    )

    class HitDef(
        val offense: String = "",
        val defence: String = "",
        val special: Boolean = false,
        val min: Int = 0,
        val max: Int = 0,
        val delay: Int? = null,
        @SerializedName("accuracy_roll") val accuracyRoll: Boolean = true,
    )

    class Drain(
        val skill: String = "",
        val min: Int = 0,
        val max: Int = 0,
        val multiplier: Double = 0.0,
    )

    class Area(
        val name: String = "",
        @SerializedName("min_x") val minX: Int = 0,
        @SerializedName("max_x") val maxX: Int = 0,
        @SerializedName("min_z") val minZ: Int = 0,
        @SerializedName("max_z") val maxZ: Int = 0,
        val level: Int = 0,
    )

    class Attack(
        val id: String = "",
        val chance: Int = 1,
        val range: Int = 1,
        val approach: Boolean = false,
        val condition: String = "",
        val say: String = "",
        val anim: Int = -1,
        val gfx: List<Gfx> = emptyList(),
        val sounds: List<Sound> = emptyList(),
        @SerializedName("target_anim") val targetAnim: Int = -1,
        @SerializedName("target_gfx") val targetGfx: List<Gfx> = emptyList(),
        @SerializedName("target_sounds") val targetSounds: List<Sound> = emptyList(),
        val projectiles: List<Proj> = emptyList(),
        @SerializedName("projectile_origin") val projectileOrigin: String = "tile",
        @SerializedName("origin_x") val originX: Int = 0,
        @SerializedName("origin_y") val originY: Int = 0,
        val hits: List<HitDef> = emptyList(),
        @SerializedName("multi_target_area") val multiTargetArea: Area? = null,
        @SerializedName("multi_target_radius") val multiTargetRadius: Int = 0,
        @SerializedName("multi_radius") val multiRadius: Int = 0,
        @SerializedName("impact_anim") val impactAnim: Int = -1,
        @SerializedName("impact_gfx") val impactGfx: List<Gfx> = emptyList(),
        @SerializedName("impact_sounds") val impactSounds: List<Sound> = emptyList(),
        @SerializedName("miss_gfx") val missGfx: List<Gfx> = emptyList(),
        @SerializedName("miss_sounds") val missSounds: List<Sound> = emptyList(),
        @SerializedName("impact_regardless") val impactRegardless: Boolean = false,
        val drains: List<Drain> = emptyList(),
        val freeze: Int = 0,
        val poison: Int = 0,
        val disease: Int = 0,
        val message: String = "",
    )

    class Row(
        val id: Int = -1,
        val name: String = "",
        @SerializedName("void_key") val voidKey: String = "",
        @SerializedName("combat_def") val combatDef: String = "",
        val height: Int = 40,
        @SerializedName("attack_range") val attackRange: Int = 1,
        val attacks: List<Attack> = emptyList(),
    )

    /** Void `ShootProjectile.DEFAULT_HEIGHT` / `Character.gfx`: a player's height. */
    private const val PLAYER_HEIGHT = 40

    /**
     * Condition names that appear in Void's data but are registered by no Void script. Void
     * `CombatApi.condition` returns true for an unregistered name, so these always pass there too
     * (`no_attackers` gates every Kree'arra attack; `confusion_cooldown` is unregistered because
     * ChaosElemental.kt registers "free_inventory_spaces" twice - SOURCE_CONFLICT, the intended check is
     * ported in chaos_elemental_attacks.plugin.kts and overrides this). Any other condition must be ported
     * with [condition] before its attack can be used.
     */
    private val VOID_UNREGISTERED_CONDITIONS = setOf("no_attackers", "confusion_cooldown")

    private val DATA_ATTACK_CYCLE = AttributeKey<Int>()

    private val conditions = HashMap<String, (Npc, Pawn) -> Boolean>()
    private val attackHooks = HashMap<String, (Npc, Pawn) -> Unit>()
    private val impactHooks = HashMap<String, (Npc, Pawn) -> Boolean>()
    private val swingHooks = HashMap<String, (Npc, Pawn, Attack) -> Unit>()
    private val rollHooks = HashMap<String, (Npc, Pawn, HitRoll) -> Unit>()
    private val dealtHooks = HashMap<String, (Npc, Pawn, HitRoll, Int) -> Unit>()

    /**
     * One npc hit after the accuracy roll and before it is dealt. Roll hooks may change [maxHit] or force [landHit];
     * [landsIgnoringPrayer] is the same roll without the protection-prayer short circuit (magic/ranged), else [landHit].
     */
    class HitRoll(
        val offense: String,
        var maxHit: Double,
        var landHit: Boolean,
        val landsIgnoringPrayer: Boolean,
    )

    @Volatile
    private var rows: Map<Int, Row> = emptyMap()

    fun load(file: File = File(DEFAULT_PATH)): Int {
        val loaded: Array<Row> = FileReader(file).use { Gson().fromJson(it, Array<Row>::class.java) }
        rows = loaded.associateBy { it.id }
        return rows.size
    }

    /** Void `npcCondition(name)`. */
    fun condition(
        name: String,
        check: (npc: Npc, target: Pawn) -> Boolean,
    ) {
        conditions[name] = check
    }

    /** Void `npcAttack(def, attack)`: runs for every target after the attack's hits. */
    fun onAttack(
        combatDef: String,
        attack: String,
        hook: (npc: Npc, target: Pawn) -> Unit,
    ) {
        attackHooks["$combatDef:$attack"] = hook
    }

    /** Void `npcImpact(def, attack)`: runs when the hit lands; returning false skips the impact effects. */
    fun onImpact(
        combatDef: String,
        attack: String,
        hook: (npc: Npc, target: Pawn) -> Boolean,
    ) {
        impactHooks["$combatDef:$attack"] = hook
    }

    /** Runs once per swing of any attack of [combatDef], before the targets are handled. */
    fun onSwing(
        combatDef: String,
        hook: (npc: Npc, target: Pawn, attack: Attack) -> Unit,
    ) {
        swingHooks[combatDef] = hook
    }

    /** Runs for every hit of [combatDef] after the accuracy roll and before the hit is dealt. */
    fun onHitRoll(
        combatDef: String,
        hook: (npc: Npc, target: Pawn, roll: HitRoll) -> Unit,
    ) {
        rollHooks[combatDef] = hook
    }

    /** Runs when a hit of [combatDef] is applied to its target, with the final damage. */
    fun onHitDealt(
        combatDef: String,
        hook: (npc: Npc, target: Pawn, roll: HitRoll, damage: Int) -> Unit,
    ) {
        dealtHooks[combatDef] = hook
    }

    fun rowFor(npcId: Int): Row? = rows[npcId]

    fun rows(): Collection<Row> = rows.values

    /**
     * The freeze an attack applies on impact: the table's own value, or for a frost dragon's dragonfire the King Black Dragon's
     * ice breath freeze ([DragonfireTable.FROST_DRAGON_FREEZE_TICKS], owner answer Q13 2026-09-14; the wiki gives no duration).
     */
    fun freezeTicks(
        combatDef: String,
        attack: Attack,
    ): Int =
        when {
            // Wyvern icy breath: OSRS Wiki "freeze the player for 6.6 seconds" (the Void table's 10 is replaced).
            attack.hits.any { it.offense == "icy_breath" } -> WyvernIcyBreath.FREEZE_TICKS
            attack.freeze != 0 -> attack.freeze
            combatDef == DragonfireTable.FROST_DRAGON_COMBAT_DEF && attack.hits.any { it.offense == "dragonfire" } ->
                DragonfireTable.FROST_DRAGON_FREEZE_TICKS
            else -> 0
        }

    /** Sourced freeze blocks: frost dragon dragonfire ([DragonfireTable.blocksFreeze]) and wyvern icy breath ([WyvernIcyBreath.blocksFreeze]). */
    fun freezeBlocked(
        combatDef: String,
        attack: Attack,
        target: Player,
    ): Boolean =
        if (attack.hits.any { it.offense == "icy_breath" }) {
            WyvernIcyBreath.blocksFreeze(target)
        } else {
            DragonfireTable.blocksFreeze(combatDef, DragonfireFormula.protectionOf(target))
        }

    /** True when an attack's condition is ported (or is one Void itself never registers). */
    fun isRunnable(attack: Attack): Boolean =
        attack.condition.isEmpty() || conditions.containsKey(attack.condition) || attack.condition in VOID_UNREGISTERED_CONDITIONS

    private fun runnable(npcId: Int): List<Attack> = rows[npcId]?.attacks?.filter { isRunnable(it) } ?: emptyList()

    /** True when [npc] fights with its data-driven attack sections instead of the generic strategy. */
    fun handles(npc: Npc): Boolean = runnable(npc.id).isNotEmpty()

    /** The longest range among [npc]'s runnable attacks, or null when the npc is not data-driven. */
    fun attackRange(npc: Npc): Int? = runnable(npc.id).maxOfOrNull { it.range }

    fun isDataAttackThisCycle(npc: Npc): Boolean = npc.attr[DATA_ATTACK_CYCLE] == npc.world.currentCycle

    /** True when at least one runnable section of [npc] can hit [target] from where the npc stands now. */
    fun hasValidAttack(
        npc: Npc,
        target: Pawn,
    ): Boolean =
        runnable(npc.id).any { attack ->
            val check = conditions[attack.condition]
            (check == null || check(npc, target)) && withinRange(npc, target, attack)
        }

    /**
     * The animation to play for a section anim on [npc]: the section's own id when it animates the npc's rev-667
     * skeleton, otherwise the npc's 667 combat-definition attack animation ([AnimSkeletons]).
     */
    fun animationFor(
        npc: Npc,
        anim: Int,
    ): Int {
        val world = npc.world
        if (AnimSkeletons.fits(world.definitions, world.filestore, npc.id, anim)) return anim
        val fallback = npc.combatDef.attackAnimation
        // Only swap to the 667 combat-definition anim when that one does animate the npc's skeleton; when no sourced
        // anim fits (SOURCE_BLOCKED families in tools/npc-attacks/skeleton-fixes-report.json) keep the section anim
        // rather than playing no attack animation at all.
        return if (fallback >= 0 && AnimSkeletons.fits(world.definitions, world.filestore, npc.id, fallback)) fallback else anim
    }

    /**
     * Performs one attack swing. Returns false when no attack section is valid from the current position,
     * so the combat cycle keeps approaching instead of attacking.
     */
    fun attack(
        npc: Npc,
        primaryTarget: Pawn,
    ): Boolean {
        val row = rows[npc.id] ?: return false
        val attack = select(npc, primaryTarget, runnable(npc.id)) ?: return false
        perform(npc, row, primaryTarget, attack)
        return true
    }

    /**
     * Performs the named attack section of [npc] regardless of weighted selection, for a boss script that
     * keeps its own mechanics (e.g. burrowing, stomps) but must issue the sourced attack visuals, sounds and
     * hits. Returns false when the npc has no such section.
     */
    fun attackWith(
        npc: Npc,
        target: Pawn,
        attackId: String,
        rowNpcId: Int = npc.id,
    ): Boolean {
        // [rowNpcId]: a variant id Void has no row for may use its sourced sibling's sections.
        val row = rows[rowNpcId] ?: return false
        val attack = row.attacks.firstOrNull { it.id == attackId } ?: return false
        perform(npc, row, target, attack)
        return true
    }

    private fun perform(
        npc: Npc,
        row: Row,
        primaryTarget: Pawn,
        attack: Attack,
    ) {
        npc.attr[DATA_ATTACK_CYCLE] = npc.world.currentCycle

        // Source
        if (attack.anim >= 0) npc.animate(animationFor(npc, attack.anim))
        playGfx(npc, row.height, attack.gfx, null)
        playSounds(npc, attack.sounds, null)
        if (attack.say.isNotEmpty()) npc.forceChat(attack.say)
        swingHooks[row.combatDef]?.invoke(npc, primaryTarget, attack)

        // Target(s)
        for (target in targets(npc, primaryTarget, attack)) {
            val targetHeight = heightOf(target)
            if (attack.targetAnim >= 0) target.animate(attack.targetAnim)
            playGfx(target, targetHeight, attack.targetGfx, null)
            playSounds(target, attack.targetSounds, null)

            val delays = IntArray(attack.projectiles.size)
            attack.projectiles.forEachIndexed { i, p ->
                delays[i] = if (p.id < 0) p.delay ?: 0 else shoot(npc, row, attack, p, target, targetHeight)
            }

            var landed = false
            var firstHit: PawnHit? = null
            attack.hits.forEachIndexed { i, h ->
                var delay = delays.getOrNull(i) ?: -1
                if (delay == -1) {
                    delay = if (isMelee(h.offense) || h.offense == "damage") 0 else 64
                }
                h.delay?.let { delay += it }
                val pawnHit = hit(npc, target, h, delay, DragonfireTable.typeFor(row.combatDef, attack.id), row.combatDef) ?: return@forEachIndexed
                if (pawnHit.hit.hitmarks.sumOf { it.damage } > 0) landed = true
                if (firstHit == null) firstHit = pawnHit
            }
            attackHooks["${row.combatDef}:${attack.id}"]?.invoke(npc, target)

            val impactDelay = delays.firstOrNull()
            val showImpact = attack.impactRegardless || landed
            playGfx(target, targetHeight, if (showImpact) attack.impactGfx else attack.missGfx, impactDelay)
            playSounds(target, if (showImpact) attack.impactSounds else attack.missSounds, impactDelay)
            val impactOn = firstHit
            val delayTicks = if (impactOn == null) hitDelayTicks(impactDelay ?: 0) else 0
            if (impactOn != null) {
                impactOn.hit.addAction { impact(npc, row, target, attack, landed) }
            } else if (delayTicks <= 1) {
                impact(npc, row, target, attack, landed)
            } else {
                npc.world.queue {
                    wait(delayTicks)
                    if (target.isDead() || (target is Player && !target.isOnline)) return@queue
                    impact(npc, row, target, attack, landed)
                }
            }
        }
    }

    // ---- selection (Void Attack.selectAttack / valid / withinRange) ------------------------------------

    private fun select(
        npc: Npc,
        target: Pawn,
        attacks: List<Attack>,
    ): Attack? {
        val valid =
            attacks.filter { attack ->
                val check = conditions[attack.condition]
                (check == null || check(npc, target)) && withinRange(npc, target, attack)
            }
        if (valid.isEmpty()) return null
        val total = valid.sumOf { max(it.chance, 0) }
        if (total <= 0) return valid.first()
        var roll = Random.nextInt(total)
        for (attack in valid) {
            roll -= max(attack.chance, 0)
            if (roll < 0) return attack
        }
        return valid.last()
    }

    private fun withinRange(
        npc: Npc,
        target: Pawn,
        attack: Attack,
    ): Boolean {
        val size = npc.getSize()
        val targetSize = target.getSize()
        if (npc.tile.height != target.tile.height) return false
        if (attack.range <= 1) {
            return Combat.areBordering(npc.tile.x, npc.tile.z, size, size, target.tile.x, target.tile.z, targetSize, targetSize)
        }
        val overlapping =
            npc.tile.x < target.tile.x + targetSize && target.tile.x < npc.tile.x + size &&
                npc.tile.z < target.tile.z + targetSize && target.tile.z < npc.tile.z + size
        if (overlapping) return false
        val nearestX = target.tile.x.coerceIn(npc.tile.x, npc.tile.x + size - 1)
        val nearestZ = target.tile.z.coerceIn(npc.tile.z, npc.tile.z + size - 1)
        if (max(abs(target.tile.x - nearestX), abs(target.tile.z - nearestZ)) > attack.range) return false
        return npc.hasLineOfSightTo(target, projectile = true, maximumDistance = attack.range + size)
    }

    // ---- targets (Void Attack.targets) --------------------------------------------------------------------

    private fun targets(
        npc: Npc,
        target: Pawn,
        attack: Attack,
    ): Set<Pawn> {
        val area = attack.multiTargetArea
        if (area == null) {
            return when {
                attack.multiTargetRadius != 0 -> around(npc, target.tile, attack.multiRadius)
                attack.multiRadius != 0 -> around(npc, npc.tile, attack.multiRadius)
                else -> setOf(target)
            }
        }
        val set = linkedSetOf(target)
        npc.world.players.forEach {
            val t = it.tile
            if (!it.isDead() && t.height == area.level && t.x in area.minX..area.maxX && t.z in area.minZ..area.maxZ) set.add(it)
        }
        return set
    }

    private fun around(
        npc: Npc,
        centre: Tile,
        radius: Int,
    ): Set<Pawn> {
        val set = linkedSetOf<Pawn>()
        val inside = { t: Tile -> t.height == centre.height && abs(t.x - centre.x) <= radius && abs(t.z - centre.z) <= radius }
        npc.world.npcs.forEach { if (it !== npc && !it.isDead() && inside(it.tile)) set.add(it) }
        npc.world.players.forEach { if (!it.isDead() && inside(it.tile)) set.add(it) }
        return set
    }

    // ---- projectiles (Void ShootProjectile.projectile / flightTime) ------------------------------------

    private fun shoot(
        npc: Npc,
        row: Row,
        attack: Attack,
        p: Proj,
        target: Pawn,
        targetHeight: Int,
    ): Int {
        val size = npc.getSize()
        val explicitOrigin = attack.originX != 0 || attack.originY != 0
        val source =
            when {
                attack.projectileOrigin == "centre" -> {
                    val half = size / 2
                    val cx = npc.tile.x + half
                    val cz = npc.tile.z + half
                    val dx = Integer.signum(target.tile.x - cx)
                    val dz = Integer.signum(target.tile.z - cz)
                    Tile(cx + dx * 2, cz + dz * 2, npc.tile.height)
                }
                explicitOrigin -> Tile(npc.tile.x + attack.originX, npc.tile.z + attack.originY, npc.tile.height)
                else -> Tile(target.tile.x.coerceIn(npc.tile.x, npc.tile.x + size - 1), target.tile.z.coerceIn(npc.tile.z, npc.tile.z + size - 1), npc.tile.height)
            }
        val distance = max(abs(source.x - target.tile.x), abs(source.z - target.tile.z)) - 1
        val flightTime = p.timeOffset + distance * p.multiplier
        if (flightTime < 0) return -1
        val startDelay = p.delay ?: p.defDelay
        val curveMin = p.curveMin
        val curveMax = p.curveMax
        val curve =
            if (curveMin != null && curveMax != null) {
                if (curveMax > curveMin) Random.nextInt(curveMin, curveMax + 1) else curveMin
            } else {
                p.defCurve
            }
        val width = if (explicitOrigin) size else 1
        val projectile =
            Projectile.Builder()
                .setTiles(start = source, target = target)
                .setGfx(gfx = p.id)
                .setHeights(startHeight = row.height + p.defHeight, endHeight = p.endHeight ?: (targetHeight + p.defEndHeight))
                .setSlope(angle = curve, steepness = width * 64 + p.sizeOffset)
                .setTimes(delay = startDelay, lifespan = startDelay + flightTime)
                .build()
        npc.world.spawn(projectile)
        return flightTime + startDelay
    }

    // ---- hits (Void Attack.kt target hits + Damage.roll) -------------------------------------------------

    fun isMelee(offense: String): Boolean =
        offense == "melee" || offense == "stab" || offense == "crush" || offense == "slash" ||
            offense == "typeless_stab" || offense == "typeless_crush" || offense == "typeless_slash"

    /** Void `Hit.kt`: client cycles to ticks; 0 is this tick. This server's earliest hit delay is 1. */
    fun hitDelayTicks(clientCycles: Int): Int = if (clientCycles <= 0) 1 else clientCycles / 30 + 1

    private fun hit(
        npc: Npc,
        target: Pawn,
        h: HitDef,
        clientDelay: Int,
        dragonfireType: DragonfireTable.Type,
        combatDef: String,
    ): PawnHit? {
        var offense = h.offense
        if (offense == "random") offense = listOf("crush", "range", "magic").random()
        if (offense == "none" || offense.isEmpty()) return null
        val formula: CombatFormula?
        val hitType: HitType
        when {
            isMelee(offense) -> {
                val style =
                    when {
                        offense.endsWith("stab") -> StyleType.STAB
                        offense.endsWith("slash") -> StyleType.SLASH
                        else -> StyleType.CRUSH
                    }
                npc.prepareAttack(CombatClass.MELEE, style, WeaponStyle.ACCURATE)
                formula = MeleeCombatFormula
                hitType = HitType.MELEE
            }
            offense == "range" -> {
                npc.prepareAttack(CombatClass.RANGED, StyleType.RANGED, WeaponStyle.ACCURATE)
                formula = RangedCombatFormula
                hitType = HitType.RANGE
            }
            offense == "dragonfire" -> {
                npc.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.ACCURATE)
                // RCV-012 decision "dragonfire = OSRS model": the max comes from the OSRS Wiki table for the dragon's
                // category (DragonfireTable), not from the section's max; a zero section max still disables the hit.
                formula = DragonfireFormula(dragonfireType)
                hitType = HitType.REGULAR_HIT
            }
            offense == "damage" -> {
                formula = null
                hitType = HitType.REGULAR_HIT
            }
            else -> { // magic, icy_breath
                npc.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.ACCURATE)
                formula = MagicCombatFormula
                hitType = HitType.MAGIC
            }
        }
        val maxHit =
            when {
                offense == "dragonfire" -> if (h.max > 0) formula!!.getMaxHit(npc, target) else 0.0
                offense == "icy_breath" && h.max > 0 && target is Player -> WyvernIcyBreath.maxHit(target, h.max / 10.0)
                h.max > 0 -> h.max / 10.0
                formula != null -> formula.getMaxHit(npc, target)
                else -> 0.0
            }
        if (maxHit <= 0.0) return null
        // Void Damage.roll: dragonfire and accuracy_roll = false never miss on accuracy.
        val rollAccuracy = h.accuracyRoll && formula != null && offense != "dragonfire"
        val rollValue = npc.world.randomDouble()
        val landHit = !rollAccuracy || formula!!.getAccuracy(npc, target) >= rollValue
        val landsIgnoringPrayer =
            when {
                !rollAccuracy -> true
                formula === MagicCombatFormula -> MagicCombatFormula.getUnprotectedAccuracy(npc, target) >= rollValue
                formula === RangedCombatFormula -> RangedCombatFormula.getUnprotectedAccuracy(npc, target) >= rollValue
                else -> landHit
            }
        val roll = HitRoll(offense = offense, maxHit = maxHit, landHit = landHit, landsIgnoringPrayer = landsIgnoringPrayer)
        rollHooks[combatDef]?.invoke(npc, target, roll)
        val minHit = (h.min / 10.0).coerceAtMost(roll.maxHit - 0.01).coerceAtLeast(0.0)
        val pawnHit =
            npc.dealHit(
                target = target,
                minHit = minHit,
                maxHit = roll.maxHit,
                landHit = roll.landHit,
                delay = hitDelayTicks(clientDelay),
                hitType = hitType,
            )
        dealtHooks[combatDef]?.let { hook ->
            pawnHit.hit.addAction { hook(npc, target, roll, pawnHit.hit.hitmarks.sumOf { it.damage }) }
        }
        return pawnHit
    }

    // ---- impact (Void Attack.kt npcCombatAttack) ----------------------------------------------------------

    private fun impact(
        npc: Npc,
        row: Row,
        target: Pawn,
        attack: Attack,
        landed: Boolean,
    ) {
        val hook = impactHooks["${row.combatDef}:${attack.id}"]
        if (hook != null && !hook(npc, target)) return
        if (attack.impactAnim >= 0) target.animate(attack.impactAnim)
        if (!attack.impactRegardless && !landed) return
        attack.drains.forEach { drain(target, it) }
        val freeze = freezeTicks(row.combatDef, attack)
        if (freeze != 0 && !(target is Player && freezeBlocked(row.combatDef, attack, target))) {
            target.freeze(freeze)
        }
        if (attack.poison != 0) target.poison((attack.poison / 10).coerceAtLeast(1))
        if (attack.message.isNotEmpty() && target is Player) target.message(attack.message)
    }

    private val SKILLS =
        mapOf(
            "attack" to Skills.ATTACK, "defence" to Skills.DEFENCE, "strength" to Skills.STRENGTH,
            "ranged" to Skills.RANGED, "prayer" to Skills.PRAYER, "magic" to Skills.MAGIC,
            "agility" to Skills.AGILITY, "summoning" to Skills.SUMMONING,
        )

    /** Void `Levels.drain` for players: max(0, amount) + max level x multiplier; floor 0 for prayer/summoning, else 1. */
    private fun drain(
        target: Pawn,
        drain: Drain,
    ) {
        if (target !is Player) return
        val skills =
            when (drain.skill) {
                "all" -> SKILLS.values.toList()
                "random" -> listOf(SKILLS.values.random())
                else -> listOfNotNull(SKILLS[drain.skill])
            }
        for (skill in skills) {
            val amount = (if (drain.max > drain.min) Random.nextInt(drain.min, drain.max + 1) else drain.min).coerceAtLeast(0) +
                (if (drain.multiplier > 0.0) (target.skills.getMaxLevel(skill) * drain.multiplier).toInt() else 0)
            val floor = if (skill == Skills.PRAYER || skill == Skills.SUMMONING) 0 else 1
            val current = target.skills.getCurrentLevel(skill)
            target.skills.setCurrentLevel(skill, (current - amount).coerceAtLeast(floor).coerceAtMost(current))
        }
    }

    // ---- visuals ------------------------------------------------------------------------------------------

    private fun heightOf(pawn: Pawn): Int = if (pawn is Npc) rows[pawn.id]?.height ?: PLAYER_HEIGHT else PLAYER_HEIGHT

    /** Void `Character.gfx`: height = character height + definition height (absent -> 0), delay from the definition. */
    private fun playGfx(
        pawn: Pawn,
        characterHeight: Int,
        list: List<Gfx>,
        delay: Int?,
    ) {
        for (g in list) {
            val d = delay ?: g.delay ?: g.defDelay
            val height = g.height ?: g.defHeight?.let { (characterHeight + it).coerceAtLeast(0) } ?: 0
            if (g.area) {
                val tile = Tile(pawn.tile.x + g.offsetX, pawn.tile.z + g.offsetY, pawn.tile.height)
                pawn.world.spawn(TileGraphic(tile, g.id, height = g.height ?: 0, delay = d))
            } else {
                pawn.graphic(g.id, height = height, delay = d)
            }
        }
    }

    /** Void `Attack.play(sounds)`: radius 0 is heard by that character only (a no-op on an npc), else an area sound. */
    private fun playSounds(
        pawn: Pawn,
        list: List<Sound>,
        delay: Int?,
    ) {
        for (s in list) {
            val d = delay ?: s.delay
            if (s.radius == 0) {
                (pawn as? Player)?.playSound(s.id, delay = d)
            } else {
                val tile = Tile(pawn.tile.x + s.offsetX, pawn.tile.z + s.offsetY, pawn.tile.height)
                pawn.world.spawn(AreaSound(tile = tile, id = s.id, radius = s.radius.coerceAtMost(15), volume = 1, delay = d))
            }
        }
    }
}
