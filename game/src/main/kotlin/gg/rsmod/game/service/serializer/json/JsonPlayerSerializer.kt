package gg.rsmod.game.service.serializer.json

import com.fasterxml.jackson.annotation.JsonProperty
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import de.mkammerer.argon2.Argon2Factory
import gg.rsmod.game.Server
import gg.rsmod.game.model.*
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.DOUBLE_ATTRIBUTES
import gg.rsmod.game.model.attr.LONG_ATTRIBUTES
import gg.rsmod.game.model.attr.SKULL_ICON_ATTR
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.interf.DisplayMode
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.priv.Privilege
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.game.service.login.PasswordPolicy
import gg.rsmod.game.service.serializer.PlayerLoadResult
import gg.rsmod.game.service.serializer.PlayerSerializerService
import gg.rsmod.net.codec.login.LoginDecoder
import gg.rsmod.net.codec.login.LoginRequest
import gg.rsmod.util.ServerProperties
import mu.KLogging
import java.io.BufferedReader
import java.io.FileReader
import java.io.Writer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * A [PlayerSerializerService] implementation that decodes and encodes player
 * data in JSON.
 *
 * @author Tom <rspsmods@gmail.com>
 */
class JsonPlayerSerializer : PlayerSerializerService() {
    private lateinit var path: Path

    override fun initSerializer(
        server: Server,
        world: World,
        serviceProperties: ServerProperties,
    ) {
        path = Paths.get(serviceProperties.getOrDefault("path", "./data/saves/"))
        if (!Files.exists(path)) {
            Files.createDirectory(path)
            logger.info("Path does not exist: $path, creating directory...")
        }
    }

