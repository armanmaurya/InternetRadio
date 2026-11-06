package com.armanmaurya.internetradio.core.utils

import android.content.Context
import com.armanmaurya.internetradio.domain.model.RecordingTimeFormat
import java.text.DateFormat
import java.text.SimpleDateFormat

import java.util.Date
import java.util.Locale

object FormatUtils {
    /**
     * Returns a time format that respects the user's 12/24 hour preference,
     * but enforces English "AM" and "PM" strings when 12-hour format is used.
     */
    fun getTimeFormat(context: Context): DateFormat {
        val format = android.text.format.DateFormat.getTimeFormat(context)
        if (format is SimpleDateFormat && !android.text.format.DateFormat.is24HourFormat(context)) {
            val symbols = format.dateFormatSymbols
            symbols.amPmStrings = arrayOf("AM", "PM")
            format.dateFormatSymbols = symbols
        }
        return format
    }

    fun RecordingTimeFormat.is24Hour(context: Context): Boolean = when (this) {
        RecordingTimeFormat.SYSTEM -> android.text.format.DateFormat.is24HourFormat(context)
        RecordingTimeFormat.TWELVE_HOUR -> false
        RecordingTimeFormat.TWENTY_FOUR_HOUR -> true
    }

    /**
     * Returns a recording file timestamp respecting the user's 12/24 hour preference.
     * Uses '-' instead of ':' for filesystem compatibility.
     */
    fun getRecordingTimestamp(
        context: Context? = null,
        date: Date = Date(),
        is24Hour: Boolean = context?.let { android.text.format.DateFormat.is24HourFormat(it) } ?: false
    ): String {
        val pattern = if (is24Hour) "d MMMM yyyy HH-mm" else "d MMMM yyyy hh-mm a"
        val sdf = SimpleDateFormat(pattern, Locale.getDefault())
        if (!is24Hour) {
            val symbols = sdf.dateFormatSymbols
            symbols.amPmStrings = arrayOf("AM", "PM")
            sdf.dateFormatSymbols = symbols
        }
        return sdf.format(date)
    }
}
