package gg.rsmod.plugins.content.areas.wilderness.bosses

import gg.rsmod.game.model.Tile
import gg.rsmod.plugins.api.BonusSlot
import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.combat.CombatScript
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.combat.WeaponStyle
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.cfg.Npcs
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.*
import gg.rsmod.plugins.content.combat.formula.MagicCombatFormula
import gg.rsmod.plugins.content.combat.formula.MeleeCombatFormula
import gg.rsmod.plugins.content.combat.formula.RangedCombatFormula
import gg.rsmod.plugins.content.combat.strategy.MagicCombatStrategy
import gg.rsmod.plugins.content.combat.strategy.RangedCombatStrategy
import gg.rsmod.plugins.content.drops.DropTableFactory
import gg.rsmod.plugins.content.mechanics.pvp.emblem.DeadmanEmblem
import gg.rsmod.plugins.content.skills.slayer.getSlayerAssignment
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sqrt

/**
 * N522 bundle 3b: the OSRS revenants of the OSRS Revenant Caves (imported as their own npcs, OsrsNpcImportTool batch rev-caves, so
 * their OSRS drop table does not touch the rev-667 revenants elsewhere in the Wilderness, which keep their 2011 table).
 *
 * Stats: OSRS Wiki infobox per revenant (2026-11-02). Spawns: the wiki LocLines (mapID 26, plane 0). Fight - OSRS Wiki "Revenants":
 * all three styles, "favour the style you are weakest against", heal below 50%, the magic attack also hits up to 10 more players
 * on the target's tile. The style choice is the one the rev-667 revenants already use ([gg.rsmod.plugins.content.areas.wilderness
 * .Revenants]): magic, unless the target protects from magic - then melee or ranged by the lower defence bonus. Projectiles, gfx and
 * the heal (1/5 per attack below half, +1/4 of the hitpoints, gfx 1221): Kronos-184 activities/miscpvm/Revenant.java (ranged 206 ->
 * 3201, magic 1415 -> 3202, magic impact 1454 -> 3203, heal 1221 -> 3204; OsrsFxImportTool batch rev-caves). Attack / defend / death
 * sequences: Kronos data/npcs/combat per monster (hobgoblin: RuneLite gameval HOBGOBLIN_*), imported in the same npc batch.
 *
 * Drops - OSRS Wiki "Template:Revenants/Drops" (DropLogProject, Jagex data), per combat level: A = floor(2200 / floor(sqrt(cb))),
 * B = 15 + floor((cb + 60)^2 / 200). Each kill: ether always; one roll in A - 1 on the rare table (the uniques, 1 / 26.667, and the
 * statuette table out of 40), min(A, B) - 1 on the 198-weight table, the rest coins (only below level 132); the blighted table at
 * 100 / (250 - cb); no looting bag (N424 / N561: out of the game). Owner decision N522: the six ancient artefacts are OUR Deadman emblems - by rank, emblem ->
 * tier 1, totem 2, statuette 3, medallion 4, effigy 5, relic 6. Unskulled off-task rates. Not ported: the Revenant cave teleport
 * (today's OSRS item is the configurable 'Middle entrance' scroll that needs the level 26 crevice, not built yet) - its weight is
 * left as nothing so every other rate stays the wiki rate; the champion scrolls (no Champions' Challenge here).
 */
object RevenantCaveRevenants {
    const val GFX_RANGED = 3201 // OSRS 206
    const val GFX_MAGIC = 3202 // OSRS 1415
    const val GFX_MAGIC_HIT = 3203 // OSRS 1454
    const val GFX_HEAL = 3204 // OSRS 1221
    const val GFX_SPLASH = 85

    const val MAGIC_EXTRA_TARGETS = 10

    data class Rev(
        val id: Int,
        val name: String,
        val combat: Int,
        val hp: Int,
        val att: Int, val str: Int, val def: Int, val mage: Int, val range: Int,
        val attBns: Int, val strBns: Int, val aMagic: Int, val mBns: Int, val aRange: Int, val rngBns: Int,
        val dStab: Int, val dSlash: Int, val dCrush: Int, val dMagic: Int, val dRange: Int,
        val maxHit: Int,
        val speed: Int,
        val respawn: Int,
        val melee: StyleType,
        val attack: Int, val defend: Int, val death: Int,
        val spawns: List<Tile>,
    )

