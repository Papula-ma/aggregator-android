package com.tughi.aggregator.data

import androidx.sqlite.db.SimpleSQLiteQuery
import java.io.Serializable

private const val SELECT__MY_FEED_ENTRY_IDS = """
    SELECT ef.docid FROM entry_fts ef WHERE
        ef.tags MATCH (
            SELECT
                CASE
                    WHEN s.i AND s.e THEN s.i||' '||s.e
                    WHEN s.i THEN s.i
                    WHEN s.e THEN '0 '||s.e
                    ELSE '0'
                END
            FROM (
                SELECT
                    (SELECT group_concat(mft.tag_id, ' OR ') FROM my_feed_tag mft WHERE mft.type = $MY_FEED_TAG_TYPE__INCLUDED) AS i,
                    (SELECT group_concat('-'||mft.tag_id, ' ') FROM my_feed_tag mft WHERE mft.type = $MY_FEED_TAG_TYPE__EXCLUDED) AS e
            ) AS s
        )
"""

@Suppress("ClassName")
object Entries : Repository<Entries.Column, Entries.TableColumn, Entries.UpdateCriteria, Entries.DeleteCriteria, Entries.QueryCriteria>("entry") {

    open class Column(name: String, projection: String, projectionTables: Array<String> = arrayOf("entry")) : Repository.Column(name, projection, projectionTables)
    interface TableColumn : Repository.TableColumn

    object ID : Column("id", "e.id"), TableColumn
    object UID : Column("uid", "e.uid"), TableColumn
    object FEED_ID : Column("feed_id", "e.feed_id"), TableColumn
    object FEED_TITLE : Column("feed_title", "COALESCE(f.custom_title, f.title)", arrayOf("feed"))
    object FEED_LANGUAGE : Column("feed_language", "f.language", arrayOf("feed"))
    object FEED_FAVICON_URL : Column("feed_favicon_url", "f.favicon_url", arrayOf("feed"))
    object TITLE : Column("title", "e.title"), TableColumn
    object LINK : Column("link", "e.link"), TableColumn
    object CONTENT : Column("content", "e.content"), TableColumn
    object AUTHOR : Column("author", "e.author"), TableColumn
    object PUBLISH_TIME : Column("publish_time", "COALESCE(e.publish_time, e.insert_time)"), TableColumn
    object INSERT_TIME : Column("insert_time", "e.insert_time"), TableColumn
    object UPDATE_TIME : Column("update_time", "e.update_time"), TableColumn
    object READ_TIME : Column("read_time", "e.read_time"), TableColumn
    object PINNED_TIME : Column("pinned_time", "e.pinned_time"), TableColumn
    object STAR_TIME : Column("star_time", "(SELECT et.tag_time FROM entry_tag et WHERE et.entry_id = e.id AND et.tag_id = ${Tags.STARRED})", arrayOf("entry", "entry_tag"))
    object TYPE : Column("type", "CASE WHEN e.read_time > 0 AND e.pinned_time = 0 THEN 'READ' ELSE 'UNREAD' END")

    fun markRead(entryId: Long): Int = update(UpdateEntryCriteria(entryId), READ_TIME to System.currentTimeMillis(), PINNED_TIME to 0)

    fun markPinned(entryId: Long): Int = update(UpdateEntryCriteria(entryId), READ_TIME to 0, PINNED_TIME to System.currentTimeMillis())

    fun markRead(criteria: EntriesQueryCriteria): Int {
        val updateCriteria = when (criteria) {
            is FeedEntriesQueryCriteria -> SimpleUpdateCriteria("feed_id = ? AND pinned_time = 0 AND read_time = 0", arrayOf(criteria.feedId))
            is MyFeedEntriesQueryCriteria -> SimpleUpdateCriteria("id IN ($SELECT__MY_FEED_ENTRY_IDS) AND pinned_time = 0 AND read_time = 0", emptyArray())
            is TagEntriesQueryCriteria -> SimpleUpdateCriteria("id IN (SELECT ef.docid FROM entry_fts ef WHERE tags MATCH ?) AND pinned_time = 0 AND read_time = 0", arrayOf(criteria.tagId))
            else -> throw IllegalArgumentException("Unsupported criteria: $criteria")
        }
        return update(updateCriteria, READ_TIME to System.currentTimeMillis())
    }

    fun queryPublishedCount(feedId: Long, since: Long): Int {
        val query = SimpleSQLiteQuery("SELECT COUNT(1) FROM entry WHERE feed_id = ? AND COALESCE(publish_time, insert_time) > ?", arrayOf(feedId, since))
        return Database.query(query) { cursor ->
            cursor.moveToNext()
            return@query cursor.getInt(0)
        }
    }

    interface UpdateCriteria : Repository.UpdateCriteria

    private class SimpleUpdateCriteria(override val selection: String?, override val selectionArgs: Array<Any>?) : UpdateCriteria

    class UpdateFeedEntryCriteria(feedId: Long, uid: String) : UpdateCriteria {
        override val selection = "feed_id = ? AND uid = ?"
        override val selectionArgs = arrayOf<Any>(feedId, uid)
    }

    class UpdateEntryCriteria(id: Long) : UpdateCriteria {
        override val selection = "id = ?"
        override val selectionArgs = arrayOf<Any>(id)
    }

    class UpdateUnreadEntryCriteria(id: Long) : UpdateCriteria {
        override val selection = "id = ? AND read_time = 0 AND pinned_time = 0"
        override val selectionArgs = arrayOf<Any>(id)
    }

