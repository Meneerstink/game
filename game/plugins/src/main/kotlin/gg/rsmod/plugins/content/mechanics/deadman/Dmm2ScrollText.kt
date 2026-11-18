package gg.rsmod.plugins.content.mechanics.deadman

/**
 * Claude 18-11 (testclient: reading the Armageddon teleport scroll asked 'Yes./No.' and said nothing; weapon scrolls were consumed on Read):
 * the Nigel reward items' dialogue, verbatim from the OSRS Wiki transcripts (Transcript:Armageddon_teleport_scroll,
 * Transcript:Annihilation_teleport_scroll, Transcript:Armageddon_weapon_scroll, Transcript:Annihilation_weapon_scroll, Transcript:Nigel
 * 'Using Armageddon cape fabric on Nigel' / 'Using Annihilation weapon scroll on Nigel'). Wiki {{mes|colour=green}} = CSS green.
 */
object Dmm2ScrollText {
    private const val RED = "<col=6f0000>"
    private const val GREEN = "<col=007f00>"

    class Teleport(val ask: String, val option: String, val learned: String, val unlocked: String, val already: String?)

    /** Read on a teleport scroll: confirm, consume, learn. [Teleport.already] is null where the wiki has no transcript (Annihilation). */
    fun teleport(reward: NigelRewardStore.Reward): Teleport? = when (reward) {
        NigelRewardStore.Reward.ARMAGEDDON_TELEPORT -> Teleport(
            "This scroll will unlock a new Home Teleport animation from Deadman: Armageddon. Would you like to read the scroll?",
            "Unlock the Deadman: Armageddon home teleport.",
            "You feel empowered by the scroll and learn the ${RED}Deadman: Armageddon Home Teleport</col>. This can be toggled by " +
                "right-clicking on the Home Teleport in your spellbook.",
            "${GREEN}You've unlocked the Deadman: Armageddon Home Teleport! This can be toggled by right-clicking on the Home Teleport in " +
                "your Spellbook.</col>",
            "This scroll contains an incantation which unlocks the ${RED}Deadman: Armageddon home teleport</col> animation. However you " +
                "have already unlocked this animation.",
        )
        NigelRewardStore.Reward.ANNIHILATION_TELEPORT -> Teleport(
            "This scroll will unlock a new Home Teleport animation from Deadman: Annihilation. Would you like to read the scroll?",
            "Unlock the Deadman: Annihilation home teleport.",
            "You feel empowered by the scroll and learn the ${RED}Deadman: Annihilation Home Teleport</col>. This can be toggled by " +
                "right-clicking on the Home Teleport in your spellbook.",
            "${GREEN}You've unlocked the Deadman: Annihilation Home Teleport! This can be toggled by right-clicking on the Home Teleport in " +
                "your Spellbook.</col>",
            null,
        )
        else -> null
    }

    /** Read on a weapon scroll only explains it; the scroll is used on Nigel. */
    fun weaponRead(reward: NigelRewardStore.Reward): String? = when (reward) {
        NigelRewardStore.Reward.ARMAGEDDON_WEAPONS ->
            "You can use this on Nigel in Lumbridge to unlock the Deadman: Armageddon ornament theme for your weapons."
        NigelRewardStore.Reward.ANNIHILATION_WEAPONS ->
            "You can use this on Nigel in Lumbridge to unlock the Deadman: Annihilation ornament theme for your weapons and armour."
        else -> null
    }

    /**
     * Use on Nigel: the option under 'This will consume the scroll.'. Fabric + Annihilation scroll are transcribed; the Armageddon weapon
     * scroll follows the same transcribed pattern ('Unlock Deadman: <theme> weapon ornaments.'). Teleport scrolls are read, not used on Nigel.
     */
    fun nigelOption(reward: NigelRewardStore.Reward): String? = when (reward) {
        NigelRewardStore.Reward.CAPE_FABRIC -> "Unlock Deadman: Armageddon cape ornaments."
        NigelRewardStore.Reward.ANNIHILATION_WEAPONS -> "Unlock Deadman: Annihilation weapon ornaments."
        NigelRewardStore.Reward.ARMAGEDDON_WEAPONS -> "Unlock Deadman: Armageddon weapon ornaments."
        else -> null
    }

    /** Nigel's answer after the unlock; none transcribed for the Armageddon weapon scroll, so none is invented. */
    fun nigelReply(reward: NigelRewardStore.Reward): String? = when (reward) {
        NigelRewardStore.Reward.CAPE_FABRIC -> "Bring me an imbued Zamorak, Guthix or Saradomin Cape and I'll give it a trim."
        NigelRewardStore.Reward.ANNIHILATION_WEAPONS ->
            "Bring me a Toxic Staff of the Dead, Bow of Faerdhinen (c) or Crystal armour and I'll give it a trim."
        else -> null
    }
}
