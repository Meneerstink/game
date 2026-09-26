package gg.rsmod.plugins.content.mechanics.death

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import java.io.File

/**
 * The numbers of the OSRS death system (gravestone timer and fees, Death's Office fee, Death's Coffer rate), read from
 * `data/cfg/deaths_domain.yml`. Every value there is the OSRS one with its source next to it; [OSRS] holds the same values
 * so the pure fee code and its tests do not depend on the working directory, and a test pins the file to [OSRS].
 */
data class DeathsDomainConfig(
    val graveDurationTicks: Int,
    val graveIdlePauseTicks: Int,
    val graveSlots: Int,
    val graveLootDistance: Int,
    val graveUnstackableKeep: Int,
    val angelCost: Int,
    val officeSlots: Int,
    val officeFreeBelow: Long,
    val officeFeePercent: Int,
    val cofferMinItemValue: Long,
    val cofferValuePercent: Int,
    val cofferMaxTotal: Int,
) {
    companion object {
        const val PATH = "./data/cfg/deaths_domain.yml"

        val OSRS =
            DeathsDomainConfig(
                graveDurationTicks = 1500,
                graveIdlePauseTicks = 17,
                graveSlots = 120,
                graveLootDistance = 7,
                graveUnstackableKeep = 28,
                angelCost = 200_000,
                officeSlots = 120,
                officeFreeBelow = 100_000L,
                officeFeePercent = 5,
                cofferMinItemValue = 10_000L,
                cofferValuePercent = 105,
                cofferMaxTotal = Int.MAX_VALUE,
            )

        /** The live values: the config file when the server runs from its root, else [OSRS] (unit tests). */
        val current: DeathsDomainConfig by lazy {
            val file = File(PATH)
            if (file.isFile) load(file) else OSRS
        }

        fun load(file: File): DeathsDomainConfig {
            val root = ObjectMapper(YAMLFactory()).readValue(file, Map::class.java)
            fun section(name: String) = root[name] as? Map<*, *> ?: error("$file: missing section '$name'")
            fun Map<*, *>.int(key: String) = (this[key] as? Number)?.toInt() ?: error("$file: missing number '$key'")
            fun Map<*, *>.long(key: String) = (this[key] as? Number)?.toLong() ?: error("$file: missing number '$key'")
            val grave = section("gravestone")
            val office = section("office")
            val coffer = section("coffer")
            return DeathsDomainConfig(
                graveDurationTicks = grave.int("duration_ticks"),
                graveIdlePauseTicks = grave.int("idle_pause_ticks"),
                graveSlots = grave.int("slots"),
                graveLootDistance = grave.int("loot_distance"),
                graveUnstackableKeep = grave.int("unstackable_keep"),
                angelCost = grave.int("angel_cost"),
                officeSlots = office.int("slots"),
                officeFreeBelow = office.long("free_below"),
                officeFeePercent = office.int("fee_percent"),
                cofferMinItemValue = coffer.long("min_item_value"),
                cofferValuePercent = coffer.int("value_percent"),
                cofferMaxTotal = coffer.int("max_total"),
            )
        }
    }
}
