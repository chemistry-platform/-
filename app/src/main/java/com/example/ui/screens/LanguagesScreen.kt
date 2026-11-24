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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.LanguagePackInfo
import com.example.ui.components.AppIcons
import com.example.ui.viewmodel.LanguagesViewModel

@Composable
fun LanguagesScreen(
    viewModel: LanguagesViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val languages by viewModel.languages.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val pendingCellularCode by viewModel.pendingCellularCode.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    val filteredLanguages = remember(languages, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) languages
        else languages.filter {
            it.arabicName.contains(q, ignoreCase = true) ||
                it.nativeName.contains(q, ignoreCase = true) ||
                it.code.contains(q, ignoreCase = true)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("languages_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = colors.onBackground
                    )
                }
                Column {
                    Text(
                        text = "اللغات",
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.onBackground
                    )
                    Text(
                        text = "${languages.size} لغة متاحة عبر ML Kit بدون اتصال",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onSearchQueryChange(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("languages_search_field"),
                placeholder = { Text("ابحث عن لغة...", color = colors.onSurfaceVariant) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.secondary) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "مسح")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.surface,
                    unfocusedContainerColor = colors.surface,
                    focusedBorderColor = colors.secondary,
                    unfocusedBorderColor = colors.outline
                )
            )

            if (!message.isNullOrBlank()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    color = colors.errorContainer,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = message.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onErrorContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = filteredLanguages,
                    key = { it.code }
                ) { lang ->
                    LanguagePackRow(
                        info = lang,
                        onDownload = { viewModel.requestDownload(lang.code) },
                        onDelete = { viewModel.deleteLanguage(lang.code) }
                    )
                }
            }
        }
    }

    if (pendingCellularCode != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissCellularDialog() },
            containerColor = colors.surface,
            title = { Text("التحميل عبر بيانات الهاتف؟", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Text(
                    "أنت متصل ببيانات الهاتف المحمول. هل تريد تحميل حزمة اللغة الآن؟",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.confirmCellularDownload() }) {
                    Text("تحميل")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissCellularDialog() }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
private fun LanguagePackRow(
    info: LanguagePackInfo,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("lang_row_${info.code}"),
        color = colors.surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, colors.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = info.arabicName,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurface
                )
                Text(
                    text = "${info.nativeName} (${info.code})",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
                Text(
                    text = if (info.isDownloaded) "محمّلة" else "غير محمّلة",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (info.isDownloaded) colors.secondary else colors.onSurfaceVariant
                )
            }

            if (info.isDownloading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        color = colors.secondary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "جاري التحميل",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.secondary
                    )
                }
            } else if (info.isDownloaded) {
                OutlinedButton(
                    onClick = onDelete,
                    border = BorderStroke(1.dp, colors.outline),
                    modifier = Modifier.testTag("delete_lang_${info.code}")
                ) {
                    Icon(
                        imageVector = AppIcons.DeleteOutline,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("حذف", color = colors.onSurface)
                }
            } else {
                Button(
                    onClick = onDownload,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.secondary,
                        contentColor = colors.onSecondary
                    ),
                    modifier = Modifier.testTag("download_lang_${info.code}")
                ) {
                    Icon(
                        imageVector = AppIcons.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("تحميل")
                }
            }
        }
    }
}
