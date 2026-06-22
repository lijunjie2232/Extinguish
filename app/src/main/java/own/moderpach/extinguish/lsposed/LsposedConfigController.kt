package own.moderpach.extinguish.lsposed

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import own.moderpach.extinguish.settings.data.SettingsTokens

class LsposedConfigController(
    private val context: Context,
    private val settingsDatabase: DataStore<Preferences>,
    lifecycleOwner: LifecycleOwner,
) {

    init {
        lifecycleOwner.lifecycleScope.launch {
            // ponytail: config only refreshes while Extinguish is in the foreground (RESUMED); target apps pick up changes on next process start, which matches LSPosed scope semantics.
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                settingsDatabase.data.map { prefs ->
                    val solStr = prefs[SettingsTokens.Solution.key] ?: SettingsTokens.Solution.default
                    val solution = runCatching {
                        SettingsTokens.SolutionValue.valueOf(solStr)
                    }.getOrElse { SettingsTokens.SolutionValue.ShizukuPowerOffScreen }
                    val enabled = solution == SettingsTokens.SolutionValue.LsposedNative
                    val targetPackages: Set<String> =
                        prefs[SettingsTokens.LsposedTargetPackages.key] ?: emptySet()
                    enabled to targetPackages
                }.collectLatest { (enabled, targetPackages) ->
                    writeConfig(context, enabled, targetPackages)
                }
            }
        }
    }
}
