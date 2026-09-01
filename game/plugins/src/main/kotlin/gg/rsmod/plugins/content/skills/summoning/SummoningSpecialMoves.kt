package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.NpcSkills
import gg.rsmod.plugins.api.ProjectileType
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.addXp
import gg.rsmod.plugins.api.ext.isMulti
import gg.rsmod.plugins.api.ext.heal
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.sendRunEnergy
import gg.rsmod.plugins.api.ext.stun
import gg.rsmod.plugins.content.combat.createProjectile
import gg.rsmod.plugins.content.combat.dealHit
import gg.rsmod.plugins.content.combat.poison
import kotlin.math.ceil

enum class FamiliarSpecialTarget { INSTANT, NPC, INVENTORY_ITEM }

data class FamiliarSpecialBinding(
    val scroll: SummoningScrollData,
    val target: FamiliarSpecialTarget,
    val detailsComponent: Int,
    val orbComponent: Int,
    val alternativeScrolls: List<SummoningScrollData> = emptyList(),
)

private val FamiliarSpecialBinding.scrolls: List<SummoningScrollData>
    get() = listOf(scroll) + alternativeScrolls

private data class DirectFamiliarSpecial(
    val maxHit: Double,
    val animation: Int,
    val sourceGraphic: Int = -1,
    val projectile: Int = -1,
    val targetGraphic: Int = -1,
    val hitType: HitType = HitType.MAGIC,
)

