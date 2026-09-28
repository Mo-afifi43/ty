package com.mhma.smartlearn.data.repository

import androidx.room.withTransaction
import com.mhma.smartlearn.data.local.AppDatabase
import com.mhma.smartlearn.data.local.dao.CourseDao
import com.mhma.smartlearn.data.local.dao.ResourceDao
import com.mhma.smartlearn.data.local.dao.SectionDao
import com.mhma.smartlearn.data.local.entity.CourseEntity
import com.mhma.smartlearn.data.local.entity.CourseFileCount
import com.mhma.smartlearn.data.local.entity.CourseSectionEntity
import com.mhma.smartlearn.data.local.entity.ResourceEntity
import com.mhma.smartlearn.data.local.entity.SectionWithItems
import com.mhma.smartlearn.data.network.MoodleClient
import com.mhma.smartlearn.data.network.MoodleUrls
import com.mhma.smartlearn.data.network.SessionExpiredException
import com.mhma.smartlearn.data.network.scraper.ItemKind
import com.mhma.smartlearn.data.network.scraper.MoodleParser
import com.mhma.smartlearn.data.network.scraper.ScrapedCourse
import com.mhma.smartlearn.data.network.scraper.ScrapedItem
import com.mhma.smartlearn.data.network.scraper.ScrapedSection
import com.mhma.smartlearn.util.FileNames
import com.mhma.smartlearn.util.LocalFiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Offline-first access to courses and their content.
 *
 * Screens only ever read from Room through the `observe*` flows. The `refresh*` functions
 * hit the portal and quietly rewrite the tables; Room then pushes the change to whoever
 * is watching.
 */
