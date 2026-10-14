package com.example.rickandmortyexplorer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rickandmortyexplorer.model.Character
import com.example.rickandmortyexplorer.model.Episode
import com.example.rickandmortyexplorer.model.Location
import com.example.rickandmortyexplorer.network.RickAndMortyApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

enum class Category(val label: String) {
    CHARACTER("Character"),
    EPISODE("Episode"),
    LOCATION("Location")
}

data class DisplayItem(
    val name: String,
    val imageUrl: String?,
    val fields: List<Pair<String, String>>
)

data class ExplorerUiState(
    val category: Category = Category.CHARACTER,
    val query: String = "",
    val totalCount: Int = 0,
    val items: List<DisplayItem> = emptyList(),
    val current: DisplayItem? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

private fun Character.toDisplayItem() =
    DisplayItem(name, image, listOf("Status" to status, "Species" to species))

private fun Episode.toDisplayItem() =
    DisplayItem(name, null, listOf("Episode" to episode, "Air date" to airDate))

private fun Location.toDisplayItem() =
    DisplayItem(name, null, listOf("Type" to type, "Dimension" to dimension))

class ExplorerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ExplorerUiState())
    val uiState: StateFlow<ExplorerUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun selectCategory(category: Category) {
        _uiState.update { it.copy(category = category) }
        load()
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        load()
    }

    fun roll() {
        _uiState.update { state ->
            val others = state.items.filter { it != state.current }
            state.copy(current = others.randomOrNull() ?: state.current)
        }
    }

    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            delay(300)
            val state = _uiState.value
            val name = state.query.ifBlank { null }
            val api = RickAndMortyApi.retrofitService
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val (count, items) = when (state.category) {
                    Category.CHARACTER -> api.getCharacters(name)
                        .let { r -> r.info.count to r.results.map { it.toDisplayItem() } }
                    Category.EPISODE -> api.getEpisodes(name)
                        .let { r -> r.info.count to r.results.map { it.toDisplayItem() } }
                    Category.LOCATION -> api.getLocations(name)
                        .let { r -> r.info.count to r.results.map { it.toDisplayItem() } }
                }
                _uiState.update {
                    it.copy(isLoading = false, totalCount = count, items = items, current = items.randomOrNull())
                }
            } catch (e: HttpException) {
                // a API responde 404 quando o filtro não encontra nada
                val message = if (e.code() == 404) "No results for \"${state.query}\"" else "Server error (${e.code()})"
                _uiState.update {
                    it.copy(isLoading = false, totalCount = 0, items = emptyList(), current = null, error = message)
                }
            } catch (e: IOException) {
                _uiState.update { it.copy(isLoading = false, error = "No internet connection") }
            }
        }
    }
}