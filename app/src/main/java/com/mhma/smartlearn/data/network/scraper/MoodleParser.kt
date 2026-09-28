package com.mhma.smartlearn.data.network.scraper

import com.mhma.smartlearn.data.network.MoodleUrls
import com.mhma.smartlearn.util.FileNames
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser

/**
 * Reads Moodle's HTML (and the JSON its own web UI fetches) and turns it into plain objects.
 *
 * Written against Moodle 4.x markup (the Boost theme) with fallbacks for the older 3.x layout.
 * No Android classes in here so it can be unit tested on a normal JVM.
 */
class MoodleParser(private val baseUrl: String = MoodleUrls.BASE) {

    // ------------------------------------------------------------------ session key

    fun sesskey(html: String): String? {
        for (pattern in sesskeyPatterns) {
            pattern.find(html)?.groupValues?.get(1)?.let { return it }
        }
        return null
    }

    // ------------------------------------------------------------------ courses

    /**
     * Course cards rendered straight into the page. On Moodle 4 the "My courses" page is
     * filled in by JavaScript, so this often comes back empty and the repository falls back
     * to [coursesFromService].
     */
    fun courses(html: String): List<ScrapedCourse> {
        val doc = Jsoup.parse(html, baseUrl)
        val found = LinkedHashMap<Long, ScrapedCourse>()

        val cards = doc.select(
            "[data-region=course-content][data-course-id], " +
                ".course-info-container[data-course-id], " +
                ".coursebox[data-courseid]"
        )
        for (card in cards) {
            val id = card.attr("data-course-id").ifEmpty { card.attr("data-courseid") }.toLongOrNull() ?: continue
            val link = card.selectFirst("a[href*=course/view.php]")
            val name = card.selectFirst(".coursename, .multiline, .course-title, h3, h4")
                ?.let(::visibleText).orEmpty()
                .ifBlank { link?.let(::visibleText).orEmpty() }
            if (name.isBlank()) continue
            val url = link?.absUrl("href").orEmpty().ifBlank { MoodleUrls.course(id) }
            found.putIfAbsent(id, ScrapedCourse(id, name, "", url))
        }
        if (found.isNotEmpty()) return found.values.toList()

        // Last resort: any course link in the main content area.
        val main = doc.selectFirst("#region-main, #page-content, [role=main], main") ?: doc.body()
        for (a in main.select("a[href*=course/view.php?id=]")) {
            if (a.closest("nav, header, footer, .navbar, .drawer, .breadcrumb, .secondary-navigation") != null) continue
            val id = courseIdFrom(a.absUrl("href")) ?: continue
            val name = visibleText(a)
            if (name.isBlank() || found.containsKey(id)) continue
            found[id] = ScrapedCourse(id, name, "", MoodleUrls.course(id))
        }
        return found.values.toList()
    }

    /** Response of `core_course_get_enrolled_courses_by_timeline_classification`. */
    fun coursesFromService(json: String): List<ScrapedCourse> {
        val data = serviceData(json) ?: return emptyList()
        val array = data.optJSONArray("courses") ?: return emptyList()
        val out = ArrayList<ScrapedCourse>(array.length())
        for (i in 0 until array.length()) {
            val c = array.optJSONObject(i) ?: continue
            val id = c.optLong("id", -1)
            if (id <= 0) continue
            val fullName = decode(c.optString("fullname"))
            if (fullName.isBlank()) continue
            out += ScrapedCourse(
                id = id,
                fullName = fullName,
                shortName = decode(c.optString("shortname")),
                url = c.optString("viewurl").ifBlank { MoodleUrls.course(id) },
            )
        }
        return out
    }

    /** The "Course details" list on the profile page, which is always server-rendered. */
    fun coursesFromProfile(html: String): List<ScrapedCourse> {
        val doc = Jsoup.parse(html, baseUrl)
        val found = LinkedHashMap<Long, ScrapedCourse>()
        val main = doc.selectFirst("#region-main, #page-content, [role=main], main") ?: doc.body()
        for (a in main.select("a[href*=course=], a[href*=course/view.php?id=]")) {
            if (a.closest("nav, header, footer, .navbar, .drawer, .breadcrumb") != null) continue
            val id = courseIdFrom(a.absUrl("href")) ?: continue
            val name = visibleText(a)
            if (name.isBlank()) continue
            found.putIfAbsent(id, ScrapedCourse(id, name, "", MoodleUrls.course(id)))
        }
        return found.values.toList()
    }

