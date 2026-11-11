# The JS bridge is called by name from the WebView fallback.
-keepclassmembers class com.pursuitiron.app.engine.WebViewEngine$Bridge { @android.webkit.JavascriptInterface <methods>; }

# Room builds a database by reflection (Class.forName(name + "_Impl").newInstance()). WorkManager's WorkDatabase,
# pulled in by Glance and started at launch by androidx.startup, lost its generated constructor under R8 full mode,
# and the release build crashed before the first frame: "Failed to create an instance of androidx.work.impl.WorkDatabase".
# Found by the release-build perf pass (smoke/perf.py); every debug run had hidden it.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class androidx.work.impl.WorkDatabase_Impl { *; }
