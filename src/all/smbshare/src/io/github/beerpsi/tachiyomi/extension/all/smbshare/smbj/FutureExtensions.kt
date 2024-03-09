package io.github.beerpsi.tachiyomi.extension.all.smbshare.smbj

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.time.DurationUnit
import kotlin.time.toDuration

internal suspend fun <T> Future<T>.await(timeout: Long, unit: TimeUnit): T {
    try {
        return withTimeout(timeout.toDuration(unit.toDurationUnit())) {
            while (!isDone) {
                delay(FUTURE_POLLING_RATE)
            }

            get()
        }
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        throw e
    } finally {
        cancel(true)
    }
}

private fun TimeUnit.toDurationUnit(): DurationUnit = when (this) {
    TimeUnit.NANOSECONDS -> DurationUnit.NANOSECONDS
    TimeUnit.MICROSECONDS -> DurationUnit.MICROSECONDS
    TimeUnit.MILLISECONDS -> DurationUnit.MILLISECONDS
    TimeUnit.SECONDS -> DurationUnit.SECONDS
    TimeUnit.MINUTES -> DurationUnit.MINUTES
    TimeUnit.HOURS -> DurationUnit.HOURS
    TimeUnit.DAYS -> DurationUnit.DAYS
}

private const val FUTURE_POLLING_RATE = 100L
