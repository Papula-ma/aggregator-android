package com.tughi.aggregator.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.lifecycle.LiveData
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.transaction
import com.tughi.aggregator.App
import com.tughi.aggregator.utilities.DATABASE_NAME
import com.tughi.aggregator.utilities.restoreFeeds
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

object Database {

    private val sqlite: SupportSQLiteOpenHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(App.instance)
                    .name(DATABASE_NAME)
                    .callback(object : SupportSQLiteOpenHelper.Callback(18) {
                        override fun onConfigure(db: SupportSQLiteDatabase?) {
                            db?.apply {
                                setForeignKeyConstraintsEnabled(true)
                                enableWriteAheadLogging()
                            }
                        }

                        override fun onCreate(database: SupportSQLiteDatabase?) {
                            database?.transaction {
                                database.execSQL("""
                                    CREATE TABLE feed (
                                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                                        url TEXT NOT NULL,
                                        title TEXT NOT NULL,
                                        link TEXT,
                                        language TEXT,
                                        custom_title TEXT,
                                        favicon_url TEXT,
                                        favicon_content BLOB,
                                        update_mode TEXT NOT NULL,
                                        last_update_time INTEGER NOT NULL DEFAULT 0,
                                        last_update_error TEXT,
                                        next_update_retry INTEGER NOT NULL DEFAULT 0,
                                        next_update_time INTEGER NOT NULL DEFAULT 0,
                                        http_etag TEXT,
                                        http_last_modified TEXT
                                    )
                                """)

                                database.execSQL("""
                                    CREATE TABLE entry (
                                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                                        feed_id INTEGER NOT NULL,
                                        uid TEXT NOT NULL,
                                        title TEXT,
                                        link TEXT,
                                        content TEXT,
                                        author TEXT,
                                        publish_time INTEGER,
                                        insert_time INTEGER NOT NULL,
                                        update_time INTEGER NOT NULL,
                                        read_time INTEGER NOT NULL DEFAULT 0,
                                        pinned_time INTEGER NOT NULL DEFAULT 0,
                                        FOREIGN KEY (feed_id) REFERENCES feed (id) ON DELETE CASCADE
                                    )
                                """)

                                database.execSQL("CREATE UNIQUE INDEX entry_index__feed_id__uid ON entry (feed_id, uid)")

                                database.execSQL("""
                                    CREATE TABLE tag (
                                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                                        name TEXT NOT NULL,
                                        editable INTEGER NOT NULL DEFAULT 1
                                    )
                                """)

                                database.execSQL("INSERT INTO tag VALUES (0, 'Starred', 0)")

                                database.execSQL("INSERT INTO tag VALUES (-1, 'Hidden', 0)")

                                database.execSQL("""
                                    CREATE TABLE feed_tag (
                                        feed_id INTEGER NOT NULL,
                                        tag_id INTEGER NOT NULL,
                                        tag_time INTEGER NOT NULL DEFAULT 0,
                                        FOREIGN KEY (feed_id) REFERENCES feed (id) ON DELETE CASCADE,
                                        FOREIGN KEY (tag_id) REFERENCES tag (id) ON DELETE CASCADE,
                                        UNIQUE (feed_id, tag_id)
                                    )
                                """)

                                database.execSQL("CREATE UNIQUE INDEX feed_tag_index__feed_id__tag_id ON feed_tag (feed_id, tag_id)")

                                database.execSQL("""
                                    CREATE TABLE entry_tag (
                                        entry_id INTEGER NOT NULL,
                                        tag_id INTEGER NOT NULL,
                                        tag_time INTEGER NOT NULL DEFAULT 0,
                                        FOREIGN KEY (entry_id) REFERENCES entry (id) ON DELETE CASCADE,
                                        FOREIGN KEY (tag_id) REFERENCES tag (id) ON DELETE CASCADE,
                                        UNIQUE (entry_id, tag_id)
                                    )
                                """)

                                database.execSQL("CREATE UNIQUE INDEX entry_tag_index__entry_id__tag_id ON entry_tag (entry_id, tag_id)")

                                database.execSQL("""
                                    CREATE TABLE my_feed_tag (
                                        tag_id INTEGER NOT NULL,
                                        type INTEGER NOT NULL,
                                        FOREIGN KEY (tag_id) REFERENCES tag (id) ON DELETE CASCADE,
                                        UNIQUE (tag_id, type)
                                    )
                                """)

                                database.execSQL("CREATE UNIQUE INDEX my_feed_tag_index__tag_id__type ON my_feed_tag (tag_id, type)")
                            }
                        }

                        override fun onUpgrade(database: SupportSQLiteDatabase?, oldVersion: Int, newVersion: Int) {
                            database?.transaction {
                                when (oldVersion) {
                                    17 -> {
                                        database.execSQL("""
                                            CREATE TABLE my_feed_tag (
                                                tag_id INTEGER NOT NULL,
                                                type INTEGER NOT NULL,
                                                FOREIGN KEY (tag_id) REFERENCES tag (id) ON DELETE CASCADE,
                                                UNIQUE (tag_id, type)
                                            )
                                        """)

                                        database.execSQL("CREATE UNIQUE INDEX my_feed_tag_index__tag_id__type ON my_feed_tag (tag_id, type)")
                                    }
                                    else -> {
                                        val tables = mutableListOf<String>()
                                        database.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'").use { cursor ->
                                            if (cursor.moveToFirst()) {
                                                do {
                                                    tables.add(cursor.getString(0))
                                                } while (cursor.moveToNext())
                                            }
                                        }

                                        if (!tables.isEmpty()) {
                                            database.execSQL("PRAGMA foreign_keys = OFF")

                                            tables.forEach {
                                                database.execSQL("DROP TABLE $it")
                                            }

                                            database.execSQL("PRAGMA foreign_keys = OFF")
                                        }

                                        onCreate(database)
                                    }
                                }
                            }
                        }

                        override fun onOpen(db: SupportSQLiteDatabase?) {
                            GlobalScope.launch {
                                restoreFeeds()
                            }
                        }
                    })
                    .build()
    )

    private fun insert(table: String, values: ContentValues, conflictAlgorithm: Int): Long {
        val id = sqlite.writableDatabase.insert(table, conflictAlgorithm, values)
        if (id != -1L) {
            invalidateTable(table)
        }
        return id
    }

    fun insert(table: String, values: ContentValues): Long = Database.insert(table, values, SQLiteDatabase.CONFLICT_FAIL)

    fun replace(table: String, values: ContentValues): Int {
        return if (Database.insert(table, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L) 1 else 0
    }

    fun update(table: String, values: ContentValues, selection: String?, selectionArgs: Array<Any>?): Int {
        val result = sqlite.writableDatabase.update(table, SQLiteDatabase.CONFLICT_FAIL, values, selection, selectionArgs)
        if (result > 0) {
            invalidateTable(table)
        }
        return result
    }

    fun delete(table: String, selection: String?, selectionArgs: Array<Any>?): Int {
        val result = sqlite.writableDatabase.delete(table, selection, selectionArgs)
        if (result > 0) {
            invalidateTable(table)
        }
        return result
    }

    private val tableObservers = mutableSetOf<TableObserver>()

    private val invalidatedTables = mutableSetOf<String>()

    private fun invalidateTable(table: String) {
        if (sqlite.writableDatabase.inTransaction()) {
            synchronized(invalidatedTables) {
                invalidatedTables.add(table)
            }

            return
        }

        synchronized(this) {
            if (tableObservers.size > 0) {
                val vanishedObservers = mutableListOf<TableObserver>()

                for (tableObserver in tableObservers) {
                    if (tableObserver.table == table) {
                        val listener = tableObserver.listener
                        if (listener != null) {
                            listener.onInvalidated()
                        } else {
                            vanishedObservers.add(tableObserver)
                        }
                    }
                }

                for (tableObserver in vanishedObservers) {
                    tableObservers.remove(tableObserver)
                }
            }
        }
    }

    fun transaction(body: () -> Unit) {
        beginTransaction()
        try {
            body()
            setTransactionSuccessful()
        } finally {
            endTransaction()
        }
    }

    private fun beginTransaction() = sqlite.writableDatabase.run {
        if (isWriteAheadLoggingEnabled) {
            beginTransactionNonExclusive()
        } else {
            beginTransaction()
        }
    }

    private fun setTransactionSuccessful() = sqlite.writableDatabase.setTransactionSuccessful()

    private fun endTransaction() {
        val database = sqlite.writableDatabase
        database.endTransaction()
        if (!database.inTransaction()) {
            synchronized(invalidatedTables) {
                for (table in invalidatedTables) {
                    invalidateTable(table)
                }
                invalidatedTables.clear()
            }
        }
    }

    fun <T> query(sqliteQuery: SupportSQLiteQuery, transform: (Cursor) -> T): T {
        sqlite.readableDatabase.query(sqliteQuery).use { cursor ->
            return transform(cursor)
        }
    }

    fun <T> liveQuery(query: Query, transform: (Cursor) -> T): LiveData<T> {
        val liveData = object : LiveData<T>(), TableObserver.Listener {
            override fun onActive() {
                if (value == null) {
                    update()
                }
            }

            override fun onInvalidated() {
                update()
            }

            private fun update() {
                GlobalScope.launch {
                    sqlite.readableDatabase.query(query).use { cursor ->
                        val data = transform(cursor)
                        postValue(data)
                    }
                }
            }
        }

        synchronized(tableObservers) {
            for (observedTable in query.observedTables) {
                tableObservers.add(TableObserver(observedTable, liveData))
            }
        }

        return liveData
    }

    private class TableObserver(val table: String, listener: Listener) {

        private val reference = WeakReference(listener)

        val listener
            get() = reference.get()

        interface Listener {

            fun onInvalidated()

        }

    }

}
