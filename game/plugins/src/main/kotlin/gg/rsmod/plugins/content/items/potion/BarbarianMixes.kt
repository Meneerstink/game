package gg.rsmod.plugins.content.items.potion

/**
 * Adjacent gap "667 barbarian mixes have recipes but cannot be drunk" (OSRS Wiki raw wikitext 2026-09-14):
 * - Barbarian Training: "Through the creation of barbarian mixes, they will additionally heal 3 or 6 Hitpoints (depending on the mix type)
 *   in addition to the regular effects to normal potions"; its table gives Heals 3 for the Attack, Antipoison, Relicym's, Strength, Restore,
 *   Energy and Combat mixes and 6 for every other mix.
 * - Attack mix (and every mix page): "Drinking a sip of the potion gives the message "You drink the lumpy potion"".
 * Each 667 mix is a [Potion] entry with its base [PotionType] and [Potion.mixHeal]; [Potions.drink] applies the base effect, heals and sends
 * [MESSAGE]. BLOCKED: Relicym's mix (Relicym's balm cures disease; this server has no disease system and no drinkable balm).
 */
object BarbarianMixes {
    const val MESSAGE = "You drink the lumpy potion"
}