/** Revision-667 dispatcher whose component ids are sourced from the matching cache interface data. */
object SummoningSpecialMoves {
    val bindings = listOf(
        FamiliarSpecialBinding(SummoningScrollData.DREADFOWL_STRIKE_SCROLL, FamiliarSpecialTarget.NPC, 77, 161),
        FamiliarSpecialBinding(SummoningScrollData.SLIME_SPRAY_SCROLL, FamiliarSpecialTarget.NPC, 129, 135),
        FamiliarSpecialBinding(SummoningScrollData.ELECTRIC_LASH_SCROLL, FamiliarSpecialTarget.NPC, 131, 134),
        FamiliarSpecialBinding(SummoningScrollData.STONY_SHELL_SCROLL, FamiliarSpecialTarget.INSTANT, 85, 157),
        FamiliarSpecialBinding(SummoningScrollData.INSANE_FEROCITY_SCROLL, FamiliarSpecialTarget.INSTANT, 115, 142),
        FamiliarSpecialBinding(SummoningScrollData.THIEVING_FINGERS_SCROLL, FamiliarSpecialTarget.INSTANT, 91, 154),
        FamiliarSpecialBinding(SummoningScrollData.UNBURDEN_SCROLL, FamiliarSpecialTarget.INSTANT, 101, 149),
        FamiliarSpecialBinding(SummoningScrollData.TIRELESS_RUN_SCROLL, FamiliarSpecialTarget.INSTANT, 139, 130),
        FamiliarSpecialBinding(SummoningScrollData.EVIL_FLAMES_SCROLL, FamiliarSpecialTarget.NPC, 87, 156),
        FamiliarSpecialBinding(SummoningScrollData.DISSOLVE_SCROLL, FamiliarSpecialTarget.NPC, 133, 133),
        FamiliarSpecialBinding(SummoningScrollData.RENDING_SCROLL, FamiliarSpecialTarget.NPC, 191, 104),
        FamiliarSpecialBinding(SummoningScrollData.DOOMSPHERE_SCROLL, FamiliarSpecialTarget.NPC, 145, 127),
        FamiliarSpecialBinding(SummoningScrollData.ABYSSAL_STEALTH_SCROLL, FamiliarSpecialTarget.INSTANT, 97, 151),
        FamiliarSpecialBinding(SummoningScrollData.TESTUDO_SCROLL, FamiliarSpecialTarget.INSTANT, 127, 136),
        FamiliarSpecialBinding(SummoningScrollData.ARCTIC_BLAST_SCROLL, FamiliarSpecialTarget.NPC, 119, 140),
        FamiliarSpecialBinding(SummoningScrollData.CRUSHING_CLAW_SCROLL, FamiliarSpecialTarget.NPC, 103, 148),
        FamiliarSpecialBinding(SummoningScrollData.MANTIS_STRIKE_SCROLL, FamiliarSpecialTarget.NPC, 105, 147),
        FamiliarSpecialBinding(SummoningScrollData.INFERNO_SCROLL, FamiliarSpecialTarget.NPC, 197, 101),
        FamiliarSpecialBinding(SummoningScrollData.VOLCANIC_STRENGTH_SCROLL, FamiliarSpecialTarget.INSTANT, 183, 108),
        FamiliarSpecialBinding(SummoningScrollData.TITANS_CONSTITUTION_SCROLL, FamiliarSpecialTarget.INSTANT, 169, 115),
        FamiliarSpecialBinding(SummoningScrollData.HEALING_AURA_SCROLL, FamiliarSpecialTarget.INSTANT, 123, 138),
        FamiliarSpecialBinding(SummoningScrollData.MAGIC_FOCUS_SCROLL, FamiliarSpecialTarget.INSTANT, 161, 119),
        FamiliarSpecialBinding(SummoningScrollData.SPIKE_SHOT_SCROLL, FamiliarSpecialTarget.NPC, 157, 121),
        FamiliarSpecialBinding(
            SummoningScrollData.ADAMANT_BULL_RUSH_SCROLL,
            FamiliarSpecialTarget.NPC,
            159,
            120,
            listOf(
                SummoningScrollData.BRONZE_BULL_RUSH_SCROLL,
                SummoningScrollData.IRON_BULL_RUSH_SCROLL,
                SummoningScrollData.STEEL_BULL_RUSH_SCROLL,
                SummoningScrollData.MITHRIL_BULL_RUSH_SCROLL,
                SummoningScrollData.RUNE_BULL_RUSH_SCROLL,
            ),
        ),
        FamiliarSpecialBinding(SummoningScrollData.POISONOUS_BLAST_SCROLL, FamiliarSpecialTarget.NPC, 151, 124),
        FamiliarSpecialBinding(SummoningScrollData.SWAMP_PLAGUE_SCROLL, FamiliarSpecialTarget.NPC, 165, 117),
        FamiliarSpecialBinding(SummoningScrollData.BOIL_SCROLL, FamiliarSpecialTarget.NPC, 171, 114),
        FamiliarSpecialBinding(SummoningScrollData.DEADLY_CLAW_SCROLL, FamiliarSpecialTarget.NPC, 153, 123),
        FamiliarSpecialBinding(SummoningScrollData.ACORN_MISSILE_SCROLL, FamiliarSpecialTarget.NPC, 149, 125),
        FamiliarSpecialBinding(SummoningScrollData.IRON_WITHIN_SCROLL, FamiliarSpecialTarget.NPC, 193, 103),
        FamiliarSpecialBinding(SummoningScrollData.SANDSTORM_SCROLL, FamiliarSpecialTarget.INSTANT, 109, 145),
        FamiliarSpecialBinding(SummoningScrollData.FIREBALL_ASSAULT_SCROLL, FamiliarSpecialTarget.INSTANT, 189, 105),
        FamiliarSpecialBinding(SummoningScrollData.EBON_THUNDER_SCROLL, FamiliarSpecialTarget.NPC, 181, 109),
        FamiliarSpecialBinding(SummoningScrollData.WINTER_STORAGE_SCROLL, FamiliarSpecialTarget.INVENTORY_ITEM, 121, 139),
        FamiliarSpecialBinding(SummoningScrollData.STEEL_OF_LEGENDS_SCROLL, FamiliarSpecialTarget.NPC, 173, 113),
    )

