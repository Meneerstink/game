package gg.rsmod.plugins.content.combat

import gg.rsmod.game.model.combat.CombatClass
import gg.rsmod.game.model.timer.MIASMIC_TIMER
import gg.rsmod.game.model.combat.StyleType
import gg.rsmod.game.model.combat.WeaponStyle
import gg.rsmod.game.model.combat.XpMode
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.WeaponType
import gg.rsmod.plugins.api.cfg.Anims
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.strategy.CombatStrategy
import gg.rsmod.plugins.content.combat.strategy.MagicCombatStrategy
import gg.rsmod.plugins.content.combat.strategy.MeleeCombatStrategy
import gg.rsmod.plugins.content.combat.strategy.RangedCombatStrategy
import gg.rsmod.plugins.content.items.helios.CrownOfHelios
import gg.rsmod.plugins.content.items.helios.CrownOfHeliosCombatStrategy

/**
 * @author Tom <rspsmods@gmail.com>
 */
object CombatConfigs {
    private const val PLAYER_DEFAULT_ATTACK_SPEED = 4

    private const val MIN_ATTACK_SPEED = 1

    private val DEFENDERS =
        intArrayOf(
            Items.BRONZE_DEFENDER,
            Items.IRON_DEFENDER,
            Items.STEEL_DEFENDER,
            Items.MITHRIL_DEFENDER,
            Items.BLACK_DEFENDER,
            Items.ADAMANT_DEFENDER,
            Items.RUNE_DEFENDER,
            Items.DRAGON_DEFENDER,
            Items.AVERNIC_DEFENDER,
        )

    val BOOKS =
        intArrayOf(
            Items.HOLY_BOOK,
            Items.BOOK_OF_BALANCE,
            Items.UNHOLY_BOOK,
            Items.BOOK_OF_LAW,
            Items.BOOK_OF_WAR,
        )

    private val BOXING_GLOVES =
        intArrayOf(
            Items.BOXING_GLOVES,
            Items.BOXING_GLOVES_7673,
        )

    private val GODSWORDS =
        intArrayOf(
            Items.ARMADYL_GODSWORD,
            Items.BANDOS_GODSWORD,
            Items.SARADOMIN_GODSWORD,
            Items.ZAMORAK_GODSWORD,
        )

    private val DRAGON_DAGGERS =
        intArrayOf(
            Items.DRAGON_DAGGER,
            Items.DRAGON_DAGGER_P,
            Items.DRAGON_DAGGER_P_5680,
            Items.DRAGON_DAGGER_P_5698,
            Items.C_DRAGON_DAGGER_DEG,
            Items.CORRUPT_DRAGON_DAGGER,
        )

    fun getCombatStrategy(pawn: Pawn): CombatStrategy =
        if (CrownOfHelios.isActive(pawn)) {
            CrownOfHeliosCombatStrategy
        } else when (getCombatClass(pawn)) {
            CombatClass.MELEE -> MeleeCombatStrategy
            CombatClass.MAGIC -> MagicCombatStrategy
            CombatClass.RANGED -> RangedCombatStrategy
            else -> throw IllegalStateException("Invalid combat class: ${getCombatClass(pawn)} for $pawn")
        }

