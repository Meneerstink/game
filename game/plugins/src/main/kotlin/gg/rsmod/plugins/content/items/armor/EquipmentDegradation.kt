package gg.rsmod.plugins.content.items.armor

import gg.rsmod.game.Server
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.World
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.game.plugin.KotlinPlugin
import gg.rsmod.game.plugin.PluginRepository
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.player
import gg.rsmod.plugins.content.combat.isAttacking
import gg.rsmod.plugins.content.combat.isBeingAttacked

/**
 * RCV-011 Q-019 root cause: combat degradation was three copies of the same timer (Barrows, PvP/corrupt, chaotics),
 * each with its own loop and id handling, and every family nobody had copied it for (the Nex sets) never degraded.
 * This is the one table-driven model for every combat-degrading worn item, the same shape as Void's `Degradation`
 * (`charges` + `degrade` per item, depleted per combat tick) and Novite's `ChargesManager.process` (every tick).
 *
 * A row ticks once per game cycle while the wearer is attacking or being attacked. Remaining charges live in
 * [ItemAttribute.CHARGES] on the item (absent = the row's full [DegradeRow.charges]); at 0 the item becomes
 * [DegradeRow.next] (fresh charges) or turns to dust ([DegradeRow.DESTROY]).
 *
 * Values per family (sources and conflicts):
 * - Barrows/Akrisae: pristine → 100 on the first combat tick, then 22,500 ticks per stage to 0 (Void rows + 2011 wiki
 *   15 hours). SOURCE_CONFLICT: Novite uses 1,000 ticks per stage; Void only charges the 100 stage.
 * - Vesta/Statius/Zuriel/Morrigan: 6,000 combat ticks then dust (Void and the existing table agree).
 * - Corrupt PvP and corrupt dragon: the existing 1,500 / 3,000 combat ticks are kept. SOURCE_CONFLICT: Void depletes
 *   both while worn ("equip") with 1,500; no second source — not flipped.
 * - Chaotics: 30,000 combat ticks then broken (Void).
 * - Torva/Pernix/Virtus/Zaryte bow: pristine → used (id + 2) on the first combat tick (Novite
 *   `getDegradeItemWhenCombating`, Void), used → broken (id + 1) after 60,000 combat ticks (Void; the broken ids exist in
 *   the 667 cache). Novite never breaks the used item; Void's virtus_robe_legs_used points at the robe top's broken id
 *   (data slip) — the cache legs id is used. Message "Your <item> degraded." (Novite).
 * - Crystal bow/shield: not in the table. SOURCE_BLOCKED: Void's chain has charges only on the full stage, Novite none.
 */
data class DegradeRow(
    val id: Int,
    val next: Int,
    val charges: Int,
    /** Message on the transition, given the degrading item's cache name; null for none. */
    val message: (String) -> String?,
) {
    companion object {
        const val DESTROY = -1
    }
}

/** What one combat tick does to an item: count down, or replace/destroy it with an optional message. */
data class DegradeStep(
    val charges: Int?,
    val replaceWith: Int?,
    val destroy: Boolean,
    val message: String?,
)

object DegradeTable {
    const val NEX_USED_CHARGES = 60_000

    val rows: List<DegradeRow> =
        buildList {
            BarrowsPiece.values.forEach { piece ->
                add(DegradeRow(piece.idForStage(BarrowsPiece.PRISTINE), piece.idForStage(BarrowsPiece.FULL), 1) { null })
                for (stage in BarrowsPiece.FULL until BarrowsPiece.BROKEN) {
                    val next = stage + 1
                    add(
                        DegradeRow(piece.idForStage(stage), piece.idForStage(next), BarrowsDegradation.STAGE_CYCLES) {
                            if (next == BarrowsPiece.BROKEN) {
                                "<col=ff0000>Your ${piece.displayName} has degraded completely and needs repairing."
                            } else {
                                "<col=ff0000>Your ${piece.displayName} has degraded."
                            }
                        },
                    )
                }
            }
            CorruptArmor.values().forEach { armour ->
                add(DegradeRow(armour.newId, armour.degradedId, 1) { null })
                add(DegradeRow(armour.degradedId, DegradeRow.DESTROY, armour.maxCharges) { name -> "<col=ff0000>Your $name has degraded into dust." })
            }
            ChaoticWeapon.values().forEach { weapon ->
                add(DegradeRow(weapon.chargedId, weapon.brokenId, ChaoticWeaponCharges.MAX_CHARGES) { name -> "<col=ff0000>Your $name has degraded and broken." })
            }
            NexArmour.values().forEach { armour ->
                add(DegradeRow(armour.pristineId, armour.usedId, 1) { name -> "Your $name degraded." })
                add(DegradeRow(armour.usedId, armour.brokenId, NEX_USED_CHARGES) { name -> "Your $name degraded." })
            }
        }

