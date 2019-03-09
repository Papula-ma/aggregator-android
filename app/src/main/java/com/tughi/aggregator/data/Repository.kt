package com.tughi.aggregator.data

import android.content.ContentValues
import android.database.Cursor

abstract class Repository<T>(protected val columns: Array<String>, protected val mapper: DataMapper<T>) {

    open class DataMapper<T> {

        open fun map(cursor: Cursor): T {
            throw UnsupportedOperationException()
        }

        fun map(data: Array<out Pair<String, Any?>>) = ContentValues(data.size).apply {
            for ((column, value) in data) {
                put(column, value)
            }
        }

        private fun ContentValues.put(column: String, value: Any?): Unit = when (value) {
            null -> putNull(column)
            is Long -> put(column, value)
            else -> put(column, convert(value))
        }

        open fun convert(value: Any): Any? {
            throw UnsupportedOperationException()
        }

    }

}
