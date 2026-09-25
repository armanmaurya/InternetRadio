# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# Keep data transfer objects for Gson to prevent obfuscation of field names
-keep class com.armanmaurya.internetradio.data.remote.dto.** { *; }

# Keep local entity and backup model classes used by Gson for export/import
-keep class com.armanmaurya.internetradio.data.local.entity.LibraryStationEntity { *; }
-keep class com.armanmaurya.internetradio.domain.model.** { *; }
-keep class com.armanmaurya.internetradio.data.backup.** { *; }
-keep class com.armanmaurya.internetradio.core.media.prober.** { *; }
-keep class com.armanmaurya.internetradio.util.State { *; }

# JNA rules for FCast SDK
-keep class com.sun.jna.** { *; }
-keep class * extends com.sun.jna.Structure { *; }
-keep class * implements com.sun.jna.Callback { *; }
-keep class org.fcast.** { *; }
-keepclassmembers class org.fcast.** { *; }
-dontwarn com.sun.jna.**

# Hilt EntryPoint interfaces accessed dynamically via EntryPointAccessors
-keep @dagger.hilt.EntryPoint interface * { *; }
-keep interface com.armanmaurya.internetradio.widget.WidgetEntryPoint { *; }

# Glance & App Widget components
-keep class com.armanmaurya.internetradio.widget.** { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidget { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }

# Playback Service and Broadcast Receivers
-keep class com.armanmaurya.internetradio.service.PlaybackService { *; }
-keep class com.armanmaurya.internetradio.service.PlaybackService$* { *; }
-keep class com.armanmaurya.internetradio.data.schedule.BootReceiver { *; }
-keep class com.armanmaurya.internetradio.data.schedule.ScheduleReceiver { *; }
-keepclassmembers class * extends android.content.BroadcastReceiver {
    @javax.inject.Inject <fields>;
}