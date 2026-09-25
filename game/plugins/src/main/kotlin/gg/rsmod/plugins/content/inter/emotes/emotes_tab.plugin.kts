package gg.rsmod.plugins.content.inter.emotes

for (emote in Emote.values) {
    on_button(interfaceId = EmotesTab.COMPONENT_ID, component = emote.component) {
        EmotesTab.performEmote(player, emote)
    }
}

// The Skill Cape emote is unlocked exactly while a skillcape or a max cape is worn (SkillcapeEmotes).
on_equip_to_slot(EquipmentType.CAPE.id) { SkillcapeEmotes.sync(player) }
on_unequip_from_slot(EquipmentType.CAPE.id) { SkillcapeEmotes.sync(player) }
on_login { SkillcapeEmotes.sync(player) }
