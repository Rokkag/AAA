package eu.kanade.tachiyomi.extension.en.pawchive

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Tachiyomi / Mihon source extension for Pawchive — a kemono-style
 * creator-archive site that mirrors content from Patreon, Fanbox,
 * Subscribestar, and other subscription platforms.
 *
 * Conceptual mapping
 * ──────────────────
 *   SManga  → a creator profile (one artist / author on one platform)
 *   SChapter→ one post made by that creator
 *   Page    → an image file attached to that post
 *
 * API base: https://pawchive.net/api/v1
 */
class Pawchive : HttpSource() {

    override val name    = "Pawchive"
    override val baseUrl = "https://pawchive.net"
    override val lang    = "en"
    override val supportsLatest = true

    // Stable numeric ID for the source. Change this only if you fork.
    override val id: Long = 0x5061776368697665L  // "Pawchive" in hex

    private val apiUrl get() = "$baseUrl/api/v1"

    // ── HTTP client ─────────────────────────────────────────────────────────
    // Two requests per second to be polite; cloud-flare-aware client so the
    // extension survives sites protected by a JS challenge page.

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), permits = 2, period = 1)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Accept", "application/json")

    // ── JSON parser ─────────────────────────────────────────────────────────

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient          = true
        coerceInputValues  = true
    }

    // ── Date parsing ────────────────────────────────────────────────────────

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    // ════════════════════════════════════════════════════════════════════════
    //  POPULAR  (creators sorted by most-recently updated)
    // ════════════════════════════════════════════════════════════════════════

    override fun popularMangaRequest(page: Int): Request {
        val offset = (page - 1) * PAGE_SIZE
        return GET("$apiUrl/creators?sort=updated&o=$offset", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val creators = json.decodeFromString<List<CreatorDto>>(response.body.string())
        val mangas   = creators.map { it.toSManga() }
        return MangasPage(mangas, hasNextPage = mangas.size >= PAGE_SIZE)
    }

    // ════════════════════════════════════════════════════════════════════════
    //  LATEST UPDATES  (global post feed, de-duped to one entry per creator)
    // ════════════════════════════════════════════════════════════════════════

    override fun latestUpdatesRequest(page: Int): Request {
        val offset = (page - 1) * PAGE_SIZE
        return GET("$apiUrl/posts?o=$offset", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val posts  = json.decodeFromString<List<PostDto>>(response.body.string())
        val seen   = mutableSetOf<String>()
        val mangas = mutableListOf<SManga>()

        for (post in posts) {
            val key = "${post.service}/${post.userId}"
            if (seen.add(key)) {
                mangas.add(
                    SManga.create().apply {
                        url           = "/${post.service}/user/${post.userId}"
                        title         = post.userName?.takeIf { it.isNotBlank() }
                            ?: post.userId ?: "Unknown Creator"
                        thumbnail_url = creatorIconUrl(post.service, post.userId)
                        status        = SManga.ONGOING
                        genre         = post.service?.titleCase()
                        author        = title
                    },
                )
            }
        }

        return MangasPage(mangas, hasNextPage = posts.size >= PAGE_SIZE)
    }

    // ════════════════════════════════════════════════════════════════════════
    //  SEARCH  (creator name query + optional service / sort / tag filters)
    // ════════════════════════════════════════════════════════════════════════

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val offset  = (page - 1) * PAGE_SIZE

        val service = filters.filterIsInstance<ServiceFilter>().firstOrNull()?.selected.orEmpty()
        val sort    = filters.filterIsInstance<SortFilter>().firstOrNull()?.selected.orEmpty()
        val tag     = filters.filterIsInstance<TagFilter>().firstOrNull()?.state.orEmpty()

        val url = "$apiUrl/creators/search".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank())   addQueryParameter("q",       query)
            if (service.isNotBlank()) addQueryParameter("service",  service)
            if (sort.isNotBlank())    addQueryParameter("sort",     sort)
            if (tag.isNotBlank())     addQueryParameter("tag",      tag)
            addQueryParameter("o", offset.toString())
        }.build()

        return GET(url.toString(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // ════════════════════════════════════════════════════════════════════════
    //  MANGA DETAILS  (full creator profile)
    // ════════════════════════════════════════════════════════════════════════

    override fun mangaDetailsRequest(manga: SManga): Request {
        val (service, creatorId) = parseCreatorUrl(manga.url)
        return GET("$apiUrl/creators/$service/$creatorId", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga =
        json.decodeFromString<CreatorDto>(response.body.string()).toSManga()

    // ════════════════════════════════════════════════════════════════════════
    //  CHAPTER LIST  (all posts for a creator, paginated automatically)
    // ════════════════════════════════════════════════════════════════════════

    // We override fetchChapterList instead of chapterListRequest/Parse so we
    // can walk through all pagination pages in one shot without blocking the
    // caller on multiple round-trips made inside a parse method.

    override fun chapterListRequest(manga: SManga): Request =
        throw UnsupportedOperationException("Use fetchChapterList instead.")

    override fun chapterListParse(response: Response): List<SChapter> =
        throw UnsupportedOperationException("Use fetchChapterList instead.")

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val (service, creatorId) = parseCreatorUrl(manga.url)

        // Collect every post across all pages.
        val chapters   = mutableListOf<SChapter>()
        var offset     = 0
        var pageNumber = 0

        while (true) {
            val url = "$apiUrl/$service/user/$creatorId/posts?o=$offset&limit=$PAGE_SIZE"
            val posts = client.newCall(GET(url, headers)).execute()
                .body.string()
                .let { json.decodeFromString<List<PostDto>>(it) }

            for (post in posts) {
                chapters.add(
                    SChapter.create().apply {
                        url            = "/${post.service}/post/${post.id}"
                        name           = post.title?.takeIf { it.isNotBlank() }
                            ?: "Post #${post.id}"
                        date_upload    = parseDate(post.published ?: post.added)
                        // Descending chapter_number so newest posts sit at top.
                        chapter_number = (pageNumber++).toFloat()
                        // Use the scanlator field to surface the platform name.
                        scanlator      = post.service?.titleCase()
                    },
                )
            }

            if (posts.size < PAGE_SIZE) break   // last page
            offset += PAGE_SIZE
        }

        // Return newest-first (Tachiyomi convention for "chapter 1 = oldest").
        // The list is already in reverse-chronological order from the API.
        return Observable.just(chapters)
    }

    // ════════════════════════════════════════════════════════════════════════
    //  PAGE LIST  (images inside one post)
    // ════════════════════════════════════════════════════════════════════════

    override fun pageListRequest(chapter: SChapter): Request {
        val (service, postId) = parsePostUrl(chapter.url)
        return GET("$apiUrl/$service/post/$postId", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val post  = json.decodeFromString<PostDto>(response.body.string())
        val pages = mutableListOf<Page>()
        var index = 0

        // 1. Primary file (hero image on most posts).
        post.file?.path?.let { path ->
            if (isImagePath(path)) {
                pages.add(Page(index++, imageUrl = "$baseUrl$path"))
            }
        }

        // 2. All attachments, filtered to image types only.
        for (attachment in post.attachments) {
            if (isImagePath(attachment.path)) {
                pages.add(Page(index++, imageUrl = "$baseUrl${attachment.path}"))
            }
        }

        if (pages.isEmpty()) {
            // Throw a descriptive error so Tachiyomi shows the user a clear message
            // rather than a blank chapter.
            throw Exception(
                "No images found in this post.\n" +
                "It may contain only text, video embeds, or non-image file types.\n" +
                "Check it directly on ${post.service?.let { "$it.com" } ?: "the platform"}.",
            )
        }

        return pages
    }

    // imageUrlParse is unused because we embed full URLs directly in Page().
    override fun imageUrlParse(response: Response) = ""

    // ════════════════════════════════════════════════════════════════════════
    //  FILTERS
    // ════════════════════════════════════════════════════════════════════════

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Filters apply to Search only"),
        Filter.Separator(),
        ServiceFilter(),
        SortFilter(),
        Filter.Separator(),
        TagFilter(),
        Filter.Separator(),
        ImageOnlyFilter(),
    )

    // ════════════════════════════════════════════════════════════════════════
    //  URL ACTIVITY HANDLER  (deep links from the browser / share sheet)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Called by [PawchiveUrlActivity] to resolve a raw pawchive.net URL into
     * the internal form Tachiyomi expects.
     *
     * Supported URL patterns:
     *   /patreon/user/12345          → SManga URL (opens creator)
     *   /patreon/user/12345/post/67  → SChapter URL (opens post)
     */
    fun handleUrl(url: String): Pair<SManga?, SChapter?> {
        // Strip query-string and fragment, work with path only.
        val path = url.removePrefix(baseUrl).substringBefore("?").substringBefore("#")
        val segments = path.trim('/').split("/")

        return when {
            segments.size == 3 && segments[1] == "user" -> {
                // /{service}/user/{id}
                val manga = SManga.create().apply {
                    this.url = "/${segments[0]}/user/${segments[2]}"
                    title    = "Pawchive Creator"
                }
                Pair(manga, null)
            }

            segments.size >= 5 && segments[1] == "user" && segments[3] == "post" -> {
                // /{service}/user/{uid}/post/{pid}
                val chapter = SChapter.create().apply {
                    this.url = "/${segments[0]}/post/${segments[4]}"
                    name     = "Post #${segments[4]}"
                }
                Pair(null, chapter)
            }

            else -> Pair(null, null)
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ════════════════════════════════════════════════════════════════════════

    /** Convert a [CreatorDto] to the [SManga] representation. */
    private fun CreatorDto.toSManga(): SManga = SManga.create().apply {
        url           = "/${service}/user/${id}"
        title         = name.ifBlank { id }
        thumbnail_url = this@toSManga.icon?.let { "$baseUrl$it" }
            ?: creatorIconUrl(service, id)
        status        = SManga.ONGOING
        author        = name.ifBlank { id }
        artist        = name.ifBlank { id }
        genre         = service.titleCase()
        description   = buildString {
            appendLine("Creator : ${name.ifBlank { id }}")
            appendLine("Platform: ${service.titleCase()}")
            if (favoriteCount > 0) appendLine("★ Favourites: $favoriteCount")
            if (!this@toSManga.description.isNullOrBlank()) {
                appendLine()
                appendLine(this@toSManga.description!!.trim())
            }
        }.trim()
    }

    /**
     * Standard CDN path for creator avatar icons.
     * Falls back gracefully when the creator DTO doesn't include an icon field.
     */
    private fun creatorIconUrl(service: String?, userId: String?): String =
        "$baseUrl/icons/${service.orEmpty()}/${userId.orEmpty()}"

    /**
     * Parse a creator URL of the form /{service}/user/{id}
     * and return (service, creatorId).
     */
    private fun parseCreatorUrl(url: String): Pair<String, String> {
        val parts = url.trimStart('/').split("/")
        // parts[0] = service, parts[1] = "user", parts[2] = id
        require(parts.size >= 3 && parts[1] == "user") {
            "Unexpected creator URL format: $url"
        }
        return parts[0] to parts[2]
    }

    /**
     * Parse a post URL of the form /{service}/post/{id}
     * and return (service, postId).
     */
    private fun parsePostUrl(url: String): Pair<String, String> {
        val parts = url.trimStart('/').split("/")
        // parts[0] = service, parts[1] = "post", parts[2] = id
        require(parts.size >= 3 && parts[1] == "post") {
            "Unexpected post URL format: $url"
        }
        return parts[0] to parts[2]
    }

    /** Returns true for file paths whose extension indicates an image type. */
    private fun isImagePath(path: String): Boolean =
        IMAGE_EXTENSIONS.any { path.substringBefore("?").lowercase().endsWith(it) }

    /** Parse a nullable ISO-8601 date string into a Unix millis timestamp. */
    private fun parseDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L
        return runCatching { dateFormat.parse(dateStr.trim())?.time ?: 0L }
            .getOrDefault(0L)
    }

    /** Capitalise the first character of a service slug for display. */
    private fun String.titleCase(): String =
        replaceFirstChar { it.uppercase() }

    // ════════════════════════════════════════════════════════════════════════
    //  CONSTANTS
    // ════════════════════════════════════════════════════════════════════════

    companion object {
        /** Number of items per API page (mirrors kemono's default). */
        const val PAGE_SIZE = 50

        /** Image extensions the extension considers readable. */
        private val IMAGE_EXTENSIONS = setOf(
            ".jpg", ".jpeg",
            ".png",
            ".gif",
            ".webp",
            ".avif",
            ".bmp",
            ".tiff", ".tif",
        )
    }
}
