package com.example.getbusy.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.getbusy.data.ActivityItem
import com.example.getbusy.data.ActivityRepository
import com.example.getbusy.data.Tag
import com.example.getbusy.data.TagCategory
import com.example.getbusy.domain.FilterSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// -----------------------------
// Main screen state & VM
// -----------------------------

data class MainUiState(
    val isLoading: Boolean = false,
    val current: ActivityItem? = null,
    val filter: FilterSpec = FilterSpec(),
    val allActiveTags: List<Tag> = emptyList(),
    val error: String? = null
)

class MainViewModel(
    private val repo: ActivityRepository
) : ViewModel() {

    private val filterFlow = MutableStateFlow(FilterSpec())
    private val triggerRandom = MutableStateFlow(0) // bump to re-pick

    private val tagsFlow = repo.getAllActiveTags()

    val uiState: StateFlow<MainUiState> =
        combine(filterFlow, triggerRandom, tagsFlow) { filter, _, tags ->
            MainUiState(
                isLoading = false,
                current = null, // will be set by refreshRandom()
                filter = filter,
                allActiveTags = tags,
                error = null
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, MainUiState())

    fun setFilter(spec: FilterSpec) {
        filterFlow.value = spec
    }

    fun toggleFilterTag(tag: Tag) {
        val cur = filterFlow.value
        val updated = when (tag.category) {
            TagCategory.PLACE -> cur.copy(
                place = cur.place.toggle(tag.id)
            )
            TagCategory.COMPANY -> cur.copy(
                company = cur.company.toggle(tag.id)
            )
            TagCategory.DURATION -> cur.copy(
                duration = cur.duration.toggle(tag.id)
            )
            null -> cur.copy(
                userTags = cur.userTags.toggle(tag.id)
            )
        }
        filterFlow.value = updated
    }

    fun refreshRandom() {
        viewModelScope.launch {
            val f = filterFlow.value
            val args = f.toRepositoryArgs()
            val item = if (f.isEmpty()) {
                repo.getRandomAny()
            } else {
                repo.getRandomFiltered(
                    placeIds = args.placeIds,
                    companyIds = args.companyIds,
                    durationIds = args.durationIds,
                    mustHaveIds = args.mustHaveIds
                )
            }
            // publish by rebuilding state
            val cur = uiState.value
            val newState = cur.copy(current = item, isLoading = false, error = null)
            _patchState(newState)
        }
    }

    private val _state = MutableStateFlow(uiState.value)
    private fun _patchState(newState: MainUiState) {
        _state.value = newState
    }

    val state: StateFlow<MainUiState> =
        combine(uiState, _state) { base, patch ->
            patch.copy(
                // keep latest lists/filter in case patch is older
                allActiveTags = base.allActiveTags,
                filter = base.filter
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, uiState.value)

    class Factory(private val repo: ActivityRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return MainViewModel(repo) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

private fun Set<Long>.toggle(id: Long): Set<Long> =
    if (contains(id)) this - id else this + id

// -----------------------------
// Manage screen state & VM
// -----------------------------

data class ActivityFormState(
    val id: Long? = null,
    val text: String = "",
    val selectedTagIds: Set<Long> = emptySet(),
    val isValid: Boolean = true,
    val error: String? = null
)

data class ManageUiState(
    val activities: List<ActivityItem> = emptyList(),
    val allTagsAnyStatus: List<Tag> = emptyList(),
    val form: ActivityFormState = ActivityFormState(),
    val isEditing: Boolean = false
)

class ManageViewModel(
    private val repo: ActivityRepository
) : ViewModel() {

    private val internal = MutableStateFlow(ManageUiState())

    val state: StateFlow<ManageUiState> =
        combine(
            repo.getAllActivities(),
            repo.getAllTagsAnyStatus(),
            internal
        ) { activities, tags, local ->
            local.copy(
                activities = activities,
                allTagsAnyStatus = tags.sortedWith(
                    compareBy<Tag> { it.category == null } // user tags last
                        .thenBy { it.category?.ordinal ?: Int.MAX_VALUE }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                )
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, ManageUiState())

    fun newForm() {
        internal.value = internal.value.copy(
            form = ActivityFormState(),
            isEditing = false
        )
    }

    fun loadForEdit(activityId: Long) {
        viewModelScope.launch {
            val item = repo.getActivityById(activityId) ?: return@launch
            val tags = repo.getTagsForActivity(activityId).map { it.id }.toSet()
            internal.value = internal.value.copy(
                form = ActivityFormState(
                    id = item.id,
                    text = item.text,
                    selectedTagIds = tags,
                    isValid = item.text.isNotBlank()
                ),
                isEditing = true
            )
        }
    }

    fun updateFormText(text: String) {
        internal.value = internal.value.copy(
            form = internal.value.form.copy(text = text, isValid = text.isNotBlank())
        )
    }

    fun toggleFormTag(tagId: Long) {
        val cur = internal.value.form.selectedTagIds
        val next = if (cur.contains(tagId)) cur - tagId else cur + tagId
        internal.value = internal.value.copy(
            form = internal.value.form.copy(selectedTagIds = next)
        )
    }

    fun saveForm() {
        viewModelScope.launch {
            val f = internal.value.form
            if (f.text.isBlank()) {
                internal.value = internal.value.copy(
                    form = f.copy(isValid = false, error = "Text je prázdný")
                )
                return@launch
            }
            if (f.id == null) {
                val id = repo.insertActivity(ActivityItem(text = f.text.trim()), f.selectedTagIds.toList())
                // Reload edit state if needed, but here just reset
                newForm()
            } else {
                repo.updateActivity(ActivityItem(id = f.id, text = f.text.trim()), f.selectedTagIds.toList())
                newForm()
            }
        }
    }

    fun deleteActivity(id: Long) {
        viewModelScope.launch { repo.deleteActivity(id) }
    }
    suspend fun getTagsForActivity(activityId: Long): List<Tag> =
        repo.getTagsForActivity(activityId)


    fun addUserTag(name: String) {
        viewModelScope.launch {
            if (name.isBlank()) return@launch
            repo.insertTag(Tag(name = name.trim(), isDefault = false, isActive = true))
        }
    }

    fun renameTag(tag: Tag, newName: String) {
        viewModelScope.launch {
            if (newName.isBlank()) return@launch
            repo.updateTag(tag.copy(name = newName.trim()))
        }
    }

    fun removeUserTag(tag: Tag) {
        viewModelScope.launch { repo.deleteTag(tag.id) }
    }

    fun setSystemTagActive(tag: Tag, active: Boolean) {
        if (tag.category == null) return
        viewModelScope.launch { repo.updateTag(tag.copy(isActive = active)) }
    }
//
    class Factory(private val repo: ActivityRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ManageViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return ManageViewModel(repo) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
