package com.seedream.app

import com.seedream.app.service.ServiceStopCoordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class ServiceStopCoordinatorTest {
    @Test
    fun stopRunsActionExactlyOnceAcrossManyThreads() {
        val runs = AtomicInteger(0)
        val coordinator = ServiceStopCoordinator(
            onStop = { runs.incrementAndGet() },
            dispatchToMain = { it() }
        )

        val threads = (0 until 8).map {
            Thread {
                repeat(50) { coordinator.stop() }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        assertEquals(1, runs.get())
        assertTrue(coordinator.isStopped)
    }

    @Test
    fun actionIsDispatchedToMainThreadExactlyOnce() {
        val dispatchCalls = AtomicInteger(0)
        val runs = AtomicInteger(0)
        val coordinator = ServiceStopCoordinator(
            onStop = { runs.incrementAndGet() },
            dispatchToMain = { dispatchCalls.incrementAndGet(); it() }
        )

        coordinator.stop()
        coordinator.stop()

        assertEquals(1, dispatchCalls.get())
        assertEquals(1, runs.get())
    }

    @Test
    fun concurrentStopsDispatchOnlyOnce() {
        val dispatchCalls = AtomicInteger(0)
        val runs = AtomicInteger(0)
        val coordinator = ServiceStopCoordinator(
            onStop = { runs.incrementAndGet() },
            dispatchToMain = { dispatchCalls.incrementAndGet(); it() }
        )

        val pool = Executors.newFixedThreadPool(4)
        val latch = CountDownLatch(4)
        repeat(4) {
            pool.execute {
                try {
                    coordinator.stop()
                } finally {
                    latch.countDown()
                }
            }
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        pool.shutdown()

        assertEquals(1, dispatchCalls.get())
        assertEquals(1, runs.get())
    }

    @Test
    fun stopBeforeCompletionIsIdempotent() {
        val runs = AtomicInteger(0)
        val coordinator = ServiceStopCoordinator(
            onStop = { runs.incrementAndGet() },
            dispatchToMain = { it() }
        )

        // Simulate: main thread stop command races generation coroutine finally.
        coordinator.stop()
        coordinator.stop()

        assertEquals(1, runs.get())
    }

    @Test
    fun dispatchRunsOnRequestedThread() {
        val threadName = AtomicReference<String>("")
        val mainLike = AtomicReference<Thread>(null)
        val mainThread = Thread.currentThread()
        val coordinator = ServiceStopCoordinator(
            onStop = { threadName.set(Thread.currentThread().name) },
            dispatchToMain = { action -> mainLike.set(Thread.currentThread()); action() }
        )

        coordinator.stop()

        // dispatchToMain is invoked on the caller thread; production passes a
        // Handler.post bound to the main looper.
        assertEquals(mainThread, mainLike.get())
        assertFalse(threadName.get().isEmpty())
    }
}
