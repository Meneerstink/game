package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.plugins.api.ext.isProtectedFromSummoning
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.combat.PawnHit
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.combat.WeaponStyle
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.ProjectileType
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.addXp
import gg.rsmod.plugins.api.ext.isMulti
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.npc
import gg.rsmod.plugins.api.ext.playSound
import gg.rsmod.plugins.api.ext.prepareAttack
import gg.rsmod.plugins.content.combat.canEngageCombat
import gg.rsmod.plugins.content.combat.createProjectile
import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.formula.CombatFormula
import gg.rsmod.plugins.content.combat.formula.MagicCombatFormula
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.formula.RangedCombatFormula
import gg.rsmod.plugins.content.combat.getCombatTarget
import gg.rsmod.plugins.content.combat.getLastHitBy
import gg.rsmod.plugins.content.combat.isAttackDelayReady
import gg.rsmod.plugins.content.combat.isBeingAttacked
import gg.rsmod.plugins.content.combat.moveToAttackRange
import gg.rsmod.plugins.content.combat.postAttackLogic
import gg.rsmod.plugins.content.combat.removeCombatTarget
import gg.rsmod.plugins.content.combat.strategy.MagicCombatStrategy

/** Native combat-engine integration for player-owned Summoning familiars. */
object FamiliarCombat {
    /** Transient per-familiar state; a queued move belongs to the live familiar, not the player. */
    private val NEXT_ATTACK_SPECIAL_ATTR = AttributeKey<SummoningScrollData>()

    /** Steel Titan combat set from `RSPS_SUMMONING_SOUNDS_2009_2012_MASTER_QC3`. */
    internal const val STEEL_TITAN_ATTACK_SOUND = 4720
    internal const val STEEL_TITAN_RANGED_SOUND = 4616
    internal const val STEEL_TITAN_RANGED_IMPACT_SOUND = 4670

    /**
     * Dedicated familiar normal-combat cues from QC3. The optional ranged and impact cues are
     * emitted only on the corresponding combat event. General entity sounds and revision-530
     * base-creature candidates are deliberately absent: neither proves a familiar binding.
     * Steel Titan remains on its existing explicit route below because that route was already
     * completed and its revision-667 attack sequence is silent.
     */
    internal data class NormalAttackSounds(
        val attack: Int,
        val ranged: Int? = null,
        val impact: Int? = null,
    )

