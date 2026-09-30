package gg.rsmod.plugins.content.items.osrs

import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.entity.Npc
import gg.rsmod.game.model.entity.Pawn
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.*
import gg.rsmod.plugins.content.combat.venom

/**
 * Serpentine helm (owner 2026-09-29, "fully working"). OSRS Wiki "Serpentine helm":
 * - "charged with Zulrah's scales, requiring 11,000 scales to fully charge it"; "It cannot be equipped if it is uncharged, except
 *   when it's fully degraded while equipped" (it then "retains the negative bonuses, but loses all positive bonuses").
 * - "uses 10 scales when the player enters combat and will use another 10 if the player is still in combat after 90 ticks".
 * - "immunity to venom and poison" while charged and worn; it "does not cure existing venom or poison effects".
 * - Envenoming on successful melee hits, "only applies to monsters": 1/6 with a non-poisoned weapon, 1/2 with a poisoned weapon or
 *   the noxious halberd, always with the toxic staff of the dead.
 * Scales live in [ItemAttribute.CHARGES] on the charged helm. ADAPTED: the messages (no sourced wording).
 */
object SerpentineHelm {
    const val MAX_SCALES = 11_000
    const val SCALES_PER_USE = 10
    const val USE_INTERVAL_TICKS = 90

    /** Combat ticks spent in the current combat spell (the helm charges on the first and every 90th). */
    private val COMBAT_TICKS = AttributeKey<Int>()

    fun scales(item: Item?): Int = if (item?.id == Items.SERPENTINE_HELM) item.attr[ItemAttribute.CHARGES] ?: 0 else 0

    fun worn(player: Player): Item? = player.getEquipment(EquipmentType.HEAD)?.takeIf { it.id == Items.SERPENTINE_HELM }

    /** Charged and worn: immune to poison and venom. */
    fun protects(pawn: Pawn): Boolean = pawn is Player && scales(worn(pawn)) > 0

    fun withScales(
        item: Item,
        scales: Int,
    ): Item {
        val id = if (scales > 0) Items.SERPENTINE_HELM else Items.SERPENTINE_HELM_UNCHARGED
        return Item(id, item.amount).copyAttr(item).also {
            if (scales > 0) it.attr[ItemAttribute.CHARGES] = scales.coerceAtMost(MAX_SCALES) else it.attr.remove(ItemAttribute.CHARGES)
        }
    }

    /** One tick while attacking or being attacked: 10 scales on the first tick of a combat spell and every 90 ticks after. */
    fun combatTick(player: Player) {
        val helm = worn(player) ?: return
        val ticks = player.attr[COMBAT_TICKS] ?: 0
        player.attr[COMBAT_TICKS] = ticks + 1
        if (ticks % USE_INTERVAL_TICKS != 0) return
        val left = scales(helm) - SCALES_PER_USE
        player.equipment[EquipmentType.HEAD.id] = withScales(helm, left)
        if (left <= 0) player.message("Your serpentine helm has run out of Zulrah's scales.")
    }

    /** Out of combat: the next combat spell starts with a fresh 10-scale charge. */
    fun resetCombat(player: Player) {
        player.attr.remove(COMBAT_TICKS)
    }

    fun venomChance(player: Player): Double {
        val weapon = player.getEquipment(EquipmentType.WEAPON)?.id ?: return 1.0 / 6
        // OSRS Wiki "Serpentine helm": "100% chance if using the toxic blowpipe, trident of the swamp, or toxic staff of the dead".
        if (weapon == Items.TOXIC_STAFF_OF_THE_DEAD || weapon == Items.TOXIC_BLOWPIPE || weapon == Items.TRIDENT_OF_THE_SWAMP) return 1.0
        if (NoxiousHalberd.isWielding(player)) return 0.5
        val name = player.world.definitions.get(gg.rsmod.game.fs.def.ItemDef::class.java, weapon).name
        return if (name.contains("(p", ignoreCase = true)) 0.5 else 1.0 / 6
    }

    /**
     * A successful hit on a monster while the charged helm is worn - any combat style (owner 2026-09-30: OSRS Wiki "for every
     * successful hit the player does"; only the melee path rolled it, so the blowpipe / trident 100 % never applied).
     */
    fun rollVenom(
        player: Player,
        target: Pawn,
    ) {
        if (target !is Npc || !protects(player)) return
        if (player.world.randomDouble() < venomChance(player)) target.venom()
    }
}
