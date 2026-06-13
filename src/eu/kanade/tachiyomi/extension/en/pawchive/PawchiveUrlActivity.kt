package eu.kanade.tachiyomi.extension.en.pawchive

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log

/**
 * Transparent trampoline Activity declared in AndroidManifest.xml.
 *
 * When the user taps a pawchive.net link in a browser or share sheet, Android
 * routes it here first. We parse the URL into a Tachiyomi deep-link Intent
 * and finish immediately — the reader / browse screen opens instead.
 *
 * Supported external URL shapes
 * ──────────────────────────────
 *   https://pawchive.net/patreon/user/12345          → open creator (browse)
 *   https://pawchive.net/patreon/user/12345/post/67  → open post (reader)
 */
class PawchiveUrlActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rawUrl = intent?.data?.toString()

        if (rawUrl.isNullOrBlank()) {
            Log.w(TAG, "PawchiveUrlActivity launched with no URL – finishing.")
            finish()
            return
        }

        // Ask the source to resolve the URL.
        val source = Pawchive()
        val (manga, chapter) = source.handleUrl(rawUrl)

        when {
            chapter != null -> {
                // Deep-link directly into the reader for this post.
                startActivity(
                    Intent().apply {
                        action = ACTION_TACHIYOMI_READER
                        putExtra(EXTRA_SOURCE_ID, source.id)
                        putExtra(EXTRA_CHAPTER_URL, chapter.url)
                        putExtra(EXTRA_CHAPTER_NAME, chapter.name)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
            }

            manga != null -> {
                // Open the manga/creator detail screen in browse.
                startActivity(
                    Intent().apply {
                        action = ACTION_TACHIYOMI_MANGA
                        putExtra(EXTRA_SOURCE_ID, source.id)
                        putExtra(EXTRA_MANGA_URL, manga.url)
                        putExtra(EXTRA_MANGA_TITLE, manga.title)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
            }

            else -> {
                Log.w(TAG, "Could not parse Pawchive URL: $rawUrl")
            }
        }

        finish()
    }

    companion object {
        private const val TAG = "PawchiveUrlActivity"

        // Standard Tachiyomi intent actions / extras.
        private const val ACTION_TACHIYOMI_MANGA   = "eu.kanade.tachiyomi.SHOW_MANGA"
        private const val ACTION_TACHIYOMI_READER  = "eu.kanade.tachiyomi.READER"
        private const val EXTRA_SOURCE_ID          = "source_id"
        private const val EXTRA_MANGA_URL          = "manga_url"
        private const val EXTRA_MANGA_TITLE        = "manga_title"
        private const val EXTRA_CHAPTER_URL        = "chapter_url"
        private const val EXTRA_CHAPTER_NAME       = "chapter_name"
    }
}