    // ------------------------------------------------------------------ course content

    fun coursePage(html: String, courseId: Long): List<ScrapedSection> {
        val doc = Jsoup.parse(html, baseUrl)
        val sectionElements = doc.select("li.section[id~=^section-\\d+\$], div.section[id~=^section-\\d+\$]")

        if (sectionElements.isEmpty()) {
            // Unknown course format. Put everything we can find in one bucket.
            val items = doc.select("li.activity").mapNotNull(::parseItem)
            if (items.isEmpty()) return emptyList()
            return listOf(ScrapedSection(syntheticSectionId(courseId, 0), 0, "Course content", null, false, items))
        }

        val result = ArrayList<ScrapedSection>(sectionElements.size)
        for ((index, element) in sectionElements.withIndex()) {
            val number = element.id().removePrefix("section-").toIntOrNull() ?: index
            val id = sectionIdOf(element) ?: syntheticSectionId(courseId, number)
            val name = sectionName(element, number)
            val pageUrl = element
                .selectFirst("h3.sectionname a[href], .sectionname a[href], .section-title a[href], h3 a[href*=section]")
                ?.absUrl("href")
                ?.takeIf { it.isNotBlank() }
            val items = element.select("li.activity")
                .filter { nearestSection(it) === element }
                .mapNotNull(::parseItem)
            // "One section per page" course layouts render the other sections as summaries.
            val summaryOnly = element.hasClass("section-summary") && items.isEmpty()
            result += ScrapedSection(id, number, name, pageUrl, summaryOnly, items)
        }
        return result
    }

    /** A single section page (`course/section.php?id=` or `view.php?id=&section=`). */
    fun sectionPage(html: String, courseId: Long, number: Int): ScrapedSection? {
        val sections = coursePage(html, courseId)
        return sections.firstOrNull { it.number == number }
            ?: sections.firstOrNull { it.items.isNotEmpty() }
    }

    private fun parseItem(li: Element): ScrapedItem? {
        val module = li.classNames().firstOrNull { it.startsWith("modtype_") }
            ?.removePrefix("modtype_")
            ?: "unknown"
        val kind = ItemKind.fromModule(module) ?: return null

        val moduleId = li.id().removePrefix("module-").toLongOrNull()
            ?: li.attr("data-id").toLongOrNull()
            ?: return null

        val link = li.selectFirst("a.aalink[href], .activityname a[href], .activity-item a[href*=/mod/], a[href*=/mod/]")
        val url = link?.absUrl("href")?.takeIf { it.isNotBlank() }
            ?: "$baseUrl/mod/$module/view.php?id=$moduleId"

        val title = firstNonBlank(
            li.selectFirst(".activity-item[data-activityname]")?.attr("data-activityname"),
            li.selectFirst(".instancename")?.let(::visibleText),
            li.selectFirst(".activityname")?.let(::visibleText),
            link?.let(::visibleText),
        ) ?: "Untitled ${module.replaceFirstChar { it.uppercase() }}"

        val icon = li.selectFirst("img.activityicon[src], .activityiconcontainer img[src], img.iconlarge[src]")
            ?.attr("src").orEmpty()
        val details = li.selectFirst(".resourcelinkdetails, .afterlink")?.let(::visibleText).orEmpty()

        val dueAt = li.select("[data-region=activity-dates] div, .activity-dates div, [data-region=activity-dates]")
            .asSequence()
            .map { it.text() }
            .firstOrNull { it.trim().startsWith("Due", ignoreCase = true) }
            ?.let { MoodleDates.parse(it) }

        return ScrapedItem(
            moduleId = moduleId,
            title = title,
            kind = kind,
            url = url,
            fileHint = if (kind == ItemKind.FILE) fileHint(icon, details, url) else "",
            dueAtMillis = dueAt,
            details = details,
        )
    }

    private fun sectionIdOf(element: Element): Long? {
        // Moodle 4 puts the database id on the <li>; 3.x only has it in the heading id.
        element.attr("data-id").toLongOrNull()?.let { return it }
        element.selectFirst("[id~=^sectionid-\\d+-title\$]")?.id()
            ?.let { sectionTitleId.find(it)?.groupValues?.get(1)?.toLongOrNull() }
            ?.let { return it }
        sectionTitleId.find(element.attr("aria-labelledby"))?.groupValues?.get(1)?.toLongOrNull()?.let { return it }
        return null
    }

