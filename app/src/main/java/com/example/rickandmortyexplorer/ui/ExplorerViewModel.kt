package com.example.rickandmortyexplorer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rickandmortyexplorer.model.Character
import com.example.rickandmortyexplorer.model.Episode
import com.example.rickandmortyexplorer.model.Location
import com.example.rickandmortyexplorer.network.RickAndMortyApi
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

enum class Category(val label: String, val fieldLabels: List<String>) {
    CHARACTER("Character", listOf("Status", "Species")),
    EPISODE("Episode", listOf("Episode", "Air date")),
    LOCATION("Location", listOf("Type", "Dimension"))
}

data class DisplayItem(
    val category: Category,
    val name: String,
    val imageUrl: String?,
    val values: List<String>
) {
    // junta cada label da categoria ao valor na mesma posição
    val fields get() = category.fieldLabels.zip(values)
}

data class ExplorerUiState(
    val category: Category = Category.CHARACTER,
    val query: String = "",
    val totalCount: Int = 0,
    val items: List<DisplayItem> = emptyList(),
    val current: DisplayItem? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val saveMessage: String? = null,
    val rollCount: Long = 0
)

private fun Character.toDisplayItem() =
    DisplayItem(Category.CHARACTER, name, image, listOf(status, species))

private fun Episode.toDisplayItem() =
    DisplayItem(Category.EPISODE, name, null, listOf(episode, airDate))

private fun Location.toDisplayItem() =
    DisplayItem(Category.LOCATION, name, null, listOf(type, dimension))

private fun DataSnapshot.toDisplayItem(): DisplayItem? {
    val categoryName = child("category").getValue(String::class.java)
    val category = Category.entries.find { it.name == categoryName } ?: return null
    return DisplayItem(
        category,
        child("name").getValue(String::class.java).orEmpty(),
        child("imageUrl").getValue(String::class.java),
        category.fieldLabels.map { child("fields").child(it).getValue(String::class.java).orEmpty() }
    )
}

class ExplorerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ExplorerUiState())
    val uiState: StateFlow<ExplorerUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private val db = FirebaseDatabase.getInstance().reference
    private val rollCountRef = db.child("rollCount")

    private val rollCountListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val count = snapshot.getValue(Long::class.java) ?: 0
            _uiState.update { it.copy(rollCount = count) }
        }

        override fun onCancelled(error: DatabaseError) {}
    }

    init {
        load()
        rollCountRef.addValueEventListener(rollCountListener)
    }

    override fun onCleared() {
        rollCountRef.removeEventListener(rollCountListener)
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
            state.copy(current = others.randomOrNull() ?: state.current, saveMessage = null)
        }
 
        rollCountRef.setValue(ServerValue.increment(1))
    }

    fun save() {
        val item = _uiState.value.current ?: return
        val data = mapOf(
            "category" to item.category.name,
            "name" to item.name,
            "imageUrl" to item.imageUrl,
            "fields" to item.fields.toMap()
        )
        db.child("lastSaved").setValue(data).addOnCompleteListener { task ->
            val message = if (task.isSuccessful) "Item saved" else "Save failed"
            _uiState.update { it.copy(saveMessage = message) }
        }
    }

    fun loadLast() {
        db.child("lastSaved").get().addOnCompleteListener { task ->
            val item = if (task.isSuccessful) task.result.toDisplayItem() else null
            if (item == null) {
                val message = if (task.isSuccessful) "Nothing saved yet" else "Load failed"
                _uiState.update { it.copy(saveMessage = message) }
            } else {
                _uiState.update { it.copy(category = item.category, query = "") }
                load(keep = item)
            }
        }
    }

    private fun load(keep: DisplayItem? = null) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            delay(300)
            val state = _uiState.value
            val name = state.query.ifBlank { null }
            val api = RickAndMortyApi.retrofitService
            _uiState.update { it.copy(isLoading = true, error = null, saveMessage = null) }
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
                    it.copy(isLoading = false, totalCount = count, items = items, current = keep ?: items.randomOrNull())
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