    fun getCombatClass(pawn: Pawn): CombatClass {
        if (pawn is Npc) {
            if (pawn.combatDef.spell > -1) return CombatClass.MAGIC
            // pawn.combatClass defaults to MELEE and is a mutable var some custom combat scripts
            // toggle per-attack (a multi-style boss switching to MAGIC for one specific hit) - if
            // something has already moved it off that default, trust it fully, unchanged from
            // before. But a simple NPC with no custom script that just declares
            // configs { attackStyle = StyleType.MAGIC/RANGED } in its combat def never touches
            // combatClass at all, so it silently stayed at the MELEE default and got routed into
            // MeleeCombatStrategy - which only understands StyleType.{STAB,SLASH,CRUSH,MAGIC_MELEE}
            // and threw on the mismatch (same class of bug as the Player/Salamander case above).
            if (pawn.combatClass != CombatClass.MELEE) return pawn.combatClass
            return when (pawn.combatDef.attackStyleType) {
                StyleType.MAGIC -> CombatClass.MAGIC
                StyleType.RANGED -> CombatClass.RANGED
                else -> CombatClass.MELEE
            }
        }

        if (pawn is Player) {
            return when {
                CrownOfHelios.isActive(pawn) -> CrownOfHelios.mode(pawn).combatClass
                pawn.attr.has(Combat.CASTING_SPELL) -> CombatClass.MAGIC
                pawn.hasWeaponType(
                    WeaponType.BOW,
                    WeaponType.SLING,
                    WeaponType.CHINCHOMPA,
                    WeaponType.CROSSBOW,
                    WeaponType.THROWN,
                ) -> CombatClass.RANGED
                else -> {
                    // Some weapon families carry a hybrid attack-style option whose resolved
                    // StyleType (see AttackStyleData/WeaponCombatData) diverges from what their
                    // WeaponType alone implies - e.g. the Salamander family's third ("Scorch")
                    // style is StyleType.MAGIC even though salamanders are melee-equippable and
                    // not caught by the ranged-weapon-type check above. Defaulting those to
                    // CombatClass.MELEE routed them into MeleeCombatStrategy, which then crashed
                    // in MeleeCombatFormula (it only understands STAB/SLASH/CRUSH/MAGIC_MELEE) -
                    // aborting that whole cycle's queued-task processing for every pawn, not just
                    // this one. Mirrors the same weapon+style-button lookup getCombatStyle(Pawn)
                    // below already does, so both stay consistent with each other.
                    val style = pawn.getAttackStyle()
                    val option = getCombatStyle(style)
                    val data = getWeaponType(pawn)?.let { WeaponCombatData.getAttackStyleType(it, option) }
                    when (data?.styleType) {
                        StyleType.MAGIC -> CombatClass.MAGIC
                        StyleType.RANGED -> CombatClass.RANGED
                        else -> CombatClass.MELEE
                    }
                }
            }
        }

        throw IllegalArgumentException("Invalid combat class for $pawn")
    }

    fun getAttackDelay(pawn: Pawn): Int {
        if (pawn is Npc) {
            return pawn.combatDef.attackSpeed
        }

        if (pawn is Player) {
            if (CrownOfHelios.isActive(pawn)) {
                return MIN_ATTACK_SPEED
            }
            val default = PLAYER_DEFAULT_ATTACK_SPEED
            val weapon = pawn.getEquipment(EquipmentType.WEAPON) ?: return default
            // Missing metadata is -1, not a one-tick weapon. Keep explicit custom speeds.
            var speed = weapon.getDef(pawn.world.definitions).attackSpeed.takeIf { it > 0 } ?: default
            // OSRS Wiki "Toxic blowpipe": "During player versus player combat, its attack speed is 4, or equal to a shortbow."
            if (weapon.id == gg.rsmod.plugins.api.cfg.Items.TOXIC_BLOWPIPE &&
                pawn.attr[gg.rsmod.game.model.attr.COMBAT_TARGET_FOCUS_ATTR]?.get() is Player
            ) {
                speed = 4
            }
            if (getCombatClass(pawn) == CombatClass.RANGED && getAttackStyle(pawn) == WeaponStyle.RAPID) {
                speed -= 1
            }
            if (getCombatClass(pawn) == CombatClass.MAGIC) {
                speed = 5
            }
            // Miasmic spells: attack speed is halved (doubled delay) for the effect duration.
            if (pawn.timers.has(MIASMIC_TIMER)) {
                speed *= 2
            }
            return Math.max(MIN_ATTACK_SPEED, speed)
        }

        throw IllegalArgumentException("Invalid attack delay for $pawn")
    }

    private fun getCombatStyle(style: Int): CombatStyle {
        if (style == CombatStyle.FIRST.id) {
            return CombatStyle.FIRST
        }
        if (style == CombatStyle.SECOND.id) {
            return CombatStyle.SECOND
        }
        if (style == CombatStyle.THIRD.id) {
            return CombatStyle.THIRD
        }
        return CombatStyle.FOURTH
    }

    private fun getWeaponType(player: Player): WeaponType? {
        return WeaponType.values().find { it.id == player.getWeaponType() }
    }

