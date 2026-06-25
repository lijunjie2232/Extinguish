package own.moderpach.extinguish

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import rikka.shizuku.Shizuku
import own.moderpach.extinguish.lsposed.LSPSED_CONFIG_FILENAME
import own.moderpach.extinguish.lsposed.LsposedConfigContentProvider
import own.moderpach.extinguish.lsposed.parseFromInputStream
import java.io.File
import java.io.FileInputStream

class SolutionDependencyManager(
    private val context: Context
) : ISolutionDependencyManager {
    private val _state = MutableStateFlow(SolutionDependencyState())
    override val state: StateFlow<SolutionDependencyState> = _state

    private interface ShizukuDependencyListener {
        val binderReceivedListener: Shizuku.OnBinderReceivedListener
        val binderDeadListener: Shizuku.OnBinderDeadListener
        val requestPermissionResultListener: Shizuku.OnRequestPermissionResultListener
    }

    private val shizuku = object : ShizukuDependencyListener {
        override val binderReceivedListener = Shizuku.OnBinderReceivedListener {
            _state.getAndUpdate {
                it.copy(isShizukuBinderAlive = true)
            }
        }
        override val binderDeadListener = Shizuku.OnBinderDeadListener {
            _state.getAndUpdate {
                it.copy(isShizukuBinderAlive = false)
            }
        }
        override val requestPermissionResultListener =
            Shizuku.OnRequestPermissionResultListener { _, grantResult ->
                _state.getAndUpdate {
                    it.copy(isShizukuPermissionGranted = grantResult == PackageManager.PERMISSION_GRANTED)
                }
            }
    }

    override fun updateImmediately() {
        _state.update {
            val isShizukuBinderAlive = runCatching { Shizuku.pingBinder() }.getOrElse { false }
            val isShizukuPermissionGranted = runCatching {
                isShizukuBinderAlive.and(
                    Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                )
            }.getOrElse { false }
            val lsposedConfiguredTargetCount = runCatching {
                FileInputStream(File(context.filesDir, LSPSED_CONFIG_FILENAME)).use { fis ->
                    parseFromInputStream(fis)?.targetPackages?.size ?: 0
                }
            }.getOrNull() ?: 0
            val isLsposedActivated = runCatching {
                val now = System.currentTimeMillis()
                var fresh = false
                context.contentResolver.query(
                    LsposedConfigContentProvider.HEARTBEAT_URI, null, null, null, null
                )?.use { c ->
                    val tsIdx = c.getColumnIndex(LsposedConfigContentProvider.COL_TIMESTAMP)
                    while (c.moveToNext()) {
                        val ts = if (tsIdx >= 0) c.getLong(tsIdx) else 0L
                        if (now - ts < LsposedConfigContentProvider.STALE_MS) {
                            fresh = true
                            break
                        }
                    }
                }
                fresh
            }.getOrNull() ?: false
            SolutionDependencyState(
                isShizukuBinderAlive = isShizukuBinderAlive,
                isShizukuPermissionGranted = isShizukuPermissionGranted,
                isLsposedActivated = isLsposedActivated,
                lsposedConfiguredTargetCount = lsposedConfiguredTargetCount
            )
        }
    }

    override fun requestShizukuPermission(onShouldShowRequestPermissionRationale: () -> Unit) {
        val isShizukuBinderAlive = runCatching { Shizuku.pingBinder() }.getOrElse { false }
        val isShizukuPermissionGranted = runCatching {
            isShizukuBinderAlive.and(
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            )
        }.getOrElse { false }
        if (isShizukuPermissionGranted) return
        if (Shizuku.shouldShowRequestPermissionRationale()) {
            onShouldShowRequestPermissionRationale()
            return
        }
        Shizuku.requestPermission(1)
    }

    init {
        Shizuku.addBinderReceivedListener(shizuku.binderReceivedListener)
        Shizuku.addBinderDeadListener(shizuku.binderDeadListener)
        Shizuku.addRequestPermissionResultListener(shizuku.requestPermissionResultListener)
    }

    override fun destroy() {
        Shizuku.removeBinderReceivedListener(shizuku.binderReceivedListener)
        Shizuku.removeBinderDeadListener(shizuku.binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(shizuku.requestPermissionResultListener)
    }

}

data class SolutionDependencyState(
    val isShizukuBinderAlive: Boolean = false,
    val isShizukuPermissionGranted: Boolean = false,
    val isLsposedActivated: Boolean = false,
    val lsposedConfiguredTargetCount: Int = 0
)
