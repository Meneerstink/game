package gg.rsmod.plugins.content.mechanics.deadman

import gg.rsmod.game.RspsPaths
import gg.rsmod.plugins.content.combat.createProjectile
import gg.rsmod.plugins.content.starter.DeadmanStarterPack
import java.io.File

/** Only reads Claude's explicit mapping output. Missing ids leave that service unavailable, never create placeholder items. */
object Dmm2Boot {
    fun install(directory: File = File(RspsPaths.data("cfg/dmm2"))) {
        // Load the same published mapping for all services before their plugin bindings are registered.
        // In particular, do not retain an earlier lazy lookup after a mapping has been supplied.
        val mapping = File(directory, "ids.tsv")
        if (mapping.isFile) {
            val uiTable = File(directory,"interfaces.tsv")
            val uiAssets = if (uiTable.isFile) uiTable.readLines().filter { it.startsWith("script\t") || it.startsWith("sprite\t") } else emptyList()
            val lines = mapping.readLines()
            val primary = Dmm2Ids.parse(lines)
            val extra = uiAssets.filter { row ->
                val f = row.split('\t'); require(f.size == 3)
                val existing = primary["${f[0]}:${f[1]}"]
                require(existing == null || existing == f[2].toInt()) { "Conflicting DMM2 UI asset mapping: $row" }
                existing == null
            }
            Dmm2Ids.install(lines + extra)
        }
        Dmm2Interactions.safeUi = if (Dmm2SafeDepositUi.ready()) Dmm2SafeDepositUi else null
        Dmm2Interactions.rewardUi = if (Dmm2RewardUi.ready()) Dmm2RewardUi else null
        val sourceIds = listOf(22331, 22333, 22335, 26149, 13660, 13317, 13318, 33063)
        val starter = sourceIds.map { Dmm2Ids.item(it) }
        if (starter.all { it != null }) DeadmanStarterPack.install(DeadmanStarterPack.Ids(
            starter[0]!!, starter[1]!!, starter[2]!!, starter[3]!!, starter[4]!!, starter[5]!!, starter[6]!!, starter[7]!!))
        val rewards = NigelRewardStore.Reward.values().mapNotNull { reward -> Dmm2Ids.item(reward.sourceId)?.let { reward to it } }.toMap()
        if (rewards.size == NigelRewardStore.Reward.values().size) {
            val file = File(directory, "skins.tsv")
            val skins = (if (!file.isFile) emptyList() else file.readLines().filter { it.isNotBlank() && !it.trimStart().startsWith("#") }.map {
                val f = it.split('\t'); require(f.size in 3..4)
                val base = f[1].toInt()
                NigelRewardStore.Skin(NigelRewardStore.Reward.valueOf(f[0]), base, f[2].toInt(),
                    if (f.size == 4) f[3].split(',').map(String::toInt).toSet() else setOf(base))
            }).toMutableList()
            // Read-only alias for gameplay/death. NigelRewardStore.convert explicitly refuses this recipe-only cape.
            val ancient = Dmm2Ids.ItemId.ANCIENT_DEADMAN_CAPE.local
            val baseAncient = gg.rsmod.plugins.content.items.osrs.AncientCape.assets.cape
            if (ancient != null && baseAncient != null && skins.none { it.cosmetic == ancient })
                skins += NigelRewardStore.Skin(NigelRewardStore.Reward.CAPE_FABRIC, baseAncient, ancient)
            NigelRewardStore.install(NigelRewardStore.Contract(rewards, skins, Dmm2Ids.item(33122)))
        }
        ThunderKhopesh.appearance = khopeshAppearance()
        ThunderKhopesh.register()
        UndeadThralls.appearance = thrallAppearance()
        registerStarterWeaponLooks()
        installHomeTeleports()
        // DMM2 fase 7 (Claude 17-11): the starter weapons never fired (appearance null -> canAttack false). Wiki Starter_staff: "equipped
        // with a built-in Fire Strike" -> our Fire Strike AV (OSRS 99/100/101 imports + the staff cast animation, CombatSpell index 1).
        // Wiki Starter_bow: "fires its own ammunition, much like a crystal bow", Ranged Strength +7 "the same as bronze arrows" -> the
        // ordinary bow animation (0 = CombatConfigs default) with the bronze arrow drawback/flight gfx. ONZEKER: the bow's arrow look
        // has no named OSRS source.
        val strike = gg.rsmod.plugins.content.combat.strategy.magic.CombatSpell.FIRE_STRIKE
        gg.rsmod.plugins.content.starter.StarterWeaponCombat.staffAppearance = gg.rsmod.plugins.content.starter.StarterWeaponCombat.Appearance(
            strike.castAnimation[1], strike.castGfx!!.id, strike.projectile, strike.impactGfx!!.id)
        gg.rsmod.plugins.content.starter.StarterWeaponCombat.bowAppearance = gg.rsmod.plugins.content.starter.StarterWeaponCombat.Appearance(
            // N629 (owner 17-11 testclient: the bow floated loose from the hand): an OSRS-imported bow needs the imported OSRS bow
            // sequence HUMAN_BOW (OSRS 426), as every other imported bow in OsrsWeaponLooks - the 667 426 fallback does not fit its model.
            gg.rsmod.plugins.content.items.osrs.OsrsSeq.HUMAN_BOW, gg.rsmod.plugins.api.cfg.Gfx.BRONZE_ARROW_DRAWBACK, gg.rsmod.plugins.api.cfg.Gfx.BRONZE_ARROW_IN_FLIGHT, 0)
    }