    override fun loadClientData(
        client: Client,
        request: LoginRequest,
    ): PlayerLoadResult {
        client.loginUsername = client.loginUsername.lowercase()

        if (!characterExists(client.loginUsername)) {
            // Audit S-06: a reconnect carries no password - it used to create an account with an
            // empty password hash for any unknown name.
            if (request.reconnecting) {
                return PlayerLoadResult.INVALID_RECONNECTION
            }
            if (!PasswordPolicy.isAcceptable(client.loginUsername, request.password)) {
                return PlayerLoadResult.INVALID_NEW_PASSWORD
            }
            if (!registrationGate(request)) {
                return PlayerLoadResult.REGISTRATION_LIMIT
            }
            configureNewPlayer(client, request)
            client.uid = PlayerUID(client.loginUsername)
            saveClientData(client)
            return PlayerLoadResult.NEW_ACCOUNT
        }
        try {
            val world = client.world
            val save = path.resolve(client.loginUsername)
            val reader = BufferedReader(FileReader(save.toFile()), 8192)
            val json = Gson()
            val data = json.fromJson(reader, JsonPlayerSaveData::class.java)
            reader.close()
            if (!request.reconnecting) {
                /*
                 * If the [request] is not a [LoginRequest.reconnecting] request, we have to
                 * verify the password is correct.
                 */

                if (!Argon2Factory.create().verify(data.passwordHash, request.password.toCharArray())) {
                    return PlayerLoadResult.INVALID_CREDENTIALS
                }
            } else {
                /*
                 * If the [request] is a [LoginRequest.reconnecting] request, we
                 * verify that the login xteas match from our previous session.
                 */
                if (!Arrays.equals(data.previousXteas, request.xteaKeys)) {
                    return PlayerLoadResult.INVALID_RECONNECTION
                }
            }
            client.loginUsername = data.username
            client.uid = PlayerUID(data.username)
            client.username = data.displayName
            client.passwordHash = data.passwordHash
            client.tile = Tile(data.x, data.z, data.height)
            client.privilege = world.privileges.get(data.privilege) ?: Privilege.DEFAULT
            client.runEnergy = data.runEnergy
            client.interfaces.displayMode =
                DisplayMode.values.firstOrNull { it.id == data.displayMode } ?: DisplayMode.FIXED
            client.appearance =
                Appearance(
                    data.appearance.looks,
                    data.appearance.colors,
                    Gender.values.firstOrNull { it.id == data.appearance.gender } ?: Gender.MALE,
                )
            data.skills.forEach { skill ->
                client.skills.setXp(skill.skill, skill.xp)
                client.skills.setCurrentLevel(skill.skill, skill.lvl)
                client.skills.setLastLevel(skill.skill, skill.lastLvl)
            }
            data.itemContainers.forEach {
                val key = world.plugins.containerKeys.firstOrNull { other -> other.name == it.name }
                if (key == null) {
                    logger.error { "Container was found in serialized data, but is not registered to our World. [key=${it.name}]" }
                    return@forEach
                }
                val container =
                    if (client.containers.containsKey(key)) {
                        client.containers[key]
                    } else {
                        client.containers[key] = ItemContainer(client.world.definitions, key)
                        client.containers[key]
                    }!!
                it.items.forEach { slot, item ->
                    container[slot] = item
                }
            }

            /**
             * The value.toInt() below loses any information that may have been stored as Double or Long.
             * Therefore, these attributes were stored as sub-attributes to their respective super-attributes.
             */
            val longAttributes = data.attributes[LONG_ATTRIBUTES.persistenceKey!!] as? Map<String, Double>
            val doubleAttributes = data.attributes[DOUBLE_ATTRIBUTES.persistenceKey!!] as? Map<String, Double>
            data.attributes
                .filter {
                    it.key != LONG_ATTRIBUTES.persistenceKey &&
                        it.key != DOUBLE_ATTRIBUTES.persistenceKey
                }.forEach { (key, value) ->
                    val attribute = AttributeKey<Any>(key)
                    client.attr[attribute] = if (value is Double) value.toInt() else value
                }
            longAttributes?.forEach { (key, value) ->
                val attribute = AttributeKey<Long>(key)
                client.attr[attribute] = value.toLong()
            }
            doubleAttributes?.forEach { (key, value) ->
                val attribute = AttributeKey<Double>(key)
                client.attr[attribute] = value
            }

            data.timers.forEach { timer ->
                var time = timer.timeLeft
                if (timer.tickOffline) {
                    val elapsed = System.currentTimeMillis() - timer.currentMs
                    val ticks = (elapsed / client.world.gameContext.cycleTime).toInt()
                    time -= ticks
                }
                val key =
                    TimerKey(
                        persistenceKey = timer.identifier,
                        tickOffline = timer.tickOffline,
                        tickForward = timer.tickForward,
                        resetOnDeath = timer.resetOnDeath,
                        removeOnZero = timer.removeOnZero,
                    )
                client.timers[key] = max(0, time)
            }
            data.varps.forEach { varp ->
                client.varps.setState(varp.id, varp.state)
            }
            client.friends = data.friends?.toMutableList() ?: mutableListOf()
            client.ignoredPlayers = data.ignoredPlayers?.toMutableList() ?: mutableListOf()
            client.publicFilterSetting =
                ChatFilterType.getSettingById(data.publicFilterSetting) ?: ChatFilterType.getSettingById(0)!!
            client.privateFilterSetting =
                ChatFilterType.getSettingById(data.privateFilterSetting) ?: ChatFilterType.getSettingById(0)!!
            client.tradeFilterSetting =
                ChatFilterType.getSettingById(data.tradeFilterSetting) ?: ChatFilterType.getSettingById(0)!!
            // -1 matches SkullIcon.NONE.id; kept as a raw literal here since that
            // enum lives in the plugins module, which the core module can't depend on.
            client.skullIcon = client.attr[SKULL_ICON_ATTR] ?: -1
            return PlayerLoadResult.LOAD_ACCOUNT
        } catch (e: Exception) {
            logger.error(e) { "Error when loading player: ${request.username}" }
            return PlayerLoadResult.MALFORMED
        }
    }

    override fun saveClientData(client: Client): Boolean {
        client.loginUsername = client.loginUsername.lowercase() // Convert username to lowercase
        // Audit S-10: one save of a given account at a time (autosave, logout save, shutdown hook
        // and ::changepass could overlap), so the snapshot and the file move stay in order.
        return synchronized(saveLock(client.loginUsername)) { writeSave(client) }
    }

