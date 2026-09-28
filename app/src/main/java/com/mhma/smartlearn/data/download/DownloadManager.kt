package com.mhma.smartlearn.data.download

import android.content.Context
import android.os.Environment
import com.mhma.smartlearn.data.local.dao.CourseDao
import com.mhma.smartlearn.data.local.dao.ResourceDao
import com.mhma.smartlearn.data.local.entity.ResourceEntity
import com.mhma.smartlearn.data.network.MoodleClient
import com.mhma.smartlearn.data.network.SessionExpiredException
import com.mhma.smartlearn.data.network.scraper.MoodleParser
import com.mhma.smartlearn.util.FileNames
import com.mhma.smartlearn.util.LocalFiles
import com.mhma.smartlearn.util.friendlyMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Streams lecture files to the app's downloads folder and keeps Room in step.
 *
 * Downloads run on an application-wide scope so leaving the screen doesn't cancel them.
 * Progress is exposed as a map keyed by resource id; entries disappear once a file is saved
 * (Room's `isDownloaded` flag takes over) and stick around on failure until dismissed.
 */
@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: MoodleClient,
    private val parser: MoodleParser,
    private val resourceDao: ResourceDao,
    private val courseDao: CourseDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<Long, Job>()

    private val _progress = MutableStateFlow<Map<Long, DownloadProgress>>(emptyMap())
    val progress: StateFlow<Map<Long, DownloadProgress>> = _progress.asStateFlow()

    fun download(resource: ResourceEntity) {
        if (jobs.containsKey(resource.id)) return
        set(DownloadProgress(resource.id, DownloadProgress.State.QUEUED))

        jobs[resource.id] = scope.launch {
            try {
                run(resource)
                remove(resource.id)
            } catch (e: CancellationException) {
                remove(resource.id)
                throw e
            } catch (e: SessionExpiredException) {
                fail(resource.id, "Sign in again to download this file.")
            } catch (e: Exception) {
                fail(resource.id, e.friendlyMessage())
            } finally {
                jobs.remove(resource.id)
            }
        }
    }

    fun cancel(resourceId: Long) {
        jobs[resourceId]?.cancel()
    }

    /** Clears a failed entry from the progress map. */
    fun dismiss(resourceId: Long) {
        if (!jobs.containsKey(resourceId)) remove(resourceId)
    }

    /** Deletes the saved file and marks the resource as not downloaded. */
    suspend fun remove(resource: ResourceEntity) {
        cancel(resource.id)
        LocalFiles.delete(resource.localFilePath)
        resourceDao.markRemoved(resource.id)
    }

    // ------------------------------------------------------------------ internals

    private suspend fun run(resource: ResourceEntity) {
        var response = client.open(startUrlFor(resource))

        // Some resources are set to "embed", so view.php hands back a page instead of a file.
        if (response.isHtml()) {
            val html = response.use { it.body?.string().orEmpty() }
            val fileUrl = parser.fileUrlFromResourcePage(html)
                ?: throw IOException("This item doesn't have a file to download. Open it in Smart Learning instead.")
            response = client.open(fileUrl)
            if (response.isHtml()) {
                response.close()
                throw IOException("Couldn't reach the file behind this link.")
            }
        }

        response.use { resp ->
            if (!resp.isSuccessful) throw IOException("Smart Learning answered ${resp.code} while downloading.")
            val body = resp.body ?: throw IOException("The server sent an empty file.")

            val fileName = fileNameFor(resource, resp)
            val folder = folderFor(resource.courseId)
            if (!folder.exists() && !folder.mkdirs()) throw IOException("Couldn't create the downloads folder.")

            val target = File(folder, fileName)
            val partial = File(folder, "$fileName.part")
            val total = body.contentLength()

            set(DownloadProgress(resource.id, DownloadProgress.State.RUNNING, 0L, total))

            try {
                body.byteStream().use { input ->
                    partial.outputStream().buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var done = 0L
                        var lastTick = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            done += read
                            coroutineContext.ensureActive()

                            val now = System.currentTimeMillis()
                            if (now - lastTick > 150) {
                                lastTick = now
                                set(DownloadProgress(resource.id, DownloadProgress.State.RUNNING, done, total))
                            }
                        }
                    }
                }
                if (target.exists()) target.delete()
                if (!partial.renameTo(target)) throw IOException("Couldn't save the file.")
            } catch (e: Exception) {
                partial.delete()
                throw e
            }

            resourceDao.markDownloaded(
                id = resource.id,
                path = target.absolutePath,
                fileType = FileNames.extensionOf(target.name),
                size = target.length(),
            )
        }
    }

    private fun startUrlFor(resource: ResourceEntity): String {
        val url = resource.downloadUrl
        return when {
            url.contains("pluginfile.php") -> url
            // Asking view.php to redirect gets us the file straight away in most setups.
            url.contains("/mod/resource/view.php") && !url.contains("redirect=1") ->
                url + (if (url.contains('?')) "&" else "?") + "redirect=1"
            else -> url
        }
    }

    private fun fileNameFor(resource: ResourceEntity, response: Response): String {
        val fromHeader = FileNames.fromContentDisposition(response.header("Content-Disposition"))
        val fromUrl = FileNames.fromUrl(response.request.url.toString())
            .takeIf { FileNames.extensionOf(it).isNotEmpty() && !it.endsWith(".php") }

        var name = fromHeader?.takeIf { it.isNotBlank() } ?: fromUrl ?: resource.title
        if (FileNames.extensionOf(name).isEmpty()) {
            val ext = extensionFromContentType(response.header("Content-Type"))
                .ifEmpty { resource.fileType }
            if (ext.isNotEmpty()) name = "$name.$ext"
        }
        return FileNames.sanitize(name, fallback = "file-${resource.id}")
    }

    private suspend fun folderFor(courseId: Long): File {
        val course = courseDao.get(courseId)
        val label = when {
            course == null -> "Course $courseId"
            course.courseCode.isNotBlank() -> course.courseCode
            else -> course.title
        }
        return File(baseDir(), FileNames.sanitize(label, fallback = "Course $courseId"))
    }

    private fun baseDir(): File =
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, Environment.DIRECTORY_DOWNLOADS)

    private fun extensionFromContentType(contentType: String?): String {
        val type = contentType?.substringBefore(';')?.trim()?.lowercase() ?: return ""
        return when (type) {
            "application/pdf" -> "pdf"
            "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "pptx"
            "application/vnd.ms-powerpoint" -> "ppt"
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
            "application/msword" -> "doc"
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx"
            "application/vnd.ms-excel" -> "xls"
            "application/zip", "application/x-zip-compressed" -> "zip"
            "application/x-rar-compressed", "application/vnd.rar" -> "rar"
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "video/mp4" -> "mp4"
            "audio/mpeg" -> "mp3"
            "text/plain" -> "txt"
            "text/csv" -> "csv"
            else -> ""
        }
    }

    private fun Response.isHtml(): Boolean =
        header("Content-Type")?.contains("text/html", ignoreCase = true) == true

    private fun set(progress: DownloadProgress) {
        _progress.update { it + (progress.resourceId to progress) }
    }

    private fun fail(resourceId: Long, message: String) {
        set(DownloadProgress(resourceId, DownloadProgress.State.FAILED, message = message))
    }

    private fun remove(resourceId: Long) {
        _progress.update { it - resourceId }
    }
}