    internal val NORMAL_ATTACK_SOUNDS: Map<SummoningPouchData, NormalAttackSounds> = mapOf(
        SummoningPouchData.THORNY_SNAIL to NormalAttackSounds(4320),
        SummoningPouchData.SPIRIT_MOSQUITO to NormalAttackSounds(4610),
        SummoningPouchData.DESERT_WYRM to NormalAttackSounds(4309),
        SummoningPouchData.SPIRIT_SCORPION to NormalAttackSounds(4280),
        SummoningPouchData.SPIRIT_TZ_KIH to NormalAttackSounds(4627, ranged = 4630),
        SummoningPouchData.COMPOST_MOUND to NormalAttackSounds(4233),
        SummoningPouchData.GIANT_CHINCHOMPA to NormalAttackSounds(4675),
        SummoningPouchData.HONEY_BADGER to NormalAttackSounds(4160),
        SummoningPouchData.VOID_RAVAGER to NormalAttackSounds(4711),
        SummoningPouchData.VOID_SHIFTER to NormalAttackSounds(4689),
        SummoningPouchData.VOID_SPINNER to NormalAttackSounds(4691),
        SummoningPouchData.VOID_TORCHER to NormalAttackSounds(4599),
        SummoningPouchData.BRONZE_MINOTAUR to NormalAttackSounds(4334),
        SummoningPouchData.IRON_MINOTAUR to NormalAttackSounds(4334),
        SummoningPouchData.STEEL_MINOTAUR to NormalAttackSounds(4334),
        SummoningPouchData.MITHRIL_MINOTAUR to NormalAttackSounds(4334),
        SummoningPouchData.ADAMANT_MINOTAUR to NormalAttackSounds(4334),
        SummoningPouchData.RUNE_MINOTAUR to NormalAttackSounds(4334),
        SummoningPouchData.PYRELORD to NormalAttackSounds(4708),
        SummoningPouchData.SPIRIT_JELLY to NormalAttackSounds(4182),
        SummoningPouchData.SPIRIT_GRAAHK to NormalAttackSounds(4605),
        SummoningPouchData.SPIRIT_KYATT to NormalAttackSounds(4650),
        SummoningPouchData.SPIRIT_LARUPIA to NormalAttackSounds(4631),
        SummoningPouchData.KARAMTHULHU_OVERLORD to NormalAttackSounds(4179),
        SummoningPouchData.SMOKE_DEVIL to NormalAttackSounds(4330),
        SummoningPouchData.ABYSSAL_LURKER to NormalAttackSounds(4215),
        SummoningPouchData.SPIRIT_COBRA to NormalAttackSounds(4325),
        SummoningPouchData.STRANGER_PLANT to NormalAttackSounds(4218),
        SummoningPouchData.BARKER_TOAD to NormalAttackSounds(4301),
        SummoningPouchData.WAR_TORTOISE to NormalAttackSounds(4296),
        SummoningPouchData.BUNYIP to NormalAttackSounds(4139),
        SummoningPouchData.EVIL_TURNIP to NormalAttackSounds(4331),
        SummoningPouchData.ARCTIC_BEAR to NormalAttackSounds(4224),
        SummoningPouchData.OBSIDIAN_GOLEM to NormalAttackSounds(4718),
        SummoningPouchData.GRANITE_LOBSTER to NormalAttackSounds(4235),
        SummoningPouchData.FORGE_REGENT to NormalAttackSounds(4643),
        SummoningPouchData.TALON_BEAST to NormalAttackSounds(4681),
        SummoningPouchData.GIANT_ENT to NormalAttackSounds(4282),
        SummoningPouchData.FIRE_TITAN to NormalAttackSounds(4614),
        SummoningPouchData.ICE_TITAN to NormalAttackSounds(4695),
        SummoningPouchData.MOSS_TITAN to NormalAttackSounds(4662),
        SummoningPouchData.HYDRA to NormalAttackSounds(4251, ranged = 4185),
        SummoningPouchData.SPIRIT_DAGANNOTH to NormalAttackSounds(4299),
        SummoningPouchData.LAVA_TITAN to NormalAttackSounds(4654),
        SummoningPouchData.SWAMP_TITAN to NormalAttackSounds(4624),
        SummoningPouchData.UNICORN_STALLION to NormalAttackSounds(4196),
        SummoningPouchData.GEYSER_TITAN to NormalAttackSounds(4602, ranged = 4713, impact = 4698),
        SummoningPouchData.ABYSSAL_TITAN to NormalAttackSounds(4644),
        SummoningPouchData.IRON_TITAN to NormalAttackSounds(4633),
        SummoningPouchData.PACK_YAK to NormalAttackSounds(4200),
    )

    internal fun hasQueuedNextAttack(familiar: Npc): Boolean =
        familiar.attr[NEXT_ATTACK_SPECIAL_ATTR] != null

    internal fun queueNextAttack(familiar: Npc, scroll: SummoningScrollData): Boolean {
        if (hasQueuedNextAttack(familiar)) return false
        familiar.attr[NEXT_ATTACK_SPECIAL_ATTR] = scroll
        return true
    }

    private fun takeQueuedNextAttack(familiar: Npc): SummoningScrollData? {
        val queued = familiar.attr[NEXT_ATTACK_SPECIAL_ATTR]
        familiar.attr.remove(NEXT_ATTACK_SPECIAL_ATTR)
        return queued
    }

    /**
     * RCV-010 A1: every familiar damage figure in this package (the [SummoningCombatDefinitions] ledger's
     * maxHit and every special-move max hit) is sourced from Void, whose life-point unit is real HP x10
     * (Vampyre bat 40, Pack yak 125). Hitpoints, hitsplats and [dealHit] are 1:1 real HP, so the
     * figures are converted here, once, the same way `NpcAttacks` converts its x10 sections (max / 10.0).
     */
    const val LEDGER_UNITS_PER_HITPOINT = 10.0

    fun ledgerToHitpoints(ledgerMaxHit: Double): Double = ledgerMaxHit / LEDGER_UNITS_PER_HITPOINT

