package com.nudge.edge.service

import android.content.Context
import android.os.Build
import android.os.RemoteCallbackList
import android.os.SystemClock
import androidx.annotation.Keep
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

@Keep
class ShizukuInputService : IShizukuInputService.Stub {

    constructor() : super()

    constructor(context: Context) : super()

    private val callbacks = RemoteCallbackList<IShizukuInputCallback>()
    private val spawnedProcesses = CopyOnWriteArrayList<Process>()
    private val deviceKeyLabels = ConcurrentHashMap<String, Map<Int, String>>()
    private val lineRegex = Regex("""^\[\s*(\d+\.\d+)\]\s+0001\s+([0-9a-fA-F]+)\s+([0-9a-fA-F]+)""")

    override fun registerCallback(callback: IShizukuInputCallback?) {
        if (callback != null) {
            callbacks.register(callback)
        }
    }

    override fun unregisterCallback(callback: IShizukuInputCallback?) {
        if (callback != null) {
            callbacks.unregister(callback)
        }
    }

    override fun startMonitoring() {
        stopMonitoring()

        val devices = discoverKeyDevices()
        for (dev in devices) {
            populateDeviceLabels(dev.path, dev.name)

            runCatching {
                val proc = Runtime.getRuntime().exec(arrayOf("/system/bin/getevent", "-t", dev.path))
                spawnedProcesses.add(proc)

                val thread = Thread({
                    val reader = BufferedReader(InputStreamReader(proc.inputStream))
                    var line: String?
                    try {
                        while (reader.readLine().also { line = it } != null) {
                            val l = line ?: continue
                            val match = lineRegex.find(l) ?: continue
                            val scanCode = match.groupValues[2].toInt(16)
                            val value = match.groupValues[3].toInt(16)
                            val now = SystemClock.uptimeMillis()
                            dispatchInputEvent(dev.name, scanCode, value, now)
                        }
                    } catch (_: Exception) {
                    } finally {
                        runCatching { reader.close() }
                    }
                }, "NudgeGetEvent-${dev.name}")
                thread.isDaemon = true
                thread.start()
            }
        }
    }

    override fun stopMonitoring() {
        for (proc in spawnedProcesses) {
            runCatching { proc.destroy() }
        }
        spawnedProcesses.clear()
    }

    override fun getKeyLabel(deviceName: String?, scanCode: Int): String {
        val label = deviceName?.let { deviceKeyLabels[it]?.get(scanCode) }
        return label ?: "Key 0x${scanCode.toString(16).uppercase().padStart(4, '0')}"
    }

    override fun destroy() {
        stopMonitoring()
        callbacks.kill()
    }

    override fun setDndEnabled(enabled: Boolean): Boolean {
        val arg = if (enabled) "priority" else "off"
        return runCatching {
            val proc = ProcessBuilder("/system/bin/cmd", "notification", "set_dnd", arg)
                .redirectErrorStream(true)
                .start()
            val finished = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                proc.waitFor(2, TimeUnit.SECONDS)
            } else {
                val start = System.currentTimeMillis()
                var exited = false
                while (System.currentTimeMillis() - start < 2000) {
                    try {
                        proc.exitValue()
                        exited = true
                        break
                    } catch (_: IllegalThreadStateException) {
                        Thread.sleep(50)
                    }
                }
                exited
            }
            if (!finished) {
                proc.destroy()
                false
            } else {
                proc.exitValue() == 0
            }
        }.getOrDefault(false)
    }

    private fun dispatchInputEvent(deviceName: String, scanCode: Int, value: Int, timestampMs: Long) {
        val count = callbacks.beginBroadcast()
        for (i in 0 until count) {
            runCatching {
                callbacks.getBroadcastItem(i).onInputEvent(deviceName, scanCode, value, timestampMs)
            }
        }
        callbacks.finishBroadcast()
    }

    private data class DiscoveredDevice(val path: String, val name: String)

    private fun discoverKeyDevices(): List<DiscoveredDevice> {
        val result = mutableListOf<DiscoveredDevice>()
        val output = runCatching {
            val proc = Runtime.getRuntime().exec(arrayOf("/system/bin/getevent", "-p"))
            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            val text = reader.readText()
            proc.waitFor()
            text
        }.getOrDefault("")

        if (output.isBlank()) return result

        var currentPath: String? = null
        var currentName: String? = null
        var hasKeys = false
        var isTouchOrSensor = false

        fun flushCurrent() {
            val path = currentPath
            val name = currentName
            if (path != null && name != null && hasKeys && !isTouchOrSensor) {
                val lowerName = name.lowercase()
                if (!lowerName.contains("touch") && !lowerName.contains("sensor") && !lowerName.contains("ts")) {
                    result.add(DiscoveredDevice(path, name))
                }
            }
            currentPath = null
            currentName = null
            hasKeys = false
            isTouchOrSensor = false
        }

        val addDeviceRegex = Regex("""add device \d+:\s+(\S+)""")
        val nameRegex = Regex("""name:\s+"([^"]+)"""")

        for (line in output.lines()) {
            val trimmed = line.trim()
            val addMatch = addDeviceRegex.find(trimmed)
            if (addMatch != null) {
                flushCurrent()
                currentPath = addMatch.groupValues[1]
                continue
            }

            val nameMatch = nameRegex.find(trimmed)
            if (nameMatch != null) {
                currentName = nameMatch.groupValues[1]
                continue
            }

            if (trimmed.contains("KEY (0001):")) {
                hasKeys = true
            }

            if (trimmed.contains("0035") || trimmed.contains("ABS_MT_POSITION_X")) {
                isTouchOrSensor = true
            }
        }
        flushCurrent()

        return result
    }

    private fun populateDeviceLabels(devPath: String, devName: String) {
        val numTokens = runCatching {
            val proc = Runtime.getRuntime().exec(arrayOf("/system/bin/getevent", "-p", devPath))
            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            val lines = reader.readLines()
            proc.waitFor()
            extractKeyTokens(lines)
        }.getOrDefault(emptyList())

        val labelTokens = runCatching {
            val proc = Runtime.getRuntime().exec(arrayOf("/system/bin/getevent", "-lp", devPath))
            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            val lines = reader.readLines()
            proc.waitFor()
            extractKeyTokens(lines)
        }.getOrDefault(emptyList())

        val labelMap = mutableMapOf<Int, String>()
        val count = minOf(numTokens.size, labelTokens.size)
        for (i in 0 until count) {
            val rawNum = numTokens[i].removeSuffix("*")
            val rawLabel = labelTokens[i].removeSuffix("*")
            val code = rawNum.toIntOrNull(16)
            if (code != null) {
                labelMap[code] = rawLabel
            }
        }
        deviceKeyLabels[devName] = labelMap
    }

    private fun extractKeyTokens(lines: List<String>): List<String> {
        val tokens = mutableListOf<String>()
        var inKeyBlock = false
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.contains("KEY (0001):")) {
                inKeyBlock = true
                val after = trimmed.substringAfter("KEY (0001):").trim()
                if (after.isNotEmpty()) {
                    tokens.addAll(after.split(Regex("""\s+""")).filter { it.isNotBlank() })
                }
                continue
            }
            if (inKeyBlock) {
                if (trimmed.contains(Regex("""^[A-Z0-9_]+\s+\([0-9a-fA-F]{4}\):""")) || trimmed.startsWith("input props:")) {
                    break
                }
                tokens.addAll(trimmed.split(Regex("""\s+""")).filter { it.isNotBlank() })
            }
        }
        return tokens
    }
}
