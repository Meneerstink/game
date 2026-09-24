package gg.rsmod.plugins.content.npcs.bankers

import gg.rsmod.plugins.content.inter.bank.openBank

val bankers =
    listOf(
        Npcs.BANKER_CLASSIC_MALE_PURPLE_44, // Various Regions Male Bankers Classic Models
        Npcs.BANKER_CLASSIC_FEMALE_PURPLE_45, // Various Regions Female Bankers Classic Models
        Npcs.BANKER_CLASSIC_MALE_GREY_494, // Various Regions Male Bankers Classic Models
        Npcs.BANKER_CLASSIC_FEMALE_GREY_495, // Various Regions Female Bankers Classic Models
        Npcs.BANKER_CLASSIC_FEMALE_GREY_497, // Various Regions Female Bankers Classic Models
        Npcs.BANKER_CLASSIC_FEMALE_PURPLE_2759, // Varrock East Female Bankers Classic Models
        Npcs.BANKER_CLASSIC_MALE_GREY_6200, // Falador Bankers All Share the Same ID Classic Models
        Npcs.BANKER_LUMBRIDGE_FEMALE_4907, // Lumbridge Castle Banker, Updated Model
        Npcs.BANKER_LATEST_MALE_HANDSBEHIND_3416, // Grand Exchange Updated Models
        Npcs.BANKER_LATEST_MALE_HANDSBEHIND_3418, // Grand Exchange Updated Models
        Npcs.BANKER_LATEST_FEMALE_HANDSBEHIND_3293, // Grand Exchange Updated Models
        Npcs.BANKER_LATEST_FEMALE_BLOND_HANDSBEHIND_2718, // Grand Exchange Updated Models
        Npcs.BANKER_LATEST_FEMALE_HANDSFRONT_4458, // Female Bankers Updated Models
        Npcs.BANKER_LATEST_FEMALE_HANDSFRONT_4459, // Female Bankers Updated Models
        Npcs.BANKER_LATEST_MALE_HANDSFRONT_4456, // Male Bankers Updated Models
        Npcs.BANKER_LATEST_MALE_HANDSFRONT_4457, // Male Bankers Updated Models
        Npcs.BANKER_FAIRY_BLOND_498, // Zanaris City
        Npcs.BANKER_FAIRY_BRUNETTE_909, // Zanaris City
        Npcs.BANKER_ELF_MALE_2354, // Lletya Elf Village
        Npcs.BANKER_ELF_FEMALE_2355, // Lletya Elf Village
        Npcs.BANKER_SPECIAL_FEMALE_BLOND_2570,
        Npcs.BANKER_SPECIAL_FEMALE_BRUNETTE_2569,
        Npcs.BANKER_SPECIAL_MALE_2568,
        Npcs.BANKER_MOBILISING_ARMIES_8948, // Mobilising Armies
        Npcs.BANKER_SPECIAL_1360, // Mage Arena Banker?
        Npcs.BANKER_NARDAH_FEMALE_5260, // Nardah City Bankers
        Npcs.BANKER_NARDAH_MALE_5258, // Nardah City Bankers
        Npcs.BANKER_PIRATE_MALE_HAT_3199, // Mos Le'Harmless
        Npcs.BANKER_PIRATE_MALE_EYEPATCH_3198, // Mos Le'Harmless
        Npcs.BANKER_CAVE_GOBLIN_MALE_YOUNG_5776, // Dorgesh-Kaan
        Npcs.BANKER_CAVE_GOBLIN_MALE_OLD_5777, // Dorgesh-Kaan
        Npcs.BANKER_OGRESS_7049, // Oo'glog City
        Npcs.BANKER_OGRESS_7050, // Oo'glog City
        Npcs.BANKER_PORTABLE_7605,
        Npcs.BANKER_CLASSIC_MALE_GREY_1036,
    )

bankers.forEach {
    on_npc_option(it, option = "Bank", lineOfSightDistance = 2) {
        player.openBank()
    }
    on_npc_option(it, option = "talk-to", lineOfSightDistance = 2) {
        player.queue { BankerDialogue.chat(this) }
    }
}

/*
 * Cornelius (Burgh de Rott) has only Talk-to in the cache: the bank is reached through him, as the booths there have no option.
 */
listOf(Npcs.CORNELIUS, Npcs.CORNELIUS_3569).forEach { cornelius ->
    on_npc_option(cornelius, option = "talk-to", lineOfSightDistance = 2) {
        player.queue { BankerDialogue.chat(this) }
    }
}
