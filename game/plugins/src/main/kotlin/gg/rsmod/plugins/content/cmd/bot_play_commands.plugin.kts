package gg.rsmod.plugins.content.cmd

import gg.rsmod.game.model.priv.Privilege
import gg.rsmod.plugins.content.combat.getCombatTarget
import gg.rsmod.plugins.content.items.potion.Potion
import gg.rsmod.plugins.content.items.potion.Potions
import gg.rsmod.plugins.content.mechanics.prayer.Prayer
import gg.rsmod.plugins.content.mechanics.prayer.Prayers
import gg.rsmod.plugins.content.mechanics.prayer.RuinousPowers
import gg.rsmod.plugins.content.mechanics.prayer.RuinousPrayer

/**
 * N609 13-11 (owner, watching the A9 bot: 'doet hij wel range pot en super combat drinken' + 'maakt ook geen gebruik van ruinous
 * prayers'): test-bot commands that play like a real player - they go through the SAME code as a click, so drains, boosts,
 * refusals (no points, level, restrictions) and sounds are the real ones. Nothing is spawned or forced.
 *   ::drink <potion name part>   drinks the first matching potion dose in the inventory (Potions.drinkAt)
 *   ::pray <prayer name part>    switches that prayer ON in the player's current book (Ruinous or normal); 'off' = all off
 */
on_command("drink", Privilege.ADMIN_POWER) {
    val query = player.getCommandArgs().joinToString(" ").lowercase().trim()
    if (query.isEmpty()) { player.message("Usage: ::drink <potion name part>"); return@on_command }
    val defs = world.definitions
    val match = (0 until player.inventory.capacity).firstNotNullOfOrNull { slot ->
        val item = player.inventory[slot] ?: return@firstNotNullOfOrNull null
        val potion = Potion.values().firstOrNull { it.item == item.id } ?: return@firstNotNullOfOrNull null
        val name = defs.get(gg.rsmod.game.fs.def.ItemDef::class.java, item.id).name.lowercase()
        if (name.contains(query)) slot to potion else null
    }
    if (match == null) { player.message("drink: no '$query' potion in the inventory."); return@on_command }
    Potions.drinkAt(player, match.second, match.first)
}

/**
 * ::summon <familiar name part> - summons the familiar of the first matching pouch in the inventory through the real pouch path
 * (Familiar.summon: level, summoning points, restrictions, pouch used). Owner 13-11 23:5x: familiars are allowed in ToA; the test bot
 * may bring a steel titan for graphics/route tests (not for raid-time measurements).
 */
on_command("summon", Privilege.ADMIN_POWER) {
    val query = player.getCommandArgs().joinToString(" ").lowercase().replace('_', ' ').trim()
    if (query.isEmpty()) { player.message("Usage: ::summon <familiar name part>"); return@on_command }
    val data = gg.rsmod.plugins.content.skills.summoning.SummoningPouchData.values().firstOrNull { d ->
        d.name.lowercase().replace('_', ' ').contains(query) && player.inventory.contains(d.pouch)
    } ?: run { player.message("summon: no '$query' pouch in the inventory."); return@on_command }
    gg.rsmod.plugins.content.skills.summoning.Familiar.summon(player, data)
}

/**
 * ::botunlock ruinous - TEST ACCOUNTS (teamtest*): Desert Treasure II finished + the Ruinous powers book read, exactly the state a
 * player reaches through the quest and the book drop (check78 got the same on live 11-06). Owner 13-11 'TESTEN = ALLES MAG'.
 */
on_command("botunlock", Privilege.ADMIN_POWER) {
    if (player.getCommandArgs().firstOrNull()?.lowercase() != "ruinous") { player.message("Usage: ::botunlock ruinous"); return@on_command }
    if (!player.username.lowercase().startsWith("teamtest")) { player.message("botunlock: test accounts only (teamtest*)."); return@on_command }
    val dt2 = gg.rsmod.plugins.content.quests.foundation.DesertTreasureII
    dt2.setStage(player, dt2.completedValue)
    player.attr[gg.rsmod.plugins.content.mechanics.prayer.RuinousUnlocks.UNLOCKED] = true
    gg.rsmod.plugins.content.quests.QuestStatus.sync(player)
    player.message("botunlock: Desert Treasure II finished + Ruinous Powers unlocked.")
}

/**
 * ::botextras - owner 13-11 00:1x (steel titan erbij) + 14-11 00:4x (ring of suffering WEG, set-ring Ultor/Venator blijft): after a gear
 * preset the set's sharks go to the bank, 2 steel titan pouches (::summon steel titan) and 100 Steel of Legends scrolls (::famspec) go in
 * the backpack. The worn ring is NOT touched any more (Ultor / Venator from the set).
 */
