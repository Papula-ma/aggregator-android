package com.tughi.aggregator.activities.reader

data class ReaderActivityEntry(
//        @ColumnInfo
        val id: Long,

//        @ColumnInfo(name = "read_time")
        val readTime: Long,

//        @ColumnInfo(name = "pinned_time")
        val pinnedTime: Long
)
