package com.example.helper.coredata.session

import android.content.Context
import com.example.helper.coredata.repository.SchoolSessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Which session the app is *looking at*. Separate from `sessions.isCurrent`
 * (where new classes land): a teacher can open 2024-25 without moving the
 * current flag. The choice survives restarts in SharedPreferences.
 *
 * One instance lives in `AppContainer`; `SchoolClassRepository` reads
 * [selectedId], so every class list follows the dropdown through one path.
 */
class SessionScope(
    context: Context,
    private val repository: SchoolSessionRepository,
    private val scope: CoroutineScope,
) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _selectedId = MutableStateFlow(prefs.getString(KEY_SELECTED, null))
    val selectedId: StateFlow<String?> = _selectedId.asStateFlow()

    init {
        scope.launch {
            val ensured = repository.ensureSession()
            val saved = _selectedId.value
            if (saved == null || repository.getById(saved) == null) select(ensured.id)
        }
    }

    fun select(id: String) {
        if (_selectedId.value == id) return
        _selectedId.value = id
        prefs.edit().putString(KEY_SELECTED, id).apply()
    }

    /** Moves the `(curr.)` marker; viewing follows the session you just chose. */
    fun makeCurrent(id: String) {
        select(id)
        scope.launch { repository.setCurrent(id) }
    }

    /** For writes that need an owner (new class, new exam). */
    suspend fun requireSelectedId(): String = selectedId.filterNotNull().first()

    private companion object {
        const val PREFS_NAME = "session_scope"
        const val KEY_SELECTED = "selected_session_id"
    }
}
