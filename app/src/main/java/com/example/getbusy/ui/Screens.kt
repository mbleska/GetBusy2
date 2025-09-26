@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.getbusy.ui

import android.app.Activity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.getbusy.data.ActivityItem
import com.example.getbusy.data.Tag
import com.example.getbusy.data.TagCategory
import com.example.getbusy.domain.FilterSpec
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Switch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.google.accompanist.flowlayout.FlowRow


// -----------------------------
// MainScreen
// -----------------------------

@Composable
fun MainScreen(
    vm: MainViewModel,
    onOpenManage: () -> Unit
) {
    val state by vm.state.collectAsStateWithLifecycle()

    var showFilter by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.refreshRandom()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(

                title = { Text("GetBusy") },
                actions = {
                    IconButton(onClick = { showFilter = true }) {
                        Icon(Icons.Filled.FilterList, contentDescription = "Filter")
                    }
                    IconButton(onClick = onOpenManage) {
                        Icon(Icons.Filled.Settings, contentDescription = "Správa")
                    }
                }
            )
        },
        bottomBar = {
            BannerAdBox(modifier = Modifier.fillMaxWidth())
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .clickable { vm.refreshRandom() },
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = state.current != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = state.current?.text ?: "",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth()
                )
            }

            if (state.current == null) {
                Text(
                    text = "Klepni pro náhodnou aktivitu",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(24.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (showFilter) {
        FilterDialog(
            allActive = state.allActiveTags,
            current = state.filter,
            onDismiss = { showFilter = false },
            onApply = { spec ->
                vm.setFilter(spec)
                vm.refreshRandom()
                showFilter = false
            },
            onToggle = { vm.toggleFilterTag(it) }
        )
    }
}

// -----------------------------
// ManageScreen
// -----------------------------

@Composable
fun ManageScreen(
    vm: ManageViewModel,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onBack: () -> Unit
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showTagManager by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Správa aktivit") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Zpět")
                    }
                },
                actions = {
                    IconButton(onClick = { showTagManager = true }) {
                        Icon(Icons.Filled.Label, contentDescription = "Tagy")  // ← akce Tagy
                    }
                }
            )
        },

        floatingActionButton = {
            FloatingActionButton(onClick = {
                vm.newForm()
                onAdd()
            }) {Icon(Icons.Filled.Add, contentDescription = "Přidat")
            }
        }
    ) { padding ->
        if (state.activities.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Zatím žádné aktivity")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(state.activities, key = { it.id }) { item ->
                    ActivityRow(
                        item = item,
                        loadTags = { vm.getTagsForActivity(item.id) },
                        onEdit = { onEdit(item.id) },
                        onDelete = { vm.deleteActivity(item.id) }
                    )
                    Divider()
                }
            }
        }
    }
    if (showTagManager) {
        TagManagerDialog(
            tags = state.allTagsAnyStatus,
            onClose = { showTagManager = false },
            onAddUserTag = { vm.addUserTag(it) },
            onRename = { tag, newName -> vm.renameTag(tag, newName) },
            onDeleteUserTag = { vm.removeUserTag(it) },
            onToggleSystemActive = { tag, active -> vm.setSystemTagActive(tag, active) }
        )
    }
}

@Composable
private fun ActivityRow(
    item: ActivityItem,
    loadTags: suspend () -> List<Tag>,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var tags by remember { mutableStateOf<List<Tag>>(emptyList()) }
    LaunchedEffect(item.id) { tags = loadTags() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                item.text,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onEdit) { Text("Upravit") }
            TextButton(onClick = onDelete) { Text("Smazat") }
        }
        if (tags.isNotEmpty()) {
            FlowChips(tags = tags)
        }
    }
}

// helper to fetch tags inside composable via VM

// -----------------------------
// EditActivityScreen
// -----------------------------