    // name, combat, hp, att str def mage range, attbns strbns amagic mbns arange rngbns, dstab dslash dcrush dmagic dstandard, max, speed, respawn
    val REVS =
        listOf(
            Rev(Npcs.REVENANT_IMP_OSRS, "Revenant imp", 7, 10, 5, 5, 4, 9, 5, 0, 0, 5, 0, 0, 0, 0, 0, 0, 5, 0, 2, 5, 33, StyleType.STAB,
                16142, 16143, 16144, listOf(Tile(3212, 10196), Tile(3218, 10188), Tile(3197, 10070), Tile(3203, 10075))),
            Rev(Npcs.REVENANT_GOBLIN_OSRS, "Revenant goblin", 15, 14, 13, 14, 14, 12, 15, 6, 8, 37, 12, 21, 12, 25, 28, 31, 1, 31, 3, 5, 83, StyleType.CRUSH,
                15643, 15642, 15641, listOf(Tile(3221, 10065), Tile(3226, 10074))),
            Rev(Npcs.REVENANT_PYREFIEND_OSRS, "Revenant pyrefiend", 52, 48, 60, 40, 33, 67, 40, 0, 0, 0, 0, 0, 0, 45, 40, 50, 15, 10, 5, 5, 60, StyleType.CRUSH,
                15646, 15645, 15644, listOf(Tile(3169, 10158), Tile(3184, 10151), Tile(3248, 10152), Tile(3254, 10141))),
            Rev(Npcs.REVENANT_HOBGOBLIN_OSRS, "Revenant hobgoblin", 60, 72, 50, 50, 41, 55, 60, 20, 24, 5, 0, 25, 0, 65, 60, 68, 30, 50, 8, 4, 50, StyleType.SLASH,
                16145, 16146, 16147, listOf(Tile(3240, 10096), Tile(3246, 10104))),
            Rev(Npcs.REVENANT_CYCLOPS_OSRS, "Revenant cyclops", 82, 110, 60, 73, 49, 65, 70, 53, 64, 0, 0, 0, 0, 110, 130, 135, 10, 135, 16, 6, 50, StyleType.CRUSH,
                16148, 16149, 16150, listOf(Tile(3167, 10195), Tile(3175, 10183))),
            Rev(Npcs.REVENANT_HELLHOUND_OSRS, "Revenant hellhound", 90, 80, 76, 80, 80, 104, 80, 38, 40, 30, 0, 0, 0, 98, 140, 142, 62, 140, 14, 5, 50, StyleType.STAB,
                16151, 16152, 16153, listOf(Tile(3240, 10160), Tile(3249, 10175))),
            Rev(Npcs.REVENANT_DEMON_OSRS, "Revenant demon", 98, 80, 83, 76, 80, 120, 80, 30, 50, 50, 24, 40, 30, 124, 118, 130, 85, 90, 15, 5, 50, StyleType.CRUSH,
                16154, 16155, 16156, listOf(Tile(3152, 10112), Tile(3169, 10115), Tile(3179, 10201), Tile(3186, 10191))),
            Rev(Npcs.REVENANT_ORK_OSRS, "Revenant ork", 105, 105, 99, 100, 60, 110, 130, 60, 55, 0, 0, 50, 0, 118, 150, 146, 50, 148, 20, 5, 27, StyleType.SLASH,
                16157, 16158, 16159, listOf(Tile(3216, 10128), Tile(3230, 10133), Tile(3207, 10093), Tile(3221, 10095))),
            Rev(Npcs.REVENANT_DARK_BEAST_OSRS, "Revenant dark beast", 120, 140, 93, 110, 80, 130, 135, 65, 60, 0, 0, 45, 45, 113, 152, 155, 70, 158, 23, 5, 27, StyleType.CRUSH,
                15658, 15659, 15660, listOf(Tile(3200, 10166), Tile(3214, 10164))),
            Rev(Npcs.REVENANT_KNIGHT_OSRS, "Revenant knight", 126, 143, 100, 119, 80, 146, 146, 69, 71, 55, 30, 55, 45, 145, 200, 180, 95, 190, 27, 5, 20, StyleType.SLASH,
                15783, 15706, 15707, listOf(Tile(3209, 10216), Tile(3228, 10221))),
            Rev(Npcs.REVENANT_DRAGON_OSRS, "Revenant dragon", 135, 155, 106, 126, 87, 150, 151, 72, 78, 61, 35, 60, 51, 143, 206, 188, 101, 197, 30, 5, 20, StyleType.SLASH,
                16160, 16161, 15612, listOf(Tile(3230, 10201), Tile(3244, 10198))),
        )