@Singleton
class CourseRepository @Inject constructor(
    private val client: MoodleClient,
    private val parser: MoodleParser,
    private val db: AppDatabase,
    private val courseDao: CourseDao,
    private val sectionDao: SectionDao,
    private val resourceDao: ResourceDao,
) {

    fun observeCourses(): Flow<List<CourseEntity>> = courseDao.observeAll()

    fun observeCourse(courseId: Long): Flow<CourseEntity?> = courseDao.observe(courseId)

    fun observeSections(courseId: Long): Flow<List<SectionWithItems>> =
        sectionDao.observeWithItems(courseId).map { sections ->
            sections.map { it.copy(items = it.items.sortedBy { item -> item.orderIndex }) }
        }

    fun observeFileCounts(): Flow<List<CourseFileCount>> = resourceDao.observeFileCounts()

    /**
     * Fetches the list of enrolled courses and mirrors it into Room.
     * @return the courses as stored, so callers can go on to refresh each one.
     */
    suspend fun refreshCourses(): List<CourseEntity> {
        val html = client.getHtml(MoodleUrls.MY_COURSES)

        // Moodle 4 fills the "My courses" page with JavaScript, so the static HTML is
        // usually empty. Fall back to the very call that page makes, then to the profile page.
        var scraped = parser.courses(html)
        if (scraped.isEmpty()) scraped = coursesFromService()
        if (scraped.isEmpty()) scraped = parser.coursesFromProfile(client.getHtml(MoodleUrls.PROFILE))
        if (scraped.isEmpty()) throw IOException("We couldn't find any courses on your account.")

        return storeCourses(scraped)
    }

    /** Fetches one course page (plus section pages and folders when needed) and mirrors it into Room. */
    suspend fun refreshCourseContent(courseId: Long) {
        val html = client.getHtml(MoodleUrls.course(courseId))
        var sections = parser.coursePage(html, courseId)

        // "One section per page" layouts only give us summaries; fetch the real thing.
        sections = sections.map { section ->
            if (!section.summaryOnly || section.pageUrl == null) return@map section
            val fetched = quietly { parser.sectionPage(client.getHtml(section.pageUrl), courseId, section.number) }
            if (fetched != null) section.copy(items = fetched.items) else section
        }

        sections = sections.map { section -> section.copy(items = expandFolders(section.items)) }

        storeCourseContent(courseId, sections)
    }

    // ------------------------------------------------------------------ internals

    private suspend fun coursesFromService(): List<ScrapedCourse> = quietly {
        val args = JSONObject()
            .put("offset", 0)
            .put("limit", 0)
            .put("classification", "all")
            .put("sort", "fullname")
        parser.coursesFromService(client.callService(METHOD_COURSES, args))
    } ?: emptyList()

    /** Replaces folder items with the files inside them, so each one gets its own download button. */
    private suspend fun expandFolders(items: List<ScrapedItem>): List<ScrapedItem> {
        if (items.none { it.kind == ItemKind.FOLDER }) return items
        val out = ArrayList<ScrapedItem>(items.size)
        var budget = MAX_FOLDERS_PER_SECTION
        for (item in items) {
            if (item.kind != ItemKind.FOLDER || budget-- <= 0) {
                out += item
                continue
            }
            val files = quietly { parser.folderFiles(client.getHtml(item.url)) }.orEmpty()
            if (files.isEmpty()) {
                out += item
                continue
            }
            for (file in files) {
                out += ScrapedItem(
                    moduleId = LocalFiles.stableId(file.url.substringBefore('?')),
                    title = file.title,
                    kind = ItemKind.FILE,
                    url = file.url,
                    fileHint = FileNames.extensionOf(file.title),
                    details = "In folder: ${item.title}",
                )
            }
        }
        return out
    }

    private suspend fun storeCourses(scraped: List<ScrapedCourse>): List<CourseEntity> {
        val existing = courseDao.getAll().associateBy { it.id }
        val entities = scraped.map { c ->
            val old = existing[c.id]
            CourseEntity(
                id = c.id,
                title = c.fullName,
                courseCode = c.shortName.ifBlank { old?.courseCode.orEmpty() },
                url = c.url,
                lastSynced = old?.lastSynced ?: 0L,
            )
        }
        val keep = entities.map { it.id }

        // Courses the student left: drop their downloads from disk before Room forgets them.
        val goneIds = existing.keys.filterNot { it in keep }
        if (goneIds.isNotEmpty()) {
            resourceDao.getDownloadedForCourses(goneIds).forEach { LocalFiles.delete(it.localFilePath) }
        }

        db.withTransaction {
            courseDao.upsertAll(entities)
            courseDao.deleteAllExcept(keep)
            resourceDao.deleteForOtherCourses(keep)
        }
        return entities
    }

    private suspend fun storeCourseContent(courseId: Long, sections: List<ScrapedSection>) {
        val existing = resourceDao.getForCourse(courseId).associateBy { it.id }

        val sectionEntities = sections.mapIndexed { index, s ->
            CourseSectionEntity(id = s.id, courseId = courseId, sectionName = s.name, orderIndex = index)
        }

        val resourceEntities = ArrayList<ResourceEntity>()
        for (section in sections) {
            section.items.forEachIndexed { index, item ->
                // Keep what the student already downloaded, as long as the file is still there.
                val kept = existing[item.moduleId]?.takeIf { it.isDownloaded && LocalFiles.exists(it.localFilePath) }
                resourceEntities += ResourceEntity(
                    id = item.moduleId,
                    sectionId = section.id,
                    courseId = courseId,
                    title = item.title,
                    kind = item.kind.name,
                    downloadUrl = item.url,
                    localFilePath = kept?.localFilePath,
                    fileType = kept?.fileType?.takeIf { it.isNotEmpty() } ?: item.fileHint,
                    isDownloaded = kept != null,
                    fileSizeBytes = kept?.fileSizeBytes ?: 0L,
                    dueDateMillis = item.dueAtMillis,
                    details = item.details,
                    orderIndex = index,
                )
            }
        }

        db.withTransaction {
            sectionDao.upsertAll(sectionEntities)
            resourceDao.upsertAll(resourceEntities)
            resourceDao.deleteStale(courseId, resourceEntities.map { it.id })
            sectionDao.deleteStale(courseId, sectionEntities.map { it.id })
            courseDao.markSynced(courseId, System.currentTimeMillis())
        }
    }

    /** Runs a best-effort step. Session loss and cancellation still propagate. */
    private inline fun <T> quietly(block: () -> T): T? = try {
        block()
    } catch (e: SessionExpiredException) {
        throw e
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val METHOD_COURSES = "core_course_get_enrolled_courses_by_timeline_classification"
        const val MAX_FOLDERS_PER_SECTION = 12
    }
}
