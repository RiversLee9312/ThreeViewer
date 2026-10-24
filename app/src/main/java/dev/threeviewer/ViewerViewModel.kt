package dev.threeviewer

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.threeviewer.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ViewerState(
    val recent: List<ModelSource> = emptyList(), val model: LoadedModel? = null,
    val loading: Boolean = false, val error: Int? = null, val theme: Int = 0,
    val dynamic: Boolean = true, val openRequest: Long = 0
)

class ViewerViewModel(app: Application) : AndroidViewModel(app) {
    private val store = ModelStore(app)
    private val _state = MutableStateFlow(ViewerState(recent = store.recent(), theme = store.theme, dynamic = store.dynamic))
    val state = _state.asStateFlow()
    private var loadJob: Job? = null

    fun open(uri: Uri? = null) {
        loadJob?.cancel()
        _state.update { it.copy(loading = true, error = null, model = null, openRequest = it.openRequest + 1) }
        loadJob = viewModelScope.launch {
            try {
                val model = store.load(uri, getApplication<Application>().getString(R.string.sample_name))
                if (uri != null) store.remember(model.source)
                _state.update { it.copy(model = model, loading = false, recent = store.recent()) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val message = when (e) {
                    is ModelTooLarge -> R.string.file_too_large
                    is InvalidModel -> R.string.invalid_glb
                    else -> R.string.file_unavailable
                }
                _state.update { it.copy(loading = false, error = message) }
            }
        }
    }

    fun home() { loadJob?.cancel(); _state.update { it.copy(model = null, loading = false, error = null) } }
    fun dismissError() { _state.update { it.copy(error = null) } }
    fun theme(value: Int) { store.theme = value; _state.update { it.copy(theme = value) } }
    fun dynamic(value: Boolean) { store.dynamic = value; _state.update { it.copy(dynamic = value) } }
    fun clearRecent() { store.clear(); _state.update { it.copy(recent = emptyList()) } }
    fun remove(uri: String) { store.remove(uri); _state.update { it.copy(recent = store.recent()) } }
}
