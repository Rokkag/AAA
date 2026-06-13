package eu.kanade.tachiyomi.extension.en.pawchive

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ──────────────────────────────────────────────
//  Creator (maps to SManga in Tachiyomi)
// ──────────────────────────────────────────────

/**
 * Returned by:
 *   GET /api/v1/creators                    (list)
 *   GET /api/v1/creators/{service}/{id}     (single)
 *   GET /api/v1/creators/search?q=...       (search results)
 */
@Serializable
data class CreatorDto(
    /** Platform-internal user ID (e.g. Patreon user number). */
    val id: String,

    /** Display name of the creator. */
    val name: String,

    /** Source platform slug: "patreon", "fanbox", "subscribestar", etc. */
    val service: String,

    /** ISO-8601 timestamp when the creator was first indexed. */
    val indexed: String? = null,

    /** ISO-8601 timestamp when the creator's content was last imported. */
    val updated: String? = null,

    /** Number of users who have favourited this creator on Pawchive. */
    @SerialName("favorited")
    val favoriteCount: Int = 0,

    /** Short bio / public description provided by the platform. */
    val description: String? = null,

    /** Optional banner image path (absolute, served by Pawchive CDN). */
    val header: String? = null,

    /** Optional avatar / icon path. Falls back to /icons/{service}/{id}. */
    val icon: String? = null,

    /** Public-facing page URL on the original platform. */
    @SerialName("public_id")
    val publicId: String? = null,
)

// ──────────────────────────────────────────────
//  Post (maps to SChapter + Page list)
// ──────────────────────────────────────────────

/**
 * Returned by:
 *   GET /api/v1/posts                                       (feed / latest)
 *   GET /api/v1/{service}/user/{id}/posts?o={offset}       (creator posts)
 *   GET /api/v1/{service}/post/{post_id}                   (single post)
 */
@Serializable
data class PostDto(
    /** Platform post ID. */
    val id: String,

    /** Post title (may be null or empty for untitled posts). */
    val title: String? = null,

    /** Rich-text post body (HTML or Markdown depending on the platform). */
    val content: String? = null,

    /** ISO-8601 publish timestamp (original platform). */
    val published: String? = null,

    /** ISO-8601 timestamp when Pawchive imported this post. */
    val added: String? = null,

    /** ISO-8601 timestamp of the last edit, if any. */
    val edited: String? = null,

    /** Platform slug this post was scraped from. */
    val service: String? = null,

    /** Creator's ID on Pawchive. */
    @SerialName("user")
    val userId: String? = null,

    /** Creator's display name (only present in feed / search results). */
    @SerialName("username")
    val userName: String? = null,

    /**
     * Primary file attached to the post.
     * On image-first posts this is usually the hero image.
     */
    val file: FileDto? = null,

    /**
     * Additional attachments (images, zips, PDFs …).
     * The extension filters these down to image types only.
     */
    val attachments: List<AttachmentDto> = emptyList(),

    /** Tags/labels assigned by the creator or the importer. */
    val tags: List<String> = emptyList(),

    /** Embed data (YouTube, Vimeo …) – skipped by the extension. */
    val embed: EmbedDto? = null,
)

// ──────────────────────────────────────────────
//  Supporting types
// ──────────────────────────────────────────────

/** A single file reference inside a post. */
@Serializable
data class FileDto(
    /** Original filename. */
    val name: String? = null,

    /**
     * CDN-relative path, e.g. "/data/ab/cd/abcd1234.jpg".
     * Prepend [Pawchive.baseUrl] to get the full URL.
     */
    val path: String? = null,
)

/** One attachment entry inside [PostDto.attachments]. */
@Serializable
data class AttachmentDto(
    val name: String = "",
    val path: String = "",
)

/** Embed metadata (video links etc.) – informational only. */
@Serializable
data class EmbedDto(
    val url: String? = null,
    val subject: String? = null,
    val description: String? = null,
)

/** Simple wrapper returned by paginated list endpoints. */
@Serializable
data class PagedResultDto<T>(
    val results: List<T> = emptyList(),
    val total: Int = 0,
)
