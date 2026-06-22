package own.moderpach.extinguish.lsposed

import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.*
import java.io.FileInputStream

/**
 * LSPosed module entry for Extinguish.
 *
 * Loaded only into user-selected target apps (scope managed by the LSPosed manager).
 * Uses the modern XposedInterface.hook(executable) builder API, not legacy XposedHelpers.
 */
class ExtinguishModule : XposedModule() {

    @Volatile private var cachedConfig: LsposedModuleConfig? = null
    private var hostPackage: String? = null

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        log(Log.INFO, TAG, "loaded into ${param.processName}")
    }

    override fun onPackageLoaded(param: PackageLoadedParam) {
        val cfg = runCatching {
            openRemoteFile(LSPSED_CONFIG_FILENAME).use { pfd ->
                parseFromInputStream(FileInputStream(pfd.fileDescriptor))
            }
        }.getOrNull() ?: return
        if (!cfg.enabled || param.packageName !in cfg.targetPackages) return
        cachedConfig = cfg
        hostPackage = param.packageName
        val cl = param.defaultClassLoader
        installSurfaceHook(cl)
        installPowerHook(cl)
        installReceiverHook(cl)
        installHeartbeatHook(cl, param.packageName)
        log(Log.INFO, TAG, "installed hooks in ${param.packageName}")
    }

    private fun isScreenOffFeatureActive(): Boolean =
        cachedConfig?.let { it.enabled && hostPackage in it.targetPackages } ?: false

    private fun installSurfaceHook(cl: ClassLoader) {
        runCatching {
            val c = Class.forName("android.view.SurfaceView", false, cl)
            val m = c.getDeclaredMethod("surfaceDestroyed", android.view.SurfaceHolder::class.java)
            hook(m).intercept { chain -> if (isScreenOffFeatureActive()) null else chain.proceed() }
        }.onFailure { log(Log.WARN, TAG, "SurfaceView.surfaceDestroyed hook failed: ${it.message}") }
        // ponytail: TextureView-equivalent best-effort target; SurfaceView path covers ExoPlayer's
        // default surface. release() is no-arg and public on android.graphics.SurfaceTexture.
        runCatching {
            val c = Class.forName("android.graphics.SurfaceTexture", false, cl)
            val m = c.getDeclaredMethod("release")
            hook(m).intercept { chain -> if (isScreenOffFeatureActive()) null else chain.proceed() }
        }.onFailure { log(Log.WARN, TAG, "SurfaceTexture.release hook failed: ${it.message}") }
    }

    private fun installPowerHook(cl: ClassLoader) {
        runCatching {
            val c = Class.forName("android.os.PowerManager", false, cl)
            val m = c.getDeclaredMethod("isInteractive")
            hook(m).intercept { chain -> if (isScreenOffFeatureActive()) true else chain.proceed() }
        }.onFailure { log(Log.WARN, TAG, "PowerManager.isInteractive hook failed: ${it.message}") }
        // isScreenOn is deprecated and absent on some API levels; guarded separately.
        runCatching {
            val c = Class.forName("android.os.PowerManager", false, cl)
            val m = c.getDeclaredMethod("isScreenOn")
            hook(m).intercept { chain -> if (isScreenOffFeatureActive()) true else chain.proceed() }
        }.onFailure { log(Log.WARN, TAG, "PowerManager.isScreenOn hook failed: ${it.message}") }
    }

    private fun installReceiverHook(cl: ClassLoader) {
        runCatching {
            val receiverCls = android.content.BroadcastReceiver::class.java
            val filterCls = android.content.IntentFilter::class.java
            // ponytail: ContextWrapper may inherit registerReceiver from Context; fall back to
            // Context.getMethod when getDeclaredMethod misses it.
            val m = runCatching {
                Class.forName("android.content.ContextWrapper", false, cl)
                    .getDeclaredMethod("registerReceiver", receiverCls, filterCls)
            }.getOrElse {
                Class.forName("android.content.Context", false, cl)
                    .getMethod("registerReceiver", receiverCls, filterCls)
            }
            hook(m).intercept { chain ->
                if (isScreenOffFeatureActive()) {
                    val filter = chain.getArg(1) as? android.content.IntentFilter
                    if (filter != null) {
                        // ponytail: IntentFilter.removeAction is @hide on the SDK stubs but
                        // exists at runtime in the framework; the module runs inside the
                        // target app's process against the real framework, so reflection
                        // works without additional access flags. Upgrade path: if a future
                        // API level exposes removeAction publicly, call it directly.
                        val remove = android.content.IntentFilter::class.java
                            .getDeclaredMethod("removeAction", String::class.java)
                        remove.invoke(filter, android.content.Intent.ACTION_SCREEN_OFF)
                        remove.invoke(filter, android.content.Intent.ACTION_USER_PRESENT)
                    }
                }
                chain.proceed()
            }
        }.onFailure { log(Log.WARN, TAG, "registerReceiver hook failed: ${it.message}") }
    }

    private fun installHeartbeatHook(cl: ClassLoader, pkg: String) {
        runCatching {
            val c = Class.forName("android.app.Application", false, cl)
            val m = c.getDeclaredMethod("attachBaseContext", android.content.Context::class.java)
            hook(m).intercept { chain ->
                val result = chain.proceed()
                if (isScreenOffFeatureActive()) {
                    val ctx = chain.getArg(0) as android.content.Context
                    sendHeartbeat(ctx, pkg)
                }
                result
            }
        }.onFailure { log(Log.WARN, TAG, "Application.attachBaseContext hook failed: ${it.message}") }
    }

    private fun sendHeartbeat(ctx: android.content.Context, pkg: String) {
        val v = android.content.ContentValues().apply {
            put(LsposedConfigContentProvider.COL_PACKAGE, pkg)
            put(LsposedConfigContentProvider.COL_TIMESTAMP, System.currentTimeMillis())
        }
        runCatching { ctx.contentResolver.insert(LsposedConfigContentProvider.HEARTBEAT_URI, v) }
    }

    companion object {
        private const val TAG = "ExtinguishModule"
    }
}