@Composable
fun EditActivityScreen(
    vm: ManageViewModel,
    onDone: () -> Unit
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val form = state.form
    val isEditing = state.isEditing

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (isEditing) "Upravit aktivitu" else "Přidat aktivitu") }
            )

        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = form.text,
                onValueChange = vm::updateFormText,
                label = { Text("Text aktivity") },
                modifier = Modifier.fillMaxWidth(),
                isError = !form.isValid
            )
            if (form.error != null) {
                Text(form.error ?: "", color = MaterialTheme.colorScheme.error)
            }

            Text("Místo", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            TagGroupChips(
                tags = state.allTagsAnyStatus.filter {
                    it.category == TagCategory.PLACE && (it.isActive || it.id in form.selectedTagIds)
                },
                selected = form.selectedTagIds,
                onToggle = { vm.toggleFormTag(it) }
            )

            Text("Společnost", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            TagGroupChips(
                tags = state.allTagsAnyStatus.filter {
                    it.category == TagCategory.COMPANY && (it.isActive || it.id in form.selectedTagIds)
                },
                selected = form.selectedTagIds,
                onToggle = { vm.toggleFormTag(it) }
            )

            Text("Délka", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            TagGroupChips(
                tags = state.allTagsAnyStatus.filter {
                    it.category == TagCategory.DURATION && (it.isActive || it.id in form.selectedTagIds)
                },
                selected = form.selectedTagIds,
                onToggle = { vm.toggleFormTag(it) }
            )

            Text("Další tagy", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            TagGroupChips(
                tags = state.allTagsAnyStatus.filter {
                    it.category == null && (it.isActive || it.id in form.selectedTagIds)
                },
                selected = form.selectedTagIds,
                onToggle = { vm.toggleFormTag(it) }
            )


            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = {
                        vm.saveForm()
                        onDone()
                    },
                    enabled = form.text.isNotBlank()
                ) { Text(if (isEditing) "Uložit" else "Přidat") }

                OutlinedButton(onClick = onDone) { Text("Zpět") }
            }
        }
    }
}

// -----------------------------
// Filter dialog
// -----------------------------

@Composable
private fun FilterDialog(
    allActive: List<Tag>,
    current: FilterSpec,
    onDismiss: () -> Unit,
    onApply: (FilterSpec) -> Unit,
    onToggle: (Tag) -> Unit
) {
    val place = allActive.filter { it.category == TagCategory.PLACE }
    val company = allActive.filter { it.category == TagCategory.COMPANY }
    val duration = allActive.filter { it.category == TagCategory.DURATION }
    val user = allActive.filter { it.category == null }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Filtr", style = MaterialTheme.typography.titleLarge)

                Spacer(Modifier.height(8.dp))
                Text("Místo", fontWeight = FontWeight.SemiBold)
                TagChips(place, current, onToggle)

                Spacer(Modifier.height(8.dp))
                Text("Společnost", fontWeight = FontWeight.SemiBold)
                TagChips(company, current, onToggle)

                Spacer(Modifier.height(8.dp))
                Text("Délka", fontWeight = FontWeight.SemiBold)
                TagChips(duration, current, onToggle)

                if (user.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Další", fontWeight = FontWeight.SemiBold)
                    TagChips(user, current, onToggle)
                }

                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onDismiss) { Text("Zrušit") }
                    Button(onClick = { onApply(current) }) { Text("Použít") }
                }
            }
        }
    }
}

@Composable
private fun TagChips(
    tags: List<Tag>,
    current: FilterSpec,
    onToggle: (Tag) -> Unit
) {
    FlowChips(
        tags = tags,
        isSelected = { tag ->
            when (tag.category) {
                TagCategory.PLACE -> current.place.contains(tag.id)
                TagCategory.COMPANY -> current.company.contains(tag.id)
                TagCategory.DURATION -> current.duration.contains(tag.id)
                null -> current.userTags.contains(tag.id)
            }
        },
        onToggle = onToggle
    )
}

@Composable
private fun TagGroupChips(
    tags: List<Tag>,
    selected: Set<Long>,
    onToggle: (Long) -> Unit
) {
    FlowRow(
        mainAxisSpacing = 8.dp,
        crossAxisSpacing = 8.dp
    ) {
        tags.forEach { tag ->
            FilterChip(
                selected = selected.contains(tag.id),
                onClick = { onToggle(tag.id) },
                label = { Text(tag.name) }
            )
        }
    }
}



