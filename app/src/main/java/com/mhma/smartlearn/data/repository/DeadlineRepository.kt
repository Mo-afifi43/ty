package com.mhma.smartlearn.data.repository

import androidx.room.withTransaction
import com.mhma.smartlearn.data.local.AppDatabase
import com.mhma.smartlearn.data.local.dao.DeadlineDao
import com.mhma.smartlearn.data.local.entity.DeadlineEntity
import com.mhma.smartlearn.data.network.MoodleClient
import com.mhma.smartlearn.data.network.MoodleUrls
import com.mhma.smartlearn.data.network.SessionExpiredException
import com.mhma.smartlearn.data.network.scraper.MoodleParser
import com.mhma.smartlearn.data.network.scraper.ScrapedEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeadlineRepository @Inject constructor(
    private val client: MoodleClient,
    private val parser: MoodleParser,
    private val db: AppDatabase,
    private val dao: DeadlineDao,
) {

    fun observeFrom(since: Long): Flow<List<DeadlineEntity>> = dao.observeFrom(since)

    suspend fun setCompleted(id: Long, done: Boolean) = dao.setCompleted(id, done)

    /** Pulls upcoming events from the calendar and mirrors them into Room. */
    suspend fun refresh() {
        val now = System.currentTimeMillis()

        var events = eventsFromCalendarPage()

        // The calendar page prints dates in words. If any of them didn't parse (or the page
        // was empty) ask the timeline service, which hands us proper timestamps.
        if (events.isEmpty() || events.any { it.dueAtMillis == null }) {
            val fromService = eventsFromService(now)
            if (fromService.isNotEmpty()) events = fromService
        }

        val dated = events.filter { it.dueAtMillis != null }
        if (dated.isEmpty() && events.isNotEmpty()) {
            throw IOException("Couldn't read the dates on the calendar page.")
        }

        val existing = dao.getAll().associateBy { it.id }
        val entities = dated.map { e ->
            DeadlineEntity(
                id = e.id,
                courseId = e.courseId,
                courseName = e.courseName,
                title = e.title,
                dueDateMillis = e.dueAtMillis!!,
                url = e.url,
                kind = e.kind,
                isCompleted = existing[e.id]?.isCompleted ?: false,
            )
        }

        db.withTransaction {
            dao.upsertAll(entities)
            dao.deleteStaleUpcoming(now, entities.map { it.id })
            dao.deleteOlderThan(now - TimeUnit.DAYS.toMillis(14))
        }
    }

    private suspend fun eventsFromCalendarPage(): List<ScrapedEvent> = try {
        parser.upcomingEvents(client.getHtml(MoodleUrls.UPCOMING_EVENTS))
    } catch (e: SessionExpiredException) {
        throw e
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList()
    }

    private suspend fun eventsFromService(now: Long): List<ScrapedEvent> = try {
        val args = JSONObject()
            .put("limitnum", 50)
            .put("timesortfrom", now / 1000 - TimeUnit.DAYS.toSeconds(3))
            .put("limittononsuspendedevents", true)
        parser.eventsFromService(client.callService(METHOD_EVENTS, args))
    } catch (e: SessionExpiredException) {
        throw e
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList()
    }

    private companion object {
        const val METHOD_EVENTS = "core_calendar_get_action_events_by_timesort"
    }
}
