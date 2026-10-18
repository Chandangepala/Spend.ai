package com.basic.spendai.ai

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import com.basic.spendai.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest

/** Where the optional Gemma model stands on this phone. */
sealed interface GemmaState {
    /** [error] explains why the last download/import didn't finish. */
    data class NotInstalled(val error: String? = null) : GemmaState

    /**
     * Downloading (Wi-Fi, via Android's download manager) or copying an imported file.
     * [waitingForWifi]: the download is queued until the phone is on Wi-Fi.
     * [verifying]: the file is complete and its checksum is being checked.
     */
    data class Copying(
        val bytesDone: Long,
        val bytesTotal: Long?,
        val downloading: Boolean,
        val waitingForWifi: Boolean = false,
        val verifying: Boolean = false,
    ) : GemmaState

    /** [loadError] is set when the file is there but the engine couldn't start with it. */
    data class Installed(val bytes: Long, val loadError: String? = null) : GemmaState
}

/**
 * The Gemma model file: getting it onto the phone (download or import), and removing it.
 * It lives in no-backup storage, so ~600 MB never goes to the user's cloud backup.
 */
object GemmaModel {
    private const val TAG = "SpendAI-Gemma"
    const val FILE_NAME = "gemma3-1b-it-int4.litertlm"
    /** Gemma 3 1B int4 is about 584 MB; used for the space check before the size is known. */
    val EXPECTED_BYTES: Long get() = BuildConfig.GEMMA_MODEL_BYTES
    /** Anything smaller can't be the model (catches picking the wrong file). */
    private const val MIN_BYTES = 50_000_000L
    /** Room to spare beyond the model itself, so the phone isn't left completely full. */
    private const val SPARE_BYTES = 300_000_000L
    private const val PREFS = "gemma"
    private const val KEY_DOWNLOAD_ID = "download_id"

    val downloadUrl: String get() = BuildConfig.GEMMA_MODEL_URL
    val canDownload: Boolean get() = downloadUrl.isNotBlank()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<GemmaState>(GemmaState.NotInstalled())
    val state: StateFlow<GemmaState> = _state.asStateFlow()
    private var job: Job? = null

    fun file(context: Context) = File(context.noBackupFilesDir, "models/$FILE_NAME")

    fun isInstalled(context: Context) = file(context).length() >= MIN_BYTES

    /** True if [free] bytes leave room for a [size]-byte model plus some spare. */
    fun hasRoomFor(size: Long, free: Long) = free >= size + SPARE_BYTES

    /** Re-reads the file (and any download still running from an earlier session). */
    fun refresh(context: Context) {
        if (job?.isActive == true) return
        val app = context.applicationContext
        val pendingId = prefs(app).getLong(KEY_DOWNLOAD_ID, -1)
        when {
            pendingId != -1L -> job = scope.launch { followDownload(app, pendingId) }
            isInstalled(app) -> if (_state.value !is GemmaState.Installed) _state.value = GemmaState.Installed(file(app).length())
            else -> _state.value = GemmaState.NotInstalled((_state.value as? GemmaState.NotInstalled)?.error)
        }
    }

    /** Called by the provider when the engine can't start with the file. */
    internal fun reportLoadError(context: Context, message: String) {
        _state.value = GemmaState.Installed(file(context).length(), message)
    }

    internal fun clearLoadError(context: Context) {
        if ((_state.value as? GemmaState.Installed)?.loadError != null) _state.value = GemmaState.Installed(file(context).length())
    }

    /** Starts a Wi-Fi-only download of the model; keeps going if the app is closed. */
    fun download(context: Context) {
        if (!canDownload || job?.isActive == true) return
        val app = context.applicationContext
        if (!hasRoomFor(EXPECTED_BYTES, freeBytes(app))) {
            _state.value = GemmaState.NotInstalled(notEnoughSpace(EXPECTED_BYTES))
            return
        }
        val temp = downloadTemp(app).apply { delete() }
        val request = DownloadManager.Request(downloadUrl.toUri())
            .setTitle("SpendAI on-device AI model")
            .setDescription("Gemma, about 584 MB")
            .setAllowedOverMetered(false)
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationUri(Uri.fromFile(temp))
        val id = app.getSystemService(DownloadManager::class.java).enqueue(request)
        prefs(app).edit { putLong(KEY_DOWNLOAD_ID, id) }
        _state.value = GemmaState.Copying(0, null, downloading = true)
        job = scope.launch { followDownload(app, id) }
    }

