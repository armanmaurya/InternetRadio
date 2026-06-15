package com.armanmaurya.internetradio.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.armanmaurya.internetradio.data.local.dao.FavoriteStationDao
import com.armanmaurya.internetradio.data.local.dao.MetadataDao
import com.armanmaurya.internetradio.data.local.dao.RecentStationDao
import com.armanmaurya.internetradio.data.local.dao.SearchHistoryDao
import com.armanmaurya.internetradio.data.local.entity.CountryEntity
import com.armanmaurya.internetradio.data.local.entity.FavoriteStationEntity
import com.armanmaurya.internetradio.data.local.entity.LanguageEntity
import com.armanmaurya.internetradio.data.local.entity.RecentStationEntity
import com.armanmaurya.internetradio.data.local.entity.SearchHistoryEntity
import com.armanmaurya.internetradio.data.local.entity.TagEntity

@Database(
    entities = [
        FavoriteStationEntity::class,
        CountryEntity::class,
        LanguageEntity::class,
        SearchHistoryEntity::class,
        RecentStationEntity::class,
        TagEntity::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class RadioDatabase : RoomDatabase() {
    abstract val favoriteStationDao: FavoriteStationDao
    abstract val metadataDao: MetadataDao
    abstract val searchHistoryDao: SearchHistoryDao
    abstract val recentStationDao: RecentStationDao
}
