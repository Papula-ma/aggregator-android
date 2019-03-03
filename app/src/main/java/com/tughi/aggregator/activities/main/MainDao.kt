package com.tughi.aggregator.activities.main

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.tughi.aggregator.AppDatabase
import com.tughi.aggregator.data.EntriesQuery
import com.tughi.aggregator.data.EntriesSortOrderByDateAsc
import com.tughi.aggregator.data.EntriesSortOrderByDateDesc
import com.tughi.aggregator.data.EntriesSortOrderByTitle
import com.tughi.aggregator.data.Entry
import com.tughi.aggregator.data.Feed
import com.tughi.aggregator.data.FeedEntriesQuery
import com.tughi.aggregator.data.MyFeedEntriesQuery

@Dao
abstract class MainDao {

    @Query("""
        SELECT
            f.id,
            COALESCE(f.custom_title, f.title) AS title,
            f.favicon_url,
            f.last_update_time,
            f.last_update_error,
            f.next_update_time,
            f.next_update_retry,
            f.update_mode,
            (SELECT COUNT(1) FROM entries e WHERE f.id = e.feed_id AND e.read_time = 0) AS unread_entry_count,
            0 AS expanded,
            0 AS updating
        FROM
            feeds f
        ORDER BY
            title
    """)
    abstract fun getFeedsFragmentFeeds(): LiveData<List<FeedsFragmentFeed>>

    fun getEntriesFragmentEntries(entriesQuery: EntriesQuery): LiveData<Array<EntriesFragmentEntry>> {
        var query = """
            SELECT
                e.id,
                f.id AS feed_id,
                COALESCE(f.custom_title, f.title) AS feed_title,
                f.favicon_url,
                e.title,
                e.link,
                e.author,
                COALESCE(e.publish_time, e.insert_time) AS formatted_date,
                COALESCE(e.publish_time, e.insert_time) AS formatted_time,
                e.read_time,
                e.pinned_time,
                (e.read_time > 0 AND e.pinned_time = 0) AS type
            FROM
                entries e
                LEFT JOIN feeds f ON f.id = e.feed_id
        """.trim().replace(Regex("\\s+"), " ")

        val orderBy = when (entriesQuery.sortOrder) {
            is EntriesSortOrderByDateAsc -> "ORDER BY COALESCE(e.publish_time, e.insert_time) ASC"
            is EntriesSortOrderByDateDesc -> "ORDER BY COALESCE(e.publish_time, e.insert_time) DESC"
            is EntriesSortOrderByTitle -> "ORDER BY e.title ASC, COALESCE(e.publish_time, e.insert_time) ASC"
        }

        val queryArgs: Array<out Any>
        if (entriesQuery.sessionTime == 0L) {
            query = when (entriesQuery) {
                is FeedEntriesQuery -> "$query WHERE e.feed_id = ? $orderBy"
                is MyFeedEntriesQuery -> "$query $orderBy"
            }

            queryArgs = when (entriesQuery) {
                is FeedEntriesQuery -> arrayOf(entriesQuery.feedId)
                is MyFeedEntriesQuery -> emptyArray()
            }
        } else {
            query = when (entriesQuery) {
                is FeedEntriesQuery -> "$query WHERE e.feed_id = ? AND (e.read_time = 0 OR e.read_time > ?) $orderBy"
                is MyFeedEntriesQuery -> "$query WHERE e.read_time = 0 OR e.read_time > ? $orderBy"
            }

            queryArgs = when (entriesQuery) {
                is FeedEntriesQuery -> arrayOf(entriesQuery.feedId, entriesQuery.sessionTime)
                is MyFeedEntriesQuery -> arrayOf(entriesQuery.sessionTime)
            }
        }

        return getEntriesFragmentEntries(SimpleSQLiteQuery(query, queryArgs))
    }

    @RawQuery(observedEntities = [Entry::class, Feed::class])
    protected abstract fun getEntriesFragmentEntries(query: SupportSQLiteQuery): LiveData<Array<EntriesFragmentEntry>>

    fun markAllEntriesRead(entriesQuery: EntriesQuery) {
        var query = "UPDATE entries SET read_time = ?"

        query = when (entriesQuery) {
            is FeedEntriesQuery -> "$query WHERE feed_id = ? AND pinned_time = 0 AND read_time = 0"
            is MyFeedEntriesQuery -> "$query WHERE pinned_time = 0 AND read_time = 0"
        }

        val readTime = System.currentTimeMillis()

        val queryArgs = when (entriesQuery) {
            is FeedEntriesQuery -> arrayOf(readTime, entriesQuery.feedId)
            is MyFeedEntriesQuery -> arrayOf(readTime)
        }

        markAllEntriesRead(SimpleSQLiteQuery(query, queryArgs))

        AppDatabase.instance.invalidationTracker.refreshVersionsAsync()
    }

    @RawQuery
    protected abstract fun markAllEntriesRead(query: SupportSQLiteQuery): Int

}