on_command("botextras", Privilege.ADMIN_POWER) {
    // Owner 14-11 00:4x 'haal de ring of recoil maar weg doe gewoon ultor en venator is meer dps': the set's own ring stays (no ring swap).
    // Owner 14-11 00:3x 'waarom zit er eten in de inventory?': the gear sets bring 10 sharks for hand play; the bot heals with ::restore,
    // so the sharks go to the bank and make room for the pouches + scrolls (fx35: the pouches silently did not fit).
    val items = gg.rsmod.plugins.api.cfg.Items
    val sharks = player.inventory.getItemCount(items.SHARK)
    if (sharks > 0 && player.bank.add(items.SHARK, sharks).hasSucceeded()) player.inventory.remove(items.SHARK, sharks)
    val pouches = (2 - player.inventory.getItemCount(items.STEEL_TITAN_POUCH)).coerceAtLeast(0)
    val gotPouches = pouches == 0 || player.inventory.add(items.STEEL_TITAN_POUCH, pouches).hasSucceeded()
    val scrolls = (100 - player.inventory.getItemCount(items.STEEL_OF_LEGENDS_SCROLL)).coerceAtLeast(0)
    val gotScrolls = scrolls == 0 || player.inventory.add(items.STEEL_OF_LEGENDS_SCROLL, scrolls).hasSucceeded()
    player.message("botextras: sharks to bank ($sharks); pouches ${if (gotPouches) "OK" else "NO ROOM"}, " +
        "Steel of Legends scrolls ${if (gotScrolls) "OK" else "NO ROOM"}.")
}

/**
 * ::spec - owner 14-11 00:3x ('specials gebruiken continue'): the special attack bar click (attack_tab.plugin.kts component 4) but always
 * ON (a click toggles): restrictions, instant specials (executeInstant) and the energy cost stay the real ones.
 */
on_command("spec", Privilege.ADMIN_POWER) {
    if (gg.rsmod.plugins.content.mechanics.restrictions.ActivityRestrictions.refuse(
            player, gg.rsmod.plugins.content.mechanics.restrictions.RestrictedAction.SPECIAL_ATTACK)) return@on_command
    if (gg.rsmod.plugins.content.combat.specialattack.SpecialAttacks.executeInstant(player)) return@on_command
    player.setVarp(gg.rsmod.plugins.content.inter.attack.AttackTab.SPECIAL_ATTACK_VARP, 1)
}

/** ::famspec - the familiar's scroll special on the player's current npc target (SummoningSpecialMoves.castOnNpc: scroll + points). */
on_command("famspec", Privilege.ADMIN_POWER) {
    val target = player.getCombatTarget() as? gg.rsmod.game.model.entity.Npc
        ?: run { player.message("famspec: no npc target."); return@on_command }
    gg.rsmod.plugins.content.skills.summoning.SummoningSpecialMoves.castOnNpc(player, target)
}

/** ::botstate - one line the test scripts read (command port): combat levels current/max, prayer, hp, active prayers, quiver charges. */
on_command("botstate", Privilege.ADMIN_POWER) {
    val s = player.skills
    val lv = listOf("att" to 0, "def" to 1, "str" to 2, "hp" to 3, "rng" to 4, "pray" to 5, "mag" to 6)
        .joinToString(" ") { (n, i) -> "$n=${s.getCurrentLevel(i)}/${s.getMaxLevel(i)}" }
    val active = if (RuinousPowers.isBook(player)) RuinousPowers.active(player).joinToString(",") { it.named }
        else Prayer.values().filter { Prayers.isActive(player, it) }.joinToString(",") { it.name }
    val quiver = player.equipment[1]?.let { "${it.id}:${gg.rsmod.plugins.content.items.osrs.DizanasQuiver.charges(it)}" } ?: "-"
    val ring = player.equipment[12]?.let { "${it.id}:${gg.rsmod.plugins.content.items.osrs.RingOfSuffering.charges(it)}" } ?: "-"
    val familiar = player.attr[gg.rsmod.plugins.content.skills.summoning.FAMILIAR_ATTR]?.get()?.id ?: -1
    player.message("botstate $lv prayers=[$active] cape=$quiver ring=$ring familiar=$familiar style=${player.getVarp(gg.rsmod.plugins.content.inter.attack.AttackTab.ATTACK_STYLE_VARP)} " +
        "spec=${gg.rsmod.plugins.content.inter.attack.AttackTab.getEnergy(player)}% specOn=${gg.rsmod.plugins.content.inter.attack.AttackTab.isSpecialEnabled(player)} " +
        "scrolls=${player.inventory.getItemCount(gg.rsmod.plugins.api.cfg.Items.STEEL_OF_LEGENDS_SCROLL)} sharks=${player.inventory.getItemCount(gg.rsmod.plugins.api.cfg.Items.SHARK)} book=${if (RuinousPowers.isBook(player)) "ruinous" else "other"}")
}

on_command("pray", Privilege.ADMIN_POWER) {
    val query = player.getCommandArgs().joinToString(" ").lowercase().replace('_', ' ').trim()
    if (query.isEmpty()) { player.message("Usage: ::pray <prayer name part> | off"); return@on_command }
    if (RuinousPowers.isBook(player)) {
        if (query == "off") { RuinousPowers.active(player).forEach { RuinousPowers.toggle(player, it) }; return@on_command }
        val prayer = RuinousPrayer.values.firstOrNull { it.named.lowercase().contains(query) }
            ?: run { player.message("pray: no Ruinous prayer '$query'."); return@on_command }
        if (!RuinousPowers.isActive(player, prayer)) RuinousPowers.toggle(player, prayer)
        return@on_command
    }
    val prayer = Prayer.values().firstOrNull { it.name.lowercase().replace('_', ' ').contains(query) }
        ?: run { player.message("pray: no prayer '$query' (normal book; Ancient Curses are not supported here)."); return@on_command }
    if (!Prayers.isActive(player, prayer)) player.queue { Prayers.toggle(this, prayer) }
}