    private val directCombat = mapOf(
        SummoningScrollData.DREADFOWL_STRIKE_SCROLL to DirectFamiliarSpecial(30.0, 5387, 1523, 1318),
        SummoningScrollData.SLIME_SPRAY_SCROLL to DirectFamiliarSpecial(80.0, 8148, 1385, 1386, 1387, HitType.RANGE),
        SummoningScrollData.ELECTRIC_LASH_SCROLL to DirectFamiliarSpecial(50.0, 7795, 1410, 1411),
        SummoningScrollData.EVIL_FLAMES_SCROLL to DirectFamiliarSpecial(100.0, 8251, 1328, 1330, 1329),
        SummoningScrollData.DISSOLVE_SCROLL to DirectFamiliarSpecial(120.0, 8575, 1361, 1360, 1360),
        SummoningScrollData.RENDING_SCROLL to DirectFamiliarSpecial(120.0, 5229, 1370, 1371, 1372, HitType.RANGE),
        SummoningScrollData.DOOMSPHERE_SCROLL to DirectFamiliarSpecial(78.0, 7974, 1478, 1479, 1480),
        SummoningScrollData.ARCTIC_BLAST_SCROLL to DirectFamiliarSpecial(130.0, 4926, 1405, 1406, 1407),
        SummoningScrollData.CRUSHING_CLAW_SCROLL to DirectFamiliarSpecial(96.0, 8118, 1351, 1352, hitType = HitType.RANGE),
        SummoningScrollData.MANTIS_STRIKE_SCROLL to DirectFamiliarSpecial(100.0, 8071, 1379, 1380, 1381, HitType.RANGE),
        SummoningScrollData.INFERNO_SCROLL to DirectFamiliarSpecial(85.0, 7871, 1394, targetGraphic = 1393),
        SummoningScrollData.SPIKE_SHOT_SCROLL to DirectFamiliarSpecial(170.0, 7787, projectile = 1426, targetGraphic = 1428, hitType = HitType.RANGE),
        SummoningScrollData.POISONOUS_BLAST_SCROLL to DirectFamiliarSpecial(120.0, 8211, projectile = 1508, targetGraphic = 1511),
        SummoningScrollData.SWAMP_PLAGUE_SCROLL to DirectFamiliarSpecial(110.0, 8223, projectile = 1462),
        SummoningScrollData.ADAMANT_BULL_RUSH_SCROLL to DirectFamiliarSpecial(200.0, 8026, 1496, 1497, hitType = HitType.RANGE),
        SummoningScrollData.BRONZE_BULL_RUSH_SCROLL to DirectFamiliarSpecial(80.0, 8026, 1496, 1497, hitType = HitType.RANGE),
        SummoningScrollData.IRON_BULL_RUSH_SCROLL to DirectFamiliarSpecial(100.0, 8026, 1496, 1497, hitType = HitType.RANGE),
        SummoningScrollData.STEEL_BULL_RUSH_SCROLL to DirectFamiliarSpecial(120.0, 8026, 1496, 1497, hitType = HitType.RANGE),
        SummoningScrollData.MITHRIL_BULL_RUSH_SCROLL to DirectFamiliarSpecial(160.0, 8026, 1496, 1497, hitType = HitType.RANGE),
        SummoningScrollData.RUNE_BULL_RUSH_SCROLL to DirectFamiliarSpecial(240.0, 8026, 1496, 1497, hitType = HitType.RANGE),
        SummoningScrollData.EBON_THUNDER_SCROLL to DirectFamiliarSpecial(140.0, 7986, 1492, 1493, 1494),
    )

    fun validate() {
        check(bindings.map { it.detailsComponent }.distinct().size == bindings.size)
        check(bindings.map { it.orbComponent }.distinct().size == bindings.size)
        bindings.forEach { binding ->
            binding.scrolls.forEach { scroll ->
                check(scroll.familiars.isNotEmpty())
                check(scroll.specialPoints in 1..Familiar.MAX_SPECIAL_POINTS)
            }
        }
    }