    fun getAttackAnimation(pawn: Pawn): Int {
        if (pawn is Npc) {
            return pawn.combatDef.attackAnimation
        }

        if (pawn is Player) {
            val style = pawn.getAttackStyle()
            val option = getCombatStyle(style)
            var weaponId = pawn.getEquipment(EquipmentType.WEAPON)?.id
            val itemAnimation = CombatAnimation.getItemAnimation(weaponId ?: -1, pawn.getWeaponType())
            if (itemAnimation != null) {
                val style = CombatAnimation.getCorrectStyle(itemAnimation, option)
                if (style != null) {
                    return style.attackAnimation.id
                }
            }
            return when {
                pawn.hasEquipped(EquipmentType.WEAPON, *GODSWORDS) ->
                    when (option) {
                        CombatStyle.THIRD -> Anims.ATTACK_GODSWORD_CRUSH
                        CombatStyle.FOURTH -> Anims.ATTACK_GODSWORD_WHACK
                        else -> Anims.ATTACK_GODSWORD_SLASH
                    }
                pawn.hasWeaponType(WeaponType.AXE) ->
                    when (option) {
                        CombatStyle.FIRST, CombatStyle.FOURTH -> Anims.ATTACK_CRUSH
                        else -> Anims.ATTACK_HACK
                    }
                pawn.hasWeaponType(WeaponType.LONG_SWORD) ->
                    when (option) {
                        CombatStyle.THIRD -> Anims.ATTACK_LUNGE
                        else -> Anims.ATTACK_SLASH
                    }
                pawn.hasWeaponType(WeaponType.TWO_HANDED) ->
                    when (option) {
                        CombatStyle.THIRD -> Anims.ATTACK_2H_CRUSH
                        else -> Anims.ATTACK_2H_SLASH
                    }
                pawn.hasWeaponType(WeaponType.PICKAXE) ->
                    when (option) {
                        CombatStyle.THIRD -> Anims.ATTACK_CRUSH
                        CombatStyle.FOURTH -> Anims.ATTACK_STAB
                        else -> Anims.ATTACK_HACK
                    }
                pawn.hasEquipped(EquipmentType.WEAPON, *DRAGON_DAGGERS) ->
                    when (option) {
                        CombatStyle.THIRD -> Anims.ATTACK_HACK
                        else -> Anims.ATTACK_JAB
                    }
                pawn.hasWeaponType(WeaponType.DAGGER) ->
                    when (option) {
                        CombatStyle.THIRD -> Anims.ATTACK_SLASH
                        else -> Anims.ATTACK_STAB
                    }
                pawn.hasWeaponType(WeaponType.MACE) ->
                    when (option) {
                        CombatStyle.THIRD -> Anims.ATTACK_STAB
                        else -> Anims.ATTACK_CRUSH
                    }
                pawn.hasWeaponType(WeaponType.WHIP) ->
                    when (option) {
                        CombatStyle.SECOND -> Anims.ATTACK_WHIP_LASH
                        else -> Anims.ATTACK_WHIP_FLICK
                    }
                pawn.hasWeaponType(WeaponType.SPEAR) ->
                    when (option) {
                        CombatStyle.SECOND -> Anims.ATTACK_SPEAR_SLASH
                        CombatStyle.THIRD -> Anims.ATTACK_SPEAR_CRUSH
                        else -> Anims.ATTACK_SPEAR_STAB
                    }
                pawn.hasWeaponType(WeaponType.HALBERD) ->
                    when (option) {
                        CombatStyle.SECOND -> Anims.ATTACK_HALBERD_SWIPE
                        else -> Anims.ATTACK_STAB
                    }
                pawn.hasWeaponType(WeaponType.SCYTHE) -> Anims.ATTACK_HALBERD_SWIPE
                pawn.hasWeaponType(WeaponType.HAMMER) || pawn.hasWeaponType(WeaponType.HAMMER_EXTRA) -> Anims.ATTACK_CRUSH
                pawn.hasWeaponType(WeaponType.BOW) -> Anims.ATTACK_BOW
                pawn.hasWeaponType(WeaponType.CROSSBOW) -> Anims.ATTACK_CROSSBOW
                pawn.hasWeaponType(WeaponType.STAFF) || pawn.hasWeaponType(WeaponType.SCEPTRE) -> Anims.ATTACK_SCEPTRE
                pawn.hasWeaponType(WeaponType.CHINCHOMPA) -> Anims.ATTACK_CHINCHOMPA
                pawn.hasWeaponType(
                    WeaponType.THROWN,
                ) ||
                    pawn.hasWeaponType(
                        WeaponType.THROWN_EXTRA,
                    ) -> if (pawn.hasEquipped(EquipmentType.WEAPON, Items.TOKTZXILUL)) Anims.ATTACK_TOKTZXILUL else
                        Anims.ATTACK_THROWN
                pawn.hasWeaponType(WeaponType.CLAWS) -> Anims.ATTACK_CLAWS
                pawn.hasWeaponType(WeaponType.SLING) -> Anims.ATTACK_SLING
                else -> if (style == 1) Anims.ATTACK_KICK else Anims.ATTACK_PUNCH
            }
        }

        throw IllegalArgumentException("Invalid attack animation for $pawn")
    }

