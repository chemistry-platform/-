package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.ArabicTextUtils
import com.example.core.ContextualTranslationAnalyzer
import com.example.core.OfflinePackManager
import com.example.core.OfflinePackState
import com.example.core.TranslationManager
import com.example.core.UserSettingsPreferences
import com.example.data.repository.LexiconRepository
import com.example.data.repository.WordLoadState
import com.example.domain.model.ConfusableWordDetail
import com.example.domain.model.InstantTranslationResult
import com.example.domain.model.LanguageDirection
import com.example.domain.model.LiveSearchPage
import com.example.domain.model.PhraseDetailItem
import com.example.domain.model.PhraseSheetData
import com.example.domain.model.SearchSuggestion
import com.example.domain.model.WordEntry
import com.google.mlkit.nl.translate.TranslateLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
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

import com.example.core.AppLanguage

enum class UsageFilterOption(val labelAr: String, val labelEn: String) {
    ALL("الكل", "All"),
    FORMAL("رسمي", "Formal"),
    INFORMAL("غير رسمي", "Informal"),
    US("أمريكي", "US"),
    UK("بريطاني", "UK");

    fun labelFor(language: AppLanguage): String =
        if (language == AppLanguage.ARABIC) labelAr else labelEn
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class WordDetailViewModel(
    private val repository: LexiconRepository,
    private val translationManager: TranslationManager,
    settingsPreferences: UserSettingsPreferences,
    initialQuery: String,
    private val fallbackBaseWord: String? = null,
    private val initialHighlightChip: String? = null,
    private val initialPreferredSense: String? = null,
    private val offlinePackManager: OfflinePackManager? = null
) : ViewModel() {

    val offlinePackState: StateFlow<OfflinePackState>? = offlinePackManager?.state

    val isOnline: StateFlow<Boolean> = repository.isOnlineFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = true
        )

    fun requestOfflinePackDownload() {
        offlinePackManager?.startDownload()
    }

    private val _currentQuery = MutableStateFlow(initialQuery)
    val currentQuery: StateFlow<String> = _currentQuery.asStateFlow()

    private val _preferredSense = MutableStateFlow(initialPreferredSense)
    val preferredSense: StateFlow<String?> = _preferredSense.asStateFlow()

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

    private val _uiState = MutableStateFlow<WordLoadState>(
        repository.peekReadyEntry(initialQuery)?.let { cached ->
            WordLoadState.Success(reorderEntryByPreferredSense(cached, initialPreferredSense), isUpdating = false)
        } ?: WordLoadState.Loading
    )
    val uiState: StateFlow<WordLoadState> = _uiState.asStateFlow()

    private val _usageFilter = MutableStateFlow(UsageFilterOption.ALL)
    val usageFilter: StateFlow<UsageFilterOption> = _usageFilter.asStateFlow()

    private val _highlightedChip = MutableStateFlow<String?>(null)
    val highlightedChip: StateFlow<String?> = _highlightedChip.asStateFlow()

    private val _confusableDetails = MutableStateFlow<Map<String, ConfusableWordDetail>>(emptyMap())
    val confusableDetails: StateFlow<Map<String, ConfusableWordDetail>> = _confusableDetails.asStateFlow()

    private val _isLoadingConfusables = MutableStateFlow(false)
    val isLoadingConfusables: StateFlow<Boolean> = _isLoadingConfusables.asStateFlow()

    private val _chipMeanings = MutableStateFlow<Map<String, String>>(emptyMap())
    val chipMeanings: StateFlow<Map<String, String>> = _chipMeanings.asStateFlow()

    private val _phrasalDetails = MutableStateFlow<List<PhraseDetailItem>>(emptyList())
    val phrasalDetails: StateFlow<List<PhraseDetailItem>> = _phrasalDetails.asStateFlow()

    private val _isLoadingPhrasals = MutableStateFlow(false)
    val isLoadingPhrasals: StateFlow<Boolean> = _isLoadingPhrasals.asStateFlow()

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

    private val _activeEntryWord = MutableStateFlow(initialQuery)
    val isFavorite: StateFlow<Boolean> = _activeEntryWord
        .flatMapLatest { word ->
            if (word.isBlank()) flowOf(false) else repository.observeIsFavorite(word)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = false
        )