    fun castInstant(player: Player, binding: FamiliarSpecialBinding): Boolean {
        if (binding.target != FamiliarSpecialTarget.INSTANT) return false
        val resolved = validateResources(player, binding) ?: return false
        val familiar = resolved.familiar
        val scroll = resolved.scroll
        val changed = when (scroll) {
            SummoningScrollData.STONY_SHELL_SCROLL ->
                boost(player, Skills.DEFENCE, 4).also { if (it) animateSelf(player, familiar, 8109, 1326) }
            SummoningScrollData.THIEVING_FINGERS_SCROLL ->
                boost(player, Skills.THIEVING, 2).also { if (it) animateSelf(player, familiar, 8020, 1336, 1300) }
            SummoningScrollData.UNBURDEN_SCROLL -> {
                val restored = restoreRunEnergy(player)
                if (restored) animateSelf(player, familiar, 7896, 1382)
                restored
            }
            SummoningScrollData.TIRELESS_RUN_SCROLL -> {
                val beforeEnergy = player.runEnergy
                val beforeAgility = player.skills.getCurrentLevel(Skills.AGILITY)
                val boostedAgility = (beforeAgility + 2).coerceAtMost(player.skills.getMaxLevel(Skills.AGILITY) + 2)
                val restoredEnergy = (beforeEnergy + boostedAgility / 2.0).coerceAtMost(100.0)
                if (boostedAgility == beforeAgility && restoredEnergy == beforeEnergy) {
                    player.message("Your Agility and run energy are already fully boosted.")
                    false
                } else {
                    if (boostedAgility != beforeAgility) player.skills.setCurrentLevel(Skills.AGILITY, boostedAgility)
                    player.runEnergy = restoredEnergy
                    player.sendRunEnergy(restoredEnergy.toInt())
                    familiar.animate(8229)
                    familiar.graphic(1521)
                    player.graphic(1300)
                    true
                }
            }
            SummoningScrollData.ABYSSAL_STEALTH_SCROLL -> {
                val agility = boost(player, Skills.AGILITY, 4)
                val thieving = boost(player, Skills.THIEVING, 4)
                (agility || thieving).also { if (it) animateSelf(player, familiar, 7682, 1339, 1302) }
            }
            SummoningScrollData.TESTUDO_SCROLL -> {
                val before = player.skills.getCurrentLevel(Skills.DEFENCE)
                val after = (before + 8).coerceAtMost(player.skills.getMaxLevel(Skills.DEFENCE) + 8)
                if (after == before) {
                    player.message("Your Defence is already fully boosted.")
                    false
                } else {
                    player.skills.setCurrentLevel(Skills.DEFENCE, after)
                    familiar.animate(8288)
                    familiar.graphic(1414)
                    player.graphic(1308)
                    true
                }
            }
            SummoningScrollData.VOLCANIC_STRENGTH_SCROLL ->
                boost(player, Skills.STRENGTH, 9).also { if (it) animateSelf(player, familiar, 8053, 1465) }
            SummoningScrollData.MAGIC_FOCUS_SCROLL ->
                boost(player, Skills.MAGIC, 7).also { if (it) animateSelf(player, familiar, 8308, 1464) }
            SummoningScrollData.HEALING_AURA_SCROLL -> {
                if (player.getCurrentLifepoints() >= player.getMaximumLifepoints()) {
                    player.message("You are already at full life points.")
                    false
                } else {
                    player.heal(ceil(player.getMaximumLifepoints() * 0.15).toInt())
                    animateSelf(player, familiar, 8267, 1356, 1300)
                    true
                }
            }
            SummoningScrollData.TITANS_CONSTITUTION_SCROLL -> {
                val defenceBoost = ceil(player.skills.getMaxLevel(Skills.DEFENCE) * 0.125).toInt()
                boost(player, Skills.DEFENCE, defenceBoost)
                player.heal(80, capValue = 80)
                when (familiar.id) {
                    SummoningPouchData.FIRE_TITAN.npc -> animateSelf(player, familiar, 7835, 1514, 1307)
                    SummoningPouchData.ICE_TITAN.npc -> animateSelf(player, familiar, 7837, 1512, 1306)
                    SummoningPouchData.MOSS_TITAN.npc -> animateSelf(player, familiar, 7837, 1513, 1308)
                }
                true
            }
            SummoningScrollData.INSANE_FEROCITY_SCROLL -> {
                val attack = 5 + ceil(player.skills.getMaxLevel(Skills.ATTACK) * 0.15).toInt()
                val strength = 5 + ceil(player.skills.getMaxLevel(Skills.STRENGTH) * 0.15).toInt()
                boost(player, Skills.ATTACK, attack)
                boost(player, Skills.STRENGTH, strength)
                drain(player, Skills.RANGED, 0.10)
                drain(player, Skills.MAGIC, 0.10)
                drain(player, Skills.DEFENCE, 0.10)
                animateSelf(player, familiar, 7928, 1397, 1399)
                true
            }
            SummoningScrollData.FIREBALL_ASSAULT_SCROLL ->
                executeAoe(player, familiar, maxTargets = 2, radius = 3, maxHit = 70.0, animation = 8257, targetGraphic = 1329)
            SummoningScrollData.SANDSTORM_SCROLL ->
                executeAoe(player, familiar, maxTargets = 6, radius = 6, maxHit = 200.0, animation = 8517, sourceGraphic = 1350, projectile = 1349)
            else -> false
        }
        if (!changed) return false
        return commitResources(player, scroll)
    }

