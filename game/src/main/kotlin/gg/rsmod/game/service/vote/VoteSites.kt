package gg.rsmod.game.service.vote

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import mu.KLogging
import java.io.File
import java.net.URLEncoder

/**
 * The toplists of the voting system, from `data/cfg/votes.yml` (read on every use, so a change needs no rebuild). Per site (the
 * sites' own callback documentation, read 2026-10-10 - see votes.yml):
 * - [id]: the site's part of the callback path (`/vote/<secret>/<id>`);
 * - [usernameParam]: the parameter the site sends the voter's name back in;
 * - [successParam]: a parameter that must be "1" (rsps-list also calls back for a refused duplicate vote, with voted=0);
 * - [secretParam] / [siteSecret]: the site's own callback secret, checked when filled in;
 * - [allowedHost]: the request must come from this host's address (TopG has no secret: "whitelist monitor.topg.org");
 * - [link]: the vote page `::vote` shows, `{name}` = the player's name.
 */
class VoteSites private constructor() {
    class Site(
        val id: String,
        val name: String,
        val usernameParam: String,
        val successParam: String,
        val secretParam: String,
        val siteSecret: String,
        val allowedHost: String,
        private val linkTemplate: String,
    ) {
        fun link(username: String): String = linkTemplate.replace("{name}", URLEncoder.encode(username.replace(' ', '_'), "UTF-8"))

        val hasLink: Boolean get() = linkTemplate.isNotBlank()
    }

    companion object : KLogging() {
        const val PATH = "./data/cfg/votes.yml"

        fun load(file: File = File(PATH)): List<Site> {
            if (!file.exists()) return emptyList()
            return runCatching {
                @Suppress("UNCHECKED_CAST")
                val root = ObjectMapper(YAMLFactory()).readValue(file, Map::class.java) as Map<String, Any?>
                @Suppress("UNCHECKED_CAST")
                (root["sites"] as? List<Map<String, Any?>>).orEmpty().mapNotNull { row ->
                    fun s(key: String) = row[key]?.toString()?.trim().orEmpty()
                    val id = s("id").ifBlank { return@mapNotNull null }
                    Site(id, s("name").ifBlank { id }, s("username_param").ifBlank { "name" }, s("success_param"), s("secret_param").ifBlank { "secret" }, s("site_secret"), s("allowed_host"), s("link"))
                }
            }.getOrElse { e ->
                logger.error(e) { "$PATH unreadable - voting has no sites." }
                emptyList()
            }
        }
    }
}
