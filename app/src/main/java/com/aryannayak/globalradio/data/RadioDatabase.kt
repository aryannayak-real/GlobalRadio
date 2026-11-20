package com.aryannayak.globalradio.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "stations")
data class StationEntity(
    @PrimaryKey val stationUuid: String,
    val name: String,
    val streamUrl: String,
    val favicon: String,
    val country: String,
    val countryCode: String,
    val language: String,
    val tags: String,
    val bitrate: Int,
    val codec: String
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val stationUuid: String,
    val name: String,
    val streamUrl: String,
    val favicon: String,
    val country: String,
    val countryCode: String,
    val language: String,
    val tags: String,
    val bitrate: Int,
    val codec: String,
    val playedAt: Long
)

fun RadioStation.toEntity() = StationEntity(
    id, name, streamUrl, favicon, country,
    countryCode, language, tags, bitrate, codec
)

fun StationEntity.toStation() = RadioStation(
    stationUuid, name, streamUrl, favicon, country,
    countryCode, language, tags, bitrate, codec
)

fun HistoryEntity.toStation() = RadioStation(
    stationUuid, name, streamUrl, favicon, country,
    countryCode, language, tags, bitrate, codec
)

fun RadioStation.toHistory() = HistoryEntity(
    id, name, streamUrl, favicon, country,
    countryCode, language, tags, bitrate, codec,
    System.currentTimeMillis()
)

@Dao
interface RadioDao {
    @Query("SELECT * FROM stations ORDER BY name COLLATE NOCASE")
    fun favorites(): Flow<List<StationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(station: StationEntity)

    @Query("DELETE FROM stations WHERE stationUuid = :id")
    suspend fun removeFavorite(id: String)

    @Query("SELECT * FROM history ORDER BY playedAt DESC LIMIT 100")
    fun history(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addHistory(item: HistoryEntity)

    @Query("DELETE FROM history")
    suspend fun clearHistory()
}

@Database(
    entities = [StationEntity::class, HistoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class RadioDatabase : RoomDatabase() {
    abstract fun radioDao(): RadioDao

    companion object {
        @Volatile
        private var INSTANCE: RadioDatabase? = null

        fun get(context: Context): RadioDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RadioDatabase::class.java,
                    "global-radio.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