    private val byId: Map<Int, DegradeRow> = rows.associateBy { it.id }

    fun rowFor(itemId: Int): DegradeRow? = byId[itemId]

    /** One combat tick for [itemId] carrying [charges] (null = never ticked); null when the item does not degrade. */
    fun step(
        itemId: Int,
        charges: Int?,
        name: String,
    ): DegradeStep? {
        val row = byId[itemId] ?: return null
        val remaining = (charges ?: row.charges) - 1
        if (remaining > 0) return DegradeStep(remaining, null, false, null)
        val destroy = row.next == DegradeRow.DESTROY
        return DegradeStep(null, if (destroy) null else row.next, destroy, row.message(name))
    }
}

/** Nex armour: pristine, used (pristine + 2) and broken (pristine + 3) ids from the 667 cache. */
enum class NexArmour(
    val pristineId: Int,
    val usedId: Int,
    val brokenId: Int,
) {
    TORVA_FULL_HELM(Items.TORVA_FULL_HELM, Items.TORVA_FULL_HELM_20137, Items.TORVA_FULL_HELM_BROKEN),
    TORVA_PLATEBODY(Items.TORVA_PLATEBODY, Items.TORVA_PLATEBODY_20141, Items.TORVA_PLATEBODY_BROKEN),
    TORVA_PLATELEGS(Items.TORVA_PLATELEGS, Items.TORVA_PLATELEGS_20145, Items.TORVA_PLATELEGS_BROKEN),
    PERNIX_COWL(Items.PERNIX_COWL, Items.PERNIX_COWL_20149, Items.PERNIX_COWL_BROKEN),
    PERNIX_BODY(Items.PERNIX_BODY, Items.PERNIX_BODY_20153, Items.PERNIX_BODY_BROKEN),
    PERNIX_CHAPS(Items.PERNIX_CHAPS, Items.PERNIX_CHAPS_20157, Items.PERNIX_CHAPS_BROKEN),
    VIRTUS_MASK(Items.VIRTUS_MASK, Items.VIRTUS_MASK_20161, Items.VIRTUS_MASK_BROKEN),
    VIRTUS_ROBE_TOP(Items.VIRTUS_ROBE_TOP, Items.VIRTUS_ROBE_TOP_20165, Items.VIRTUS_ROBE_TOP_BROKEN),
    VIRTUS_ROBE_LEGS(Items.VIRTUS_ROBE_LEGS, Items.VIRTUS_ROBE_LEGS_20169, Items.VIRTUS_ROBE_LEGS_BROKEN),
    ZARYTE_BOW(Items.ZARYTE_BOW, Items.ZARYTE_BOW_20173, Items.ZARYTE_BOW_BROKEN),
}

class EquipmentDegradation(
    r: PluginRepository,
    world: World,
    server: Server,
) : KotlinPlugin(r, world, server) {
    private val degradeCheck = TimerKey()

    init {
        on_login {
            player.timers[degradeCheck] = 1
        }

        on_timer(degradeCheck) {
            if (player.isAttacking() || player.isBeingAttacked()) {
                tick(player)
            }
            player.timers[degradeCheck] = 1
        }
    }

    private fun tick(player: Player) {
        for (slot in 0 until player.equipment.capacity) {
            val item = player.equipment[slot] ?: continue
            if (DegradeTable.rowFor(item.id) == null) continue
            val def = player.world.definitions.get(ItemDef::class.java, item.id)
            val step = DegradeTable.step(item.id, item.attr[ItemAttribute.CHARGES], def.name) ?: continue
            if (step.charges != null) {
                item.attr[ItemAttribute.CHARGES] = step.charges
                continue
            }
            player.equipment.remove(item)
            step.replaceWith?.let { player.equipment.add(Item(it, item.amount), beginSlot = def.equipSlot) }
            step.message?.let { player.message(it) }
        }
    }
}