    val BY_ID: Map<Int, Rev> = REVS.associateBy { it.id }
    val ids: IntArray = REVS.map { it.id }.toIntArray()

    // ------------------------------------------------------------------ drops

    fun a(cb: Int): Int = floor(2200.0 / floor(sqrt(cb.toDouble()))).toInt()

    fun b(cb: Int): Int = 15 + ((cb + 60) * (cb + 60)) / 200

    /** Ether: "Revenant ether is only dropped in even quantity amounts", 2 .. (floor(sqrt(cb)) + 1) * 2. */
    fun maxEther(cb: Int): Int = (floor(sqrt(cb.toDouble())).toInt() + 1) * 2

    fun maxCoins(cb: Int): Int = 1 + 25 * floor(sqrt(cb.toDouble())).toInt()

    fun emblem(tier: Int): Int = DeadmanEmblem.FIRST_EMBLEM + tier - 1

    /**
     * The rare table. Unskulled: uniques 1/26.667 (avarice 2/5, each wilderness weapon 1/5) and the statuette table out of 40 (x10:
     * 390 + 15 = 405). Skulled (OSRS Wiki: "uniques nearly doubled", the template's alternative rarities): uniques 1/14.667 and the
     * statuette table out of 22 without the dragon med helm (x10 per 22: 220 + 15 = 235, the uniques 15/235 = 1/15.67 ~ 1/14.667).
     */
    fun rareTable(skulled: Boolean) =
        if (skulled) rareSkulled else rareUnskulled

    // Template:Revenants/Drops oldid=15089327 (N522-WILDERNESS-ONDERZOEK 1.3): the artefact ("statuette") roll is its own table out of
    // 40 (skulled 22, no dragon med helm), taken on the 1/A rare slot; the uniques are a separate roll (below). Before 2026-11-04 both
    // shared one 405 / 235 table, which gave 1/27 instead of 1/26.667 and 60/405 instead of 6/40.
    private val rareSkulled =
        DropTableFactory.build {
            main {
                total(22)
                obj(emblem(1), slots = 1) // ancient emblem 1/22 skulled
                obj(emblem(2), slots = 4) // ancient totem
                obj(emblem(3), slots = 2) // ancient statuette
                obj(emblem(4), slots = 1) // ancient medallion
                obj(emblem(5), slots = 1) // ancient effigy
                obj(emblem(6), slots = 1) // ancient relic
                obj(Items.ANCIENT_CRYSTAL, slots = 3)
                obj(Items.MAGIC_SEED, 2..6, slots = 4)
                obj(Items.YEW_SEED, 2..6, slots = 4)
                nothing(1) // the skulled lines sum to 21/22
            }
        }

    private val rareUnskulled =
        DropTableFactory.build {
            main {
                total(40)
                // The six ancient artefacts are our Deadman emblems (owner decision N522), by rank.
                obj(emblem(1), slots = 6) // ancient emblem 6/40
                obj(emblem(2), slots = 4) // ancient totem 4/40
                obj(emblem(3), slots = 2) // ancient statuette 2/40
                obj(emblem(4), slots = 1) // ancient medallion 1/40
                obj(emblem(5), slots = 1) // ancient effigy 1/40
                obj(emblem(6), slots = 1) // ancient relic 1/40
                obj(Items.ANCIENT_CRYSTAL, slots = 3)
                obj(Items.MAGIC_SEED, 2..6, slots = 4)
                obj(Items.YEW_SEED, 2..6, slots = 4)
                obj(Items.DRAGON_MED_HELM, slots = 13)
                nothing(1) // the lines sum to 39/40
            }
        }

