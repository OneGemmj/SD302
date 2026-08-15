package com.seedream.app.service

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Guarantees a teardown action runs exactly once and only on the main thread,
 * even when multiple threads race to stop the same resource. This protects the
 * service teardown (wake-lock release, stopForeground, stopSelf) from being
 * invoked concurrently by the main thread (stop command) and the generation
 * coroutine (finally block), which would otherwise crash with
 * "WakeLock under-locked" / illegal Service lifecycle calls.
 *
 * @param dispatchToMain executes [onStop] on the main thread. Pass
 *        `Handler(Looper.getMainLooper())::post` in production; pass `{ it() }`
 *        in tests that assert on idempotency only.
 */
class ServiceStopCoordinator(
    private val onStop: () -> Unit,
    private val dispatchToMain: (() -> Unit) -> Unit
) {
    private val stopped = AtomicBoolean(false)

    fun stop() {
        if (stopped.compareAndSet(false, true)) {
            dispatchToMain(onStop)
        }
    }

    val isStopped: Boolean get() = stopped.get()
}
