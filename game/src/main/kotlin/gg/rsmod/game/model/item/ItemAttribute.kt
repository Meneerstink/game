package gg.rsmod.game.model.item

/**
 * An [ItemAttribute] is a broad-use attribute key which can be used for different
 * effects on different items. [Item]s that have attributes will stop being tradeable
 * and can no longer stack with other items of the same id.
 *
 * @author Tom <rspsmods@gmail.com>
 */
enum class ItemAttribute {
    /**
     * Can represent any type of charge on an item.
     */
    CHARGES,

    /**
     * Represents the degrade percentage of an item. This can range from
     * 0 to 10000. 0 represents 0.0% and 10000 represents 100.00%. This
     * value is an integer, but is presented as a double (percentage) to the
     * player.
     */
    DEGRADE,

    /**
     * Some items can have another item 'attached' to them in some form or
     * another.
     *
     * Example: Toxic blowpipe can have darts attached to it.
     * Example: Rune pouches can have rune essence or pure essence attached to it.
     */
    ATTACHED_ITEM_ID,

    /**
     * The amount of [ATTACHED_ITEM_ID]s left on the item.
     */
    ATTACHED_ITEM_COUNT,

    /**
     * The amount of attacks that this item has dealt to a target. This attribute
     * can be reset at any point, such as for the Toxic blowpipe resetting every
     * three attacks.
     */
    ATTACK_COUNT,

    /**
     * The current tab this item might be residing in the bank
     */
    BANK_TAB,

    /**
     * 1 when an item's toggleable effect is switched off (e.g. the Ring of suffering recoil setting, stored per ring).
     */
    TOGGLED_OFF,

    /**
     * Rune pouch / divine rune pouch slots (OSRS import 2026-09-17): the rune item id and amount stored in each slot. The rune pouch
     * uses slots 1-3, the divine rune pouch 1-4.
     */
    RUNE_POUCH_ID_1,
    RUNE_POUCH_AMOUNT_1,
    RUNE_POUCH_ID_2,
    RUNE_POUCH_AMOUNT_2,
    RUNE_POUCH_ID_3,
    RUNE_POUCH_AMOUNT_3,
    RUNE_POUCH_ID_4,
    RUNE_POUCH_AMOUNT_4,

    /**
     * Audit D-15: 1 when an untradeable item without its own broken variant was lost on a PvP death.
     * OSRS keeps such an item with the victim "in broken form"; it can't be worn until Perdu repairs it.
     */
    BROKEN,
}
