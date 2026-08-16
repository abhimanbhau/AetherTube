package com.abhimankolte.aethertube.tv.ui.search.compose

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import com.abhimankolte.aethertube.tv.ui.common.compose.AetherTubeTheme
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup
import com.liskovsoft.smartyoutubetv2.common.app.models.search.MediaServiceSearchTagProvider
import com.liskovsoft.smartyoutubetv2.common.app.models.search.SearchTagsProvider
import com.liskovsoft.smartyoutubetv2.common.app.models.search.vineyard.Tag
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter
import com.liskovsoft.smartyoutubetv2.common.app.views.SearchView
import com.liskovsoft.smartyoutubetv2.common.prefs.SearchData
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity

/**
 * Compose-for-TV replacement for [com.liskovsoft.smartyoutubetv2.tv.ui.search.tags.SearchTagsFragment].
 *
 * KNOWN GAPS vs the leanback screen (follow-ups, not blockers):
 *  - No crash-recovery restore of the last-played video (CrashRestorer integration).
 */
private const val VOICE_REQUEST_CODE = 8412
private const val MIN_VOICE_QUERY_LENGTH = 2

class ComposeSearchFragment : Fragment(), SearchView {
    private lateinit var searchPresenter: SearchPresenter
    private lateinit var searchData: SearchData
    private var tagsProvider: MediaServiceSearchTagProvider? = null
    private var isFragmentCreated = false

    // Compose-observable UI state
    private var currentSearchText by mutableStateOf("")
    private var showProgress by mutableStateOf(false)
    private var focusedBackdropUrl by mutableStateOf<String?>(null)
    private val tags = mutableStateListOf<Tag>()
    private val resultRows = mutableStateListOf<SearchResultRow>()
    private val rowsById = LinkedHashMap<Int, SearchResultRow>()

    // Compose's LazyRow crashes outright (IllegalArgumentException: Key "..." was already used) on a
    // repeated item key, unlike leanback's RecyclerView adapters which tolerated a duplicate silently.
    // A retried/duplicated continuation could otherwise hand back the same video twice across two
    // different SearchResultRow objects - mirrors ComposeHomeFragment's identical guard.
    private val seenVideoIds = HashSet<String>()
    private val searchFieldFocusRequester = FocusRequester()

