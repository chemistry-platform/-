package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.UpdateUiState
import com.example.ui.theme.AccentRed
import com.example.ui.theme.CanvasBackground
import com.example.ui.theme.DividerBorder
import com.example.ui.theme.LinkBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.viewmodel.MoreViewModel

@Composable
fun MoreScreen(
    viewModel: MoreViewModel,
    modifier: Modifier = Modifier
) {
    val cachedCount by viewModel.cachedWordCount.collectAsStateWithLifecycle()
    val mlKitStatus by viewModel.mlKitStatus.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
    val updateUiState by viewModel.updateUiState.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CanvasBackground)
            .statusBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column {
                    Text(
                        text = stringResource(R.string.nav_more),
                        style = MaterialTheme.typography.headlineLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = "إدارة الترجمة بدون إنترنت والذاكرة المحلية والتحديثات",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }

            if (!actionMessage.isNullOrBlank()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = SurfaceCard,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, LinkBlue)
                    ) {
                        Text(
                            text = actionMessage.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = LinkBlue,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            // App Self-Update Card
            item {
                val isChecking = updateUiState is UpdateUiState.Checking
                val updateStatusText = (updateUiState as? UpdateUiState.Message)?.text
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = SurfaceCard,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, DividerBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SystemUpdate,
                                contentDescription = null,
                                tint = AccentRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "تحديثات التطبيق",
                                style = MaterialTheme.typography.headlineSmall,
                                color = TextPrimary
                            )
                        }
                        if (!updateStatusText.isNullOrBlank()) {
                            Text(
                                text = updateStatusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (updateStatusText == "التطبيق محدّث") SuccessGreen else TextMuted,
                                modifier = Modifier.testTag("update_status_message")
                            )
                        }
                        Button(
                            onClick = { viewModel.checkForUpdateNow() },
                            enabled = !isChecking,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("check_update_button")
                        ) {
                            if (isChecking) {
                                CircularProgressIndicator(
                                    color = TextPrimary,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = if (isChecking) "جاري التحقق..." else "تحقق من التحديث",
                                style = MaterialTheme.typography.labelLarge,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }

            // ML Kit Offline Language Pack Card
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = SurfaceCard,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, DividerBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CloudDownload,
                                contentDescription = null,
                                tint = LinkBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "حزمة الترجمة بدون إنترنت (ML Kit)",
                                style = MaterialTheme.typography.headlineSmall,
                                color = TextPrimary
                            )
                        }
                        Text(
                            text = "تسمح لك بترجمة الكلمات والجمل بين العربية والإنجليزية بدون استهلاك بيانات الهاتف.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val ready = mlKitStatus.isArabicDownloaded && mlKitStatus.isEnglishDownloaded
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = if (ready) SuccessGreen else TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (ready) "الحزمة محملة وجاهزة للعمل دون اتصال" else "الحزمة غير محملة بالكامل بعد",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (ready) SuccessGreen else TextMuted
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { viewModel.downloadOfflineModels() },
                                enabled = !mlKitStatus.isDownloading && isOnline,
                                colors = ButtonDefaults.buttonColors(containerColor = LinkBlue),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("download_mlkit_button")
                            ) {
                                if (mlKitStatus.isDownloading) {
                                    CircularProgressIndicator(
                                        color = CanvasBackground,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = if (mlKitStatus.isDownloading) "جاري التحميل..." else "تحميل الحزمة",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = CanvasBackground
                                )
                            }

                            OutlinedButton(
                                onClick = { viewModel.deleteOfflineModels() },
                                border = BorderStroke(1.dp, DividerBorder),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "حذف الحزمة",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                }
            }

            // Room Offline Cache Card
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = SurfaceCard,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, DividerBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Storage,
                                contentDescription = null,
                                tint = AccentRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "الذاكرة المحلية للقاموس",
                                style = MaterialTheme.typography.headlineSmall,
                                color = TextPrimary
                            )
                        }
                        Text(
                            text = "عدد الكلمات المحفوظة مع معانيها وأمثلتها للعمل بدون إنترنت: $cachedCount كلمة",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.clearCache() },
                                border = BorderStroke(1.dp, DividerBorder),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = null,
                                    tint = AccentRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "مسح الكاش",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextPrimary
                                )
                            }
                            OutlinedButton(
                                onClick = { viewModel.clearHistory() },
                                border = BorderStroke(1.dp, DividerBorder),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "مسح السجل",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                }
            }

            // About & Open Sources Card
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = SurfaceCard,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, DividerBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = null,
                                tint = LinkBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "عن معجم المخزنجي",
                                style = MaterialTheme.typography.headlineSmall,
                                color = TextPrimary
                            )
                        }
                        HorizontalDivider(color = DividerBorder, thickness = 1.dp)
                        Text(
                            text = "تطبيق معجم ومترجم عربي-إنجليزي مجاني يعتمد حصرياً على المصادر المفتوحة:\n• Free Dictionary API للتعريفات والصوتيات\n• Datamuse API للمترادفات والأضداد والروابط الدلالية\n• Google ML Kit للترجمة الفورية على الجهاز\n• Gemini عبر Firebase AI Logic لتوليد أمثلة أصلية لكل معنى",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }
            }
        }
    }
}