    private fun sectionName(element: Element, number: Int): String {
        element.attr("data-sectionname").takeIf { it.isNotBlank() }?.let { return clean(it) }
        val heading = element.selectFirst(".sectionname, .section-title, h3, h4")
        val text = heading?.let(::visibleText).orEmpty()
        return text.ifBlank { if (number == 0) "General" else "Section $number" }
    }

    private fun nearestSection(item: Element): Element? =
        item.parents().firstOrNull { it.hasClass("section") && it.id().startsWith("section-") }

    /** Real section ids are positive database ids; made-up ones are negative so they can't collide. */
    private fun syntheticSectionId(courseId: Long, number: Int): Long = -(courseId * 1000 + number)

    // ------------------------------------------------------------------ events

    /** `calendar/view.php?view=upcoming`. */
    fun upcomingEvents(html: String): List<ScrapedEvent> {
        val doc = Jsoup.parse(html, baseUrl)
        val out = ArrayList<ScrapedEvent>()
        for (event in doc.select("[data-region=event-item], div.event[data-event-id]")) {
            val id = event.attr("data-event-id").toLongOrNull() ?: continue
            val kind = event.attr("data-event-eventtype").ifBlank { "due" }
            val rawTitle = event.attr("data-event-title").ifBlank {
                event.selectFirst("h3.name, .name")?.let(::visibleText).orEmpty()
            }
            val title = tidyEventTitle(clean(rawTitle), kind)
            if (title.isBlank()) continue

            val courseLink = event.selectFirst("a[href*=course/view.php]")
            val courseId = event.attr("data-course-id").toLongOrNull()?.takeIf { it > 1 }
                ?: courseLink?.let { courseIdFrom(it.absUrl("href")) }
                ?: 0L
            val courseName = courseLink?.let(::visibleText).orEmpty()

            val url = event.selectFirst(".card-footer a[href], a.card-link[href], a[href*=/mod/]")
                ?.absUrl("href")
                ?.takeIf { it.isNotBlank() }

            val dueAt = event.select(".description .col-11, .description .row div, .date, .description")
                .asSequence()
                .map { it.text() }
                .mapNotNull { MoodleDates.parse(it) }
                .firstOrNull()

            out += ScrapedEvent(id, title, courseId, courseName, dueAt, url, kind)
        }
        return out
    }

    /** Response of `core_calendar_get_action_events_by_timesort` (what the Timeline block uses). */
    fun eventsFromService(json: String): List<ScrapedEvent> {
        val data = serviceData(json) ?: return emptyList()
        val array = data.optJSONArray("events") ?: return emptyList()
        val out = ArrayList<ScrapedEvent>(array.length())
        for (i in 0 until array.length()) {
            val e = array.optJSONObject(i) ?: continue
            val id = e.optLong("id", -1)
            if (id <= 0) continue
            val seconds = e.optLong("timesort", e.optLong("timestart", 0L))
            if (seconds <= 0) continue
            val course = e.optJSONObject("course")
            val courseId = course?.optLong("id", 0L) ?: 0L
            val kind = e.optString("eventtype").ifBlank { "due" }
            out += ScrapedEvent(
                id = id,
                title = tidyEventTitle(decode(e.optString("name")), kind),
                courseId = if (courseId > 1) courseId else 0L,
                courseName = decode(course?.optString("fullname").orEmpty()),
                dueAtMillis = seconds * 1000,
                url = e.optString("url").takeIf { it.isNotBlank() },
                kind = kind,
            )
        }
        return out
    }

    // ------------------------------------------------------------------ files

    /** Finds the actual file behind a `mod/resource/view.php` page when Moodle embeds instead of redirecting. */
    fun fileUrlFromResourcePage(html: String): String? {
        val doc = Jsoup.parse(html, baseUrl)
        val selectors = listOf(
            ".resourceworkaround a[href]",
            "#resourceobject[data]",
            "object[data*=pluginfile.php]",
            "iframe[src*=pluginfile.php]",
            "#resourceobject[src]",
            ".resourcecontent a[href*=pluginfile.php]",
            "img.resourceimage[src]",
            "video source[src]",
            "audio source[src]",
            "a[href*=pluginfile.php]",
        )
        for (selector in selectors) {
            val element = doc.selectFirst(selector) ?: continue
            val attr = when {
                element.hasAttr("href") -> "href"
                element.hasAttr("data") -> "data"
                else -> "src"
            }
            val url = element.absUrl(attr)
            if (url.isNotBlank()) return url
        }
        return null
    }

