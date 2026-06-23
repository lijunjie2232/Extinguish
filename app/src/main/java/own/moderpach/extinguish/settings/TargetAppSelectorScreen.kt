package own.moderpach.extinguish.settings

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import own.moderpach.extinguish.ExtinguishNavGraph
import own.moderpach.extinguish.ExtinguishNavRoute
import own.moderpach.extinguish.R
import own.moderpach.extinguish.home.cards.TargetAppSelector
import own.moderpach.extinguish.settings.components.SettingCard
import own.moderpach.extinguish.settings.components.SettingLazyColumn
import own.moderpach.extinguish.settings.components.SettingListItem
import own.moderpach.extinguish.settings.data.ISettingsRepository
import own.moderpach.extinguish.ui.components.ExtinguishTopAppBarWithNavigationBack
import own.moderpach.extinguish.ui.navigation.extinguishComposable

// ponytail: skip @Preview — FakeSettingsRepository covers the input but a real
// PackageManager is needed for the list. Building a fake PM for one preview isn't
// worth the boilerplate. Upgrade: add a FakePackageManager when a second screen
// needs it.

private data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Bitmap?,
    val isSystem: Boolean,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
)

private enum class SortKey { Name, FirstInstall, LastUpdate }
private enum class SortDir { Asc, Desc }

// ponytail: AdaptiveIconDrawable (Android O+) isn't a BitmapDrawable, so a naive
// cast loses the icon. Draw any Drawable into a Bitmap once at load time. Ceiling:
// ~one 96x96 bitmap per installed app held in memory (~hundreds of KB). Upgrade
// path: wrap icons in LruCache + soft refs if memory pressure shows in profiling.
private fun Drawable.toBitmapOrNull(size: Int = 96): Bitmap? {
    if (this is BitmapDrawable && bitmap != null) return bitmap
    return try {
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bmp ->
            val canvas = Canvas(bmp)
            setBounds(0, 0, size, size)
            draw(canvas)
        }
    } catch (e: Exception) {
        null
    }
}

