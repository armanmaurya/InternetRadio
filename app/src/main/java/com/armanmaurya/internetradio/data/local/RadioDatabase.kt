package com.armanmaurya.internetradio.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.DeleteTable
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import com.armanmaurya.internetradio.data.local.converter.Converters
import com.armanmaurya.internetradio.data.local.dao.ScheduleDao
import com.armanmaurya.internetradio.data.local.dao.StationDao
import com.armanmaurya.internetradio.data.local.dao.TrackHistoryDao
import com.armanmaurya.internetradio.data.local.entity.ScheduleEntity
import com.armanmaurya.internetradio.data.local.entity.StationEntity
import com.armanmaurya.internetradio.data.local.entity.TrackHistoryEntity
import com.armanmaurya.internetradio.data.local.migration.DatabaseMigrations

@Database(
    entities = [
        StationEntity::class,
        TrackHistoryEntity::class,
        ScheduleEntity::class
    ],
    version = 11,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2, spec = RadioDatabase.Migration1To2Spec::class),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 4, to = 5, spec = RadioDatabase.Migration4To5Spec::class),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7, spec = RadioDatabase.Migration6To7Spec::class),
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9),
        AutoMigration(from = 9, to = 10)
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

    @DeleteColumn(tableName = "library_stations", columnName = "country")
    @DeleteColumn(tableName = "library_stations", columnName = "language")
    @DeleteColumn(tableName = "recent_stations", columnName = "country")
    @DeleteColumn(tableName = "recent_stations", columnName = "language")
    class Migration6To7Spec : AutoMigrationSpec

    abstract val stationDao: StationDao
    abstract val trackHistoryDao: TrackHistoryDao
    abstract val scheduleDao: ScheduleDao

    companion object {
        val MIGRATION_3_4 = DatabaseMigrations.MIGRATION_3_4
        val MIGRATION_10_11 = DatabaseMigrations.MIGRATION_10_11
    }
}