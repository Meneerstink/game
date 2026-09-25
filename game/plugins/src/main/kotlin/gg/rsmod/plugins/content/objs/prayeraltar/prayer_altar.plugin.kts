package gg.rsmod.plugins.content.objs.prayeraltar

import gg.rsmod.game.fs.def.ObjectDef
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.content.mechanics.prayer.Prayers
import gg.rsmod.plugins.content.mechanics.prayer.AncientCurses
import gg.rsmod.plugins.content.magic.Spellbooks
import gg.rsmod.plugins.content.areas.home.FeroxObjects
import gg.rsmod.plugins.content.skills.prayer.burying.BoneData

private val ALTARS_PRAY_AT =
    setOf(
        Objs.ALTAR_27661, Objs.CHAOS_ALTAR, Objs.ALTAR_24343, Objs.ALTAR_2640, Objs.ALTAR, Objs.ALTAR_19145,
        // Ferox Enclave altar (imported LocType, option [Pray-at]).
        gg.rsmod.plugins.content.areas.home.FeroxObjects.ALTAR,
    )

private val ALTARS_PRAY =
    setOf(Objs.ALTAR_36972, Objs.ALTAR_OF_GUTHIX, Objs.ALTAR_34616, Objs.ALTAR_18254, Objs.ALTAR_39842)

/**
 * @author Kevin Senez <ksenez94@gmail.com>
 */
ALTARS_PRAY_AT.forEach { altar ->
    on_obj_option(obj = altar, "pray-at") {
        player.queue {
            player.animate(Anims.ALTAR_PRAY)
            player.filterableMessage("You recharge your Prayer points.")
            player.playSound(Sfx.PRAYER_RECHARGE)
            Prayers.rechargePrayerPoints(player)
            if (altar == FeroxObjects.ALTAR) {
                when (options("Use Normal Prayers.", "Use Ancient Curses.", "Unlock Ancient Curses (50,000 coins).", "Change my spellbook.", "Keep my books.")) {
                    1 -> AncientCurses.switchBook(player, AncientCurses.PrayerBook.NORMAL)
                    2 -> AncientCurses.switchBook(player, AncientCurses.PrayerBook.ANCIENT)
                    3 -> AncientCurses.unlock(player)
                    4 ->
                        when (options("Standard spellbook.", "Ancient Magicks.", "Lunar spellbook.")) {
                            1 -> Spellbooks.select(player, Spellbook.STANDARD)
                            2 -> Spellbooks.select(player, Spellbook.ANCIENT)
                            3 -> Spellbooks.select(player, Spellbook.LUNAR)
                        }
                }
            }
        }
    }
}

/*
 * Every other prayer altar, from its cache definition (owner 2026-09-24: "Check alle donors ... port t dan"; Void PrayerAltars.kt binds
 * "Pray" on every altar, while the two hand lists above left 67 altar ids answering nothing - ROOT_CAUSES #35). A loc whose name contains
 * "altar" and whose option is "Pray" / "Pray-at" recharges prayer exactly like the listed ones. It is an object FALLBACK - consulted only
 * when no explicit handler is bound - so the special altars (God Wars, spellbook, Ferox, POH, chaos) keep their own behaviour.
 */
val PRAY_OPTIONS = setOf("pray", "pray-at")

world.plugins.bindObjectFallback { player, obj, opt ->
    val def = player.world.definitions.get(ObjectDef::class.java, obj.getTransform(player))
    if (!def.name.lowercase().contains("altar")) return@bindObjectFallback false
    if (def.options.getOrNull(opt - 1)?.lowercase() !in PRAY_OPTIONS) return@bindObjectFallback false
    player.queue {
        player.animate(Anims.ALTAR_PRAY)
        player.filterableMessage("You recharge your Prayer points.")
        player.playSound(Sfx.PRAYER_RECHARGE)
        Prayers.rechargePrayerPoints(player)
    }
    true
}
ALTARS_PRAY.forEach { altar ->
    on_obj_option(obj = altar, "pray") {
        player.queue {
            player.animate(Anims.ALTAR_PRAY)
            player.filterableMessage("You recharge your Prayer points.")
            player.playSound(Sfx.PRAYER_RECHARGE)
            Prayers.rechargePrayerPoints(player)
        }
    }
}

/**
 * Gilded altar (POH) bone offering. Object id cross-verified: Novite's own rev-667
 * `player/actions/prayer/AltarAction.java` hardcodes object id 13199, and this target's own
 * generated `Objs.ALTAR_13199` resolves to the same numeric id - a real same-revision match, not a
 * guess. Novite's formula is `bone.getExperience() * 3` (three times the bury XP for the same
 * bone), ported directly via the existing [BoneData] table this project's own bone-burying feature
 * already uses (same bones, same base XP values).
 *
 * No animation or graphic ported: Novite's `AltarAction` claims animation 896 and graphic 624, but
 * this project's own generated `Anims.kt` names id 896 `SPIN_SPINNING_WHEEL` - a real, specific,
 * conflicting name for an unrelated action - not something to silently trust from a single donor.
 * Graphic 624 has no independently-confirmed name to cross-check it against either, so it is left
 * out alongside the contradicted animation rather than mixing a confirmed-safe id with a
 * contradicted one from the same source. SOURCE_BLOCKED: the real offering animation/graphic.
 */
BoneData.values.forEach { data ->
    on_item_on_obj(obj = Objs.ALTAR_13199, item = data.bone) {
        val xp = GildedAltarOffering.xpFor(data.bone) ?: return@on_item_on_obj
        if (player.inventory.remove(data.bone).hasSucceeded()) {
            player.filterableMessage("You offer the bones to the gods...")
            player.filterableMessage("They give you prayer experience in return.")
            player.addXp(Skills.PRAYER, xp, checkBrawlingGloves = true)
        }
    }
}