    /** Files listed on a `mod/folder/view.php` page. */
    fun folderFiles(html: String): List<ScrapedFile> {
        val doc = Jsoup.parse(html, baseUrl)
        val found = LinkedHashMap<String, ScrapedFile>()
        for (a in doc.select("a[href*=pluginfile.php]")) {
            val url = a.absUrl("href")
            if (url.isBlank()) continue
            val name = visibleText(a).ifBlank { FileNames.fromUrl(url) }
            if (name.isBlank()) continue
            found.putIfAbsent(url.substringBefore('?'), ScrapedFile(name, url))
        }
        return found.values.toList()
    }

    // ------------------------------------------------------------------ helpers

    private fun fileHint(iconUrl: String, details: String, url: String): String {
        FileNames.extensionFromUrl(url).takeIf { it.isNotEmpty() }?.let { return it }

        val icon = iconTypePattern.find(iconUrl.replace("%2F", "/", ignoreCase = true))
            ?.groupValues?.get(1)?.lowercase()
        when (icon) {
            "pdf" -> return "pdf"
            "powerpoint" -> return "pptx"
            "document", "writer" -> return "docx"
            "spreadsheet", "calc" -> return "xlsx"
            "archive" -> return "zip"
            "video", "mpeg", "avi", "quicktime" -> return "mp4"
            "audio", "mp3", "wav" -> return "mp3"
            "image", "jpeg", "png", "gif" -> return "jpg"
            "text", "markup", "sourcecode" -> return "txt"
        }

        val lower = details.lowercase()
        return when {
            "pdf" in lower -> "pdf"
            "powerpoint" in lower || "presentation" in lower -> "pptx"
            "word" in lower -> "docx"
            "excel" in lower || "spreadsheet" in lower -> "xlsx"
            "zip" in lower || "archive" in lower -> "zip"
            "video" in lower -> "mp4"
            "image" in lower -> "jpg"
            else -> ""
        }
    }

    private fun tidyEventTitle(title: String, kind: String): String {
        var t = title
        if (kind == "due") t = t.removeSuffix(" is due")
        if (kind == "close") t = t.removeSuffix(" closes")
        if (kind == "open") t = t.removeSuffix(" opens")
        return t.trim()
    }

    private fun serviceData(json: String): JSONObject? {
        val first = try {
            JSONArray(json).optJSONObject(0)
        } catch (e: Exception) {
            null
        } ?: return null
        if (first.optBoolean("error", false)) return null
        return first.optJSONObject("data")
    }

    private fun courseIdFrom(url: String): Long? {
        val pattern = if (url.contains("course/view.php")) idParam else courseParam
        return pattern.find(url)?.groupValues?.get(1)?.toLongOrNull()
    }

    /** Text without the screen-reader-only bits Moodle sprinkles everywhere. */
    private fun visibleText(element: Element): String {
        val copy = element.clone()
        copy.select(".accesshide, .sr-only, .visually-hidden").remove()
        return clean(copy.text())
    }

    private fun decode(value: String): String = clean(Parser.unescapeEntities(value, false))

    private fun clean(value: String): String =
        value.replace('\u00a0', ' ').replace(whitespace, " ").trim()

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }?.let(::clean)?.takeIf { it.isNotBlank() }

    private companion object {
        val sesskeyPatterns = listOf(
            Regex("\"sesskey\"\\s*:\\s*\"([A-Za-z0-9]+)\""),
            Regex("sesskey=([A-Za-z0-9]{6,})"),
            Regex("name=\"sesskey\"\\s+value=\"([A-Za-z0-9]+)\""),
        )
        val sectionTitleId = Regex("sectionid-(\\d+)-title")
        val idParam = Regex("[?&]id=(\\d+)")
        val courseParam = Regex("[?&]course=(\\d+)")
        // Matches ".../f/pdf-24" (slash arguments) and "...&image=f/pdf" (query arguments).
        val iconTypePattern = Regex("[/=]f/([a-z0-9]+)(?:-\\d+)?(?:[?#.&]|$)", RegexOption.IGNORE_CASE)
        val whitespace = Regex("\\s+")
    }
}
