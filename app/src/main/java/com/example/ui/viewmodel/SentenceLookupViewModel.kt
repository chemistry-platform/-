package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.ArabicTextUtils
import com.example.core.TranslationManager
import com.example.core.UserSettingsPreferences
import com.example.data.repository.LexiconRepository
import com.example.domain.model.InstantTranslationResult
import com.example.domain.model.LanguageDirection
import com.example.domain.model.LiveSearchPage
import com.example.domain.model.PhraseSheetData
import com.example.domain.model.SearchSuggestion
import com.example.domain.model.SentenceLookupResult
import com.google.mlkit.nl.translate.TranslateLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SentenceLookupViewModel(
    private val repository: LexiconRepository,
    private val translationManager: TranslationManager,
    settingsPreferences: UserSettingsPreferences,
    initialQuery: String
) : ViewModel() {

    private val _query = MutableStateFlow(initialQuery)
    val query: StateFlow<String> = _query.asStateFlow()

    private val _inlineQuery = MutableStateFlow(initialQuery)
    val inlineQuery: StateFlow<String> = _inlineQuery.asStateFlow()

    private val _isSearchingInline = MutableStateFlow(false)
    val isSearchingInline: StateFlow<Boolean> = _isSearchingInline.asStateFlow()

    private val _isLoadingInline = MutableStateFlow(false)
    val isLoadingInline: StateFlow<Boolean> = _isLoadingInline.asStateFlow()

    private val _inlineSuggestions = MutableStateFlow<List<SearchSuggestion>>(emptyList())
    val inlineSuggestions: StateFlow<List<SearchSuggestion>> = _inlineSuggestions.asStateFlow()

    private val _hasMoreInlineSuggestions = MutableStateFlow(false)
    val hasMoreInlineSuggestions: StateFlow<Boolean> = _hasMoreInlineSuggestions.asStateFlow()

    private val _isExpandedAllInline = MutableStateFlow(false)
    val isExpandedAllInline: StateFlow<Boolean> = _isExpandedAllInline.asStateFlow()

    private val _isLoadingMoreInline = MutableStateFlow(false)
    val isLoadingMoreInline: StateFlow<Boolean> = _isLoadingMoreInline.asStateFlow()

    private var cachedFullInlineQuery: String = ""
    private var cachedFullInlineSuggestions: List<SearchSuggestion> = emptyList()

    private val _direction = MutableStateFlow(
        if (ArabicTextUtils.containsArabicLetters(initialQuery)) {
            LanguageDirection.AR_TO_EN
        } else {
            LanguageDirection.EN_TO_AR
        }
    )
    val direction: StateFlow<LanguageDirection> = _direction.asStateFlow()

    private val _state = MutableStateFlow(
        SentenceLookupResult(
            originalText = initialQuery.trim(),
            isArabicInput = ArabicTextUtils.containsArabicLetters(initialQuery),
            wordChips = ArabicTextUtils.extractWordTokens(initialQuery),
            isUpdating = true
        )
    )
    val state: StateFlow<SentenceLookupResult> = _state.asStateFlow()

    private val _chipMeanings = MutableStateFlow<Map<String, String>>(emptyMap())
    val chipMeanings: StateFlow<Map<String, String>> = _chipMeanings.asStateFlow()

    private val _phraseSheet = MutableStateFlow<PhraseSheetData?>(null)
    val phraseSheet: StateFlow<PhraseSheetData?> = _phraseSheet.asStateFlow()

    private val _openWordEvent = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val openWordEvent: SharedFlow<String> = _openWordEvent.asSharedFlow()

    val inlineTranslation: StateFlow<InstantTranslationResult?> = flowOf(null)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = null
        )

    val showArabicTranslations: StateFlow<Boolean> = settingsPreferences.showArabicTranslations
    val showMachineTranslationLabel: StateFlow<Boolean> = settingsPreferences.showMachineTranslationLabel
    val audioSpeed: StateFlow<Float> = settingsPreferences.audioSpeed

    private val _isDownloadingPack = MutableStateFlow(false)
    val isDownloadingPack: StateFlow<Boolean> = _isDownloadingPack.asStateFlow()

    private val _showCellularConfirm = MutableStateFlow(false)
    val showCellularConfirm: StateFlow<Boolean> = _showCellularConfirm.asStateFlow()

    private val _packDownloadMessage = MutableStateFlow<String?>(null)
    val packDownloadMessage: StateFlow<String?> = _packDownloadMessage.asStateFlow()

    private var lookupJob: Job? = null
    private var chipJob: Job? = null
    private var chipTapJob: Job? = null

    init {
        viewModelScope.launch {
            _inlineQuery
                .debounce(80L)
                .flatMapLatest { raw ->
                    val trimmed = raw.trim()
                    if (!_isSearchingInline.value || trimmed.isEmpty()) {
                        _isLoadingInline.value = false
                        _hasMoreInlineSuggestions.value = false
                        flowOf(Triple(trimmed, emptyList<SearchSuggestion>(), false))
                    } else {
                        _isLoadingInline.value = true
                        repository.observeLiveSuggestions(
                            query = trimmed,
                            limit = QuickTranslateViewModel.INITIAL_PAGE_SIZE
                        ).onEach {
                            _isLoadingInline.value = false
                        }.catch { e ->
                            if (e is CancellationException) throw e
                            _isLoadingInline.value = false
                            emit(LiveSearchPage(emptyList(), hasMore = false))
                        }.let { sugFlow ->
                            flow {
                                sugFlow.collect { page ->
                                    emit(Triple(trimmed, page.items, page.hasMore))
                                }
                            }
                        }
                    }
                }
                .collect { (activeQuery, items, hasMore) ->
                    val currentTrimmed = _inlineQuery.value.trim()
                    if (!_isSearchingInline.value || currentTrimmed.isEmpty() || currentTrimmed != activeQuery) {
                        return@collect
                    }
                    if (_isExpandedAllInline.value &&
                        cachedFullInlineQuery == currentTrimmed &&
                        cachedFullInlineSuggestions.isNotEmpty()
                    ) {
                        _inlineSuggestions.value = cachedFullInlineSuggestions
                        _hasMoreInlineSuggestions.value = false
                    } else {
                        _inlineSuggestions.value = items
                        _hasMoreInlineSuggestions.value = hasMore
                        _isExpandedAllInline.value = false
                    }
                    _isLoadingInline.value = false
                    _isLoadingMoreInline.value = false
                }
        }

        lookupSentence(initialQuery)
    }

    fun onInlineQueryChanged(value: String) {
        val changed = value != _inlineQuery.value
        _inlineQuery.value = value
        if (changed) {
            _isSearchingInline.value = true
            _inlineSuggestions.value = emptyList()
            _hasMoreInlineSuggestions.value = false
            _isExpandedAllInline.value = false
            _isLoadingMoreInline.value = false
            cachedFullInlineQuery = ""
            cachedFullInlineSuggestions = emptyList()
            if (value.isNotBlank()) {
                _direction.value = if (ArabicTextUtils.containsArabicLetters(value)) {
                    LanguageDirection.AR_TO_EN
                } else {
                    LanguageDirection.EN_TO_AR
                }
            } else {
                _isLoadingInline.value = false
            }
        }
    }

    fun clearInlineSearch() {
        _inlineQuery.value = ""
        _isSearchingInline.value = true
        _inlineSuggestions.value = emptyList()
        _hasMoreInlineSuggestions.value = false
        _isExpandedAllInline.value = false
        _isLoadingInline.value = false
        _isLoadingMoreInline.value = false
        cachedFullInlineQuery = ""
        cachedFullInlineSuggestions = emptyList()
    }

    fun expandAllInlineSuggestions() {
        val trimmed = _inlineQuery.value.trim()
        if (trimmed.isEmpty() || _isLoadingMoreInline.value) return
        if (cachedFullInlineQuery == trimmed &&
            cachedFullInlineSuggestions.size > QuickTranslateViewModel.INITIAL_PAGE_SIZE
        ) {
            _isExpandedAllInline.value = true
            _inlineSuggestions.value = cachedFullInlineSuggestions
            _hasMoreInlineSuggestions.value = false
            return
        }
        _isLoadingMoreInline.value = true
        viewModelScope.launch {
            try {
                val page = repository.getLiveSuggestionsPaged(
                    query = trimmed,
                    offset = 0,
                    limit = QuickTranslateViewModel.EXPANDED_PAGE_SIZE
                )
                if (_inlineQuery.value.trim() == trimmed && _isSearchingInline.value) {
                    cachedFullInlineQuery = trimmed
                    cachedFullInlineSuggestions = page.items
                    _inlineSuggestions.value = page.items
                    _isExpandedAllInline.value = true
                    _hasMoreInlineSuggestions.value = false
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Ignore
            } finally {
                _isLoadingMoreInline.value = false
            }
        }
    }

    fun collapseInlineSuggestions() {
        if (!_isExpandedAllInline.value) return
        val current = _inlineSuggestions.value
        val trimmed = _inlineQuery.value.trim()
        if (cachedFullInlineQuery != trimmed || cachedFullInlineSuggestions.size < current.size) {
            cachedFullInlineQuery = trimmed
            cachedFullInlineSuggestions = current
        }
        _inlineSuggestions.value = current.take(QuickTranslateViewModel.INITIAL_PAGE_SIZE)
        _isExpandedAllInline.value = false
        _hasMoreInlineSuggestions.value = current.size > QuickTranslateViewModel.INITIAL_PAGE_SIZE
    }

    fun lookupSentence(text: String) {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return
        _isSearchingInline.value = false
        _inlineSuggestions.value = emptyList()
        _hasMoreInlineSuggestions.value = false
        _isExpandedAllInline.value = false
        _query.value = cleaned
        _inlineQuery.value = cleaned
        _direction.value = if (ArabicTextUtils.containsArabicLetters(cleaned)) {
            LanguageDirection.AR_TO_EN
        } else {
            LanguageDirection.EN_TO_AR
        }
        _chipMeanings.value = emptyMap()
        _phraseSheet.value = null
        _packDownloadMessage.value = null

        lookupJob?.cancel()
        chipJob?.cancel()
        lookupJob = viewModelScope.launch {
            repository.observeSentenceLookup(cleaned).collect { result ->
                _state.value = result
                if (!result.isUpdating && result.wordChips.isNotEmpty()) {
                    loadChipMeanings(result.wordChips)
                }
            }
        }
    }

    private fun loadChipMeanings(chips: List<String>) {
        val distinct = chips.map { it.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase(Locale.US) }
        if (distinct.isEmpty()) return
        chipJob?.cancel()
        chipJob = viewModelScope.launch {
            val cached = repository.getCachedChipMeanings(distinct)
            if (cached.isNotEmpty()) {
                _chipMeanings.value = _chipMeanings.value + cached
            }
            val missingFetched = repository.fetchMissingChipMeanings(distinct, _chipMeanings.value)
            if (missingFetched.isNotEmpty()) {
                _chipMeanings.value = _chipMeanings.value + missingFetched
            }
        }
    }

    fun onChipTapped(phrase: String) {
        val cleaned = phrase.trim()
        if (cleaned.isEmpty()) return
        chipTapJob?.cancel()
        chipTapJob = viewModelScope.launch {
            var resolved = false
            val loadingDelayJob = launch {
                delay(140L)
                if (!resolved && _phraseSheet.value == null) {
                    _phraseSheet.value = PhraseSheetData(phrase = cleaned, isLoading = true)
                }
            }
            try {
                val (hasEntry, sheetData) = repository.inspectChipTap(cleaned)
                resolved = true
                loadingDelayJob.cancel()
                if (hasEntry) {
                    _phraseSheet.value = null
                    _openWordEvent.emit(sheetData.phrase)
                } else {
                    _phraseSheet.value = sheetData
                }
            } catch (e: CancellationException) {
                loadingDelayJob.cancel()
                throw e
            } catch (_: Exception) {
                resolved = true
                loadingDelayJob.cancel()
                _phraseSheet.value = PhraseSheetData(phrase = cleaned, isLoading = false)
            }
        }
    }

    fun dismissPhraseSheet() {
        chipTapJob?.cancel()
        _phraseSheet.value = null
    }

    fun refreshIfPackBecameAvailable() {
        if (!_state.value.missingTranslationPack) return
        viewModelScope.launch {
            if (translationManager.isPairDownloaded(TranslateLanguage.ENGLISH, TranslateLanguage.ARABIC)) {
                lookupSentence(_query.value)
            }
        }
    }

    fun requestDownloadArabicPack() {
        if (_isDownloadingPack.value) return
        if (!translationManager.isOnline()) {
            _packDownloadMessage.value = "يرجى الاتصال بالإنترنت لتحميل حزمة الترجمة"
            return
        }
        if (translationManager.isOnCellularOnly()) {
            _showCellularConfirm.value = true
        } else {
            performDownloadArabicPack()
        }
    }

    fun confirmCellularDownload() {
        _showCellularConfirm.value = false
        performDownloadArabicPack()
    }

    fun dismissCellularDownload() {
        _showCellularConfirm.value = false
    }

    private fun performDownloadArabicPack() {
        viewModelScope.launch {
            _isDownloadingPack.value = true
            _packDownloadMessage.value = null
            val res = translationManager.downloadPair(
                TranslateLanguage.ENGLISH,
                TranslateLanguage.ARABIC
            )
            _isDownloadingPack.value = false
            if (res.isSuccess) {
                lookupSentence(_query.value)
            } else {
                _packDownloadMessage.value = "تعذر تحميل حزمة اللغة، حاول مجدداً."
            }
        }
    }

    fun toggleDirectionManual() {
        _direction.value = when (_direction.value) {
            LanguageDirection.EN_TO_AR -> LanguageDirection.AR_TO_EN
            LanguageDirection.AR_TO_EN -> LanguageDirection.EN_TO_AR
        }
    }

    class Factory(
        private val repository: LexiconRepository,
        private val translationManager: TranslationManager,
        private val settingsPreferences: UserSettingsPreferences,
        private val initialQuery: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SentenceLookupViewModel(
                repository = repository,
                translationManager = translationManager,
                settingsPreferences = settingsPreferences,
                initialQuery = initialQuery
            ) as T
        }
    }
}
