package own.moderpach.extinguish.lsposed

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

private const val TAG = "LsposedConfig"

const val LSPSED_CONFIG_FILENAME = "lsposed_config.json"

@Serializable
data class LsposedModuleConfig(
    val enabled: Boolean,
    val targetPackages: List<String>,
    val version: Int = 1
)

private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

fun parseFromString(s: String): LsposedModuleConfig? = runCatching {
    json.decodeFromString<LsposedModuleConfig>(s)
}.getOrElse {
    Log.w(TAG, "parseFromString failed: ${it.message}")
    null
}

fun parseFromInputStream(stream: InputStream): LsposedModuleConfig? = runCatching {
    json.decodeFromString<LsposedModuleConfig>(stream.readBytes().toString(Charsets.UTF_8))
}.getOrElse {
    Log.w(TAG, "parseFromInputStream failed: ${it.message}")
    null
}

fun writeConfig(context: Context, enabled: Boolean, targetPackages: Set<String>) {
    val cfg = LsposedModuleConfig(enabled, targetPackages.toList())
    val target = File(context.filesDir, LSPSED_CONFIG_FILENAME)
    val tmp = File(context.filesDir, "$LSPSED_CONFIG_FILENAME.tmp")
    runCatching {
        FileOutputStream(tmp).use { fos ->
            fos.write(json.encodeToString(cfg).toByteArray(Charsets.UTF_8))
            fos.fd.sync()
        }
        // ponytail: renameTo fails across filesystem volumes; filesDir and its .tmp sibling are always the same volume, so this fallback is rarely hit but keeps the config from going stale.
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }.onFailure {
        Log.e(TAG, "writeConfig failed: ${it.message}")
    }
}

fun main() {
    val cfg = LsposedModuleConfig(true, listOf("com.foo", "com.bar"))
    val s = Json.encodeToString(cfg)
    val back = Json.decodeFromString<LsposedModuleConfig>(s)
    assert(back == cfg) { "config round-trip failed: $back" }
    println("LsposedConfig round-trip OK")
}