    interface DeleteCriteria : Repository.DeleteCriteria

    interface QueryCriteria : Repository.QueryCriteria<Column> {
        fun config(query: Query.Builder)
    }

    class QueryRowCriteria(private val id: Long) : QueryCriteria {
        override fun config(query: Query.Builder) {
            query.where("e.id = ?", arrayOf(id))
        }
    }

    abstract class EntriesQueryCriteria : QueryCriteria, Serializable {
        abstract val sessionTime: Long
        abstract val sortOrder: SortOrder

        abstract fun copy(sessionTime: Long? = null, sortOrder: SortOrder? = null): EntriesQueryCriteria
    }

    class FeedEntriesQueryCriteria(val feedId: Long, override val sessionTime: Long, override val sortOrder: SortOrder) : EntriesQueryCriteria() {
        override fun config(query: Query.Builder) {
            val selection: String?
            val selectionArgs: Array<Any?>
            if (sessionTime != 0L) {
                selection = "e.feed_id = ? AND (e.read_time = 0 OR e.read_time > ?)"
                selectionArgs = arrayOf(feedId, sessionTime)
            } else {
                selection = "e.feed_id = ?"
                selectionArgs = arrayOf(feedId)
            }
            query.where(selection, selectionArgs)
            query.orderBy(sortOrder.orderBy)
        }

        override fun copy(sessionTime: Long?, sortOrder: SortOrder?) = FeedEntriesQueryCriteria(
                feedId = feedId,
                sessionTime = sessionTime ?: this.sessionTime,
                sortOrder = sortOrder ?: this.sortOrder
        )
    }

    class MyFeedEntriesQueryCriteria(override val sessionTime: Long, override val sortOrder: SortOrder) : EntriesQueryCriteria() {
        override fun config(query: Query.Builder) {
            if (sessionTime != 0L) {
                val selection = "e.id IN ($SELECT__MY_FEED_ENTRY_IDS) AND (e.read_time = 0 OR e.read_time > ?)"
                val selectionArgs: Array<Any?> = arrayOf(sessionTime)
                query.where(selection, selectionArgs)
            }
            query.addObservedTables("my_feed_tag")
            query.orderBy(sortOrder.orderBy)
        }

        override fun copy(sessionTime: Long?, sortOrder: SortOrder?) = MyFeedEntriesQueryCriteria(
                sessionTime = sessionTime ?: this.sessionTime,
                sortOrder = sortOrder ?: this.sortOrder
        )
    }

    class TagEntriesQueryCriteria(val tagId: Long, override val sessionTime: Long, override val sortOrder: SortOrder) : EntriesQueryCriteria() {
        override fun config(query: Query.Builder) {
            val selection: String
            val selectionArgs: Array<Any?>
            if (sessionTime != 0L) {
                selection = "e.id IN (SELECT e1.id FROM entry_fts ef LEFT JOIN entry e1 ON ef.docid = e1.id WHERE ef.tags MATCH ? AND (e1.read_time = 0 OR e1.read_time > ?))"
                selectionArgs = arrayOf(tagId, sessionTime)
            } else {
                selection = "e.id IN (SELECT e1.id FROM entry_fts ef LEFT JOIN entry e1 ON ef.docid = e1.id WHERE ef.tags MATCH ?)"
                selectionArgs = arrayOf(tagId)
            }
            query.addObservedTables("entry", "entry_tag", "feed_tag")
            query.where(selection, selectionArgs)
            query.orderBy(sortOrder.orderBy)
        }

        override fun copy(sessionTime: Long?, sortOrder: SortOrder?) = TagEntriesQueryCriteria(
                tagId = tagId,
                sessionTime = sessionTime ?: this.sessionTime,
                sortOrder = sortOrder ?: this.sortOrder
        )
    }

    sealed class SortOrder(private val value: String, internal val orderBy: String) : Serializable {
        fun serialize() = value

        companion object {
            fun deserialize(value: String) = when (value) {
                "date-asc" -> ByDateAscending
                "date-desc" -> ByDateDescending
                "title-asc" -> ByTitle
                else -> ByDateAscending
            }
        }

        object ByDateAscending : SortOrder("date-asc", "COALESCE(e.publish_time, e.insert_time) ASC")
        object ByDateDescending : SortOrder("date-desc", "COALESCE(e.publish_time, e.insert_time) DESC")
        object ByTitle : SortOrder("title-asc", "e.title ASC, COALESCE(e.publish_time, e.insert_time) ASC")
    }

    abstract class QueryHelper<R>(vararg columns: Column) : Repository.QueryHelper<Column, QueryCriteria, R>(columns) {
        private val tables: String
            get() {
                val tables = StringBuilder("entry e")

                var feed = false
                for (column in columns) {
                    if (column !is TableColumn) {
                        when (column) {
                            is FEED_TITLE -> feed = true
                            is FEED_LANGUAGE -> feed = true
                            is FEED_FAVICON_URL -> feed = true
                        }
                    }
                    if (feed) {
                        break
                    }
                }
                if (feed) {
                    tables.append(" LEFT JOIN feed f ON f.id = e.feed_id")
                }

                return tables.toString()
            }

        override fun createQuery(criteria: QueryCriteria) = Query.Builder(columns, tables)
                .also { criteria.config(it) }
                .create()
    }

}