    fun getBlockAnimation(pawn: Pawn): Int {
        if (pawn is Npc) {
            return pawn.combatDef.blockAnimation
        }

        if (pawn is Player) {
            val shield = pawn.getEquipment(EquipmentType.SHIELD)?.id
            val shieldAnimation =
                shield?.let {
                    CombatAnimation.getItemAnimation(
                        it,
                        equipType = EquipmentType.SHIELD.id,
                    )
                }
            if (shieldAnimation != null) {
                return shieldAnimation.blockAnimation.id
            }
            val weapon = pawn.getEquipment(EquipmentType.WEAPON)?.id ?: -1
            val weaponAnimation = weapon.let { CombatAnimation.getItemAnimation(it, weaponType = pawn.getWeaponType()) }
            if (weaponAnimation != null) {
                return weaponAnimation.blockAnimation.id
            }
            return when {
                pawn.hasEquipped(EquipmentType.SHIELD, *BOOKS) -> Anims.BLOCK_BOTH_HANDS
                pawn.hasEquipped(EquipmentType.WEAPON, Items.SLED_4084) -> Anims.BLOCK_SLED
                pawn.hasEquipped(EquipmentType.WEAPON, Items.BASKET_OF_EGGS) -> Anims.BLOCK_BASKET_EGGS
                pawn.hasEquipped(EquipmentType.SHIELD, *DEFENDERS) -> Anims.BLOCK_DEFENDER
                pawn.getEquipment(EquipmentType.SHIELD) != null -> Anims.BLOCK_SHIELD // If wearing any shield, this animation is
                // used

                pawn.hasEquipped(EquipmentType.WEAPON, *BOXING_GLOVES) -> Anims.BLOCK_BOXING_GLOVES
                pawn.hasEquipped(EquipmentType.WEAPON, *GODSWORDS) -> Anims.BLOCK_GODSWORD
                pawn.hasEquipped(EquipmentType.WEAPON, Items.ZAMORAKIAN_SPEAR) -> Anims.BLOCK_ZAMORAKIAN_SPEAR

                pawn.hasWeaponType(WeaponType.DAGGER) -> Anims.BLOCK_LOW
                pawn.hasWeaponType(WeaponType.LONG_SWORD) -> Anims.BLOCK_LONGSWORD
                pawn.hasWeaponType(WeaponType.PICKAXE, WeaponType.CLAWS) -> Anims.BLOCK_PICKAXE
                pawn.hasWeaponType(WeaponType.MACE) -> Anims.BLOCK_MACE
                pawn.hasWeaponType(WeaponType.TWO_HANDED) -> Anims.BLOCK_GODSWORD
                pawn.hasWeaponType(WeaponType.STAFF) || pawn.hasWeaponType(WeaponType.SCEPTRE) -> Anims.BLOCK_BOTH_HANDS
                pawn.hasWeaponType(WeaponType.BOW) -> Anims.BLOCK_UNARMED
                pawn.hasWeaponType(WeaponType.SPEAR, WeaponType.HALBERD, WeaponType.SCYTHE) -> Anims.BLOCK_SPEAR
                pawn.hasWeaponType(WeaponType.WHIP) || pawn.hasWeaponType(WeaponType.SLING) -> Anims.BLOCK_WHIP
                else -> Anims.BLOCK_UNARMED
            }
        }
        throw IllegalArgumentException("Invalid block animation for $pawn")
    }

    fun getAttackStyle(pawn: Pawn): WeaponStyle {
        if (pawn.entityType.isNpc) {
            return (pawn as Npc).weaponStyle
        }
        if (pawn is Player) {
            val style = pawn.getAttackStyle()
            val option = getCombatStyle(style)
            val data = getWeaponType(pawn)?.let { WeaponCombatData.getAttackStyleType(it, option) }
            if (data != null) {
                return data.weaponStyle
            }
        }
        throw IllegalArgumentException("Invalid attack style for $pawn")
    }

    fun getCombatStyle(pawn: Pawn): StyleType {
        if (pawn.entityType.isNpc) {
            return (pawn as Npc).combatDef.attackStyleType
        }
        if (pawn is Player) {
            val style = pawn.getAttackStyle()
            val option = getCombatStyle(style)
            val data = getWeaponType(pawn)?.let { WeaponCombatData.getAttackStyleType(it, option) }
            if (data != null) {
                return data.styleType
            }
        }
        throw IllegalArgumentException("Invalid combat style for $pawn")
    }

    fun getXpMode(player: Player): XpMode {
        val style = player.getAttackStyle()
        val option = getCombatStyle(style)
        val data = getWeaponType(player)?.let { WeaponCombatData.getAttackStyleType(it, option) }
        if (data != null) {
            return data.xpMode
        }
        throw IllegalArgumentException("Invalid xp mode for $player")
    }
}
