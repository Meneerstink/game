package gg.rsmod.plugins.content.skills.firemaking

import gg.rsmod.plugins.api.cfg.Items

enum class FiremakingData(
    val raw: Int,
    val levelRequired: Int,
    val experience: Double,
    // Burn duration in game ticks. Null = no cross-checked donor value (not guessed), falls
    // back to the legacy flat random range. Sourced from Novite's Firemaking.Fire.life (already
    // in 600ms ticks matching this engine) and cross-checked against Void's firemaking.tables.toml
    // life values, which are exactly 2x (Void uses a finer tick rate) - independent agreement.
    val life: Int? = null,
) {
    NORMAL_LOGS(raw = Items.LOGS, levelRequired = 1, experience = 40.0, life = 30),
    ACHEY_LOGS(raw = Items.ACHEY_TREE_LOGS, levelRequired = 1, experience = 40.0, life = 30),
    OAK_LOGS(raw = Items.OAK_LOGS, levelRequired = 15, experience = 60.0, life = 45),
    WILLOW_LOGS(raw = Items.WILLOW_LOGS, levelRequired = 30, experience = 90.0, life = 45),
    TEAK_LOGS(raw = Items.TEAK_LOGS, levelRequired = 35, experience = 105.0, life = 45),
    ARCTIC_PINE_LOGS(raw = Items.ARCTIC_PINE_LOGS, levelRequired = 42, experience = 125.0, life = 50),
    MAPLE_LOGS(raw = Items.MAPLE_LOGS, levelRequired = 45, experience = 135.0, life = 50),
    MAHOGANY_LOGS(raw = Items.MAHOGANY_LOGS, levelRequired = 50, experience = 157.5, life = 70),
    EUCALYPTUS_LOGS(raw = Items.EUCALYPTUS_LOGS, levelRequired = 58, experience = 193.5, life = 70),
    YEW_LOGS(raw = Items.YEW_LOGS, levelRequired = 60, experience = 202.5, life = 80),
    MAGIC_LOGS(raw = Items.MAGIC_LOGS, levelRequired = 75, experience = 303.8, life = 90),
    CURLY_ROOT(raw = Items.CURLY_ROOT, levelRequired = 75, experience = 161.6),
    CURSED_MAGIC_LOGS(raw = Items.CURSED_MAGIC_LOGS, levelRequired = 82, experience = 303.8, life = 100),
    ;

    companion object {
        val values = enumValues<FiremakingData>()
        val firemakingDefinitions = values.associateBy { it.raw }
    }
}
