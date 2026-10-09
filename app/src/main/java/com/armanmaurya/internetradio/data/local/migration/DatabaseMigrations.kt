package com.armanmaurya.internetradio.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `library_stations` (`stationUuid` TEXT NOT NULL, `name` TEXT NOT NULL, `url` TEXT NOT NULL, `urlResolved` TEXT NOT NULL, `favicon` TEXT NOT NULL, `tags` TEXT NOT NULL, `country` TEXT NOT NULL, `countryCode` TEXT NOT NULL, `language` TEXT NOT NULL, `codec` TEXT NOT NULL, `bitrate` INTEGER NOT NULL, `isCustom` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`stationUuid`))"
            )
            database.execSQL(
                "INSERT OR IGNORE INTO `library_stations` (`stationUuid`, `name`, `url`, `urlResolved`, `favicon`, `tags`, `country`, `countryCode`, `language`, `codec`, `bitrate`, `isCustom`, `addedAt`) SELECT `stationUuid`, `name`, `url`, `urlResolved`, `favicon`, `tags`, `country`, `countryCode`, `language`, `codec`, `bitrate`, 0, `addedAt` FROM `favorite_stations`"
            )
            database.execSQL(
                "INSERT OR IGNORE INTO `library_stations` (`stationUuid`, `name`, `url`, `urlResolved`, `favicon`, `tags`, `country`, `countryCode`, `language`, `codec`, `bitrate`, `isCustom`, `addedAt`) SELECT `stationUuid`, `name`, `url`, `urlResolved`, `favicon`, `tags`, `country`, `countryCode`, `language`, `codec`, `bitrate`, 1, `addedAt` FROM `user_stations`"
            )
            database.execSQL("DROP TABLE IF EXISTS `favorite_stations`")
            database.execSQL("DROP TABLE IF EXISTS `user_stations`")
        }
    }

    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("PRAGMA foreign_keys=OFF;")

            // 1. Create the new unified stations table
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS `stations` (
                    `stationUuid` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `url` TEXT NOT NULL,
                    `urlResolved` TEXT NOT NULL,
                    `favicon` TEXT NOT NULL,
                    `tags` TEXT NOT NULL,
                    `countryCode` TEXT NOT NULL,
                    `languageCodes` TEXT NOT NULL DEFAULT '',
                    `codec` TEXT NOT NULL,
                    `bitrate` INTEGER NOT NULL,
                    `homepage` TEXT NOT NULL DEFAULT '',
                    `iso3166_2` TEXT,
                    `geoLat` REAL,
                    `geoLong` REAL,
                    `isCustom` INTEGER NOT NULL DEFAULT 0,
                    `isFavorite` INTEGER NOT NULL DEFAULT 0,
                    `addedAt` INTEGER,
                    `orderIndex` INTEGER NOT NULL DEFAULT 0,
                    `lastPlayedAt` INTEGER,
                    PRIMARY KEY(`stationUuid`)
                )
            """.trimIndent())

            // 2. Copy favorites from library_stations (marking isFavorite = 1)
            database.execSQL("""
                INSERT OR REPLACE INTO `stations` (
                    `stationUuid`, `name`, `url`, `urlResolved`, `favicon`, `tags`,
                    `countryCode`, `languageCodes`, `codec`, `bitrate`, `homepage`,
                    `iso3166_2`, `geoLat`, `geoLong`, `isCustom`, `isFavorite`,
                    `addedAt`, `orderIndex`
                )
                SELECT 
                    `stationUuid`, `name`, `url`, `urlResolved`, `favicon`, `tags`,
                    `countryCode`, `languageCodes`, `codec`, `bitrate`, `homepage`,
                    `iso3166_2`, `geoLat`, `geoLong`, `isCustom`, 1,
                    `addedAt`, `orderIndex`
                FROM `library_stations`
            """.trimIndent())

            // 3. Merge lastPlayedAt timestamps for stations that were in both tables
            database.execSQL("""
                UPDATE `stations` 
                SET `lastPlayedAt` = (
                    SELECT `recent_stations`.`lastPlayedAt` 
                    FROM `recent_stations` 
                    WHERE `recent_stations`.`stationUuid` = `stations`.`stationUuid`
                )
                WHERE EXISTS (
                    SELECT 1 FROM `recent_stations` 
                    WHERE `recent_stations`.`stationUuid` = `stations`.`stationUuid`
                )
            """.trimIndent())

            // 4. Insert recent stations that were NOT favorites (isFavorite = 0)
            database.execSQL("""
                INSERT OR IGNORE INTO `stations` (
                    `stationUuid`, `name`, `url`, `urlResolved`, `favicon`, `tags`,
                    `countryCode`, `languageCodes`, `codec`, `bitrate`, `homepage`,
                    `iso3166_2`, `geoLat`, `geoLong`, `isCustom`, `isFavorite`,
                    `lastPlayedAt`
                )
                SELECT 
                    `stationUuid`, `name`, `url`, `urlResolved`, `favicon`, `tags`,
                    `countryCode`, `languageCodes`, `codec`, `bitrate`, `homepage`,
                    `iso3166_2`, `geoLat`, `geoLong`, `isCustom`, 0,
                    `lastPlayedAt`
                FROM `recent_stations`
            """.trimIndent())

            // 5. Drop deprecated tables
            database.execSQL("DROP TABLE IF EXISTS `library_stations`")
            database.execSQL("DROP TABLE IF EXISTS `recent_stations`")

            database.execSQL("PRAGMA foreign_keys=ON;")
        }
    }

    val ALL_MIGRATIONS = arrayOf(MIGRATION_3_4, MIGRATION_10_11)
}
