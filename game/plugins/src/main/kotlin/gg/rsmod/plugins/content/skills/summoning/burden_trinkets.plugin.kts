package gg.rsmod.plugins.content.skills.summoning

import gg.rsmod.plugins.api.ext.getInteractingItem
import gg.rsmod.plugins.api.ext.getInteractingNpc
import gg.rsmod.plugins.api.ext.itemMessageBox
import gg.rsmod.plugins.api.ext.message
import gg.rsmod.plugins.api.ext.options

/**
 * N423 Beast of burden trinkets ([BurdenTrinkets]): Bind / Check / Unbind on the trinket, and the trinket used on your pet (= Bind).
 * The fifth inventory op is empty in the cache (no Drop, no Destroy); a drop packet from a modified client is refused here too.
 */

// The pet tables are read by pets.plugin.kts; plugin scripts load in no fixed order, so make sure they are here.
if (Pets.variants.isEmpty()) Pets.load()

fun bindDialogue(
    player: gg.rsmod.game.model.entity.Player,
    tier: BurdenTrinkets.Tier,
) {
    when (val check = BurdenTrinkets.canBind(player, tier)) {
        is BurdenTrinkets.BindCheck.Refused -> player.message(check.message)
        is BurdenTrinkets.BindCheck.Ok ->
            player.queue {
                itemMessageBox(BurdenTrinkets.bindQuestion(tier, check), tier.item)
                if (options("Yes, bind it to my ${check.name}.", "No, not now.") == 1) BurdenTrinkets.bind(player, tier)
            }
    }
}

BurdenTrinkets.Tier.values().forEach { tier ->
    on_item_option(item = tier.item, option = "bind") {
        bindDialogue(player, tier)
    }
    on_item_option(item = tier.item, option = "check") {
        player.message(BurdenTrinkets.checkText(player, tier))
    }
    on_item_option(item = tier.item, option = "unbind") {
        val targets = BurdenTrinkets.unbindTargets(player, tier)
        when {
            targets.isEmpty() -> player.message("Your ${tier.trinketName} is not bound to a pet.")
            targets.size == 1 -> BurdenTrinkets.unbind(player, tier, targets[0])
            else ->
                player.queue {
                    val shown = targets.take(5)
                    val choice = options(*shown.map { BurdenTrinkets.upgradedName(BurdenTrinkets.petName(player, it)) }.toTypedArray(), title = "Unbind it from which pet?")
                    shown.getOrNull(choice - 1)?.let { BurdenTrinkets.unbind(player, tier, it) }
                }
        }
    }
    can_drop_item(item = tier.item) {
        player.message("You can't drop your ${tier.trinketName}; it is yours to keep.")
        false
    }
    // Used on a pet: the follower is the only pet a trinket binds to.
    (Pets.variants.map { it.npc } + Pets.upgradedNpcs).distinct().forEach { npcId ->
        // OpNpcT sets INTERACTING_ITEM (the stack used), not INTERACTING_ITEM_ID - that one can still hold an earlier click's item.
        on_item_on_npc(item = tier.item, npc = npcId) {
            if (Pets.current(player) !== player.getInteractingNpc()) {
                player.message("This isn't your pet.")
            } else {
                bindDialogue(player, BurdenTrinkets.tier(player.getInteractingItem().id) ?: tier)
            }
        }
    }
}

on_login {
    BurdenTrinkets.recordOwned(player)
    // N436: pet items carried over from before the Upgraded item copies (or a binding that no longer counts) show the right name.
    Pets.reconcileItems(player)
}

// N423 deel 5: a boss kill rolls the three smaller trinkets (BurdenTrinketDrops); a breach npc rolls per contributor in DeadmanBreach.
on_npc_killed { killer, npc ->
    if (gg.rsmod.plugins.content.mechanics.pvp.breach.DeadmanBreach.isBreachNpc(npc)) return@on_npc_killed
    BurdenTrinketDrops.onKill(killer, npc.id, npc.tile)
}

on_world_init {
    // An Upgraded copy examines as its pet (its own def has no examine text) ...
    Pets.upgradedNpcs.forEach { upgraded ->
        val base = world.definitions.getNullable(gg.rsmod.game.fs.def.NpcDef::class.java, Pets.baseNpc(upgraded))
        world.definitions.getNullable(gg.rsmod.game.fs.def.NpcDef::class.java, upgraded)?.let { it.examine = it.examine ?: base?.examine }
    }
    // ... plus the purple line, for every player who examines it (design v6: no slot count).
    // N436: the "Upgraded" pet item examines as its pet, plus the purple line naming the trinket the player has bound to it.
    Pets.upgradedItems.forEach { upgraded ->
        val base = world.definitions.getNullable(gg.rsmod.game.fs.def.ItemDef::class.java, Pets.baseItemOf(upgraded))
        world.definitions.getNullable(gg.rsmod.game.fs.def.ItemDef::class.java, upgraded)?.let { it.examine = it.examine ?: base?.examine }
    }
    world.itemExamineExtra = { player, item ->
        val variant = if (Pets.isUpgradedItem(item)) Pets.variantOfItem(item) else null
        val tier = variant?.let { BurdenTrinkets.upgradeOf(player, it.pet) }
        tier?.let { "<col=${BurdenTrinkets.PURPLE}>This pet has been upgraded with a ${it.trinketName}.</col>" }
    }
    world.npcExamineExtra = { npc ->
        val owner = npc.owner
        val tier = if (Pets.isUpgradedNpc(npc.id) && owner != null && Pets.current(owner) === npc) BurdenTrinkets.followerTier(owner) else null
        tier?.let { "<col=${BurdenTrinkets.PURPLE}>This pet has been upgraded with a ${it.trinketName}.</col>" }
    }
}