    fun castOnNpc(player: Player, binding: FamiliarSpecialBinding, target: Npc): Boolean {
        if (binding.target != FamiliarSpecialTarget.NPC) return false
        val resolved = validateResources(player, binding) ?: return false
        val familiar = resolved.familiar
        val scroll = resolved.scroll
        if (!target.isAlive() || target === familiar || target.tile.height != familiar.tile.height || familiar.tile.getDistance(target.tile) > 16) {
            player.message("Your familiar cannot use that special move on this target.")
            return false
        }
        if (!player.tile.isMulti(player.world) || !target.tile.isMulti(player.world) || !player.world.plugins.canAttack(player, target)) {
            player.message("Your familiar cannot attack that target here.")
            return false
        }
        if (!commitResources(player, scroll)) return false
        familiar.facePawn(target)
        val direct = directCombat[scroll]
        when {
            direct != null -> executeDirectCombat(player, familiar, target, scroll, direct)
            scroll == SummoningScrollData.BOIL_SCROLL -> executeBoil(player, familiar, target)
            scroll == SummoningScrollData.DEADLY_CLAW_SCROLL -> executeVolley(familiar, target, 3, 100.0, HitType.MAGIC)
            scroll == SummoningScrollData.ACORN_MISSILE_SCROLL -> {
                executeDirectCombat(player, familiar, target, scroll, DirectFamiliarSpecial(100.0, 7858, projectile = 1362, targetGraphic = 1363))
                executeSplash(player, familiar, target, maxTargets = 9, radius = 1, maxHit = 100.0, projectile = 1362, targetGraphic = 1363)
            }
            scroll == SummoningScrollData.IRON_WITHIN_SCROLL -> {
                familiar.animate(7954)
                familiar.graphic(1450)
                val melee = familiar.tile.getDistance(target.tile) <= 1
                executeVolley(familiar, target, 3, if (melee) 230.0 else 220.0, if (melee) HitType.MELEE else HitType.MAGIC)
            }
            scroll == SummoningScrollData.STEEL_OF_LEGENDS_SCROLL -> {
                familiar.animate(8190)
                target.graphic(1449)
                repeat(4) { index ->
                    player.world.spawn(familiar.createProjectile(target, 1445, ProjectileType.ARROW))
                    familiar.dealHit(target, maxHit = 244.0, landHit = true, delay = index + 1, hitType = HitType.RANGE)
                }
            }
            else -> return false
        }
        familiar.attack(target)
        return true
    }