@Composable
private fun FlowChips(
    tags: List<Tag>,
    isSelected: (Tag) -> Boolean = { false },
    onToggle: (Tag) -> Unit = {}
) {
    FlowRow(
        mainAxisSpacing = 8.dp,
        crossAxisSpacing = 8.dp
    ) {
        tags.forEach { tag ->
            FilterChip(
                selected = isSelected(tag),
                onClick = { onToggle(tag) },
                label = { Text(tag.name) }
            )
        }
    }
}





// Confirm dialog (optional hook)
// -----------------------------

@Composable
fun ConfirmDeleteDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Button(onClick = onConfirm) { Text("Smazat") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Zrušit") } },
        title = { Text("Smazat položku?") },
        text = { Text("Tuto akci nelze vrátit.") }
    )
}

// -----------------------------
// BannerAdBox
// -----------------------------

@Composable
fun BannerAdBox(
    modifier: Modifier = Modifier,
    adUnitId: String = testBannerId()
) {
    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        factory = { ctx ->
            AdView(ctx).apply {
                setAdSize(AdSize.BANNER)
                this.adUnitId = adUnitId
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}
// -----------------------------
// Tag Manager (Dialog)
// -----------------------------

@Composable
fun TagManagerDialog(
    tags: List<Tag>,
    onClose: () -> Unit,
    onAddUserTag: (String) -> Unit,
    onRename: (Tag, String) -> Unit,
    onDeleteUserTag: (Tag) -> Unit,
    onToggleSystemActive: (Tag, Boolean) -> Unit
) {
    var newTag by remember { mutableStateOf("") }
    var renamingTag: Tag? by remember { mutableStateOf(null) }

    val place = tags.filter { it.category == TagCategory.PLACE }
    val company = tags.filter { it.category == TagCategory.COMPANY }
    val duration = tags.filter { it.category == TagCategory.DURATION }
    val user = tags.filter { it.category == null }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            tonalElevation = 1.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Zpět")
                    }
                    Text(
                        "Správa tagů",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(12.dp))

                TagSection(
                    title = "Místo",
                    items = place,
                    system = true,
                    onToggleSystemActive = onToggleSystemActive
                )
                TagSection(
                    title = "Společnost",
                    items = company,
                    system = true,
                    onToggleSystemActive = onToggleSystemActive
                )
                TagSection(
                    title = "Délka",
                    items = duration,
                    system = true,
                    onToggleSystemActive = onToggleSystemActive
                )

                Spacer(Modifier.height(8.dp))
                Text("Vlastní tagy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (user.isEmpty()) {
                    Text("Žádné vlastní tagy", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    user.forEach { tag ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(tag.name, modifier = Modifier.weight(1f))
                            IconButton(onClick = { renamingTag = tag }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Přejmenovat")
                            }
                            IconButton(onClick = { onDeleteUserTag(tag) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Smazat")
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newTag,
                        onValueChange = { newTag = it },
                        label = { Text("Nový tag") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val name = newTag.trim()
                            if (name.isNotEmpty()) {
                                onAddUserTag(name)
                                newTag = ""
                            }
                        }
                    ) { Text("Přidat") }
                }

                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onClose) { Text("Hotovo") }
                }
            }
        }
    }

    renamingTag?.let { tag ->
        RenameTagDialog(
            tag = tag,
            onDismiss = { renamingTag = null },
            onConfirm = { newName ->
                onRename(tag, newName)
                renamingTag = null
            }
        )
    }
}


@Composable
private fun TagSection(
    title: String,
    items: List<Tag>,
    system: Boolean,
    onToggleSystemActive: (Tag, Boolean)-> Unit
    ) {
    if (items.isEmpty()) return
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Column {
        items.forEach { tag ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(tag.name, modifier = Modifier.weight(1f))
                if (system) {
                    Switch(
                        checked = tag.isActive,
                        onCheckedChange = { onToggleSystemActive(tag, it) }
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun RenameTagDialog(
    tag: Tag,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(tag.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = { val t = text.trim(); if (t.isNotEmpty()) onConfirm(t) }) { Text("Uložit") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Zrušit") } },
        title = { Text("Přejmenovat tag") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Název") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    )
}


private fun testBannerId(): String = "ca-app-pub-3940256099942544/6300978111"