    /**
     * The uniques roll per kill (same template): unskulled 1/(A x 26.667) = 3/(80A), skulled 1/(A x 14.667) = 3/(44A); on a revenant
     * Slayer task 1/(A x 5.333) = 3/(16A) and skulled 1/(A x 2.933) = 15/(44A). Of a unique the Amulet of avarice is 2/5 and each
     * wilderness weapon 1/5, so the four lines take 6 + 3 + 3 + 3 = 15 slots of [uniqueTotal].
     */
    fun uniqueTotal(a: Int, skulled: Boolean, onTask: Boolean): Int =
        when {
            onTask && skulled -> 44 * a
            onTask -> 80 * a
            skulled -> 220 * a
            else -> 400 * a
        }

    /**
     * The 198-weight table ("mediocre" in the wiki template). [note] turns an item into its banknote: the Amulet of avarice notes
     * the drops in the Revenant Caves "except secondary supply rolls and uniques".
     */
    fun standardTable(note: (Int) -> Int) =
        DropTableFactory.build {
            main {
                total(198)
                obj(note(Items.BRACELET_OF_ETHEREUM_UNCHARGED), slots = 30)
                obj(note(Items.BATTLESTAFF), quantity = 4, slots = 10)
                obj(note(Items.RUNE_FULL_HELM), quantity = 2, slots = 4)
                obj(note(Items.RUNE_PLATEBODY), quantity = 2, slots = 4)
                obj(note(Items.RUNE_PLATELEGS), quantity = 2, slots = 4)
                obj(note(Items.RUNE_KITESHIELD), quantity = 2, slots = 4)
                obj(note(Items.RUNE_WARHAMMER), quantity = 2, slots = 4)
                obj(note(Items.DRAGON_PLATELEGS), 1..2, slots = 1)
                obj(note(Items.DRAGON_PLATESKIRT), 1..2, slots = 1)
                obj(note(Items.DRAGON_DAGGER), quantity = 2, slots = 2)
                obj(note(Items.DRAGON_LONGSWORD), quantity = 2, slots = 2)
                obj(note(Items.DRAGON_BOLT_TIPS), 20..40, slots = 6) // OSRS "Dragonstone bolt tips" 9193
                obj(note(Items.ONYX_BOLT_TIPS), 3..6, slots = 6)
                obj(note(Items.LAW_RUNE), 20..45, slots = 10)
                obj(note(Items.DEATH_RUNE), 30..60, slots = 10)
                obj(note(Items.BLOOD_RUNE), 50..100, slots = 10)
                obj(note(Items.COAL_NOTED), 30..60, slots = 12)
                obj(note(Items.ADAMANT_BAR), 4..6, slots = 12)
                obj(note(Items.RUNITE_ORE), 2..4, slots = 8)
                obj(note(Items.RUNE_BAR), 2..3, slots = 6)
                obj(note(Items.BLACK_DRAGONHIDE), quantity = 4, slots = 8)
                obj(note(Items.MAHOGANY_PLANK_NOTED), 8..16, slots = 6)
                obj(note(Items.BLIGHTED_MANTA_RAY), 10..15, slots = 8)
                obj(note(Items.YEW_LOGS_NOTED), 20..40, slots = 8)
                obj(note(Items.MAGIC_LOGS_NOTED), 8..16, slots = 4)
                obj(note(Items.UNCUT_DRAGONSTONE), 2..5, slots = 2)
                obj(note(Items.BLIGHTED_SUPER_RESTORE4), 1..3, slots = 6)
                nothing(10) // Revenant cave teleport, not ported
            }
        }