    fun castOnInventoryItem(player: Player, binding: FamiliarSpecialBinding, slot: Int): Boolean {
        if (binding.target != FamiliarSpecialTarget.INVENTORY_ITEM || binding.scroll != SummoningScrollData.WINTER_STORAGE_SCROLL) return false
        val resolved = validateResources(player, binding) ?: return false
        val familiar = resolved.familiar
        val scroll = resolved.scroll
        val selected = player.inventory[slot] ?: return false
        if (selected.id == binding.scroll.scroll) {
            player.message("Your familiar refuses to bank the scroll powering its special move.")
            return false
        }
        val bankProbe = ItemContainer(player.bank)
        if (!bankProbe.add(selected.id, 1, assureFullInsertion = true).hasSucceeded()) {
            player.message("Your bank is too full to store that item.")
            return false
        }
        if (!commitResources(player, scroll)) return false
        if (!player.inventory.remove(selected.id, 1, assureFullRemoval = true, beginSlot = slot).hasSucceeded()) {
            refundResources(player, scroll)
            return false
        }
        if (!player.bank.add(selected.id, 1, assureFullInsertion = true).hasSucceeded()) {
            player.inventory.add(selected.id, 1, assureFullInsertion = true, beginSlot = slot)
            refundResources(player, scroll)
            return false
        }
        familiar.graphic(1358)
        player.message("Your pack yak sends the item to your bank.")
        return true
    }

    private data class ResolvedSpecial(val familiar: Npc, val scroll: SummoningScrollData)

    private fun validateResources(player: Player, binding: FamiliarSpecialBinding): ResolvedSpecial? {
        val familiar = Familiar.current(player)
        if (familiar == null) {
            player.message("You need the matching familiar summoned to use this scroll.")
            return null
        }
        val scroll = binding.scrolls.singleOrNull { familiar.id in it.familiars }
        if (scroll == null) {
            player.message("You need the matching familiar summoned to use this scroll.")
            return null
        }
        if (!player.inventory.contains(scroll.scroll)) {
            player.message("You need the matching summoning scroll to use this special move.")
            return null
        }
        if (Familiar.currentSpecialPoints(player) < scroll.specialPoints) {
            player.message("You do not have enough familiar special-move energy.")
            return null
        }
        return ResolvedSpecial(familiar, scroll)
    }

    private fun commitResources(player: Player, scroll: SummoningScrollData): Boolean {
        if (!player.inventory.remove(scroll.scroll, 1, assureFullRemoval = true).hasSucceeded()) return false
        if (!Familiar.consumeSpecialPoints(player, scroll.specialPoints)) {
            player.inventory.add(scroll.scroll, 1, assureFullInsertion = true)
            return false
        }
        player.addXp(Skills.SUMMONING, scroll.useExperience)
        return true
    }

    private fun refundResources(player: Player, scroll: SummoningScrollData) {
        player.inventory.add(scroll.scroll, 1, assureFullInsertion = true)
        Familiar.restoreSpecialPoints(player, scroll.specialPoints)
    }

