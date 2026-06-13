package eu.kanade.tachiyomi.extension.en.pawchive

import eu.kanade.tachiyomi.source.model.Filter

// ─────────────────────────────────────────────────────────────
//  Service / platform filter
// ─────────────────────────────────────────────────────────────

class ServiceFilter : Filter.Select<String>("Service", SERVICE_LABELS) {
    val selected: String get() = SERVICE_SLUGS[state]
}

private val SERVICE_LABELS = arrayOf(
    "All",
    "Patreon",
    "Fanbox (Pixiv)",
    "Subscribestar",
    "Gumroad",
    "Discord",
    "DLsite",
    "Boosty",
    "Afdian",
    "Coomer (OnlyFans)",
)

val SERVICE_SLUGS = arrayOf(
    "",
    "patreon",
    "fanbox",
    "subscribestar",
    "gumroad",
    "discord",
    "dlsite",
    "boosty",
    "afdian",
    "onlyfans",
)

// ─────────────────────────────────────────────────────────────
//  Sort order filter
// ─────────────────────────────────────────────────────────────

class SortFilter : Filter.Select<String>("Sort by", SORT_LABELS) {
    val selected: String get() = SORT_KEYS[state]
}

private val SORT_LABELS = arrayOf(
    "Recently Updated",
    "Name (A → Z)",
    "Most Favourited",
    "Newest Import",
)

private val SORT_KEYS = arrayOf(
    "updated",
    "name",
    "favorited",
    "indexed",
)

// ─────────────────────────────────────────────────────────────
//  Tag / keyword filter
// ─────────────────────────────────────────────────────────────

/** Free-text tag that is appended as ?tag= on creator-search requests. */
class TagFilter : Filter.Text("Tag (optional)")

// ─────────────────────────────────────────────────────────────
//  Image-only toggle
// ─────────────────────────────────────────────────────────────

/**
 * When checked, the chapter list skips posts that contain zero
 * readable image attachments (e.g. pure text or video posts).
 */
class ImageOnlyFilter : Filter.CheckBox("Hide non-image posts", true)

// ─────────────────────────────────────────────────────────────
//  Post date range
// ─────────────────────────────────────────────────────────────

class DateRangeGroup : Filter.Group<Filter.Text>(
    "Date range (YYYY-MM-DD)",
    listOf(
        Filter.Text("From"),
        Filter.Text("To"),
    ),
)
