package gg.rsmod.game.model

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.Appender
import org.apache.logging.log4j.core.Core
import org.apache.logging.log4j.core.Filter
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.plugins.Plugin
import org.apache.logging.log4j.core.config.plugins.PluginAttribute
import org.apache.logging.log4j.core.config.plugins.PluginElement
import org.apache.logging.log4j.core.config.plugins.PluginFactory

/**
 * The debug toolkit's crash catcher (`C:\RSPS\TRACES.md`), wired once in log4j2.xml instead of in each of the dozens of
 * try/catch blocks: every ERROR the server logs - packet handlers, plugins, timers, queues, hit actions, login/logout hooks,
 * deaths, saves - goes to `C:\RSPS\bugs\crashes-<date>.log` (always, traces on or off), and with the traces on also becomes a
 * `[trace:plugin] PROBLEM` line. WARN lines become PROBLEM lines while the traces are on.
 */
@Plugin(name = "TraceCrash", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE, printObject = true)
class TraceCrashAppender private constructor(
    name: String,
    filter: Filter?,
) : AbstractAppender(name, filter, null, true) {
    override fun append(event: LogEvent) {
        // Never react to the toolkit's own lines (no feedback loop).
        if (event.loggerName?.endsWith("Trace") == true || event.loggerName?.endsWith("TraceCrashAppender") == true) return
        val text = event.message?.formattedMessage ?: ""
        val source = event.loggerName?.substringAfterLast('.') ?: "?"
        if (event.level.isMoreSpecificThan(Level.ERROR)) {
            Trace.crash("$source: $text", event.thrown)
        } else if (event.level == Level.WARN) {
            Trace.problem(Trace.Cat.PLUGIN) { "warning $source: $text${event.thrown?.let { " (${it.javaClass.simpleName} at ${Trace.where(it)})" } ?: ""}" }
        }
    }

    companion object {
        @JvmStatic
        @PluginFactory
        fun create(
            @PluginAttribute("name") name: String?,
            @PluginElement("Filter") filter: Filter?,
        ): TraceCrashAppender = TraceCrashAppender(name ?: "TraceCrash", filter)
    }
}
