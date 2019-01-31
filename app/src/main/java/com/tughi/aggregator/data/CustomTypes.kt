package com.tughi.aggregator.data

import androidx.room.TypeConverter

object CustomTypeConverters {

    @TypeConverter
    @JvmStatic
    fun deserializeUpdateMode(updateMode: String): UpdateMode {
        return UpdateMode.deserialize(updateMode)
    }

    @TypeConverter
    @JvmStatic
    fun serializeUpdateMode(updateMode: UpdateMode): String {
        return updateMode.serialize()
    }

}
