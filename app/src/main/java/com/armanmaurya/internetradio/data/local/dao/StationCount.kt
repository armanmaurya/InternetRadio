package com.armanmaurya.internetradio.data.local.dao

/** A row count for one station, as returned by the per-station count queries. */
data class StationCount(val stationUuid: String, val count: Int)