    // Mirrors SearchTagsFragment's de-dupe/voice-detection bookkeeping
    private var lastSubmittedQuery: String? = null
    private var pendingTypedQuery: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null) // Real restore takes place in the presenter

        val context = requireContext()
        isFragmentCreated = true
        searchData = SearchData.instance(context)
        searchPresenter = SearchPresenter.instance(context)
        searchPresenter.setView(this)
    }

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                AetherTubeTheme {
                    SearchScreen(
                        searchText = currentSearchText,
                        onSearchTextChange = ::onSearchTextChanged,
                        onSearchSubmit = { loadSearchResult(currentSearchText) },
                        showProgress = showProgress,
                        backdropUrl = focusedBackdropUrl,
                        tags = tags,
                        onTagClick = { tag -> startSearchInternal(tag.tag, enableRecognition = false) },
                        onTagLongClick = { tag -> searchPresenter.onTagLongClicked(tag) },
                        resultRows = resultRows,
                        onVideoClick = { video -> searchPresenter.onVideoItemClicked(video) },
                        onVideoFocus = { video ->
                            searchPresenter.onVideoItemSelected(video)
                            focusedBackdropUrl = video.cardImageUrl
                        },
                        onVideoLongClick = { video -> searchPresenter.onVideoItemLongClicked(video) },
                        onScrollEnd = { video -> searchPresenter.onScrollEnd(video) },
                        onSearchSettingsClick = { searchPresenter.onSearchSettingsClicked() },
                        searchFieldFocusRequester = searchFieldFocusRequester
                    )
                }
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        searchPresenter.onViewInitialized()
    }

    override fun onDestroy() {
        super.onDestroy()
        searchPresenter.onViewDestroyed()
    }

    override fun onResume() {
        super.onResume()

        if (!isFragmentCreated) {
            searchPresenter.onViewResumed()
        }

        isFragmentCreated = false
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == VOICE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = results?.firstOrNull()
            if (!spoken.isNullOrEmpty()) {
                startSearchInternal(spoken, enableRecognition = false)
            }
            return
        }

        super.onActivityResult(requestCode, resultCode, data)
    }

    fun hasResults(): Boolean = resultRows.isNotEmpty()

    fun focusOnSearchField() {
        // Compose may not have attached the search field's Modifier.focusRequester yet
        // (e.g. presenter requests focus from onViewInitialized, before first composition).
        try {
            searchFieldFocusRequester.requestFocus()
        } catch (e: IllegalStateException) {
            // no-op: field isn't composed yet, nothing to focus
        }
    }

    fun onFinish() {
        searchPresenter.onFinish()
    }

    // ---- SearchView ----

    override fun updateSearch(group: VideoGroup) {
        when (group.action) {
            VideoGroup.ACTION_REPLACE -> clearSearch()
            VideoGroup.ACTION_SYNC -> {
                syncRow(group)
                return
            }
        }

        if (group.isEmpty) {
            return
        }

        val newVideos = group.videos.filter { video ->
            val id = video.videoId ?: video.channelId
            id == null || seenVideoIds.add(id)
        }

        if (newVideos.isEmpty()) {
            return
        }

        val existing = rowsById[group.id]

        if (existing == null) {
            val row = SearchResultRow(group.id, group.title)
            row.videos.addAll(newVideos)
            rowsById[group.id] = row
            resultRows.add(row)
        } else {
            existing.videos.addAll(newVideos)
        }
    }

    override fun clearSearch() {
        lastSubmittedQuery = null
        rowsById.clear()
        resultRows.clear()
        seenVideoIds.clear()
    }

    override fun clearSearchTags() {
        tags.clear()
    }

    override fun removeSearchTag(tag: Tag) {
        tags.remove(tag)
    }

    override fun setTagsProvider(provider: MediaServiceSearchTagProvider) {
        tagsProvider = provider
    }

    override fun showProgressBar(show: Boolean) {
        showProgress = show
    }

    override fun startSearch(searchText: String?) {
        startSearchInternal(searchText, enableRecognition = false)
    }

    override fun getSearchText(): String = currentSearchText

    override fun startVoiceRecognition() {
        startSearchInternal(null, enableRecognition = true)
    }

    override fun finishReally() {
        (activity as? LeanbackActivity)?.finishReally()
    }

    // ---- internals ----

    private fun syncRow(group: VideoGroup) {
        val row = rowsById[group.id] ?: return

        for (updated in group.videos) {
            val index = row.videos.indexOfFirst { it.videoId == updated.videoId }
            if (index != -1) {
                row.videos[index] = updated
            }
        }
    }

    private fun onSearchTextChanged(newQuery: String) {
        currentSearchText = newQuery

        loadSearchTags(newQuery)

        // Voice recognition sometimes drops the whole recognized phrase in one shot
        // instead of char-by-char typing; treat that as an implicit submit.
        if (isLikelyVoiceQuery(newQuery)) {
            loadSearchResult(newQuery)
        }
    }

    private fun startSearchInternal(text: String?, enableRecognition: Boolean) {
        pendingTypedQuery = null

        if (text != null) {
            currentSearchText = text
            loadSearchTags(text)
            loadSearchResult(text)
        } else {
            currentSearchText = ""
            loadSearchTags("")
            loadSearchResult("") // no-op: empty query is intentionally ignored, see loadSearchResult

            if (enableRecognition && isRecognitionAvailable()) {
                startRecognition()
            } else {
                focusOnSearchField()
            }
        }
    }

    private fun loadSearchTags(query: String) {
        tagsProvider?.search(query, SearchTagsProvider.ResultsCallback { results ->
            tags.clear()
            if (results != null) {
                tags.addAll(results)
            }
        })
    }

    private fun loadSearchResult(query: String) {
        // Don't show suggested videos (empty query); mirrors SearchTagsFragment's behavior.
        if (!TextUtils.isEmpty(query) && query != lastSubmittedQuery) {
            lastSubmittedQuery = query
            searchPresenter.onSearch(query)
        }
    }

    private fun isLikelyVoiceQuery(newQuery: String): Boolean {
        if (TextUtils.isEmpty(newQuery)) {
            pendingTypedQuery = null
            return false
        }

        // Real voice-recognition results (SpeechRecognizer, via startVoiceRecognition()) never reach
        // this heuristic at all - they submit directly through startSearchInternal(). This only covers
        // the remote/keyboard's built-in dictation typing a whole phrase into the field in one IME
        // event, which looks identical to a clipboard paste at this layer (both arrive as one bulk
        // onValueChange with no previously-typed text). MIN_VOICE_QUERY_LENGTH just keeps a short
        // 2-character paste/autocomplete artifact from mistakenly auto-submitting.
        val isVoice = pendingTypedQuery == null && newQuery.length > MIN_VOICE_QUERY_LENGTH
        pendingTypedQuery = newQuery

        return isVoice
    }

    private fun isRecognitionAvailable(): Boolean {
        return try {
            SpeechRecognizer.isRecognitionAvailable(requireContext())
        } catch (e: NullPointerException) {
            false
        }
    }

    private fun startRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        }

        try {
            startActivityForResult(intent, VOICE_REQUEST_CODE)
        } catch (e: ActivityNotFoundException) {
            focusOnSearchField()
        }
    }
}