    /**
     * The full table of one revenant at combat level [cb], for the killing (or viewing) player: skulled -> the skulled rare table,
     * Amulet of avarice -> noted standard drops, bracelet absorbing -> the ether goes into the bracelet ([RevenantGear.absorbEther]).
     */
    fun table(cb: Int) =
        DropTableFactory.build {
            val skulled = gg.rsmod.plugins.content.mechanics.pvp.PvpSkull.isSkulled(player)
            val avarice = RevenantGear.wearsAvarice(player)
            val defs = player.world.definitions
            val note: (Int) -> Int = { id -> if (avarice) gg.rsmod.game.model.item.Item(id).toNoted(defs).id else id }
            if (!RevenantGear.absorbsEther(player)) {
                guaranteed {
                    obj(Items.REVENANT_ETHER, 2..maxEther(cb), step = 2)
                }
            }
            main {
                val a = a(cb)
                val m = min(a, b(cb))
                total(a)
                table(rareTable(skulled), slots = 1)
                table(standardTable(note), slots = m - 1)
                if (a > m) obj(Items.COINS_995, 1..maxCoins(cb), slots = a - m)
            }
            table("uniques") {
                val onTask = player.getSlayerAssignment() == gg.rsmod.game.model.combat.SlayerAssignment.REVENANT &&
                    (player.attr[gg.rsmod.game.model.attr.SLAYER_AMOUNT] ?: 0) > 0
                total(uniqueTotal(a(cb), skulled, onTask))
                obj(Items.AMULET_OF_AVARICE, slots = 6)
                obj(Items.CRAWS_BOW_U, slots = 3)
                obj(Items.THAMMARONS_SCEPTRE_U, slots = 3)
                obj(Items.VIGGORAS_CHAINMACE_U, slots = 3)
            }
            table("blighted") {
                total(250 - cb)
                obj(Items.BLIGHTED_ANCIENT_ICE_SACK, 1..10, slots = 10)
                obj(Items.BLIGHTED_ENTANGLE_SACK, 1..10, slots = 20)
                obj(Items.BLIGHTED_TELEPORT_SPELL_SACK, 1..10, slots = 10)
                obj(Items.BLIGHTED_VENGEANCE_SACK, 1..10, slots = 10)
                obj(Items.BLIGHTED_MANTA_RAY, 1..2, slots = 15)
                obj(Items.BLIGHTED_ANGLERFISH, 1..2, slots = 15)
                obj(Items.BLIGHTED_KARAMBWAN, 1..2, slots = 10)
                obj(Items.BLIGHTED_SUPER_RESTORE4, slots = 5)
                nothing(5 + (150 - cb)) // the Revenant cave teleport (5, not ported) + the miss: 95 + 5 + 150 - cb = 250 - cb
            }
        }

    /** One even ether amount 2 .. [maxEther] (the wiki note), uniformly - the same roll as the table's step-2 line. */
    fun rollEther(cb: Int): Int = 2 + 2 * (0..(maxEther(cb) - 2) / 2).random()

    // ------------------------------------------------------------------ fight

    object Combat : CombatScript() {
        override val ids = RevenantCaveRevenants.ids

        enum class Style { MELEE, RANGED, MAGIC }

        /** Magic, unless the target protects from magic; then melee or ranged by its lower defence (rev-667 revenant rule). */
        fun chooseStyle(protectsMagic: Boolean, stabDef: Int, rangedDef: Int): Style =
            when {
                !protectsMagic -> Style.MAGIC
                stabDef <= rangedDef -> Style.MELEE
                else -> Style.RANGED
            }