fun NavGraphBuilder.targetAppSelector(
    onBack: () -> Unit,
    settingsRepository: ISettingsRepository,
    onNavigateTo: (ExtinguishNavRoute) -> Unit
) = extinguishComposable(
    ExtinguishNavGraph.TargetAppSelector,
) {
    TargetAppSelectorScreen(onBack, settingsRepository, onNavigateTo)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TargetAppSelectorScreen(
    onBack: () -> Unit,
    settingsRepository: ISettingsRepository,
    onNavigateTo: (ExtinguishNavRoute) -> Unit
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .map { ai ->
                    val info = try { pm.getPackageInfo(ai.packageName, 0) } catch (e: Exception) { null }
                    InstalledApp(
                        packageName = ai.packageName,
                        label = try { pm.getApplicationLabel(ai).toString() } catch (e: Exception) { ai.packageName },
                        icon = try { pm.getApplicationIcon(ai).toBitmapOrNull() } catch (e: Exception) { null },
                        isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                        firstInstallTime = info?.firstInstallTime ?: 0L,
                        lastUpdateTime = info?.lastUpdateTime ?: 0L,
                    )
                }
                .sortedBy { it.label.lowercase() }
        }
    }

    val selectedSet = remember {
        // ponytail: toMutableStateSet() is not in compose-runtime 1.11; build via
        // mutableStateSetOf() + addAll. Upgrade path: replace with .toMutableStateSet()
        // if/when the project bumps to a compose-runtime version that exposes it.
        mutableStateSetOf<String>().apply {
            addAll(settingsRepository.lsposedTargetPackages)
        }
    }
    var sortKey by remember { mutableStateOf(SortKey.Name) }
    var sortDir by remember { mutableStateOf(SortDir.Asc) }
    var showSystem by remember { mutableStateOf(false) }
    var selectedToTop by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    val visibleApps by remember {
        derivedStateOf {
            // ponytail: regex compile on every query; fine for typical device-scale
            // (~200 apps). Upgrade to compiled-on-change if a profiler flags it.
            val filtered = apps.filter { app ->
                (showSystem || !app.isSystem) &&
                    (searchQuery.isEmpty() ||
                        runCatching { Regex(searchQuery, RegexOption.IGNORE_CASE) }
                            .getOrNull()?.let { rx -> rx.containsMatchIn(app.label) || rx.containsMatchIn(app.packageName) }
                        ?: (app.label.contains(searchQuery, ignoreCase = true) ||
                            app.packageName.contains(searchQuery, ignoreCase = true)))
            }
            val comparator: Comparator<InstalledApp> = when (sortKey) {
                SortKey.Name -> compareBy<InstalledApp> { it.label.lowercase() }
                SortKey.FirstInstall -> compareBy<InstalledApp> { it.firstInstallTime }
                SortKey.LastUpdate -> compareBy<InstalledApp> { it.lastUpdateTime }
            }.let { if (sortDir == SortDir.Asc) it else it.reversed() }
            val sorted = filtered.sortedWith(comparator)
            if (selectedToTop) {
                // ponytail: partition + concat is O(n); a single in-place stable
                // partition would shave one allocation but isn't worth the complexity here.
                val (sel, unsel) = sorted.partition { it.packageName in selectedSet }
                sel + unsel
            } else sorted
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.displayCutout),
        topBar = {
            ExtinguishTopAppBarWithNavigationBack(
                onBack = onBack,
                titleString = stringResource(R.string.str_Select_applications),
                scrollBehavior = scrollBehavior
            )
        },
        bottomBar = {
            // ponytail: BottomBar carries Cancel/Confirm so the long list scrolls
            // under it; cheaper than a sticky item at the tail of a LazyColumn.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) {
                    Text(stringResource(R.string.str_Cancel))
                }
                Button(
                    onClick = {
                        settingsRepository.lsposedTargetPackages = selectedSet.toSet()
                        onBack()
                    },
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(stringResource(R.string.str_Confirm))
                }
            }
        },
    ) { innerPadding ->
        SettingLazyColumn(
            contentPadding = innerPadding,
        ) {
            item {
                SettingCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // ponytail: plain MutableState<Boolean> for the dropdown expanded flag;
                    // Material3 1.4 has no rememberExposedDropdownMenuState helper, so use the
                    // raw expanded/onExpandedChange pair directly.
                    var sortKeyExpanded by remember { mutableStateOf(false) }
                    var sortDirExpanded by remember { mutableStateOf(false) }
                    val sortKeyOptions = listOf(
                        SortKey.Name to stringResource(R.string.str_Sort_name),
                        SortKey.FirstInstall to stringResource(R.string.str_Sort_first_install),
                        SortKey.LastUpdate to stringResource(R.string.str_Sort_last_update),
                    )
                    val sortDirOptions = listOf(
                        SortDir.Asc to stringResource(R.string.str_Sort_asc),
                        SortDir.Desc to stringResource(R.string.str_Sort_desc),
                    )
                    ExposedDropdownMenuBox(
                        expanded = sortKeyExpanded,
                        onExpandedChange = { sortKeyExpanded = it }
                    ) {
                        OutlinedTextField(
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                            readOnly = true,
                            value = sortKeyOptions.first { it.first == sortKey }.second,
                            onValueChange = {},
                            label = { Text(stringResource(R.string.str_Sort_by)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sortKeyExpanded) },
                        )
                        ExposedDropdownMenu(
                            expanded = sortKeyExpanded,
                            onDismissRequest = { sortKeyExpanded = false }
                        ) {
                            sortKeyOptions.forEach { (key, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        sortKey = key
                                        sortKeyExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    ExposedDropdownMenuBox(
                        expanded = sortDirExpanded,
                        onExpandedChange = { sortDirExpanded = it }
                    ) {
                        OutlinedTextField(
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                            readOnly = true,
                            value = sortDirOptions.first { it.first == sortDir }.second,
                            onValueChange = {},
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sortDirExpanded) },
                        )
                        ExposedDropdownMenu(
                            expanded = sortDirExpanded,
                            onDismissRequest = { sortDirExpanded = false }
                        ) {
                            sortDirOptions.forEach { (dir, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        sortDir = dir
                                        sortDirExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    SettingListItem(
                        headline = stringResource(R.string.str_Show_system_apps),
                        trailingContent = {
                            Checkbox(checked = showSystem, onCheckedChange = { showSystem = it })
                        },
                        onClick = { showSystem = !showSystem }
                    )
                    SettingListItem(
                        headline = stringResource(R.string.str_Sort_selected_to_top),
                        trailingContent = {
                            Checkbox(checked = selectedToTop, onCheckedChange = { selectedToTop = it })
                        },
                        onClick = { selectedToTop = !selectedToTop }
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text(stringResource(R.string.str_Search)) },
                        singleLine = true,
                    )
                }
            }
            items(visibleApps, key = { it.packageName }) { app ->
                val isSelected = app.packageName in selectedSet
                SettingCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (isSelected) selectedSet.remove(app.packageName)
                        else selectedSet.add(app.packageName)
                    }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        app.icon?.asImageBitmap()?.let {
                            Image(
                                bitmap = it,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                app.label,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "  (${app.packageName})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = {
                                if (it) selectedSet.add(app.packageName)
                                else selectedSet.remove(app.packageName)
                            }
                        )
                    }
                }
            }
        }
    }
}