    /**
     * DMM2 fase 7 (Claude 17-11): the undead-trinket thralls were silently NOT_READY - appearance was never set. Sources:
     *  - NPCs: OSRS DEADMAN_THRALL_ZOMBIE_GREATER_ZUK 15569 / _SKELETAL_GREATER_PRINCESS 15570 / _GHOSTLY_GREATER_WISP 15571 (ids.tsv).
     *  - Wiki Trinket_of_undead: "These thralls function the same as greater thralls" (melee / ranged / magic).
     *  - Wiki Thrall: "Skeletal and Ghostly thralls have an attack range of 6"; "The magic thralls' projectiles will hit enemies 1-2 ticks
     *    earlier (depending on distance) than the ranged thralls"; zombified = melee.
     *  - Projectiles: the greater-thrall spotanims THRALL_RANGED_TRAVEL 1906, THRALL_MAGIC_TRAVEL 1907, THRALL_MAGIC_IMPACT 1908
     *    (OsrsFxImportTool batch dmm2-thrall-av).
     *  - Attack seqs: the attack seq of each pet skeleton, ZUKREK_ATTACK 7978 and KALPHITE_UPDATE_FLYING_QUEEN_RANGED_ATTACK 6234.
     * ONZEKER (no OSRS video of these thralls): the attack seqs, the ranged delay (player bow formula) and the Wisp attack seq (its skeleton's
     * NPC_WHISPERER_01_ATTACK_MAGIC_01 10241 is skeletal; a single-seq bake for the already-applied Wisp is open) - a missing seq or
     * projectile only drops that visual, the attack itself still lands. Missing NPC ids keep the trinket NOT_READY, as before.
     */
    private fun thrallAppearance(): UndeadThralls.Appearance? {
        val npcs = UndeadThralls.Choice.values().associateWith { Dmm2Ids.npc(it.sourceNpc) ?: return null }
        val attackSeq = mapOf(UndeadThralls.Choice.ZUK to Dmm2Ids.seq(7978), UndeadThralls.Choice.KAL to Dmm2Ids.seq(6234),
            UndeadThralls.Choice.WISP to Dmm2Ids.seq(WISP_MAGIC_ATTACK))
        val rangedTravel = Dmm2Ids.spot(1906)
        val magicTravel = Dmm2Ids.spot(1907)
        val magicImpact = Dmm2Ids.spot(1908)
        return object : UndeadThralls.Appearance {
            override fun spawn(player: gg.rsmod.game.model.entity.Player, choice: UndeadThralls.Choice): gg.rsmod.game.model.entity.Npc? {
                val npc = gg.rsmod.game.model.entity.Npc(player, npcs.getValue(choice), player.tile, player.world)
                npc.ignoresEntityCollision = true
                player.world.spawn(npc)
                return npc
            }

            override fun reach(actor: gg.rsmod.game.model.entity.Npc, target: gg.rsmod.game.model.entity.Npc, choice: UndeadThralls.Choice): Boolean =
                if (choice.hit == gg.rsmod.plugins.api.HitType.MELEE) {
                    gg.rsmod.game.model.path.MeleeReach.bordersWithoutWall(actor.world.collision, actor.tile, actor.getSize(), actor.getSize(),
                        target.tile, target.getSize(), target.getSize())
                } else {
                    distanceToBody(actor.tile, target) <= RANGE && actor.hasLineOfSightTo(target, projectile = true, maximumDistance = RANGE + target.getSize())
                }

            override fun follow(actor: gg.rsmod.game.model.entity.Npc, player: gg.rsmod.game.model.entity.Player, target: gg.rsmod.game.model.entity.Npc?) {
                if (target != null) {
                    actor.facePawn(target)
                    if (!reach(actor, target, choiceOf(actor) ?: return)) actor.walkTo(target.tile)
                    return
                }
                actor.resetFacePawn()
                if (actor.tile.height != player.tile.height || !actor.tile.isWithinRadius(player.tile, 12)) {
                    actor.moveTo(player.tile)
                } else if (!actor.tile.isWithinRadius(player.tile, 1)) {
                    actor.walkTo(player.tile)
                }
            }

            override fun attack(actor: gg.rsmod.game.model.entity.Npc, target: gg.rsmod.game.model.entity.Npc, choice: UndeadThralls.Choice): Int {
                attackSeq[choice]?.let { actor.animate(it) }
                if (choice.hit == gg.rsmod.plugins.api.HitType.MELEE) return 1
                val ranged = gg.rsmod.plugins.content.combat.strategy.RangedCombatStrategy.getHitDelay(actor.tile, target.tile)
                val delay = if (choice.hit == gg.rsmod.plugins.api.HitType.MAGIC) (ranged - 1).coerceAtLeast(1) else ranged
                val travel = if (choice.hit == gg.rsmod.plugins.api.HitType.MAGIC) magicTravel else rangedTravel
                travel?.let {
                    val type = if (choice.hit == gg.rsmod.plugins.api.HitType.MAGIC) gg.rsmod.plugins.api.ProjectileType.MAGIC
                        else gg.rsmod.plugins.api.ProjectileType.ARROW
                    actor.world.spawn(actor.createProjectile(target, it, type))
                }
                if (choice.hit == gg.rsmod.plugins.api.HitType.MAGIC) magicImpact?.let { target.graphic(it, 100, delay * 30) }
                return delay
            }

            private fun choiceOf(actor: gg.rsmod.game.model.entity.Npc): UndeadThralls.Choice? = npcs.entries.firstOrNull { it.value == actor.id }?.key
        }
    }