    private var loadJob: Job? = null
    private var chipMeaningsJob: Job? = null
    private var phrasalsJob: Job? = null
    private var chipTapJob: Job? = null
    private var openWordJob: Job? = null
    private var confusableLoadedForWord: String? = null
    private var confusablePrefetchedForWord: String? = null
    private var chipMeaningsLoadedForWord: String? = null
    private var phrasalsLoadedForWord: String? = null

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
                        repository.observeLiveSuggestions(
                            query = trimmed,
                            limit = QuickTranslateViewModel.INITIAL_PAGE_SIZE
                        ).onEach { page ->
                            val candidateWords = page.items
                                .asSequence()
                                .filter { !it.isIdiom }
                                .take(QuickTranslateViewModel.INITIAL_PAGE_SIZE)
                                .map { it.word }
                                .toList()
                            if (candidateWords.isNotEmpty()) {
                                repository.prebuildLocalCandidates(candidateWords)
                            }
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

        loadWord(
            query = initialQuery,
            forceRefresh = false,
            fallbackWord = fallbackBaseWord,
            highlightChip = initialHighlightChip,
            preferredSense = initialPreferredSense
        )
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
                    val existingKeys = _inlineSuggestions.value.mapTo(HashSet()) { it.word.lowercase() }
                    val merged = if (page.items.size >= _inlineSuggestions.value.size) {
                        page.items
                    } else {
                        _inlineSuggestions.value + page.items.filter { it.word.lowercase() !in existingKeys }
                    }
                    cachedFullInlineQuery = trimmed
                    cachedFullInlineSuggestions = merged
                    _inlineSuggestions.value = merged
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

    fun loadWord(
        query: String,
        forceRefresh: Boolean = false,
        fallbackWord: String? = null,
        highlightChip: String? = null,
        preferredSense: String? = _preferredSense.value
    ) {
        val cleaned = query.trim()
        if (cleaned.isEmpty()) return
        _preferredSense.value = preferredSense
        _isSearchingInline.value = false
        _inlineSuggestions.value = emptyList()
        _hasMoreInlineSuggestions.value = false
        _isExpandedAllInline.value = false
        _isLoadingInline.value = false
        _isLoadingMoreInline.value = false
        _currentQuery.value = cleaned
        _inlineQuery.value = cleaned
        _direction.value = if (ArabicTextUtils.containsArabicLetters(cleaned)) {
            LanguageDirection.AR_TO_EN
        } else {
            LanguageDirection.EN_TO_AR
        }
        _usageFilter.value = UsageFilterOption.ALL
        _confusableDetails.value = emptyMap()
        _isLoadingConfusables.value = false
        _chipMeanings.value = emptyMap()
        _phrasalDetails.value = emptyList()
        _isLoadingPhrasals.value = false
        _phraseSheet.value = null
        _packDownloadMessage.value = null
        confusableLoadedForWord = null
        confusablePrefetchedForWord = null
        chipMeaningsLoadedForWord = null
        phrasalsLoadedForWord = null

        loadJob?.cancel()
        chipMeaningsJob?.cancel()
        phrasalsJob?.cancel()
        loadJob = viewModelScope.launch {
            var switchedToFallback = false
            repository.observeWordEntry(cleaned, forceRefresh = forceRefresh).collect { state ->
                when (state) {
                    is WordLoadState.Loading -> {
                        // Hold screen silently without displaying a loading state
                    }
                    is WordLoadState.Success -> {
                        val orderedEntry = reorderEntryByPreferredSense(state.entry, preferredSense)
                        _activeEntryWord.value = orderedEntry.displayWord
                        _uiState.value = WordLoadState.Success(orderedEntry, isUpdating = false)
                        populateChipMeanings(orderedEntry, fetchMissing = true)
                        if (orderedEntry.confusableCandidates.isNotEmpty() &&
                            confusablePrefetchedForWord != orderedEntry.word
                        ) {
                            confusablePrefetchedForWord = orderedEntry.word
                            prefetchFirstConfusablesPage(orderedEntry)
                        }
                        if ((orderedEntry.prepositionsAndPhrases.isNotEmpty() ||
                                orderedEntry.compoundExpressions.isNotEmpty() ||
                                orderedEntry.phrasalVerbsAndIdioms.isNotEmpty()) &&
                            phrasalsLoadedForWord != orderedEntry.word
                        ) {
                            phrasalsLoadedForWord = orderedEntry.word
                            loadPhrasalsForEntry(orderedEntry)
                        }
                    }
                    is WordLoadState.Error -> {
                        if (!switchedToFallback && !fallbackWord.isNullOrBlank()) {
                            switchedToFallback = true
                            _highlightedChip.value = highlightChip ?: cleaned
                            _currentQuery.value = fallbackWord
                            _inlineQuery.value = fallbackWord
                            repository.observeWordEntry(fallbackWord, forceRefresh = false)
                                .collect { fbState ->
                                    when (fbState) {
                                        is WordLoadState.Loading -> Unit
                                        is WordLoadState.Success -> {
                                            val orderedFb = reorderEntryByPreferredSense(fbState.entry, preferredSense)
                                            _activeEntryWord.value = orderedFb.displayWord
                                            populateChipMeanings(orderedFb, fetchMissing = true)
                                            if ((orderedFb.prepositionsAndPhrases.isNotEmpty() ||
                                                    orderedFb.compoundExpressions.isNotEmpty() ||
                                                    orderedFb.phrasalVerbsAndIdioms.isNotEmpty()) &&
                                                phrasalsLoadedForWord != orderedFb.word
                                            ) {
                                                phrasalsLoadedForWord = orderedFb.word
                                                loadPhrasalsForEntry(orderedFb)
                                            }
                                            _uiState.value = WordLoadState.Success(orderedFb, isUpdating = false)
                                        }
                                        is WordLoadState.Error -> {
                                            _uiState.value = fbState
                                        }
                                    }
                                }
                        } else {
                            _uiState.value = state
                        }
                    }
                }
            }
        }
    }

    fun openWordReady(word: String) {
        val cleaned = word.trim()
        if (cleaned.isEmpty()) return
        openWordJob?.cancel()
        openWordJob = viewModelScope.launch {
            repository.ensureWordReadyForOpen(cleaned, timeoutMs = 1450L)
            loadWord(cleaned)
        }
    }

    private fun reorderEntryByPreferredSense(entry: WordEntry, preferredSense: String?): WordEntry {
        val hint = preferredSense?.trim().orEmpty()
        if (hint.isBlank()) return entry
        val hintTokens = hint.lowercase(Locale.US)
            .split(Regex("[^a-zA-Z\u0600-\u06FF]+"))
            .filter { it.length >= 2 }
            .toSet()

        fun scoreSense(def: String, defAr: String?): Int {
            var score = 0
            val dLower = def.lowercase(Locale.US)
            val aLower = defAr.orEmpty().lowercase(Locale.US)
            val hintLower = hint.lowercase(Locale.US)
            if (dLower.contains(hintLower) || aLower.contains(hintLower)) score += 10
            if (hintTokens.isNotEmpty()) {
                score += ContextualTranslationAnalyzer.scoreSenseInContext(
                    definitionText = "$def ${defAr.orEmpty()}",
                    contextTokens = hintTokens
                )
            }
            return score
        }

        val reorderedGroups = entry.posGroups.map { group ->
            val sortedSenses = group.senses
                .withIndex()
                .sortedWith(compareByDescending<IndexedValue<com.example.domain.model.SenseItem>> {
                    scoreSense(it.value.definition, it.value.definitionArabic)
                }.thenBy { it.index })
                .map { it.value }
            group.copy(senses = sortedSenses)
        }.withIndex()
            .sortedWith(compareByDescending<IndexedValue<com.example.domain.model.PosSenseGroup>> {
                it.value.senses.maxOfOrNull { s -> scoreSense(s.definition, s.definitionArabic) } ?: 0
            }.thenBy { it.index })
            .map { it.value }

        return entry.copy(posGroups = reorderedGroups)
    }

    private fun populateChipMeanings(entry: WordEntry, fetchMissing: Boolean) {
        val allPhrases = buildList {
            addAll(entry.synonyms)
            addAll(entry.antonyms)
            addAll(entry.collocations)
            addAll(entry.compoundExpressions)
            addAll(entry.prepositionsAndPhrases)
            addAll(entry.wordFamily.map { it.word })
            addAll(entry.generalTerms)
            addAll(entry.specificTerms)
            addAll(entry.relatedWords)
            addAll(entry.confusableCandidates.map { it.word })
            entry.posGroups.forEach { group ->
                group.senses.forEach { sense ->
                    addAll(sense.synonyms)
                    addAll(sense.antonyms)
                }
            }
            if (entry.isArabicWord) {
                addAll(entry.machineTranslations)
            }
        }.map { it.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase(Locale.US) }

        if (allPhrases.isEmpty()) return
        val shouldFetch = fetchMissing && chipMeaningsLoadedForWord != "${entry.word}_${allPhrases.size}"
        if (shouldFetch) {
            chipMeaningsLoadedForWord = "${entry.word}_${allPhrases.size}"
        }

        chipMeaningsJob?.cancel()
        chipMeaningsJob = viewModelScope.launch {
            val cached = repository.getCachedChipMeanings(allPhrases)
            if (cached.isNotEmpty()) {
                _chipMeanings.value = _chipMeanings.value + cached
            }
            if (shouldFetch) {
                val mergedSoFar = _chipMeanings.value
                val fetched = repository.fetchMissingChipMeanings(allPhrases, mergedSoFar)
                if (fetched.isNotEmpty()) {
                    _chipMeanings.value = _chipMeanings.value + fetched
                }
            }
        }
    }

    private fun loadPhrasalsForEntry(entry: WordEntry) {
        if (entry.isArabicWord) {
            if (entry.phrasalVerbsAndIdioms.isNotEmpty()) {
                _phrasalDetails.value = entry.phrasalVerbsAndIdioms
            }
            val existingNorm = entry.phrasalVerbsAndIdioms
                .map { ArabicTextUtils.normalizeArabicForMatching(it.phrase) }
                .toSet()
            val missingArIdioms = entry.idioms
                .map { it.trim() }
                .filter { it.isNotBlank() && it.contains(" ") && ArabicTextUtils.normalizeArabicForMatching(it) !in existingNorm }
            if (missingArIdioms.isEmpty()) return

            phrasalsJob?.cancel()
            phrasalsJob = viewModelScope.launch {
                _isLoadingPhrasals.value = true
                try {
                    val loaded = repository.loadArabicIdiomDetails(
                        phrases = missingArIdioms,
                        baseWord = entry.word,
                        maxCount = 25
                    )
                    val merged = (entry.phrasalVerbsAndIdioms + loaded)
                        .distinctBy { ArabicTextUtils.normalizeArabicForMatching(it.phrase) }
                    _phrasalDetails.value = merged
                } finally {
                    _isLoadingPhrasals.value = false
                }
            }
            return
        }

        val idiomList = entry.idioms
            .map { it.trim() }
            .filter { it.isNotBlank() && it.contains(" ") }
            .distinctBy { it.lowercase(Locale.US) }
        val idiomSet = idiomList.map { it.lowercase(Locale.US) }.toSet()

        val phrasalAndOther = entry.prepositionsAndPhrases
            .map { it.trim() }
            .filter { it.isNotBlank() && it.contains(" ") && it.lowercase(Locale.US) !in idiomSet }
            .distinctBy { it.lowercase(Locale.US) }
        val compoundList = entry.compoundExpressions
            .map { it.trim() }
            .filter { it.isNotBlank() && it.contains(" ") && it.lowercase(Locale.US) !in idiomSet }
            .distinctBy { it.lowercase(Locale.US) }

        val phrases = (idiomList.take(25) + phrasalAndOther.take(25) + compoundList.take(15))
            .distinctBy { it.lowercase(Locale.US) }

        if (phrases.isEmpty()) {
            if (entry.phrasalVerbsAndIdioms.isNotEmpty()) {
                _phrasalDetails.value = entry.phrasalVerbsAndIdioms
            }
            return
        }
        val compoundSet = entry.compoundExpressions
            .map { it.trim().lowercase(Locale.US) }
            .filter { it.isNotBlank() }
            .toSet()

        phrasalsJob?.cancel()
        phrasalsJob = viewModelScope.launch {
            _isLoadingPhrasals.value = true
            try {
                val loaded = repository.loadPhrasalAndIdiomDetails(
                    phrases = phrases,
                    baseWord = entry.word,
                    idiomSet = idiomSet,
                    compoundSet = compoundSet,
                    maxCount = 50
                )
                val merged = (loaded + entry.phrasalVerbsAndIdioms)
                    .distinctBy { it.phrase.trim().lowercase(Locale.US) }
                _phrasalDetails.value = merged
            } finally {
                _isLoadingPhrasals.value = false
            }
        }
    }

    fun onChipTapped(phrase: String) {
        val cleaned = phrase.trim()
        if (cleaned.isEmpty()) return
        clearHighlightedChip()
        chipTapJob?.cancel()
        chipTapJob = viewModelScope.launch {
            try {
                // Hold screen silently (no spinner) while resolving chip/entry up to 1.45s
                val (hasEntry, sheetData) = repository.inspectChipTap(cleaned)
                if (hasEntry) {
                    repository.ensureWordReadyForOpen(sheetData.phrase, timeoutMs = 1450L)
                    _phraseSheet.value = null
                    _openWordEvent.emit(sheetData.phrase)
                } else {
                    _phraseSheet.value = sheetData
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _phraseSheet.value = PhraseSheetData(phrase = cleaned, isLoading = false)
            }
        }
    }

    fun dismissPhraseSheet() {
        chipTapJob?.cancel()
        _phraseSheet.value = null
    }

    fun refreshIfPackBecameAvailable() {
        val currentSuccess = _uiState.value as? WordLoadState.Success
        val currentError = _uiState.value as? WordLoadState.Error
        val needsPack = currentSuccess?.entry?.missingTranslationPack == true ||
            currentError?.needsLanguagePack == true
        if (!needsPack) return

        viewModelScope.launch {
            if (translationManager.isPairDownloaded(TranslateLanguage.ENGLISH, TranslateLanguage.ARABIC)) {
                loadWord(_currentQuery.value, forceRefresh = false)
            }
        }
    }

    fun requestDownloadArabicPack() {
        if (_isDownloadingPack.value) return
        if (!translationManager.isOnline()) {
            _packDownloadMessage.value = "يرجى الاتصال بالإنترنت لتحميل حزمة الترجمة العربية"
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
                loadWord(_currentQuery.value, forceRefresh = false)
            } else {
                _packDownloadMessage.value = "تعذر تحميل حزمة اللغة، حاول مجدداً."
            }
        }
    }

    private fun prefetchFirstConfusablesPage(entry: WordEntry) {
        if (entry.confusableCandidates.isEmpty()) return
        viewModelScope.launch {
            val firstPage = repository.loadConfusableDetails(entry.confusableCandidates, maxCount = 4)
            if (firstPage.isNotEmpty()) {
                _confusableDetails.value = _confusableDetails.value + firstPage
            }
        }
    }

    fun selectUsageFilter(option: UsageFilterOption) {
        _usageFilter.value = option
    }

    fun toggleDirectionManual() {
        _direction.value = when (_direction.value) {
            LanguageDirection.EN_TO_AR -> LanguageDirection.AR_TO_EN
            LanguageDirection.AR_TO_EN -> LanguageDirection.EN_TO_AR
        }
    }

    fun clearHighlightedChip() {
        _highlightedChip.value = null
    }

    fun loadConfusablesIfNeeded() {
        val entry = (_uiState.value as? WordLoadState.Success)?.entry ?: return
        if (entry.confusableCandidates.isEmpty()) return
        if (confusableLoadedForWord == entry.word || _isLoadingConfusables.value) return

        confusableLoadedForWord = entry.word
        viewModelScope.launch {
            _isLoadingConfusables.value = true
            try {
                val loaded = repository.loadConfusableDetails(entry.confusableCandidates, maxCount = 45)
                _confusableDetails.value = loaded
            } finally {
                _isLoadingConfusables.value = false
            }
        }
    }

    fun toggleFavorite() {
        val entry: WordEntry = (_uiState.value as? WordLoadState.Success)?.entry ?: return
        viewModelScope.launch {
            repository.toggleFavorite(entry, isFavorite.value)
        }
    }

    class Factory(
        private val repository: LexiconRepository,
        private val translationManager: TranslationManager,
        private val settingsPreferences: UserSettingsPreferences,
        private val initialQuery: String,
        private val fallbackBaseWord: String? = null,
        private val initialHighlightChip: String? = null,
        private val initialPreferredSense: String? = null,
        private val offlinePackManager: OfflinePackManager? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return WordDetailViewModel(
                repository = repository,
                translationManager = translationManager,
                settingsPreferences = settingsPreferences,
                initialQuery = initialQuery,
                fallbackBaseWord = fallbackBaseWord,
                initialHighlightChip = initialHighlightChip,
                initialPreferredSense = initialPreferredSense,
                offlinePackManager = offlinePackManager
            ) as T
        }
    }
}