        override suspend fun handleSpecialCombat(it: QueueTask) {
            val npc = it.npc
            val rev = BY_ID[npc.id] ?: return
            var target = npc.getCombatTarget() ?: return
            while (it.awaitAttackDelay(npc) && npc.canEngageCombat(target)) {
                npc.facePawn(target)
                val style =
                    if (target is Player) {
                        chooseStyle(target.isProtectedFrom(CombatClass.MAGIC), target.getBonus(BonusSlot.DEFENCE_STAB), target.getBonus(BonusSlot.DEFENCE_RANGED))
                    } else {
                        Style.MAGIC
                    }
                val distance = if (style == Style.MELEE) 1 else 5
                if (npc.moveToAttackRange(it, target, distance = distance, projectile = style != Style.MELEE)) {
                    if (npc.getCurrentLifepoints() < npc.getMaximumLifepoints() / 2 && npc.world.random(4) == 0) {
                        heal(npc)
                    } else {
                        when (style) {
                            Style.MELEE -> melee(npc, rev, target)
                            Style.RANGED -> ranged(npc, rev, target)
                            Style.MAGIC -> magic(npc, rev, target)
                        }
                    }
                }
                npc.postAttackLogic(target)
                it.wait(npc.combatDef.attackSpeed)
                target = npc.getCombatTarget() ?: break
            }
            npc.resetFacePawn()
            npc.removeCombatTarget()
        }

        private fun heal(npc: Npc) {
            npc.graphic(GFX_HEAL)
            npc.setCurrentLifepoints((npc.getCurrentLifepoints() + npc.getMaximumLifepoints() / 4).coerceAtMost(npc.getMaximumLifepoints()))
        }

        private fun damage(target: Pawn, max: Int): Double =
            if (target is Player) RevenantGear.revenantMaxHitAgainst(target, max) else max.toDouble()

        private fun melee(npc: Npc, rev: Rev, target: Pawn) {
            npc.prepareAttack(CombatClass.MELEE, rev.melee, WeaponStyle.ACCURATE)
            npc.animate(rev.attack)
            val land = MeleeCombatFormula.getAccuracy(npc, target) >= npc.world.randomDouble()
            npc.dealHit(target = target, maxHit = damage(target, rev.maxHit), landHit = land, delay = 1, hitType = HitType.MELEE)
        }

        private fun ranged(npc: Npc, rev: Rev, target: Pawn) {
            npc.prepareAttack(CombatClass.RANGED, StyleType.RANGED, WeaponStyle.ACCURATE)
            npc.animate(rev.attack)
            npc.world.spawn(npc.createProjectile(target, GFX_RANGED, startHeight = 30, endHeight = 31, angle = 10, steepness = 15, delay = 41, lifespan = 41 + 51))
            val delay = RangedCombatStrategy.getHitDelay(npc.getFrontFacingTile(target), target.getCentreTile())
            val land = RangedCombatFormula.getAccuracy(npc, target) >= npc.world.randomDouble()
            npc.dealHit(target = target, maxHit = damage(target, rev.maxHit), landHit = land, delay = delay, hitType = HitType.RANGE)
        }

        /** Also hits up to 10 more players standing on the target's tile (OSRS Wiki "Revenants"). */
        private fun magic(npc: Npc, rev: Rev, target: Pawn) {
            npc.prepareAttack(CombatClass.MAGIC, StyleType.MAGIC, WeaponStyle.ACCURATE)
            npc.animate(rev.attack)
            val delay = MagicCombatStrategy.getHitDelay(npc.getFrontFacingTile(target), target.getCentreTile())
            val victims = arrayListOf(target)
            npc.world.players.forEach { p ->
                if (victims.size <= MAGIC_EXTRA_TARGETS && p !== target && p.isOnline && !p.isDead() && p.tile.sameAs(target.tile)) victims += p
            }
            victims.forEach { v ->
                npc.world.spawn(npc.createProjectile(v, GFX_MAGIC, startHeight = 43, endHeight = 31, angle = 10, steepness = 15, delay = 41, lifespan = 41 + 56))
                val land = MagicCombatFormula.getAccuracy(npc, v) >= npc.world.randomDouble()
                npc.dealHit(target = v, maxHit = damage(v, rev.maxHit), landHit = land, delay = delay, hitType = HitType.MAGIC, onHit = {
                    if (land) v.graphic(GFX_MAGIC_HIT, height = 124) else v.graphic(GFX_SPLASH, height = 124)
                })
            }
        }
    }
}
