package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.AudioPlayerManager
import com.example.ui.components.HighlightedEnglishText
import com.example.ui.components.LoadingCard
import com.example.ui.components.UkUsMiniButtons
import com.example.ui.theme.CanvasBackground
import com.example.ui.theme.DividerBorder
import com.example.ui.theme.LinkBlue
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.viewmodel.WordDetailUiState
import com.example.ui.viewmodel.WordDetailViewModel
import java.util.Locale

@Composable
fun PhrasesListScreen(
    word: String,
    viewModel: WordDetailViewModel,
    audioManager: AudioPlayerManager,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CanvasBackground)
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
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("phrases_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = TextPrimary
                    )
                }
                Text(
                    text = "عبارات وتراكيب: $word",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
            }

            when (val state = uiState) {
                is WordDetailUiState.Loading -> LoadingCard("جاري تحميل العبارات...")
                is WordDetailUiState.Error -> {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                is WordDetailUiState.Success -> {
                    val phrases = state.entry.phrases
                    if (phrases.isEmpty()) {
                        Text(
                            text = "لا توجد عبارات إضافية مسجلة لهذه الكلمة.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted,
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(phrases) { idx, phrase ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = SurfaceCard,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, DividerBorder)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                            Text(
                                                text = phrase.phrase,
                                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                                color = LinkBlue
                                            )
                                        }
                                        Text(
                                            text = phrase.arabicMeaning,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextPrimary
                                        )
                                        if (phrase.exampleEnglish.isNotBlank()) {
                                            Surface(
                                                color = SurfaceElevated,
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Column(
                                                        modifier = Modifier.weight(1f),
                                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                                    ) {
                                                        HighlightedEnglishText(
                                                            sentence = phrase.exampleEnglish,
                                                            targetWord = word
                                                        )
                                                        if (phrase.exampleArabic.isNotBlank()) {
                                                            Text(
                                                                text = phrase.exampleArabic,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = TextMuted
                                                            )
                                                        }
                                                    }
                                                    UkUsMiniButtons(
                                                        onPlayUk = {
                                                            audioManager.speakSentence(
                                                                phrase.exampleEnglish,
                                                                Locale.UK,
                                                                "full_ph_uk_$idx"
                                                            )
                                                        },
                                                        onPlayUs = {
                                                            audioManager.speakSentence(
                                                                phrase.exampleEnglish,
                                                                Locale.US,
                                                                "full_ph_us_$idx"
                                                            )
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
