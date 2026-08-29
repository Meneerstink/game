package gg.rsmod.game.service.serializer.json

import com.fasterxml.jackson.annotation.JsonProperty
import com.google.gson.GsonBuilder
import de.mkammerer.argon2.Argon2Factory
import gg.rsmod.game.Server
import gg.rsmod.game.model.*
import gg.rsmod.game.model.attr.AttributeKey
import gg.rsmod.game.model.attr.DOUBLE_ATTRIBUTES
import gg.rsmod.game.model.attr.LONG_ATTRIBUTES
import gg.rsmod.game.model.container.ItemContainer
import gg.rsmod.game.model.entity.Client
import gg.rsmod.game.model.interf.DisplayMode
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.priv.Privilege
import gg.rsmod.game.model.timer.TimerKey
import gg.rsmod.game.service.GameService
import gg.rsmod.game.service.serializer.PlayerLoadResult
import gg.rsmod.game.service.serializer.PlayerSerializerService
import gg.rsmod.net.codec.login.LoginRequest
import gg.rsmod.util.ServerProperties
import mu.KLogging
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * A [PlayerSerializerService] implementation that decodes and encodes player
 * data in JSON.
 *
 * @author Tom <rspsmods@gmail.com>
 */
class JsonPlayerSerializer : PlayerSerializerService() {
    private lateinit var world: World
    private lateinit var store: JsonPlayerSaveStore

    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val accountLocks = ConcurrentHashMap<String, Any>()
    private val saveExecutor =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "player-save-writer").apply { isDaemon = true }
        }
    private val autosaveScheduler =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "player-autosave-scheduler").apply { isDaemon = true }
        }

    private var autosaveIntervalSeconds = DEFAULT_AUTOSAVE_INTERVAL_SECONDS
    private var autosaveFuture: ScheduledFuture<*>? = null

    /**
     * Once shutdown begins no new autosave snapshot may be submitted to the
     * writer. A game-thread job may otherwise outlive the scheduler that
     * created it and submit to an already terminated executor.
     */
    @Volatile
    private var acceptingSaves = true

    override fun initSerializer(
        server: Server,
        world: World,
        serviceProperties: ServerProperties,
    ) {
        this.world = world
        val savePath = Paths.get(serviceProperties.getOrDefault("path", "./data/saves/"))
        store = JsonPlayerSaveStore(savePath, gson)
        autosaveIntervalSeconds =
            serviceProperties.getOrDefault("autosave-interval-seconds", DEFAULT_AUTOSAVE_INTERVAL_SECONDS)
    }

    override fun postLoad(
        server: Server,
        world: World,
    ) {
        if (autosaveIntervalSeconds <= 0) {
            logger.info("Periodic player autosave is disabled.")
            return
        }

        autosaveFuture =
            autosaveScheduler.scheduleWithFixedDelay(
                ::requestAutosave,
                autosaveIntervalSeconds,
                autosaveIntervalSeconds,
                TimeUnit.SECONDS,
            )
        logger.info("Player autosave scheduled every {} seconds.", autosaveIntervalSeconds)
    }

    override fun terminate(
        server: Server,
        world: World,
    ) {
        acceptingSaves = false
        autosaveFuture?.cancel(false)
        autosaveScheduler.shutdown()
        try {
            if (!autosaveScheduler.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                logger.error("Player autosave scheduler did not terminate within {} seconds.", SHUTDOWN_TIMEOUT_SECONDS)
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            logger.error(e) { "Interrupted while waiting for the player autosave scheduler to stop." }
        }
        saveExecutor.shutdown()
        try {
            if (!saveExecutor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                logger.error("Player save writer did not terminate within {} seconds.", SHUTDOWN_TIMEOUT_SECONDS)
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            logger.error(e) { "Interrupted while waiting for the player save writer to stop." }
        }
    }

    override fun loadClientData(
        client: Client,
        request: LoginRequest,
    ): PlayerLoadResult {
        client.loginUsername = client.loginUsername.lowercase()
        val accountLock = accountLocks.computeIfAbsent(client.loginUsername) { Any() }
        return synchronized(accountLock) {
            loadClientDataLocked(client, request)
        }
    }

    private fun loadClientDataLocked(
        client: Client,
        request: LoginRequest,
    ): PlayerLoadResult {
        if (!characterExists(client.loginUsername)) {
            configureNewPlayer(client, request)
            client.uid = PlayerUID(client.loginUsername)
            store.write(client.loginUsername, createSaveData(client))
            return PlayerLoadResult.NEW_ACCOUNT
        }
        try {
            val world = client.world
            val data = store.read(client.loginUsername)
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
            return PlayerLoadResult.LOAD_ACCOUNT
        } catch (e: Exception) {
            logger.error(e) { "Error when loading player: ${request.username}" }
            return PlayerLoadResult.MALFORMED
        }
    }

    override fun saveClientData(client: Client): Boolean {
        if (!acceptingSaves) {
            logger.warn { "Ignoring save for ${client.loginUsername}: player save service is shutting down." }
            return false
        }
        client.loginUsername = client.loginUsername.lowercase() // Convert username to lowercase
        val data = createSaveData(client)
        return try {
            saveExecutor.submit<Boolean> {
                writeSnapshot(client.loginUsername, data)
                true
            }.get()
        } catch (e: Exception) {
            logger.error(e) { "Error when saving player: ${client.loginUsername}" }
            false
        }
    }

    private fun requestAutosave() {
        if (!acceptingSaves) {
            return
        }
        val gameService = world.getService(GameService::class.java) ?: return
        gameService.submitGameThreadJob {
            if (!acceptingSaves) {
                return@submitGameThreadJob
            }
            val snapshots = mutableMapOf<String, JsonPlayerSaveData>()
            world.players.forEach { player ->
                if (player is Client) {
                    snapshots[player.loginUsername.lowercase()] = createSaveData(player)
                }
            }

            if (snapshots.isNotEmpty()) {
                try {
                    saveExecutor.execute {
                        snapshots.forEach { (username, data) ->
                            try {
                                writeSnapshot(username, data)
                            } catch (e: Exception) {
                                logger.error(e) { "Error during autosave for player: $username" }
                            }
                        }
                    }
                } catch (_: RejectedExecutionException) {
                    if (acceptingSaves) {
                        logger.error { "Player autosave writer rejected a save while still accepting saves." }
                    }
                }
            }
        }
    }

    private fun writeSnapshot(
        username: String,
        data: JsonPlayerSaveData,
    ) {
        val accountLock = accountLocks.computeIfAbsent(username) { Any() }
        synchronized(accountLock) {
            store.write(username, data)
        }
    }

    private fun createSaveData(client: Client): JsonPlayerSaveData =
        JsonPlayerSaveData(
            schemaVersion = JsonPlayerSaveData.CURRENT_SCHEMA_VERSION,
            username = client.loginUsername,
            passwordHash = client.passwordHash,
            privilege = client.privilege.id,
            displayName = client.username,
            x = client.tile.x,
            z = client.tile.z,
            height = client.tile.height,
            previousXteas = client.currentXteaKeys.copyOf(),
            displayMode = client.interfaces.displayMode.id,
            runEnergy = client.runEnergy,
            appearance = client.getPersistentAppearance(),
            attributes = client.attr.toPersistentMap(),
            timers = client.timers.toPersistentTimers(),
            skills = client.getPersistentSkills(),
            itemContainers = client.getPersistentContainers(),
            varps = client.varps.getAll().filter { it.state != 0 },
            friends = client.friends.toMutableList(),
            ignoredPlayers = client.ignoredPlayers.toMutableList(),
            publicFilterSetting = client.publicFilterSetting.settingId,
            privateFilterSetting = client.privateFilterSetting.settingId,
            tradeFilterSetting = client.tradeFilterSetting.settingId,
        )

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
        return store.exists(username)
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

    companion object : KLogging() {
        private const val DEFAULT_AUTOSAVE_INTERVAL_SECONDS = 300
        private const val SHUTDOWN_TIMEOUT_SECONDS = 10L
    }
}