    private fun writeSave(client: Client): Boolean {
        client.attr[SKULL_ICON_ATTR] = client.skullIcon
        val data =
            JsonPlayerSaveData(
                username = client.loginUsername,
                passwordHash = client.passwordHash,
                privilege = client.privilege.id,
                displayName = client.username, // this order didnt change tho hmm
                x = client.tile.x,
                z = client.tile.z,
                height = client.tile.height,
                previousXteas = client.currentXteaKeys,
                displayMode = client.interfaces.displayMode.id,
                runEnergy = client.runEnergy,
                appearance = client.getPersistentAppearance(),
                attributes = client.attr.toPersistentMap(),
                timers = client.timers.toPersistentTimers(),
                skills = client.getPersistentSkills(),
                itemContainers = client.getPersistentContainers(),
                varps = client.varps.getAll().filter { it.state != 0 },
                friends = client.friends,
                ignoredPlayers = client.ignoredPlayers,
                publicFilterSetting = client.publicFilterSetting.settingId,
                privateFilterSetting = client.privateFilterSetting.settingId,
                tradeFilterSetting = client.tradeFilterSetting.settingId,
            )
        /*
         * Write to a sibling temp file and move it over the real save atomically. A JVM crash,
         * native OOM or disk error mid-write used to leave a truncated save behind, which loads as
         * MALFORMED and locks the account out with all progress gone.
         */
        val json = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
        writeAtomically(path.resolve(client.loginUsername)) { writer -> json.toJson(data, writer) }
        return true
    }

    private fun saveLock(username: String): Any = saveLocks.computeIfAbsent(username.lowercase()) { Any() }

    private fun Client.getPersistentContainers(): List<PersistentContainer> {
        val persistent = mutableListOf<PersistentContainer>()

        containers.forEach { (key, container) ->
            if (!container.isEmpty) {
                persistent.add(PersistentContainer(key.name, container.toMap()))
            }
        }

        return persistent
    }

    private fun Client.getPersistentSkills(): List<PersistentSkill> {
        val skills = mutableListOf<PersistentSkill>()

        for (i in 0 until this.skills.maxSkills) {
            val xp = this.skills.getCurrentXp(i)
            val lvl = this.skills.getCurrentLevel(i)
            val lastLevel = this.skills.getLastLevel(i)

            skills.add(PersistentSkill(skill = i, xp = xp, lvl = lvl, lastLvl = lastLevel))
        }

        return skills
    }

    private fun Client.getPersistentAppearance(): PersistentAppearance =
        PersistentAppearance(appearance.gender.id, appearance.looks, appearance.colors)

    /**
     * Checks to see if the player exists in the saves folder
     *
     * @param username The username of the player
     *
     * @return If the player exists in the server's save files.
     */
    fun characterExists(username: String): Boolean {
        // A blank name resolved to the saves directory itself and "existed" (a blank friend was added that way).
        if (username.isBlank()) return false
        // Audit S-13: names from report abuse, friends/ignore lists and friends chat reach this
        // unvalidated; "../../game.yml" probed for files outside the saves directory. Only a name
        // the login decoder would accept can have a save.
        if (!isValidSaveName(username)) return false
        val save = path.resolve(username.trim())

        return Files.isRegularFile(save)
    }

    data class PersistentAppearance(
        @JsonProperty("gender") val gender: Int,
        @JsonProperty("looks") val looks: IntArray,
        @JsonProperty("colors") val colors: IntArray,
    )

    data class PersistentContainer(
        @JsonProperty("name") val name: String,
        @JsonProperty("items") val items: Map<Int, Item>,
    )

    data class PersistentSkill(
        @JsonProperty("skill") val skill: Int,
        @JsonProperty("xp") val xp: Double,
        @JsonProperty("lvl") val lvl: Int,
        @JsonProperty("lastLvl") val lastLvl: Int,
    )

    /**
     * Audit S-10: per-account save locks. Entries are one small object per account that saved
     * during this run.
     */
    private val saveLocks = ConcurrentHashMap<String, Any>()

    companion object : KLogging() {
        /** Audit S-13: the login decoder's own username rule, so no other name can reach the file system. */
        fun isValidSaveName(username: String): Boolean = LoginDecoder.isValidUsername(username.trim())

        /**
         * Audit S-10: writes [save] through a temp file of its own and moves it over [save]
         * atomically. Every write gets a unique temp file: all saves of one account used to share
         * `<name>.tmp`, so two overlapping saves (shutdown hook vs autosave/logout) could interleave
         * into a mixed or cut-off file that then loaded as MALFORMED and locked the account.
         */
        fun writeAtomically(
            save: Path,
            content: (Writer) -> Unit,
        ) {
            val directory = save.toAbsolutePath().parent
            val temp = Files.createTempFile(directory, save.fileName.toString() + ".", ".tmp")
            try {
                Files.newBufferedWriter(temp).use { writer -> content(writer) }
                try {
                    Files.move(temp, save, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                } catch (e: java.nio.file.AtomicMoveNotSupportedException) {
                    Files.move(temp, save, StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                Files.deleteIfExists(temp)
            }
        }
    }
}
