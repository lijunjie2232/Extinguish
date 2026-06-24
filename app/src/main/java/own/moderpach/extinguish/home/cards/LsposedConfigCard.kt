package own.moderpach.extinguish.home.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import own.moderpach.extinguish.ExtinguishNavGraph
import own.moderpach.extinguish.ExtinguishNavRoute
import own.moderpach.extinguish.LocalSolutionDependencyManager
import own.moderpach.extinguish.R
import own.moderpach.extinguish.home.HomeScreenCardKey
import own.moderpach.extinguish.home.HomeScreenCardKeys
import own.moderpach.extinguish.settings.data.ISettingsRepository
import own.moderpach.extinguish.ui.components.ExtinguishCard
import own.moderpach.extinguish.ui.components.ExtinguishOutlinedButton

// ponytail: skip @Preview here — LocalSolutionDependencyManager throws if not provided,
// and building a fake manager just for a preview isn't worth the boilerplate. Upgrade
// path: add a FakeSolutionDependencyManager (see guide/GuideShizukuRunningScreen.kt) and
// re-enable a preview wrapped in `LocalSolutionDependencyManager provides ...`.

val ExtinguishNavGraph.TargetAppSelector: ExtinguishNavRoute get() = "TargetAppSelector"

val HomeScreenCardKeys.lsposedConfig: HomeScreenCardKey get() = "LsposedConfig"

fun LazyStaggeredGridScope.lsposedConfigCard(
    settingsRepository: ISettingsRepository,
    onNavigateTo: (ExtinguishNavRoute) -> Unit
) = item(
    key = HomeScreenCardKeys.lsposedConfig
) {
    LsposedConfigCard(
        settingsRepository,
        onNavigateTo
    )
}

@Composable
fun LsposedConfigCard(
    settingsRepository: ISettingsRepository,
    onNavigateTo: (ExtinguishNavRoute) -> Unit
) {
    val solutionDependencyManager = LocalSolutionDependencyManager.current
    val solutionState by solutionDependencyManager.state.collectAsStateWithLifecycle()

    ExtinguishCard(
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            stringResource(R.string.Lsposed),
            style = MaterialTheme.typography.labelMedium
        )
        Text(
            if (solutionState.isLsposedActivated) stringResource(R.string.str_Lsposed_activated)
            else stringResource(R.string.str_Lsposed_not_activated),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            stringResource(R.string.str_Lsposed_target_count, solutionState.lsposedConfiguredTargetCount),
            style = MaterialTheme.typography.bodyMedium
        )
        ExtinguishOutlinedButton(
            onClick = { onNavigateTo(ExtinguishNavGraph.TargetAppSelector) },
            icon = painterResource(R.drawable.swap_horiz_20px),
            text = stringResource(R.string.str_Select_applications)
        )
    }
}
