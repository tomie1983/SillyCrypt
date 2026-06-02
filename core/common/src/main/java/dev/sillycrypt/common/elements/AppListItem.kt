package dev.sillycrypt.common.elements

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.sillycrypt.common.entities.ApplicationInfoWithData

@Composable
fun AppListItem(
    appWithFlag: ApplicationInfoWithData,
    onPackageInstallChanged: (applicationInfo: ApplicationInfo, install: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val applicationInfo = appWithFlag.applicationInfo

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                bitmap = appWithFlag.icon,
                contentDescription = appWithFlag.title,
                modifier = Modifier.size(40.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = appWithFlag.title,
                    style = MaterialTheme.typography.bodyLarge
                )

                Text(
                    text = appWithFlag.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Switch(
                checked = appWithFlag.toInstall,
                onCheckedChange = { checked ->
                    onPackageInstallChanged(applicationInfo, checked)
                }
            )
        }
    }
}