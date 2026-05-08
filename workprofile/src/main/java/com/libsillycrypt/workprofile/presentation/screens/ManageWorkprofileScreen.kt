package com.libsillycrypt.workprofile.presentation.screens

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dev.sillycrypt.settings.presentation.screens.AppListItem
import com.libsillycrypt.workprofile.R
import dev.sillycrypt.common.entities.ApplicationInfoWithFlag
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageWorkprofileScreen(
    apps: ImmutableList<ApplicationInfoWithFlag>,
    onPackageInstallChanged: (app: ApplicationInfo, install: Boolean) -> Unit,
    innerPadding: PaddingValues,
    refreshApps: suspend () -> Unit,
    deleteWorkProfile: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var isRefreshing by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            isRefreshing = true
            try {
                refreshApps()
            } finally {
                isRefreshing = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = ::refresh,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = apps,
                    key = { it.applicationInfo.packageName }
                ) { appWithFlag ->
                    AppListItem(
                        modifier = Modifier.animateItem(),
                        appWithFlag = appWithFlag,
                        onPackageInstallChanged = onPackageInstallChanged
                    )
                }
            }
        }

        Button(onClick = deleteWorkProfile) {
            Text(stringResource(R.string.delete_workprofile))
        }
    }
}