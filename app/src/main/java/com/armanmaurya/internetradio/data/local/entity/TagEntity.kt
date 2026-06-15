package com.armanmaurya.internetradio.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.armanmaurya.internetradio.data.model.Tag

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey val name: String,
    val stationCount: Int
)

fun TagEntity.toDomain() = Tag(
    name = name,
    stationCount = stationCount
)

fun Tag.toEntity() = TagEntity(
    name = name,
    stationCount = stationCount
)