    /** The single familiar damage boundary: takes a ledger (x10) max hit and deals 1:1 damage. */
    fun dealLedgerHit(
        familiar: Npc,
        target: Pawn,
        ledgerMaxHit: Double,
        landHit: Boolean,
        delay: Int,
        hitType: HitType,
        onHit: (PawnHit) -> Unit = {},
    ): PawnHit {
        val maxHit = ledgerToHitpoints(ledgerMaxHit)
        // dealHit rolls in [0.1, maxHit); a max below one real hitpoint can only ever splat 0.
        val lands = landHit && maxHit > 0.1
        return familiar.dealHit(target, 0.1, if (lands) maxHit else 1.0, lands, delay, onHit, hitType)
    }

    fun commandAttack(player: Player, target: Pawn, silent: Boolean = false): Boolean {
        val familiar = Familiar.current(player) ?: return false
        val definition = SummoningCombatDefinitions.getByNpc(familiar.id)
        val capabilities = FamiliarCapabilityTable.forNpc(familiar.id)
        if (definition == null || !definition.isExecutable || capabilities?.canReceiveAttackCommand != true) {
            if (!silent) player.message("Your familiar cannot fight that target.")
            return false
        }
        if (target === player || target === familiar || !target.isAlive()) {
            if (!silent) player.message("Your familiar cannot attack that target.")
            return false
        }
        /*
         * The multi-way rule is two separate rules with two separate messages, and collapsing them
         * into one told the player the wrong thing half the time: standing in single combat is a
         * different failure from standing in multi and pointing at something that is not.
         * Wordings supplied by the owner from period screenshots.
         */
        if (!player.tile.isMulti(player.world)) {
            if (!silent) player.message("Your familiar cannot fight unless it is in a multi-way combat area.")
            return false
        }
        if (!target.tile.isMulti(player.world)) {
            if (!silent) player.message("Your familiar cannot fight a target that is not in a multi-way combat area.")
            return false
        }
        if (!player.world.plugins.canAttack(player, target)) {
            if (!silent) player.message("You cannot order your familiar to attack that target.")
            return false
        }
        familiar.attack(target)
        return familiar.getCombatTarget() === target
    }

    /** Joins a deliberate owner fight, while preserving the four defensive-only exceptions. */
    fun assist(player: Player) = engageOwnerTarget(player, retarget = false)

    /**
     * The "Call familiar" button's second job: "If you are fighting in a multicombat area, this
     * button will also make your familiar attack your enemy." Unlike [assist] this is allowed to
     * pull the familiar off a target it is already locked onto, which is the whole point of
     * recalling it mid-fight. It still obeys the same defensive-only policy.
     */
    fun recallToOwnerTarget(player: Player) = engageOwnerTarget(player, retarget = true)

    private fun engageOwnerTarget(
        player: Player,
        retarget: Boolean,
    ) {
        val familiar = Familiar.current(player) ?: return
        val current = familiar.getCombatTarget()
        if (current != null && !retarget) return
        val definition = SummoningCombatDefinitions.getByNpc(familiar.id) ?: return
        if (!definition.isExecutable || definition.assistMode == FamiliarAssistMode.NONE) return
        val target = ownerAssistTarget(player, definition.assistMode) ?: return
        if (current === target) return
        commandAttack(player, target, silent = true)
    }

    internal fun ownerAssistTarget(player: Player, mode: FamiliarAssistMode): Pawn? {
        if (mode == FamiliarAssistMode.NONE) return null
        // Novite Familiar.processNPC checks the owner's attacked-by delay; Void also assists
        // from npcCombatStart. A last-hit reference alone can survive long after combat ends.
        val attacker = player.getLastHitBy()?.takeIf { player.isBeingAttacked() && it.isAlive() }
        if (mode == FamiliarAssistMode.DEFENSIVE_ONLY) return attacker
        return player.getCombatTarget()?.takeIf { it.isAlive() } ?: attacker
    }

