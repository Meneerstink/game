package gg.rsmod.plugins.content.items.armor

import gg.rsmod.game.Server
import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.World
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
 * Q-019: Dungeoneering chaotic weapon/shield degrade. Real values sourced from Void's own
 * production `data/skill/dungeoneering/dungeoneering.items.toml` (the plan's donor hierarchy
 * falls back to Void's real config data when a mechanic is absent from Novite): every chaotic
 * item carries `charges = 30000`, `deplete = "combat"`, `degrade = "<name>_broken"` - a real
 * 30000-charge budget that depletes while in combat and ends in a distinct "broken" id, not
 * guessed. Neither donor's own generic degrade engine exposed the exact per-hit/per-tick
 * decrement granularity for its "deplete" system in a form this session could locate (2 targeted
 * codegraph searches, R9 cap), so this reuses the identical decrement cadence already shipped and
 * working for PvP armour in [CorruptArmorCharges] (1 charge per combat timer tick while attacking
 * or being attacked) rather than inventing a different rate.
 *
 * Unlike corrupt/PvP armour, a broken chaotic weapon is terminal here (it does not itself further
 * deplete into dust) - per the toml, `_broken` items carry no `charges`/`degrade` fields of their
 * own. Repairing a broken chaotic weapon (Dungeoneering token cost) is out of scope for this
 * batch; see Q-020 (assembly/dismantling/repair).
 */
class ChaoticWeaponCharges(
    r: PluginRepository,
    world: World,
    server: Server,
) : KotlinPlugin(r, world, server) {
    private val chargesCheck = TimerKey()

    companion object {
        const val MAX_CHARGES = 30_000
    }

    init {
        on_login {
            player.timers[chargesCheck] = 1
        }

        on_timer(chargesCheck) {
            if (player.isAttacking() || player.isBeingAttacked()) {
                player.equipment.forEach { equipped ->
                    val chaotic = ChaoticWeapon.values().firstOrNull { it.chargedId == equipped?.id }
                    if (chaotic != null && equipped != null) {
                        val current = equipped.attr[ItemAttribute.CHARGES] ?: MAX_CHARGES
                        val remaining = current - 1
                        if (remaining <= 0) {
                            val def = player.world.definitions.get(ItemDef::class.java, equipped.id)
                            val equipSlot = def.equipSlot
                            player.equipment.remove(equipped)
                            player.equipment.add(Item(chaotic.brokenId, equipped.amount), beginSlot = equipSlot)
                            player.message("<col=ff0000>Your ${def.name} has degraded and broken.")
                        } else {
                            equipped.attr[ItemAttribute.CHARGES] = remaining
                        }
                    }
                }
            }
            player.timers[chargesCheck] = 1
        }
    }
}

enum class ChaoticWeapon(val chargedId: Int, val brokenId: Int) {
    CHAOTIC_RAPIER(Items.CHAOTIC_RAPIER, Items.CHAOTIC_RAPIER_BROKEN),
    CHAOTIC_LONGSWORD(Items.CHAOTIC_LONGSWORD, Items.CHAOTIC_LONGSWORD_BROKEN),
    CHAOTIC_MAUL(Items.CHAOTIC_MAUL, Items.CHAOTIC_MAUL_BROKEN),
    CHAOTIC_STAFF(Items.CHAOTIC_STAFF, Items.CHAOTIC_STAFF_BROKEN),
    CHAOTIC_CROSSBOW(Items.CHAOTIC_CROSSBOW, Items.CHAOTIC_CROSSBOW_BROKEN),
    CHAOTIC_KITESHIELD(Items.CHAOTIC_KITESHIELD, Items.CHAOTIC_KITESHIELD_BROKEN),
}
