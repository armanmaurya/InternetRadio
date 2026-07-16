package com.armanmaurya.internetradio.data.local.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteTable
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.armanmaurya.internetradio.data.local.dao.LibraryStationDao
import com.armanmaurya.internetradio.data.local.dao.RecentStationDao
import com.armanmaurya.internetradio.data.local.dao.TrackHistoryDao
import com.armanmaurya.internetradio.data.local.entity.LibraryStationEntity
import com.armanmaurya.internetradio.data.local.entity.RecentStationEntity
import com.armanmaurya.internetradio.data.local.entity.TrackHistoryEntity

@Database(
    entities = [
        LibraryStationEntity::class,
        RecentStationEntity::class,
        TrackHistoryEntity::class
    ],
    version = 5,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2, spec = RadioDatabase.Migration1To2Spec::class),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 4, to = 5, spec = RadioDatabase.Migration4To5Spec::class)
    ]
)
@TypeConverters(Converters::class)
abstract class RadioDatabase : RoomDatabase() {
    
    @DeleteTable(tableName = "countries")
    @DeleteTable(tableName = "languages")
    @DeleteTable(tableName = "tags")
    class Migration1To2Spec : AutoMigrationSpec

    class Migration4To5Spec : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            val cursor = db.query("SELECT stationUuid FROM library_stations ORDER BY addedAt DESC")
            var index = 0
            while (cursor.moveToNext()) {
                val uuid = cursor.getString(0)
                db.execSQL("UPDATE library_stations SET orderIndex = $index WHERE stationUuid = '$uuid'")
                index++
            }
            cursor.close()
        }
    }

    abstract val libraryStationDao: LibraryStationDao
    abstract val recentStationDao: RecentStationDao
    abstract val trackHistoryDao: TrackHistoryDao

    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create the new table
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `library_stations` (`stationUuid` TEXT NOT NULL, `name` TEXT NOT NULL, `url` TEXT NOT NULL, `urlResolved` TEXT NOT NULL, `favicon` TEXT NOT NULL, `tags` TEXT NOT NULL, `country` TEXT NOT NULL, `countryCode` TEXT NOT NULL, `language` TEXT NOT NULL, `codec` TEXT NOT NULL, `bitrate` INTEGER NOT NULL, `isCustom` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`stationUuid`))"
                )
                
                // Copy data from favorite_stations
                database.execSQL(
                    "INSERT OR IGNORE INTO `library_stations` (`stationUuid`, `name`, `url`, `urlResolved`, `favicon`, `tags`, `country`, `countryCode`, `language`, `codec`, `bitrate`, `isCustom`, `addedAt`) SELECT `stationUuid`, `name`, `url`, `urlResolved`, `favicon`, `tags`, `country`, `countryCode`, `language`, `codec`, `bitrate`, 0, `addedAt` FROM `favorite_stations`"
                )
                
                // Copy data from user_stations
                database.execSQL(
                    "INSERT OR IGNORE INTO `library_stations` (`stationUuid`, `name`, `url`, `urlResolved`, `favicon`, `tags`, `country`, `countryCode`, `language`, `codec`, `bitrate`, `isCustom`, `addedAt`) SELECT `stationUuid`, `name`, `url`, `urlResolved`, `favicon`, `tags`, `country`, `countryCode`, `language`, `codec`, `bitrate`, 1, `addedAt` FROM `user_stations`"
                )
                
                // Drop the old tables
                database.execSQL("DROP TABLE IF EXISTS `favorite_stations`")
                database.execSQL("DROP TABLE IF EXISTS `user_stations`")
            }
        }
    }
}
