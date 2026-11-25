package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.UpdateUiState
import com.example.core.tr

@Composable
fun AppUpdateDialogs(
    state: UpdateUiState,
    onAcceptUpdate: () -> Unit,
    onDismiss: () -> Unit,
    onCancelDownload: () -> Unit,
    onOpenInstallPermissionSettings: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    when (state) {
        is UpdateUiState.UpdateAvailable -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = colors.surface,
                textContentColor = colors.onSurface,
                text = {
                    Text(
                        text = tr("App Update", "تحديث التطبيق"),
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.onSurface,
                        modifier = Modifier.testTag("update_dialog_text")
                    )
                },
                confirmButton = {
                    Button(
                        onClick = onAcceptUpdate,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primary,
                            contentColor = colors.onPrimary
                        ),
                        modifier = Modifier.testTag("update_confirm_button")
                    ) {
                        Text(
                            text = tr("Update", "تحديث"),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("update_later_button")
                    ) {
                        Text(
                            text = tr("Later", "لاحقاً"),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            )
        }

        is UpdateUiState.Downloading -> {
            AlertDialog(
                onDismissRequest = {},
                containerColor = colors.surface,
                titleContentColor = colors.onSurface,
                textContentColor = colors.onSurfaceVariant,
                title = {
                    Text(
                        text = tr("App Update", "تحديث التطبيق"),
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.onSurface
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (state.indeterminate) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = colors.secondary,
                                trackColor = colors.outline
                            )
                        } else {
                            LinearProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier.fillMaxWidth(),
                                color = colors.secondary,
                                trackColor = colors.outline
                            )
                            Text(
                                text = "${(state.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(
                        onClick = onCancelDownload,
                        modifier = Modifier.testTag("update_cancel_download_button")
                    ) {
                        Text(
                            text = tr("Cancel", "إلغاء"),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.primary
                        )
                    }
                }
            )
        }

        is UpdateUiState.NeedsInstallPermission -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = colors.surface,
                titleContentColor = colors.onSurface,
                textContentColor = colors.onSurfaceVariant,
                title = {
                    Text(
                        text = tr("Install Permission Required", "إذن التثبيت مطلوب"),
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.onSurface
                    )
                },
                text = {
                    Text(
                        text = tr(
                            "Please allow Al-Makhzanji Lexicon to install updates in device settings, then return to continue.",
                            "يرجى السماح لتطبيق معجم المخزنجي بتثبيت التحديثات من إعدادات الجهاز ثم العودة للمتابعة."
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                },
                confirmButton = {
                    Button(
                        onClick = onOpenInstallPermissionSettings,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.secondary,
                            contentColor = colors.onSecondary
                        ),
                        modifier = Modifier.testTag("open_install_permission_button")
                    ) {
                        Text(
                            text = tr("Settings", "الإعدادات"),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = tr("Later", "لاحقاً"),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            )
        }

        else -> Unit
    }
}