    /** Copies a model file the user picked (e.g. downloaded in the browser) into app storage. */
    fun import(context: Context, uri: Uri) {
        if (job?.isActive == true) return
        val app = context.applicationContext
        job = scope.launch {
            val part = File(file(app).path + ".part")
            try {
                val (name, size) = describe(app, uri)
                if (name != null && !name.endsWith(".litertlm", ignoreCase = true)) {
                    _state.value = GemmaState.NotInstalled("That's not a model file. Pick the file ending in .litertlm.")
                    return@launch
                }
                if (size != null && size < MIN_BYTES) {
                    _state.value = GemmaState.NotInstalled("That file is too small to be the Gemma model.")
                    return@launch
                }
                if (!hasRoomFor(size ?: EXPECTED_BYTES, freeBytes(app))) {
                    _state.value = GemmaState.NotInstalled(notEnoughSpace(size ?: EXPECTED_BYTES))
                    return@launch
                }
                part.parentFile?.mkdirs()
                _state.value = GemmaState.Copying(0, size, downloading = false)
                val input = app.contentResolver.openInputStream(uri) ?: error("Couldn't open the file.")
                input.use { inp ->
                    part.outputStream().use { out ->
                        val buffer = ByteArray(1 shl 20)
                        var done = 0L
                        var lastReport = 0L
                        while (isActive) {
                            val n = inp.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            done += n
                            if (done - lastReport >= 8_000_000) {
                                lastReport = done
                                _state.value = GemmaState.Copying(done, size, downloading = false)
                            }
                        }
                    }
                }
                if (!isActive) return@launch
                install(app, part)
            } catch (e: CancellationException) {
                part.delete()
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "import failed", e)
                part.delete()
                _state.value = GemmaState.NotInstalled(e.message ?: "Couldn't copy the file.")
            }
        }
    }

    /** Stops a running download or import and removes the partial file. */
    fun cancel(context: Context) {
        val app = context.applicationContext
        job?.cancel()
        val id = prefs(app).getLong(KEY_DOWNLOAD_ID, -1)
        if (id != -1L) app.getSystemService(DownloadManager::class.java).remove(id)
        prefs(app).edit { remove(KEY_DOWNLOAD_ID) }
        downloadTemp(app).delete()
        File(file(app).path + ".part").delete()
        _state.value = GemmaState.NotInstalled()
    }

    /** Deletes the model to free the space; AI falls back to Gemini Nano or the rules. */
    fun delete(context: Context) {
        file(context.applicationContext).delete()
        _state.value = GemmaState.NotInstalled()
    }

    private suspend fun followDownload(app: Context, id: Long) {
        val manager = app.getSystemService(DownloadManager::class.java)
        try {
            while (true) {
                val row = manager.query(DownloadManager.Query().setFilterById(id))?.use { c ->
                    if (!c.moveToFirst()) null else DownloadRow(
                        c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                        c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)),
                        c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                        c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                    )
                }
                if (row == null) {
                    finishDownload(app, GemmaState.NotInstalled("The download was cancelled."))
                    return
                }
                val (status, reason, done, total) = row
                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        prefs(app).edit { remove(KEY_DOWNLOAD_ID) }
                        val temp = downloadTemp(app)
                        _state.value = GemmaState.Copying(temp.length(), temp.length(), downloading = true, verifying = true)
                        // Only the exact published file is used; anything else (a broken download, an
                        // error page, a swapped file) is thrown away.
                        if (!matchesExpected(temp)) {
                            finishDownload(app, GemmaState.NotInstalled("The download was damaged. Please try again."))
                            return
                        }
                        install(app, temp)
                        return
                    }
                    DownloadManager.STATUS_FAILED -> {
                        manager.remove(id)
                        finishDownload(app, GemmaState.NotInstalled("Download failed. Check Wi-Fi and try again."))
                        return
                    }
                    else -> _state.value = GemmaState.Copying(
                        done,
                        total.takeIf { it > 0 },
                        downloading = true,
                        waitingForWifi = status == DownloadManager.STATUS_PAUSED &&
                            reason in setOf(DownloadManager.PAUSED_QUEUED_FOR_WIFI, DownloadManager.PAUSED_WAITING_FOR_NETWORK),
                    )
                }
                delay(1_000)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "download tracking failed", e)
            finishDownload(app, GemmaState.NotInstalled(e.message ?: "Download failed."))
        }
    }

    private data class DownloadRow(val status: Int, val reason: Int, val done: Long, val total: Long)

    /** Size and SHA-256 match the published model (checked only when a checksum is configured). */
    private fun matchesExpected(file: File): Boolean {
        val expected = BuildConfig.GEMMA_MODEL_SHA256
        if (expected.isBlank()) return file.length() >= MIN_BYTES
        if (file.length() != BuildConfig.GEMMA_MODEL_BYTES) return false
        return sha256(file).equals(expected, ignoreCase = true)
    }

    internal fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 20)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun finishDownload(app: Context, state: GemmaState) {
        prefs(app).edit { remove(KEY_DOWNLOAD_ID) }
        downloadTemp(app).delete()
        _state.value = state
    }

    /** Moves a finished file into place (copying when it sits on another volume). */
    private fun install(app: Context, from: File) {
        val target = file(app)
        target.parentFile?.mkdirs()
        if (from.length() < MIN_BYTES) {
            from.delete()
            _state.value = GemmaState.NotInstalled("The file is too small to be the Gemma model.")
            return
        }
        if (!from.renameTo(target)) {
            from.copyTo(target, overwrite = true)
            from.delete()
        }
        _state.value = GemmaState.Installed(target.length())
    }

    private fun describe(app: Context, uri: Uri): Pair<String?, Long?> =
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use null to null
            val name = c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let(c::getString)
            val size = c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let(c::getLong)
            name to size
        } ?: (null to null)

    // DownloadManager can only write to shared or app-specific external storage; the file moves to no-backup storage after.
    private fun downloadTemp(app: Context) = File(app.getExternalFilesDir(null) ?: app.cacheDir, "$FILE_NAME.download")

    private fun freeBytes(app: Context) = StatFs(app.noBackupFilesDir.path).availableBytes

    private fun prefs(app: Context) = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun notEnoughSpace(size: Long) =
        "Not enough free space: the model needs about ${(size + SPARE_BYTES) / 1_000_000} MB free."
}
