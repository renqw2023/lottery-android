package com.example.lottery.data.database

import android.content.Context
import androidx.room.*
import com.example.lottery.data.dao.LotteryDao
import com.example.lottery.data.model.LotteryResult
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Database(
    entities = [LotteryResult::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class LotteryDatabase : RoomDatabase() {
    abstract fun lotteryDao(): LotteryDao

    companion object {
        @Volatile
        private var INSTANCE: LotteryDatabase? = null

        fun getDatabase(context: Context): LotteryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LotteryDatabase::class.java,
                    "lottery_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class Converters {
    @TypeConverter
    fun fromJson(value: String): List<Int> = 
        Gson().fromJson(value, object : TypeToken<List<Int>>() {}.type)

    @TypeConverter
    fun toJson(list: List<Int>): String = 
        Gson().toJson(list)

    @TypeConverter
    fun fromStringList(value: String): List<String> =
        Gson().fromJson(value, object : TypeToken<List<String>>() {}.type)

    @TypeConverter
    fun toStringList(list: List<String>): String =
        Gson().toJson(list)
} 