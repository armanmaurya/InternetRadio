package com.armanmaurya.internetradio.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.armanmaurya.internetradio.data.local.entity.CountryEntity
import com.armanmaurya.internetradio.data.local.entity.LanguageEntity
import com.armanmaurya.internetradio.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MetadataDao {

    @Query("SELECT * FROM countries ORDER BY stationCount DESC")
    suspend fun getCountries(): List<CountryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCountries(countries: List<CountryEntity>)

    @Query("DELETE FROM countries")
    suspend fun clearCountries()

    @Query("SELECT * FROM languages ORDER BY stationCount DESC")
    suspend fun getLanguages(): List<LanguageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLanguages(languages: List<LanguageEntity>)

    @Query("DELETE FROM languages")
    suspend fun clearLanguages()

    @Query("SELECT * FROM tags ORDER BY stationCount DESC")
    suspend fun getTags(): List<TagEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTags(tags: List<TagEntity>)

    @Query("DELETE FROM tags")
    suspend fun clearTags()
}
