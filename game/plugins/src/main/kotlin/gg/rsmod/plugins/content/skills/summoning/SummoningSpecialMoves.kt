package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.HitType
import gg.rsmod.plugins.api.ProjectileType
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.ext.addXp
import gg.rsmod.plugins.api.ext.isMulti
import gg.rsmod.plugins.api.ext.heal
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.sendRunEnergy
import gg.rsmod.plugins.content.combat.createProjectile
import gg.rsmod.plugins.content.combat.dealHit
import kotlin.math.ceil

enum class FamiliarSpecialTarget { INSTANT, NPC, INVENTORY_ITEM }

data class FamiliarSpecialBinding(
    val scroll: SummoningScrollData,
    val target: FamiliarSpecialTarget,
    val detailsComponent: Int,
    val orbComponent: Int,
)

/** Revision-667 dispatcher whose component ids are sourced from the matching cache interface data. */
object SummoningSpecialMoves {
    val bindings = listOf(
        FamiliarSpecialBinding(SummoningScrollData.DREADFOWL_STRIKE_SCROLL, FamiliarSpecialTarget.NPC, 77, 161),
        FamiliarSpecialBinding(SummoningScrollData.STONY_SHELL_SCROLL, FamiliarSpecialTarget.INSTANT, 85, 157),
        FamiliarSpecialBinding(SummoningScrollData.INSANE_FEROCITY_SCROLL, FamiliarSpecialTarget.INSTANT, 115, 142),
        FamiliarSpecialBinding(SummoningScrollData.THIEVING_FINGERS_SCROLL, FamiliarSpecialTarget.INSTANT, 91, 154),
        FamiliarSpecialBinding(SummoningScrollData.UNBURDEN_SCROLL, FamiliarSpecialTarget.INSTANT, 101, 149),
        FamiliarSpecialBinding(SummoningScrollData.TIRELESS_RUN_SCROLL, FamiliarSpecialTarget.INSTANT, 139, 130),
        FamiliarSpecialBinding(SummoningScrollData.ABYSSAL_STEALTH_SCROLL, FamiliarSpecialTarget.INSTANT, 97, 151),
        FamiliarSpecialBinding(SummoningScrollData.TESTUDO_SCROLL, FamiliarSpecialTarget.INSTANT, 127, 136),
        FamiliarSpecialBinding(SummoningScrollData.VOLCANIC_STRENGTH_SCROLL, FamiliarSpecialTarget.INSTANT, 183, 108),
        FamiliarSpecialBinding(SummoningScrollData.TITANS_CONSTITUTION_SCROLL, FamiliarSpecialTarget.INSTANT, 169, 115),
        FamiliarSpecialBinding(SummoningScrollData.HEALING_AURA_SCROLL, FamiliarSpecialTarget.INSTANT, 123, 138),
        FamiliarSpecialBinding(SummoningScrollData.MAGIC_FOCUS_SCROLL, FamiliarSpecialTarget.INSTANT, 161, 119),
        FamiliarSpecialBinding(SummoningScrollData.WINTER_STORAGE_SCROLL, FamiliarSpecialTarget.INVENTORY_ITEM, 121, 139),
        FamiliarSpecialBinding(SummoningScrollData.STEEL_OF_LEGENDS_SCROLL, FamiliarSpecialTarget.NPC, 173, 113),
    )

    fun validate() {
        check(bindings.map { it.detailsComponent }.distinct().size == bindings.size)
        check(bindings.map { it.orbComponent }.distinct().size == bindings.size)
        bindings.forEach { binding ->
            check(binding.scroll.familiars.isNotEmpty())
            check(binding.scroll.specialPoints in 1..Familiar.MAX_SPECIAL_POINTS)
        }
    }

    fun castInstant(player: Player, binding: FamiliarSpecialBinding): Boolean {
        if (binding.target != FamiliarSpecialTarget.INSTANT) return false
        val familiar = validateResources(player, binding.scroll) ?: return false
        val changed = when (binding.scroll) {
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
            else -> false
        }
        if (!changed) return false
        return commitResources(player, binding.scroll)
    }

    fun castOnNpc(player: Player, binding: FamiliarSpecialBinding, target: Npc): Boolean {
        if (binding.target != FamiliarSpecialTarget.NPC) return false
        val familiar = validateResources(player, binding.scroll) ?: return false
        if (!target.isAlive() || target === familiar || target.tile.height != familiar.tile.height || familiar.tile.getDistance(target.tile) > 16) {
            player.message("Your familiar cannot use that special move on this target.")
            return false
        }
        if (!player.tile.isMulti(player.world) || !target.tile.isMulti(player.world) || !player.world.plugins.canAttack(player, target)) {
            player.message("Your familiar cannot attack that target here.")
            return false
        }
        if (!commitResources(player, binding.scroll)) return false
        familiar.facePawn(target)
        when (binding.scroll) {
            SummoningScrollData.DREADFOWL_STRIKE_SCROLL -> {
                familiar.animate(5387)
                familiar.graphic(1523)
                player.world.spawn(familiar.createProjectile(target, 1318, ProjectileType.MAGIC))
                familiar.dealHit(target, maxHit = 30.0, landHit = true, delay = 2, hitType = HitType.MAGIC)
            }
            SummoningScrollData.STEEL_OF_LEGENDS_SCROLL -> {
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
        val familiar = validateResources(player, binding.scroll) ?: return false
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
        if (!commitResources(player, binding.scroll)) return false
        if (!player.inventory.remove(selected.id, 1, assureFullRemoval = true, beginSlot = slot).hasSucceeded()) {
            refundResources(player, binding.scroll)
            return false
        }
        if (!player.bank.add(selected.id, 1, assureFullInsertion = true).hasSucceeded()) {
            player.inventory.add(selected.id, 1, assureFullInsertion = true, beginSlot = slot)
            refundResources(player, binding.scroll)
            return false
        }
        familiar.graphic(1358)
        player.message("Your pack yak sends the item to your bank.")
        return true
    }

    private fun validateResources(player: Player, scroll: SummoningScrollData): Npc? {
        val familiar = Familiar.current(player)
        if (familiar == null || familiar.id !in scroll.familiars) {
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
        return familiar
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