    private fun executeDirectCombat(
        player: Player,
        familiar: Npc,
        target: Npc,
        scroll: SummoningScrollData,
        effect: DirectFamiliarSpecial,
    ) {
        familiar.animate(effect.animation)
        if (effect.sourceGraphic >= 0) familiar.graphic(effect.sourceGraphic)
        if (effect.projectile >= 0) {
            val projectileType = if (effect.hitType == HitType.RANGE) ProjectileType.ARROW else ProjectileType.MAGIC
            player.world.spawn(familiar.createProjectile(target, effect.projectile, projectileType))
        }
        if (effect.targetGraphic >= 0) target.graphic(effect.targetGraphic)
        familiar.dealHit(
            target,
            maxHit = effect.maxHit,
            landHit = true,
            delay = if (effect.projectile >= 0) 2 else 1,
            onHit = { pawnHit ->
                pawnHit.hit.addAction {
                    when (scroll) {
                        SummoningScrollData.ELECTRIC_LASH_SCROLL -> target.stun(5)
                        SummoningScrollData.ARCTIC_BLAST_SCROLL -> if (target.getSize() <= 1 && player.world.randomDouble() < 0.20) target.stun(3)
                        SummoningScrollData.MANTIS_STRIKE_SCROLL -> if (target.getSize() <= 1) target.stun(3)
                    SummoningScrollData.SPIKE_SHOT_SCROLL -> target.stun(5)
                    SummoningScrollData.POISONOUS_BLAST_SCROLL -> if (player.world.randomDouble() < 0.50) target.poison(20)
                    SummoningScrollData.SWAMP_PLAGUE_SCROLL -> target.poison(80)
                    SummoningScrollData.BRONZE_BULL_RUSH_SCROLL,
                    SummoningScrollData.IRON_BULL_RUSH_SCROLL,
                    SummoningScrollData.STEEL_BULL_RUSH_SCROLL,
                    SummoningScrollData.MITHRIL_BULL_RUSH_SCROLL,
                    SummoningScrollData.ADAMANT_BULL_RUSH_SCROLL,
                    SummoningScrollData.RUNE_BULL_RUSH_SCROLL,
                    -> if (player.world.randomDouble() < (1.0 / 3.0)) target.stun(5)
                        SummoningScrollData.CRUSHING_CLAW_SCROLL -> drainNpc(target, NpcSkills.DEFENCE, 0.05)
                        SummoningScrollData.DISSOLVE_SCROLL -> drainNpc(target, NpcSkills.ATTACK, 0.10)
                        SummoningScrollData.RENDING_SCROLL -> drainNpc(target, NpcSkills.STRENGTH, 0.10)
                        SummoningScrollData.EVIL_FLAMES_SCROLL -> drainNpc(target, NpcSkills.MAGIC, amount = 1)
                        SummoningScrollData.DOOMSPHERE_SCROLL -> drainNpc(target, NpcSkills.MAGIC, 0.05)
                        else -> Unit
                    }
                }
            },
            hitType = effect.hitType,
        )
    }

    private fun executeBoil(player: Player, familiar: Npc, target: Npc) {
        familiar.animate(7883)
        familiar.graphic(1373)
        val melee = familiar.tile.getDistance(target.tile) <= 1
        val hitType = if (melee) HitType.MELEE else if (player.world.randomDouble() < 0.50) HitType.RANGE else HitType.MAGIC
        if (!melee) {
            val projectileType = if (hitType == HitType.RANGE) ProjectileType.ARROW else ProjectileType.MAGIC
            player.world.spawn(familiar.createProjectile(target, 1376, projectileType))
            target.graphic(1377)
        }
        familiar.dealHit(target, maxHit = 240.0, landHit = true, delay = if (melee) 1 else 2, hitType = hitType)
    }

    private fun executeVolley(familiar: Npc, target: Npc, hits: Int, maxHit: Double, hitType: HitType) {
        if (hits == 3 && maxHit == 100.0) familiar.animate(7348)
        repeat(hits) { index ->
            familiar.dealHit(target, maxHit = maxHit, landHit = true, delay = 1 + index / 2, hitType = hitType)
        }
    }

