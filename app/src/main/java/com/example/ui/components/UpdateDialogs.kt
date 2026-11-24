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
import com.example.ui.theme.AccentRed
import com.example.ui.theme.CanvasBackground
import com.example.ui.theme.DividerBorder
import com.example.ui.theme.LinkBlue
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

@Composable
fun AppUpdateDialogs(
    state: UpdateUiState,
    onAcceptUpdate: () -> Unit,
    onDismiss: () -> Unit,
    onCancelDownload: () -> Unit,
    onOpenInstallPermissionSettings: () -> Unit
) {
    when (state) {
        is UpdateUiState.UpdateAvailable -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = SurfaceCard,
                textContentColor = TextPrimary,
                text = {
                    Text(
                        text = "تحديث التطبيق",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary,
                        modifier = Modifier.testTag("update_dialog_text")
                    )
                },
                confirmButton = {
                    Button(
                        onClick = onAcceptUpdate,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                        modifier = Modifier.testTag("update_confirm_button")
                    ) {
                        Text(
                            text = "تحديث",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("update_later_button")
                    ) {
                        Text(
                            text = "لاحقاً",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextMuted
                        )
                    }
                }
            )
        }

        is UpdateUiState.Downloading -> {
            AlertDialog(
                onDismissRequest = {},
                containerColor = SurfaceCard,
                titleContentColor = TextPrimary,
                textContentColor = TextMuted,
                title = {
                    Text(
                        text = "تحديث التطبيق",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary
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
                                color = LinkBlue,
                                trackColor = DividerBorder
                            )
                        } else {
                            LinearProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier.fillMaxWidth(),
                                color = LinkBlue,
                                trackColor = DividerBorder
                            )
                            Text(
                                text = "${(state.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextMuted
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
                            text = "إلغاء",
                            style = MaterialTheme.typography.labelLarge,
                            color = AccentRed
                        )
                    }
                }
            )
        }

        is UpdateUiState.NeedsInstallPermission -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = SurfaceCard,
                titleContentColor = TextPrimary,
                textContentColor = TextMuted,
                title = {
                    Text(
                        text = "إذن التثبيت مطلوب",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary
                    )
                },
                text = {
                    Text(
                        text = "يرجى السماح لتطبيق معجم المخزنجي بتثبيت التحديثات من إعدادات الجهاز ثم العودة للمتابعة.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                },
                confirmButton = {
                    Button(
                        onClick = onOpenInstallPermissionSettings,
                        colors = ButtonDefaults.buttonColors(containerColor = LinkBlue),
                        modifier = Modifier.testTag("open_install_permission_button")
                    ) {
                        Text(
                            text = "الإعدادات",
                            style = MaterialTheme.typography.labelLarge,
                            color = CanvasBackground
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = "لاحقاً",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextMuted
                        )
                    }
                }
            )
        }

        else -> Unit
    }
}