    /**
     * DMM2 fase 7 (Claude 17-11): Nigel's "Home Teleport animation" choice was stored but never played - DeadmanHomeTeleport.install was
     * never called, so home_teleport_spells always fell back to the default cast. Sources, OSRS cache 2686 (decoded 17-11):
     *  - Annihilation: six stages HUMAN_DEADMAN_ANNIHILATION_REWARD_TELEPORT_01.._06 13702..13707 paired with spotanims
     *    DEADMAN_2026_HOME_TELE_SPOTANIM_01.._06 3566..3571 (their seqs 13709..13714); every stage is 90 client cycles = 3 ticks
     *    (the _FULL versions 13701 / 3565 are 541 / 540 cycles = the same 18 ticks in one piece).
     *  - Armageddon: one stage HUMAN_DEADMAN_ARMAGEDDON_REWARD_TELEPORT_01 11444 (690 cycles = 23 ticks) with spotanim 2909, whose seq is
     *    VFX_DM_ARMAGEDDON_TELEPORT_01 11445 (also 690 cycles). Spotanim 2909 comes in with OsrsFxImportTool batch dmm2-home-tele-av.
     * A style whose ids are not all imported stays unavailable (the default cast plays), as before.
     */
    private fun installHomeTeleports() {
        val annihilation = (0 until 6).map { i ->
            DeadmanHomeTeleport.Stage(Dmm2Ids.seq(13702 + i) ?: return@map null, Dmm2Ids.spot(3566 + i) ?: return@map null, 3)
        }
        if (annihilation.all { it != null }) DeadmanHomeTeleport.install(NigelRewardStore.Teleport.ANNIHILATION, annihilation.filterNotNull())
        val armageddonSeq = Dmm2Ids.seq(11444)
        val armageddonSpot = Dmm2Ids.spot(2909)
        if (armageddonSeq != null && armageddonSpot != null)
            DeadmanHomeTeleport.install(NigelRewardStore.Teleport.ARMAGEDDON, listOf(DeadmanHomeTeleport.Stage(armageddonSeq, armageddonSpot, 23)))
    }

