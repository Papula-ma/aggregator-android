package com.tughi.aggregator.data

@Suppress("ClassName")
object Tags : Repository<Tags.Column, Tags.TableColumn, Tags.UpdateCriteria, Tags.DeleteCriteria, Tags.QueryCriteria>("tag") {

    const val STARRED = 0L
    const val HIDDEN = -1L

    open class Column(name: String, projection: String, projectionTables: Array<String> = arrayOf("tag")) : Repository.Column(name, projection, projectionTables)
    interface TableColumn : Repository.TableColumn

    object ID : Column("id", "t.id"), TableColumn
    object NAME : Column("name", "t.name"), TableColumn
    object EDITABLE : Column("editable", "t.editable"), TableColumn
    object ENTRY_COUNT : Column("entry_count", "SUM(CASE WHEN tag_time THEN 1 ELSE 0 END)", arrayOf("entry_tag", "tag"))
    object UNREAD_ENTRY_COUNT : Column("unread_entry_count", "SUM(CASE WHEN e.read_time = 0 THEN 1 WHEN e.pinned_time THEN 1 ELSE 0 END)", arrayOf("entry", "entry_tag", "tag"))

    interface UpdateCriteria : Repository.UpdateCriteria

    class UpdateTagCriteria(tagId: Long) : UpdateCriteria {
        override val selection = "id = ?"
        override val selectionArgs = arrayOf<Any>(tagId)
    }

    interface DeleteCriteria : Repository.DeleteCriteria

    class DeleteTagCriteria(tagId: Long) : DeleteCriteria {
        override val selection = "id = ? AND editable = 1"
        override val selectionArgs = arrayOf<Any>(tagId)
    }

    interface QueryCriteria : Repository.QueryCriteria<Column> {
        fun config(query: Query.Builder, columns: Array<out Column>)
    }

    class QueryTagCriteria(private val tagId: Long) : QueryCriteria {
        override fun config(query: Query.Builder, columns: Array<out Column>) {
            query.where("t.id = ?", arrayOf(tagId))
        }
    }

    object QueryVisibleTagsCriteria : QueryCriteria {
        override fun config(query: Query.Builder, columns: Array<out Column>) {
            query.where("t.id != ?", arrayOf(HIDDEN))
            query.orderBy("(CASE t.id WHEN 0 THEN 0 ELSE 1 END), t.name")
        }
    }

    abstract class QueryHelper<Row>(vararg columns: Column) : Repository.QueryHelper<Column, QueryCriteria, Row>(columns) {
        private val tables: String
            get() {
                val tables = StringBuilder("tag t")

                var entry = false
                var entryTag = false
                for (column in columns) {
                    if (column !is TableColumn) {
                        when (column) {
                            is ENTRY_COUNT -> {
                                entryTag = true
                            }
                            is UNREAD_ENTRY_COUNT -> {
                                entry = true
                                entryTag = true
                            }
                        }
                    }
                    if (entry && entryTag) {
                        break
                    }
                }
                if (entryTag) {
                    tables.append(" LEFT JOIN entry_tag et ON et.tag_id = t.id")
                }
                if (entry) {
                    tables.append(" LEFT JOIN entry e ON e.id = et.entry_id")
                }

                return tables.toString()
            }

        override fun createQuery(criteria: QueryCriteria) = Query.Builder(columns, tables)
                .groupBy("t.id")
                .orderBy("t.name")
                .also { criteria.config(it, columns) }
                .create()
    }

}
