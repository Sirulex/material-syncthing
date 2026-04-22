package dev.lostf1sh.syncthing.di

import dev.lostf1sh.syncthing.data.model.BandwidthSample

class BandwidthTracker(
    private val appState: AppState,
) {
    private var lastTotalIn: Long = -1
    private var lastTotalOut: Long = -1
    private var lastSampleAt: Long = 0

    fun reset() {
        lastTotalIn = -1
        lastTotalOut = -1
        lastSampleAt = 0
    }

    fun record(totalIn: Long, totalOut: Long) {
        val now = System.currentTimeMillis()
        if (lastTotalIn < 0) {
            lastTotalIn = totalIn
            lastTotalOut = totalOut
            lastSampleAt = now
            return
        }

        val elapsedSec = ((now - lastSampleAt) / 1000.0).coerceAtLeast(0.5)
        val deltaIn = (totalIn - lastTotalIn).coerceAtLeast(0L)
        val deltaOut = (totalOut - lastTotalOut).coerceAtLeast(0L)
        appState.pushBandwidthSample(
            BandwidthSample(
                timestamp = now,
                inBytesPerSec = (deltaIn / elapsedSec).toLong(),
                outBytesPerSec = (deltaOut / elapsedSec).toLong(),
            )
        )
        lastTotalIn = totalIn
        lastTotalOut = totalOut
        lastSampleAt = now
    }

    fun format(bytesPerSecond: Long): String {
        val units = listOf("B/s", "KB/s", "MB/s", "GB/s")
        var value = bytesPerSecond.toDouble()
        var unitIndex = 0
        while (value >= 1024.0 && unitIndex < units.lastIndex) {
            value /= 1024.0
            unitIndex += 1
        }
        return if (unitIndex == 0) {
            "${value.toLong()} ${units[unitIndex]}"
        } else {
            "%.1f %s".format(value, units[unitIndex])
        }
    }
}