    /**
     * N629 (owner 17-11 testclient: the starter bow floated loose from the hand, the starter sword vanished mid-swing): the DMM2 starter
     * weapons are OSRS imports but were not in OsrsWeaponLooks, so they fell back to the 667 weapon classes (HD sequences such as 15071
     * that OSRS never had - the same root cause as owner 2026-09-24 'FIX alle OSRS GEPORTE WEAPONS'). Same rows as the other imports:
     *  - Starter sword: wiki Starter_sword "actually a slash sword", Chop/Slash/Lunge/Block = slash, slash, stab, slash ->
     *    HUMAN_SWORD_SLASH 390 / HUMAN_SWORD_STAB 386, block HUMAN_SWORD_DEF 388 (the OsrsWeaponLooks slash-sword row).
     *  - Starter bow: HUMAN_BOW 426 on every style, block OSRS_DEFEND_RANGED (the OsrsWeaponLooks bow row).
     */
    private fun registerStarterWeaponLooks() {
        val looks = gg.rsmod.plugins.content.items.osrs.OsrsWeaponLooks
        val seq = gg.rsmod.plugins.content.items.osrs.OsrsSeq
        Dmm2Ids.item(22331)?.let { sword ->
            looks.register(gg.rsmod.plugins.content.items.osrs.OsrsWeaponLooks.Look(intArrayOf(seq.HUMAN_SWORD_SLASH, seq.HUMAN_SWORD_SLASH, seq.HUMAN_SWORD_STAB, seq.HUMAN_SWORD_SLASH),
                block = seq.HUMAN_SWORD_DEF), sword)
        }
        Dmm2Ids.item(22333)?.let { bow ->
            looks.register(gg.rsmod.plugins.content.items.osrs.OsrsWeaponLooks.Look(IntArray(4) { seq.HUMAN_BOW }, block = seq.OSRS_DEFEND_RANGED), bow)
        }
    }

    private const val RANGE = 6
    // RuneLite gameval NPC_WHISPERER_01_ATTACK_MAGIC_01; cache2686, narrow extra-seq import (PREFLIGHT 7.1).
    const val WISP_MAGIC_ATTACK = 10241

    /** Chebyshev distance from [from] to the nearest tile of [target]'s body. */
    private fun distanceToBody(from: gg.rsmod.game.model.Tile, target: gg.rsmod.game.model.entity.Npc): Int {
        val s = target.getSize()
        val dx = maxOf(0, target.tile.x - from.x, from.x - (target.tile.x + s - 1))
        val dz = maxOf(0, target.tile.z - from.z, from.z - (target.tile.z + s - 1))
        return maxOf(dx, dz)
    }

    /**
     * DMM2 fase 7 (Claude 17-11, testclient: 4 treffers zonder bliksem - appearance was never set, so passive, special and the chest
     * entry were silently off). Every asset is an applied cache-2686 import (ids.tsv): seq HUMAN_SPECIAL_KHOPESH 11812, spotanims
     * FX_KHOPESH_SPECIAL 3030, FX_KHOPESH_LIGHTNING_BASIC 3032 (passive) and FX_KHOPESH_LIGHTNING_SPECIAL 3033 (special/PvP bolts)
     * (RuneLite gameval names). Missing any of them keeps the weapon's extras off, as before.
     * ONZEKER (no OSRS timing source yet, phase-3 advice): the bolt lands with the melee hit (delay 1); measure in an OSRS video.
     */
    private fun khopeshAppearance(): ThunderKhopesh.Appearance? {
        val specialSeq = Dmm2Ids.seq(11812) ?: return null
        val specialGfx = Dmm2Ids.spot(3030) ?: return null
        val basic = Dmm2Ids.spot(3032) ?: return null
        val specialBolt = Dmm2Ids.spot(3033) ?: return null
        return object : ThunderKhopesh.Appearance {
            override val passiveDelay = ThunderKhopesh.STANDARD_MELEE_DELAY
            override val pvmHitDelays = listOf(ThunderKhopesh.STANDARD_MELEE_DELAY, ThunderKhopesh.STANDARD_MELEE_DELAY)
            override fun swing(player: gg.rsmod.game.model.entity.Player, special: Boolean) {
                if (!special) return // ordinary swings keep the sword animation from CorruptedWeapons
                player.animate(specialSeq)
                player.graphic(specialGfx)
            }
            override fun bolt(player: gg.rsmod.game.model.entity.Player, tile: gg.rsmod.game.model.Tile, special: Boolean) {
                player.world.spawn(gg.rsmod.game.model.TileGraphic(tile, if (special) specialBolt else basic, 0))
            }
        }
    }
}