    suspend fun handleCombat(task: QueueTask) {
        val familiar = task.npc
        val owner = familiar.owner ?: run {
            familiar.removeCombatTarget()
            return
        }
        val definition = SummoningCombatDefinitions.getByNpc(familiar.id)
        val capabilities = FamiliarCapabilityTable.forNpc(familiar.id)
        if (definition == null || !definition.isExecutable || capabilities?.retaliates != true || Familiar.current(owner) !== familiar) {
            familiar.removeCombatTarget()
            return
        }
        var target = familiar.getCombatTarget() ?: return
        while (Familiar.current(owner) === familiar && familiar.canEngageCombat(target)) {
            if (!familiar.isAttackDelayReady()) {
                task.wait(1)
                target = familiar.getCombatTarget() ?: break
                continue
            }
            familiar.facePawn(target)
            val projectile = definition.style != FamiliarAttackStyle.MELEE
            if (!familiar.moveToAttackRange(task, target, definition.attackRange, projectile)) {
                task.wait(1)
                target = familiar.getCombatTarget() ?: break
                continue
            }
            val queuedSpecial = takeQueuedNextAttack(familiar)
            if (queuedSpecial != null && target is Npc) {
                SummoningSpecialMoves.executeNextAttack(owner, familiar, target, queuedSpecial)
            } else {
                attack(familiar, owner, target, definition)
            }
            familiar.postAttackLogic(target)
            task.wait(definition.attackSpeed)
            target = familiar.getCombatTarget() ?: break
        }
        familiar.resetFacePawn()
        familiar.removeCombatTarget()
    }

    /**
     * Whether [target]'s current overhead is a Summoning protection and therefore stops familiar
     * damage outright. Shared with [SummoningSpecialMoves] so an ordinary familiar attack and a
     * special move cannot disagree about whether Deflect Summoning is doing anything - see the
     * note inside [attack] for the source and for why "does not land" is the sourced outcome
     * rather than a guessed percentage.
     */
    fun blockedBySummoningProtection(target: Pawn): Boolean = target is Player && target.isProtectedFromSummoning()

    private fun attack(
        familiar: Npc,
        owner: Player,
        target: Pawn,
        definition: SummoningCombatDefinition,
    ) {
        val (combatClass, styleType, weaponStyle, formula, hitType, projectileType) =
            when (definition.style) {
                FamiliarAttackStyle.MELEE -> AttackSetup(
                    CombatClass.MELEE, StyleType.CRUSH, WeaponStyle.CONTROLLED,
                    MeleeCombatFormula, HitType.MELEE, null,
                )
                FamiliarAttackStyle.RANGED -> AttackSetup(
                    CombatClass.RANGED, StyleType.RANGED, WeaponStyle.ACCURATE,
                    RangedCombatFormula, HitType.RANGE, ProjectileType.ARROW,
                )
                FamiliarAttackStyle.MAGIC -> AttackSetup(
                    CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.ACCURATE,
                    MagicCombatFormula, HitType.MAGIC, ProjectileType.MAGIC,
                )
                FamiliarAttackStyle.NONE -> return
            }
        familiar.prepareAttack(combatClass, styleType, weaponStyle)
        // Steel Titan is the proven exception: its attack sequence (8190), source graphic (1444),
        // and projectile (1445) are silent in this revision. Its completed route is kept explicit.
        // Other source-backed dedicated cues use the same event boundaries here; sequence-attached
        // sounds remain client-owned and are not replaced with base-creature candidates.
        val sourcedSounds = NORMAL_ATTACK_SOUNDS[definition.pouch]
        if (definition.pouch == SummoningPouchData.STEEL_TITAN) {
            FamiliarAudio.play(owner, STEEL_TITAN_ATTACK_SOUND)
        } else {
            sourcedSounds?.let { FamiliarAudio.play(owner, it.attack) }
        }
        familiar.animate(definition.attackAnimation, priority = true)
        if (definition.attackGraphic >= 0) familiar.graphic(definition.attackGraphic)

        var hitDelay = 1
        if (definition.projectile >= 0 && projectileType != null) {
            val projectile = familiar.createProjectile(target, definition.projectile, projectileType)
            familiar.world.spawn(projectile)
            if (definition.pouch == SummoningPouchData.STEEL_TITAN) {
                FamiliarAudio.play(owner, STEEL_TITAN_RANGED_SOUND)
            } else {
                sourcedSounds?.ranged?.let { FamiliarAudio.play(owner, it) }
            }
            hitDelay = MagicCombatStrategy.getHitDelay(familiar.getCentreTile(), target.getCentreTile())
        }

        /*
         * Protect from Summoning (normal book, slot 16) and Deflect Summoning (curse book, slot 6)
         * are the only two prayers in the game whose entire purpose is familiar damage, and until
         * now neither one did anything at all: their overheads rendered and nothing else happened,
         * so the curse the owner named as unfinished was cosmetic.
         *
         * SOURCE: the overhead itself is proven - Novite's rev-667 `Prayer.getPrayerHeadIcon`
         * builds exactly this project's `PrayerIcon` ids (curse slot 6 alone -> 15, with Deflect
         * Melee/Magic/Missiles -> 16/18/17; normal slot 16 alone -> 7, combined -> 8/10/9), and the
         * 2011 knowledge base describes both as protecting against familiars. Neither donor nor
         * Darkan implements a damage *number* for it, so none is invented here: this uses the same
         * outcome this codebase already gives every other protection prayer against a non-player
         * attacker - the hit does not land (`MeleeCombatFormula.getAccuracy` returns 0.0 when
         * `pawn !is Player`). A familiar is always an Npc, so that branch is the whole rule and
         * there is no percentage to guess.
         */
        val summoningProtected = target is Player && target.isProtectedFromSummoning()
        val accuracy = formula.getAccuracy(familiar, target)
        dealLedgerHit(
            familiar,
            target,
            definition.maxHit.toDouble(),
            !summoningProtected && accuracy >= familiar.world.randomDouble(),
            hitDelay,
            hitType,
        ) { hit ->
            attachOwnerExperience(hit, familiar, owner, target)
            if (definition.pouch == SummoningPouchData.STEEL_TITAN && definition.projectile >= 0) {
                FamiliarAudio.play(owner, STEEL_TITAN_RANGED_IMPACT_SOUND)
            } else if (definition.projectile >= 0) {
                sourcedSounds?.impact?.let { FamiliarAudio.play(owner, it) }
            }
        }
    }

