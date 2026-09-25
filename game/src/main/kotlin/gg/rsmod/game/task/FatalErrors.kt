package gg.rsmod.game.task

/**
 * Audit T-01: the game-thread safety nets used to catch only [Exception]. Any other [Throwable] - a
 * `NotImplementedError` from a plugin's `TODO()`, an `ExceptionInInitializerError`, a `StackOverflowError`
 * from runaway recursion - escaped `GameService.cycle`, and `scheduleAtFixedRate` then silently cancelled
 * every later cycle while Netty kept accepting connections into a frozen world.
 *
 * The per-entity and per-task nets now catch [Throwable] and only let a genuinely fatal JVM error through:
 * a [VirtualMachineError] such as `OutOfMemoryError` or `InternalError`, where continuing the entity loop
 * could corrupt state further. [StackOverflowError] is a [VirtualMachineError] too, but the stack has
 * already unwound by the time it is caught, so it is treated like any other plugin failure. A fatal error
 * still never stops the tick loop itself: `GameService` logs it and schedules the next cycle.
 */
internal fun Throwable.isFatalGameError(): Boolean = this is VirtualMachineError && this !is StackOverflowError

/** Rethrows this throwable when it is fatal (see [isFatalGameError]); otherwise returns normally. */
internal fun Throwable.rethrowIfFatal() {
    if (isFatalGameError()) {
        throw this
    }
}
