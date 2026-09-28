package com.mhma.smartlearn.data.network.scraper

// Plain results coming out of the parser. The repository turns these into Room entities.

data class ScrapedCourse(
    val id: Long,
    val fullName: String,
    val shortName: String,
    val url: String,
)

enum class ItemKind {
    FILE, FOLDER, ASSIGNMENT, QUIZ, LINK, PAGE, FORUM, OTHER;

    companion object {
        /** Maps Moodle's `modtype_*` class to something the UI understands. Null means "don't show". */
        fun fromModule(module: String): ItemKind? = when (module) {
            "resource" -> FILE
            "folder" -> FOLDER
            "assign", "workshop" -> ASSIGNMENT
            "quiz", "choice", "feedback", "survey" -> QUIZ
            "url" -> LINK
            "page", "book", "lesson", "h5pactivity", "scorm", "glossary", "wiki" -> PAGE
            "forum", "chat" -> FORUM
            // Labels are just text on the course page and subsections show up as their own sections.
            "label", "subsection" -> null
            else -> OTHER
        }
    }
}

data class ScrapedItem(
    val moduleId: Long,
    val title: String,
    val kind: ItemKind,
    val url: String,
    /** Best guess at the file extension ("pdf", "pptx"...) or empty if unknown. */
    val fileHint: String = "",
    val dueAtMillis: Long? = null,
    /** Small print shown under the title, e.g. "Uploaded 3/9/25". */
    val details: String = "",
)

data class ScrapedSection(
    val id: Long,
    val number: Int,
    val name: String,
    /** Link to the section's own page, when the heading is a link. */
    val pageUrl: String?,
    /** True when the course shows one section per page and this one is only a summary here. */
    val summaryOnly: Boolean,
    val items: List<ScrapedItem>,
)

data class ScrapedEvent(
    val id: Long,
    val title: String,
    /** 0 when the event isn't tied to a course. */
    val courseId: Long,
    val courseName: String,
    val dueAtMillis: Long?,
    val url: String?,
    /** Moodle's event type: due, open, close, gradingdue... */
    val kind: String,
)

data class ScrapedFile(
    val title: String,
    val url: String,
)