    /**
     * Credits the owner for damage their familiar dealt, "as if you had inflicted the damage
     * yourself".
     *
     * Which skill is credited comes from the familiar's sourced [FamiliarSkillFocus], not from how
     * it swings: the knowledge base gives every fighting familiar a Skill Focus of Attack,
     * Strength, Defence, Controlled, Ranged or Magic, and several familiars that attack in melee
     * animation credit Magic or Ranged. Deriving the skill from [FamiliarAttackStyle] instead - as
     * this used to - gave every melee familiar the three-way controlled split, which is only
     * correct for the twelve familiars whose focus really is Controlled.
     *
     * The rates themselves are unchanged from the existing implementation.
     */
    private fun attachOwnerExperience(
        pawnHit: PawnHit,
        familiar: Npc,
        owner: Player,
        target: Pawn,
    ) {
        val focus = SummoningCatalogue.getByNpc(familiar.id)?.skillFocus ?: return
        pawnHit.hit.addAction {
            if (Familiar.current(owner) !== familiar || !owner.isOnline) return@addAction
            val damage = pawnHit.hit.hitmarks.sumOf { it.damage }.coerceAtMost(target.getMaximumLifepoints())
            if (damage <= 0) return@addAction
            when (focus) {
                FamiliarSkillFocus.ATTACK -> owner.addXp(Skills.ATTACK, damage * 0.4)
                FamiliarSkillFocus.STRENGTH -> owner.addXp(Skills.STRENGTH, damage * 0.4)
                FamiliarSkillFocus.DEFENCE -> owner.addXp(Skills.DEFENCE, damage * 0.4)
                FamiliarSkillFocus.CONTROLLED -> {
                    owner.addXp(Skills.ATTACK, damage * 0.133)
                    owner.addXp(Skills.STRENGTH, damage * 0.133)
                    owner.addXp(Skills.DEFENCE, damage * 0.133)
                }
                FamiliarSkillFocus.RANGED -> owner.addXp(Skills.RANGED, damage * 0.4)
                FamiliarSkillFocus.MAGIC -> owner.addXp(Skills.MAGIC, damage * 0.4)
                FamiliarSkillFocus.NONE -> return@addAction
            }
            owner.addXp(Skills.CONSTITUTION, damage * 0.133)
        }
    }

    private data class AttackSetup(
        val combatClass: CombatClass,
        val styleType: StyleType,
        val weaponStyle: WeaponStyle,
        val formula: CombatFormula,
        val hitType: HitType,
        val projectileType: ProjectileType?,
    )
}