    private fun executeAoe(
        player: Player,
        familiar: Npc,
        maxTargets: Int,
        radius: Int,
        maxHit: Double,
        animation: Int,
        sourceGraphic: Int = -1,
        projectile: Int = -1,
        targetGraphic: Int = -1,
    ): Boolean {
        val targets = nearbyAttackableNpcs(player, familiar, familiar, radius, maxTargets)
        if (targets.isEmpty()) {
            player.message("There are no valid targets for your familiar's special move.")
            return false
        }
        familiar.animate(animation)
        if (sourceGraphic >= 0) familiar.graphic(sourceGraphic)
        targets.forEach { target ->
            if (projectile >= 0) player.world.spawn(familiar.createProjectile(target, projectile, ProjectileType.MAGIC))
            if (targetGraphic >= 0) target.graphic(targetGraphic)
            familiar.dealHit(target, maxHit = maxHit, landHit = true, delay = if (projectile >= 0) 2 else 1, hitType = HitType.MAGIC)
            familiar.attack(target)
        }
        return true
    }

    private fun executeSplash(
        player: Player,
        familiar: Npc,
        primary: Npc,
        maxTargets: Int,
        radius: Int,
        maxHit: Double,
        projectile: Int = -1,
        targetGraphic: Int = -1,
    ) {
        nearbyAttackableNpcs(player, familiar, primary, radius, maxTargets, excluded = primary).forEach { target ->
            if (projectile >= 0) player.world.spawn(familiar.createProjectile(target, projectile, ProjectileType.MAGIC))
            if (targetGraphic >= 0) target.graphic(targetGraphic)
            familiar.dealHit(target, maxHit = maxHit, landHit = true, delay = if (projectile >= 0) 2 else 1, hitType = HitType.MAGIC)
        }
    }

    private fun nearbyAttackableNpcs(
        player: Player,
        familiar: Npc,
        center: Npc,
        radius: Int,
        maxTargets: Int,
        excluded: Npc? = null,
    ): List<Npc> {
        val targets = mutableListOf<Npc>()
        player.world.npcs.forEach { npc ->
            if (targets.size >= maxTargets) return@forEach
            if (npc === familiar || npc === excluded || !npc.isAlive() || npc.tile.height != center.tile.height) return@forEach
            if (!npc.tile.isWithinRadius(center.tile, radius)) return@forEach
            if (!player.tile.isMulti(player.world) || !npc.tile.isMulti(player.world)) return@forEach
            if (!player.world.plugins.canAttack(player, npc)) return@forEach
            targets.add(npc)
        }
        return targets
    }

    private fun drainNpc(target: Npc, skill: Int, multiplier: Double = 0.0, amount: Int = 0) {
        val current = target.stats.getCurrentLevel(skill)
        val drain = if (amount > 0) amount else ceil(target.stats.getMaxLevel(skill) * multiplier).toInt()
        target.stats.setCurrentLevel(skill, (current - drain).coerceAtLeast(1))
    }

    private fun boost(player: Player, skill: Int, amount: Int): Boolean {
        val before = player.skills.getCurrentLevel(skill)
        val after = (before + amount).coerceAtMost(player.skills.getMaxLevel(skill) + amount)
        if (after == before) return false
        player.skills.setCurrentLevel(skill, after)
        return true
    }

    private fun drain(player: Player, skill: Int, multiplier: Double) {
        val current = player.skills.getCurrentLevel(skill)
        val amount = ceil(player.skills.getMaxLevel(skill) * multiplier).toInt()
        player.skills.setCurrentLevel(skill, (current - amount).coerceAtLeast(1))
    }

    private fun restoreRunEnergy(player: Player): Boolean {
        if (player.runEnergy >= 100.0) {
            player.message("Your run energy is already full.")
            return false
        }
        player.runEnergy = (player.runEnergy + player.skills.getCurrentLevel(Skills.AGILITY) / 2.0).coerceAtMost(100.0)
        player.sendRunEnergy(player.runEnergy.toInt())
        return true
    }

    private fun animateSelf(player: Player, familiar: Npc, animation: Int, sourceGraphic: Int, ownerGraphic: Int = -1) {
        familiar.animate(animation)
        familiar.graphic(sourceGraphic)
        if (ownerGraphic >= 0) player.graphic(ownerGraphic)
    }
}
