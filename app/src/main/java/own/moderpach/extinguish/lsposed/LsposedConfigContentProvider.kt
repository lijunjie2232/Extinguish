package own.moderpach.extinguish.lsposed

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import java.util.concurrent.ConcurrentHashMap

// ponytail: exported without a permission — any app could spoof a heartbeat; worst case is a
// false "activated" indicator. This is a status indicator, not a security control. Upgrade
// path: a signature-protected permission won't work (target apps aren't signed by Extinguish),
// so accept it.
class LsposedConfigContentProvider : ContentProvider() {

    private lateinit var prefs: SharedPreferences
    // ponytail: ConcurrentHashMap keeps insert/query safe across concurrent binder calls; the
    // ceiling is per-key granularity, which is exactly what heartbeat-overwrites-previous needs.
    private val heartbeats = ConcurrentHashMap<String, Long>()

    override fun onCreate(): Boolean {
        prefs = context!!.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.all.forEach { (k, v) -> (v as? Long)?.let { heartbeats[k] = it } }
        return true
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        if (uri.lastPathSegment != PATH_HEARTBEAT) return null
        val pkg = values?.getAsString(COL_PACKAGE) ?: return null
        val ts = values.getAsLong(COL_TIMESTAMP) ?: System.currentTimeMillis()
        return runCatching {
            heartbeats[pkg] = ts
            prefs.edit().putLong(pkg, ts).apply()
            ContentUris.withAppendedId(HEARTBEAT_URI, ts)
        }.getOrNull()
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = if (uri.lastPathSegment != PATH_HEARTBEAT) null
        else MatrixCursor(arrayOf(COL_PACKAGE, COL_TIMESTAMP)).apply {
            heartbeats.forEach { (pkg, ts) -> addRow(arrayOf<Any?>(pkg, ts)) }
        }

    override fun getType(uri: Uri): String? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    companion object {
        const val AUTHORITY = "own.moderpach.extinguish.lsposed"
        val HEARTBEAT_URI: Uri = Uri.parse("content://$AUTHORITY/heartbeat")
        const val PATH_HEARTBEAT = "heartbeat"
        const val COL_PACKAGE = "packageName"
        const val COL_TIMESTAMP = "timestamp"
        const val STALE_MS = 60_000L
        private const val PREFS_NAME = "lsposed_heartbeat"
    }
}
