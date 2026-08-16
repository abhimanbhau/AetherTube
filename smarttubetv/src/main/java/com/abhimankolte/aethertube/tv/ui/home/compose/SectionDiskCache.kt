package com.abhimankolte.aethertube.tv.ui.home.compose

import android.content.Context
import com.liskovsoft.sharedutils.helpers.Helpers
import com.liskovsoft.sharedutils.mylogger.Log
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video
import java.io.File

/**
 * Persists what a section was last showing, so a cold start paints real content instead of a
 * shimmer while the network round-trip happens.
 *
 * Rows are stored with the same string encoding upstream already uses for [Video] - SidebarService
 * persists its pinned items exactly this way - relying on Helpers' delimiter hierarchy to nest:
 * `&vi;` inside a Video, `%AR%` between videos, `%OB%` between a row's fields, `%PR%` between rows.
 * Freshness comes from the file's own mtime rather than a stored timestamp.
 *
 * This is a paint-sooner cache, never a source of truth: whatever is seeded from here is replaced
 * the moment the real load delivers anything (see ComposeHomeFragment's deferred-replace handling).
 */
class SectionDiskCache(context: Context) {
    private val dir = File(context.cacheDir, DIR_NAME)

    companion object {
        private const val TAG = "SectionDiskCache"
        private const val DIR_NAME = "aethertube-sections"
        private const val ROW_DELIM = "%PR%"

        /**
         * Past this, seeding would show yesterday's feed. Short enough that stale content is a
         * plausible "not refreshed yet" rather than obviously wrong, long enough to cover the
         * daily-use case this exists for.
         */
        private const val MAX_AGE_MS = 12L * 60 * 60 * 1000

        /** A section with more rows/videos than this isn't worth the write cost on a cheap box. */
        private const val MAX_ROWS = 12
        private const val MAX_VIDEOS_PER_ROW = 60
    }

    fun load(sectionId: Int): List<HomeRow>? {
        val file = fileFor(sectionId)

        if (!file.exists()) {
            return null
        }

        if (System.currentTimeMillis() - file.lastModified() > MAX_AGE_MS) {
            file.delete()
            return null
        }

        return try {
            val rows = file.readText()
                .split(ROW_DELIM)
                .mapNotNull { decodeRow(it) }
                .filter { it.videos.isNotEmpty() }
            rows.ifEmpty { null }
        } catch (e: Exception) {
            // A partially written or format-changed file must never be fatal - it is only a cache.
            Log.e(TAG, "Dropping unreadable cache for section %s: %s", sectionId, e.message)
            file.delete()
            null
        }
    }

    /** Writes on the calling thread's behalf in the background; callers need not wait. */
    fun save(sectionId: Int, rows: List<HomeRow>) {
        if (rows.isEmpty()) {
            return
        }

        // Snapshot on the caller's thread: these are SnapshotStateLists still being mutated by
        // pagination, and iterating them off-thread would race.
        val encoded = rows.take(MAX_ROWS)
            .filter { it.videos.isNotEmpty() }
            .joinToString(ROW_DELIM) { encodeRow(it) }

        if (encoded.isEmpty()) {
            return
        }

        Thread {
            try {
                dir.mkdirs()
                fileFor(sectionId).writeText(encoded)
            } catch (e: Exception) {
                Log.e(TAG, "Could not cache section %s: %s", sectionId, e.message)
            }
        }.start()
    }

    fun clear(sectionId: Int) {
        runCatching { fileFor(sectionId).delete() }
    }

    private fun fileFor(sectionId: Int) = File(dir, sectionId.toString())

    private fun encodeRow(row: HomeRow): String {
        val videos = row.videos.take(MAX_VIDEOS_PER_ROW).map { it.toString() }
        return Helpers.mergeData(row.id, row.title, Helpers.mergeList(videos))
    }

    private fun decodeRow(encoded: String): HomeRow? {
        val parts = Helpers.splitData(encoded) ?: return null
        val id = Helpers.parseInt(parts, 0, -1)
        if (id == -1) {
            return null
        }

        val row = HomeRow(id, Helpers.parseStr(parts, 1) ?: "")
        Helpers.parseList(parts, 2, Video::fromString)?.filterNotNull()?.let { row.videos.addAll(it) }
        return row
    }
}
