package com.mhma.smartlearn.data.network.scraper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class MoodleParserTest {

    private val parser = MoodleParser("https://smartlearning.must.edu.eg")
    private val cairo = ZoneId.of("Africa/Cairo")

    // ---------------------------------------------------------------- sesskey

    @Test
    fun `sesskey is read from M cfg`() {
        assertEquals("Kq9zT2ab7C", parser.sesskey(fixture("course_page.html")))
    }

    @Test
    fun `sesskey falls back to the logout link`() {
        val html = """<a href="https://smartlearning.must.edu.eg/login/logout.php?sesskey=AbCdEf123">Log out</a>"""
        assertEquals("AbCdEf123", parser.sesskey(html))
        assertNull(parser.sesskey("<html><body>nothing here</body></html>"))
    }

    // ---------------------------------------------------------------- courses

    @Test
    fun `server rendered course cards are parsed and nav links ignored`() {
        val courses = parser.courses(fixture("my_courses_legacy.html"))
        assertEquals(listOf(42L, 17L), courses.map { it.id })
        assertEquals("Data Structures & Algorithms", courses[0].fullName)
        assertEquals("https://smartlearning.must.edu.eg/course/view.php?id=42", courses[0].url)
    }

    @Test
    fun `javascript only course page yields nothing`() {
        val html = """<html><body id="page-my-courses"><div id="region-main"><div data-region="myoverview"></div></div></body></html>"""
        assertTrue(parser.courses(html).isEmpty())
    }

    @Test
    fun `course service json is parsed with entities decoded`() {
        val courses = parser.coursesFromService(fixture("my_courses_service.json"))
        assertEquals(2, courses.size)
        assertEquals("Data Structures & Algorithms", courses[0].fullName)
        assertEquals("CS201", courses[0].shortName)
        assertEquals("https://smartlearning.must.edu.eg/course/view.php?id=42", courses[0].url)
        assertEquals("CS305", courses[1].shortName)
    }

    @Test
    fun `service errors give an empty list`() {
        assertTrue(parser.coursesFromService(fixture("service_error.json")).isEmpty())
        assertTrue(parser.coursesFromService("not json at all").isEmpty())
    }

    @Test
    fun `profile page lists enrolled courses once each`() {
        val courses = parser.coursesFromProfile(fixture("profile.html"))
        assertEquals(listOf(42L, 17L), courses.map { it.id })
        assertEquals("Data Structures & Algorithms", courses[0].fullName)
        assertEquals("Operating Systems", courses[1].fullName)
    }

    // ---------------------------------------------------------------- course page (Moodle 4)

    @Test
    fun `moodle 4 course page sections and items`() {
        val sections = parser.coursePage(fixture("course_page.html"), courseId = 42)

        assertEquals(listOf(501L, 502L, 503L, 504L), sections.map { it.id })
        assertEquals(listOf("General", "Week 1: Introduction", "Week 2: Stacks and Queues", "Extra reading"), sections.map { it.name })
        assertEquals(listOf(0, 1, 2, 3), sections.map { it.number })

        // General: the label is skipped, the forum stays.
        val general = sections[0]
        assertEquals(1, general.items.size)
        assertEquals(ItemKind.FORUM, general.items[0].kind)
        assertEquals("Announcements", general.items[0].title)
        assertFalse(general.summaryOnly)

        // Week 1: two files (icon based type detection), one folder, one link.
        val week1 = sections[1]
        assertEquals(listOf(9101L, 9102L, 9103L, 9104L), week1.items.map { it.moduleId })
        assertEquals("Lecture 1 - Arrays & Lists", week1.items[0].title)
        assertEquals(ItemKind.FILE, week1.items[0].kind)
        assertEquals("pdf", week1.items[0].fileHint)
        assertEquals("Uploaded 3/09/25", week1.items[0].details)
        assertEquals("https://smartlearning.must.edu.eg/mod/resource/view.php?id=9101", week1.items[0].url)
        assertEquals("pptx", week1.items[1].fileHint) // icon passed as image=f%2Fpowerpoint-24
        assertEquals(ItemKind.FOLDER, week1.items[2].kind)
        assertEquals(ItemKind.LINK, week1.items[3].kind)

        // Week 2: the assignment carries its due date; the subsection wrapper is dropped
        // and its file lands in the nested section instead of being counted twice.
        val week2 = sections[2]
        assertEquals(1, week2.items.size)
        val assignment = week2.items[0]
        assertEquals(ItemKind.ASSIGNMENT, assignment.kind)
        assertNotNull(assignment.dueAtMillis)
        val due = LocalDateTime.of(2025, 10, 19, 23, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(due, assignment.dueAtMillis)

        val extra = sections[3]
        assertEquals(listOf(9203L), extra.items.map { it.moduleId })
        assertEquals("docx", extra.items[0].fileHint)
    }

    // ---------------------------------------------------------------- course page (Moodle 3)

    @Test
    fun `legacy course page sections, ids from headings and summary-only sections`() {
        val sections = parser.coursePage(fixture("course_page_legacy.html"), courseId = 17)

        assertEquals(listOf(700L, 701L), sections.take(2).map { it.id })
        assertEquals("General", sections[0].name)
        assertEquals("28 September - 4 October", sections[1].name)

        val outline = sections[0].items.single()
        assertEquals("Course outline", outline.title)
        assertEquals("pdf", outline.fileHint)

        val week = sections[1]
        assertEquals(listOf(ItemKind.FILE, ItemKind.QUIZ), week.items.map { it.kind })
        assertEquals("pptx", week.items[0].fileHint)

        // The id comes from aria-labelledby when the heading itself has none.
        val summary = sections[2]
        assertEquals(702L, summary.id)
        assertTrue(summary.summaryOnly)
        assertEquals("https://smartlearning.must.edu.eg/course/view.php?id=17&section=2", summary.pageUrl)
        assertEquals("5 October - 11 October", summary.name)
    }

    @Test
    fun `sections without any id get a negative made-up one`() {
        val html = """
            <html><body><ul class="topics">
              <li id="section-4" class="section main clearfix"><div class="content"><h3 class="sectionname">Week 4</h3>
                <ul class="section"><li class="activity modtype_page" id="module-77"><a class="aalink" href="/mod/page/view.php?id=77"><span class="instancename">Notes</span></a></li></ul>
              </div></li>
            </ul></body></html>
        """.trimIndent()
        val section = parser.coursePage(html, courseId = 17).single()
        assertTrue(section.id < 0)
        assertEquals(4, section.number)
        assertEquals("Week 4", section.name)
        assertEquals(ItemKind.PAGE, section.items.single().kind)
    }

    @Test
    fun `section page picks the requested section number`() {
        val section = parser.sectionPage(fixture("course_page_legacy.html"), courseId = 17, number = 1)
        assertNotNull(section)
        assertEquals(701L, section!!.id)
    }

    @Test
    fun `page without sections still surfaces activities`() {
        val html = """
            <html><body><div class="course-content"><ul>
              <li class="activity modtype_resource" id="module-55"><a class="aalink" href="/mod/resource/view.php?id=55"><span class="instancename">Notes<span class="accesshide"> File</span></span></a></li>
            </ul></div></body></html>
        """.trimIndent()
        val sections = parser.coursePage(html, courseId = 9)
        assertEquals(1, sections.size)
        assertEquals("Course content", sections[0].name)
        assertEquals("Notes", sections[0].items.single().title)
        assertEquals("https://smartlearning.must.edu.eg/mod/resource/view.php?id=55", sections[0].items.single().url)
    }

    // ---------------------------------------------------------------- events

    @Test
    fun `upcoming events page is parsed`() {
        val events = parser.upcomingEvents(fixture("upcoming.html"))
        assertEquals(3, events.size)

        val assignment = events[0]
        assertEquals(7001L, assignment.id)
        assertEquals("Assignment 1", assignment.title) // " is due" stripped
        assertEquals(42L, assignment.courseId)
        assertEquals("Data Structures", assignment.courseName)
        assertEquals("due", assignment.kind)
        assertEquals("https://smartlearning.must.edu.eg/mod/assign/view.php?id=9201", assignment.url)
        val due = LocalDateTime.of(2025, 10, 19, 23, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(due, assignment.dueAtMillis)

        val quiz = events[1]
        assertEquals("Quiz 1", quiz.title)
        assertEquals("close", quiz.kind)
        assertEquals(17L, quiz.courseId)
        val closes = LocalDateTime.of(2025, 10, 23, 20, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(closes, quiz.dueAtMillis) // the &nbsp; in the date doesn't matter

        val site = events[2]
        assertEquals(0L, site.courseId) // course 1 is the site itself
        assertEquals("", site.courseName)
        val midterm = LocalDateTime.of(2025, 11, 1, 23, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(midterm, site.dueAtMillis)
    }

    @Test
    fun `timeline service json is parsed with epoch seconds`() {
        val events = parser.eventsFromService(fixture("events_service.json"))
        assertEquals(2, events.size)
        assertEquals("Assignment 1", events[0].title)
        assertEquals(1760914740L * 1000, events[0].dueAtMillis)
        assertEquals(42L, events[0].courseId)
        assertEquals("Data Structures & Algorithms", events[0].courseName)
        assertEquals("https://smartlearning.must.edu.eg/mod/assign/view.php?id=9201", events[0].url)
        assertEquals("Quiz 1", events[1].title)
        assertEquals("close", events[1].kind)
    }

    // ---------------------------------------------------------------- files

    @Test
    fun `embedded resource page exposes the file url`() {
        val url = parser.fileUrlFromResourcePage(fixture("resource_embed.html"))
        assertEquals(
            "https://smartlearning.must.edu.eg/pluginfile.php/1234/mod_resource/content/3/Lecture%201%20-%20Arrays%20%26%20Lists.pdf?forcedownload=1",
            url,
        )
        assertNull(parser.fileUrlFromResourcePage("<html><body><p>Nothing</p></body></html>"))
    }

    @Test
    fun `folder page lists each file once`() {
        val files = parser.folderFiles(fixture("folder.html"))
        assertEquals(listOf("Lab 1.pdf", "starter_code.zip"), files.map { it.title })
        assertTrue(files[0].url.endsWith("Lab%201.pdf?forcedownload=1"))
    }

    // ---------------------------------------------------------------- dates

    @Test
    fun `moodle date formats`() {
        val now = LocalDateTime.of(2025, 10, 1, 9, 0)
        fun at(y: Int, m: Int, d: Int, h: Int, min: Int) =
            LocalDateTime.of(y, m, d, h, min).atZone(cairo).toInstant().toEpochMilli()

        assertEquals(at(2025, 10, 19, 23, 59), MoodleDates.parse("Sunday, 19 October 2025, 11:59 PM", cairo, now))
        assertEquals(at(2025, 10, 19, 23, 59), MoodleDates.parse("Due: Sunday, 19 October 2025, 11:59 PM", cairo, now))
        assertEquals(at(2025, 10, 19, 23, 59), MoodleDates.parse("19 October 2025, 23:59", cairo, now))
        assertEquals(at(2025, 10, 19, 23, 59), MoodleDates.parse("Sun, 19 Oct 2025, 11:59 pm", cairo, now))
        assertEquals(at(2025, 10, 19, 23, 59), MoodleDates.parse("19/10/25, 23:59", cairo, now))
        assertEquals(at(2025, 10, 19, 23, 59), MoodleDates.parse("Sunday, 19 October 2025", cairo, now))
        assertEquals(at(2025, 10, 19, 23, 59), MoodleDates.parse("October 19, 2025, 11:59 PM", cairo, now))
        // No year: assume this year...
        assertEquals(at(2025, 10, 19, 23, 59), MoodleDates.parse("Sunday, 19 October, 11:59 PM", cairo, now))
        // ...unless that would be long gone, then it's next year.
        assertEquals(at(2026, 1, 5, 8, 0), MoodleDates.parse("Monday, 5 January, 8:00 AM", cairo, now))
        // Relative words from the timeline block.
        assertEquals(at(2025, 10, 1, 23, 59), MoodleDates.parse("Today, 11:59 PM", cairo, now))
        assertEquals(at(2025, 10, 2, 8, 0), MoodleDates.parse("Tomorrow, 8:00 AM", cairo, now))
        // Junk stays junk.
        assertNull(MoodleDates.parse("Go to activity", cairo, now))
        assertNull(MoodleDates.parse("", cairo, now))
        assertNull(MoodleDates.parse(null, cairo, now))
    }
}
