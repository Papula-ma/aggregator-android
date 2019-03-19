package com.tughi.aggregator.activities.reader

import android.database.Cursor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tughi.aggregator.data.Entries

internal class ReaderFragmentViewModel(entryId: Long, entryReadTime: Long) : ViewModel() {

    val entry = Entries.liveQueryOne(Entries.QueryRowCriteria(entryId), Entry.QueryHelper)

    data class Entry(
            val id: Long,
            val title: String?,
            val link: String?,
            val content: String?,
            val author: String?,
            val publishTime: Long,
            val feedTitle: String,
            val feedLanguage: String?,
            val readTime: Long,
            val pinnedTime: Long,
            val starTime: Long
    ) {
        object QueryHelper : Entries.QueryHelper<Entry>(
                Entries.ID,
                Entries.TITLE,
                Entries.LINK,
                Entries.CONTENT,
                Entries.AUTHOR,
                Entries.PUBLISH_TIME,
                Entries.FEED_TITLE,
                Entries.FEED_LANGUAGE,
                Entries.READ_TIME,
                Entries.PINNED_TIME,
                Entries.STAR_TIME
        ) {
            override fun createRow(cursor: Cursor) = Entry(
                    id = cursor.getLong(0),
                    title = cursor.getString(1),
                    link = cursor.getString(2),
                    content = cursor.getString(3),
                    author = cursor.getString(4),
                    publishTime = cursor.getLong(5),
                    feedTitle = cursor.getString(6),
                    feedLanguage = cursor.getString(7),
                    readTime = cursor.getLong(8),
                    pinnedTime = cursor.getLong(9),
                    starTime = cursor.getLong(10)
            )
        }
    }

    class Factory(private val entryId: Long, private val entryReadTime: Long) : ViewModelProvider.Factory {

        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ReaderFragmentViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return ReaderFragmentViewModel(entryId, entryReadTime) as T
            }
            throw UnsupportedOperationException()
        }

    }

}
