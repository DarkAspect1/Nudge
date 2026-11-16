package com.nudge.edge.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.nudge.edge.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

enum class UpdateCheckFrequency(val id: String, val label: String, val intervalMs: Long?) {
    MANUAL("MANUAL", "Manual Only", null),
    ON_START("ON_START", "Every App Launch", 0L),
    EVERY_6H("EVERY_6H", "Every 6 Hours", 6L * 60L * 60L * 1000L),
    EVERY_12H("EVERY_12H", "Every 12 Hours", 12L * 60L * 60L * 1000L),
    DAILY("DAILY", "Daily", 24L * 60L * 60L * 1000L),
    WEEKLY("WEEKLY", "Weekly", 7L * 24L * 60L * 60L * 1000L);

    companion object {
        fun fromId(id: String): UpdateCheckFrequency =
            entries.firstOrNull { it.id == id } ?: DAILY
    }
}

data class GitHubReleaseInfo(
    val versionTag: String,
    val releaseTitle: String,
    val changelogBody: String,
    val apkDownloadUrl: String?,
    val apkSizeBytes: Long,
    val isUpdateAvailable: Boolean
)

sealed class UpdateDownloadState {
    data object Idle : UpdateDownloadState()
    data object Checking : UpdateDownloadState()
    data class Downloading(
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : UpdateDownloadState()
    data class ReadyToInstall(val apkFile: File) : UpdateDownloadState()
    data class Error(val message: String) : UpdateDownloadState()
}

class GitHubUpdateManager(
    private val context: Context,
    private val preferencesRepository: SettingsRepository,
    private val scope: CoroutineScope
) {
    private val _releaseInfo = MutableStateFlow<GitHubReleaseInfo?>(null)
    val releaseInfo: StateFlow<GitHubReleaseInfo?> = _releaseInfo.asStateFlow()

    private val _downloadState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val downloadState: StateFlow<UpdateDownloadState> = _downloadState.asStateFlow()

    private var downloadJob: Job? = null
    private var pendingInstallApkFile: File? = null

    init {
        val initialSettings = preferencesRepository.settings.value
        if (initialSettings.cachedReleaseVersionTag.isNotBlank()) {
            val isNewer = isRemoteVersionNewer(
                currentVersion = BuildConfig.VERSION_NAME,
                remoteTag = initialSettings.cachedReleaseVersionTag
            )
            val info = GitHubReleaseInfo(
                versionTag = initialSettings.cachedReleaseVersionTag,
                releaseTitle = initialSettings.cachedReleaseVersionTag,
                changelogBody = initialSettings.cachedReleaseChangelog,
                apkDownloadUrl = initialSettings.cachedReleaseApkUrl.ifBlank { null },
                apkSizeBytes = initialSettings.cachedReleaseApkSize,
                isUpdateAvailable = isNewer && initialSettings.cachedReleaseApkUrl.isNotBlank()
            )
            _releaseInfo.value = info
            val cachedApk = getTargetApkFile(initialSettings.cachedReleaseVersionTag)
            if (info.isUpdateAvailable && cachedApk.exists() && cachedApk.length() > 0L) {
                _downloadState.value = UpdateDownloadState.ReadyToInstall(cachedApk)
            }
        }
    }

    fun checkIfDue(currentFrequencyId: String, lastCheckMs: Long) {
        if (!BuildConfig.ENABLE_UPDATER) return
        val freq = UpdateCheckFrequency.fromId(currentFrequencyId)
        val interval = freq.intervalMs ?: return
        val now = System.currentTimeMillis()
        if (interval == 0L || (now - lastCheckMs) >= interval) {
            checkForUpdates(isManual = false)
        }
    }

    fun checkForUpdates(isManual: Boolean = true) {
        if (!BuildConfig.ENABLE_UPDATER) return
        if (_downloadState.value is UpdateDownloadState.Downloading) return

        scope.launch(Dispatchers.IO) {
            if (isManual) {
                _downloadState.value = UpdateDownloadState.Checking
            }
            runCatching {
                val owner = BuildConfig.GITHUB_OWNER.ifBlank { "DarkAspect1" }
                val repo = BuildConfig.GITHUB_REPO.ifBlank { "Nudge" }
                val apiUrl = "https://api.github.com/repos/$owner/$repo/releases/latest"
                val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 12_000
                    readTimeout = 12_000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "Nudge-Android-Updater")
                }
                conn.connect()
                if (conn.responseCode !in 200..299) {
                    throw IOException("GitHub API returned HTTP ${conn.responseCode}")
                }

                val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseBody)

                val tagName = json.optString("tag_name", "v${BuildConfig.VERSION_NAME}")
                val releaseName = json.optString("name", tagName)
                val body = json.optString("body", "No changelog provided.")

                var selectedApkUrl: String? = null
                var selectedApkSize = 0L
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            val url = asset.optString("browser_download_url", "")
                            val size = asset.optLong("size", 0L)
                            if (selectedApkUrl == null || name.contains("online", ignoreCase = true)) {
                                selectedApkUrl = url
                                selectedApkSize = size
                            }
                        }
                    }
                }

                val isNewer = isRemoteVersionNewer(
                    currentVersion = BuildConfig.VERSION_NAME,
                    remoteTag = tagName
                )

                val info = GitHubReleaseInfo(
                    versionTag = tagName,
                    releaseTitle = releaseName,
                    changelogBody = body,
                    apkDownloadUrl = selectedApkUrl,
                    apkSizeBytes = selectedApkSize,
                    isUpdateAvailable = isNewer && !selectedApkUrl.isNullOrBlank()
                )
                _releaseInfo.value = info

                preferencesRepository.saveCachedRelease(
                    versionTag = tagName,
                    changelog = body,
                    apkUrl = selectedApkUrl.orEmpty(),
                    apkSize = selectedApkSize,
                    checkTimestampMs = System.currentTimeMillis()
                )

                val cachedApk = getTargetApkFile(tagName)
                if (info.isUpdateAvailable && cachedApk.exists() && cachedApk.length() > 0L) {
                    _downloadState.value = UpdateDownloadState.ReadyToInstall(cachedApk)
                } else {
                    _downloadState.value = UpdateDownloadState.Idle
                }
            }.onFailure { e ->
                if (isManual) {
                    _downloadState.value = UpdateDownloadState.Error(
                        e.localizedMessage ?: "Unable to check for updates"
                    )
                } else {
                    _downloadState.value = UpdateDownloadState.Idle
                }
            }
        }
    }

    fun downloadAndInstallUpdate(release: GitHubReleaseInfo) {
        val downloadUrl = release.apkDownloadUrl ?: run {
            _downloadState.value = UpdateDownloadState.Error("No APK asset found in this release")
            return
        }

        downloadJob?.cancel()
        downloadJob = scope.launch(Dispatchers.IO) {
            runCatching {
                val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
                updatesDir.listFiles()?.forEach { file ->
                    if (file.name != "nudge-${release.versionTag}.apk") {
                        file.delete()
                    }
                }

                val targetFile = getTargetApkFile(release.versionTag)
                val tempFile = File(updatesDir, "nudge-${release.versionTag}.apk.tmp")
                if (tempFile.exists()) tempFile.delete()

                var currentUrl = downloadUrl
                var redirects = 0
                var conn: HttpURLConnection

                while (true) {
                    conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                        instanceFollowRedirects = false
                        connectTimeout = 15_000
                        readTimeout = 30_000
                        setRequestProperty("Accept", "application/octet-stream")
                        setRequestProperty("User-Agent", "Nudge-Android-Updater")
                    }
                    conn.connect()
                    val code = conn.responseCode
                    if (code in 300..399) {
                        val location = conn.getHeaderField("Location") ?: break
                        conn.disconnect()
                        currentUrl = location
                        redirects++
                        if (redirects > 5) throw IOException("Too many redirects")
                    } else {
                        break
                    }
                }

                if (conn.responseCode !in 200..299) {
                    throw IOException("Download failed (HTTP ${conn.responseCode})")
                }

                val totalBytes = conn.contentLengthLong.takeIf { it > 0L } ?: release.apkSizeBytes
                var downloadedBytes = 0L
                var lastEmitTimeMs = 0L

                _downloadState.value = UpdateDownloadState.Downloading(
                    progress = 0f,
                    downloadedBytes = 0L,
                    totalBytes = totalBytes
                )

                conn.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            ensureActive()
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead

                            val now = System.currentTimeMillis()
                            if (now - lastEmitTimeMs >= 32L || downloadedBytes == totalBytes) {
                                lastEmitTimeMs = now
                                val progress = if (totalBytes > 0L) {
                                    (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                } else 0f
                                _downloadState.value = UpdateDownloadState.Downloading(
                                    progress = progress,
                                    downloadedBytes = downloadedBytes,
                                    totalBytes = totalBytes
                                )
                            }
                        }
                        output.flush()
                    }
                }

                if (targetFile.exists()) targetFile.delete()
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }

                _downloadState.value = UpdateDownloadState.ReadyToInstall(targetFile)

                withContext(Dispatchers.Main) {
                    promptInstallApk(targetFile)
                }
            }.onFailure { e ->
                if (e is CancellationException) {
                    _downloadState.value = UpdateDownloadState.Idle
                } else {
                    _downloadState.value = UpdateDownloadState.Error(
                        e.localizedMessage ?: "Failed to download APK"
                    )
                }
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _downloadState.value = UpdateDownloadState.Idle
    }

    fun promptInstallApk(apkFile: File) {
        if (!apkFile.exists()) {
            _downloadState.value = UpdateDownloadState.Error("APK file not found. Please download again.")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            pendingInstallApkFile = apkFile
            val permIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(permIntent)
            return
        }

        pendingInstallApkFile = null
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.updateprovider",
            apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
    }

    fun onActivityResume() {
        val pending = pendingInstallApkFile ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()
        ) {
            pendingInstallApkFile = null
            promptInstallApk(pending)
        }
    }

    private fun getTargetApkFile(versionTag: String): File {
        val safeTag = versionTag.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        return File(updatesDir, "nudge-$safeTag.apk")
    }

    private fun isRemoteVersionNewer(currentVersion: String, remoteTag: String): Boolean {
        val cleanCurrent = currentVersion.trimStart('v', 'V').split("-", "+").first()
        val cleanRemote = remoteTag.trimStart('v', 'V').split("-", "+").first()
        val currentParts = cleanCurrent.split(".").map { it.toIntOrNull() ?: 0 }
        val remoteParts = cleanRemote.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(currentParts.size, remoteParts.size)
        for (i in 0 until maxLen) {
            val c = currentParts.getOrElse(i) { 0 }
            val r = remoteParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    companion object {
        @Volatile
        private var instance: GitHubUpdateManager? = null

        fun getInstance(
            context: Context,
            preferencesRepository: SettingsRepository,
            scope: CoroutineScope
        ): GitHubUpdateManager {
            return instance ?: synchronized(this) {
                instance ?: GitHubUpdateManager(context.applicationContext, preferencesRepository, scope).also {
                    instance = it
                }
            }
        }
    }
}
