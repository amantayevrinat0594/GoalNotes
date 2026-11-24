@file:OptIn(ExperimentalMaterial3Api::class)

package com.rinat.goalnotes

import android.Manifest
import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Reminders.createChannel(this)
        Reminders.scheduleAll(this)
        val goalId = intent.getLongExtra(EXTRA_GOAL_ID, -1L)
        setContent {
            AppTheme {
                App(openGoalId = if (goalId > 0) goalId else null)
            }
        }
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme(primary = Color(0xFF3F51B5))
    }
    MaterialTheme(colorScheme = colors, content = content)
}

sealed interface Screen {
    data object Home : Screen
    data class EditNote(val id: Long?) : Screen
    data class EditGoal(val id: Long?) : Screen
}

@Composable
fun App(openGoalId: Long?) {
    val ctx = LocalContext.current
    var data by remember { mutableStateOf(Store.load(ctx)) }
    var screen by remember { mutableStateOf<Screen>(if (openGoalId != null) Screen.EditGoal(openGoalId) else Screen.Home) }
    var tab by remember { mutableIntStateOf(if (openGoalId != null) 1 else 0) }

    fun update(d: AppData) {
        data = d
        Store.save(ctx, d)
    }

    AskNotificationPermission()

    when (val s = screen) {
        Screen.Home -> HomeScreen(
            data = data,
            tab = tab,
            onTab = { tab = it },
            onOpenNote = { screen = Screen.EditNote(it) },
            onOpenGoal = { screen = Screen.EditGoal(it) },
        )

        is Screen.EditNote -> NoteEditor(
            note = s.id?.let { id -> data.notes.find { it.id == id } },
            onSave = { n ->
                if (n != null) update(data.copy(notes = listOf(n) + data.notes.filter { it.id != n.id }))
                screen = Screen.Home
            },
            onDelete = { id ->
                update(data.copy(notes = data.notes.filter { it.id != id }))
                screen = Screen.Home
            },
        )

        is Screen.EditGoal -> GoalEditor(
            goal = s.id?.let { id -> data.goals.find { it.id == id } },
            onSave = { g ->
                if (g != null) {
                    val exists = data.goals.any { it.id == g.id }
                    val goals = if (exists) data.goals.map { if (it.id == g.id) g else it } else listOf(g) + data.goals
                    update(data.copy(goals = goals))
                    Reminders.schedule(ctx, g)
                }
                screen = Screen.Home
            },
            onDelete = { id ->
                Reminders.cancel(ctx, id)
                update(data.copy(goals = data.goals.filter { it.id != id }))
                screen = Screen.Home
            },
        )
    }
}

@Composable
fun AskNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

// ---------------------------------------------------------------- Главный экран

@Composable
fun HomeScreen(
    data: AppData,
    tab: Int,
    onTab: (Int) -> Unit,
    onOpenNote: (Long?) -> Unit,
    onOpenGoal: (Long?) -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(if (tab == 0) "Заметки" else "Мои цели") }) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0, onClick = { onTab(0) },
                    icon = { Icon(Icons.Filled.Edit, null) }, label = { Text("Заметки") },
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { onTab(1) },
                    icon = { Icon(Icons.Filled.Star, null) }, label = { Text("Цели") },
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { if (tab == 0) onOpenNote(null) else onOpenGoal(null) }) {
                Icon(Icons.Filled.Add, contentDescription = "Добавить")
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            if (tab == 0) NotesList(data.notes, onOpenNote) else GoalsList(data.goals, onOpenGoal)
        }
    }
}

private val dateFormat = SimpleDateFormat("d MMM, HH:mm", Locale.forLanguageTag("ru"))

@Composable
fun NotesList(notes: List<Note>, onOpen: (Long?) -> Unit) {
    var query by remember { mutableStateOf("") }
    val shown = notes
        .filter { query.isBlank() || it.title.contains(query, true) || it.text.contains(query, true) }
        .sortedByDescending { it.updated }

    Column(Modifier.fillMaxSize()) {
        if (notes.isNotEmpty()) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("Поиск") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        if (shown.isEmpty()) {
            EmptyHint(if (notes.isEmpty()) "Здесь будут твои заметки.\nНажми «+», чтобы записать первую." else "Ничего не найдено")
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(shown, key = { it.id }) { n ->
                Card(Modifier.fillMaxWidth().clickable { onOpen(n.id) }) {
                    Column(Modifier.padding(16.dp)) {
                        if (n.title.isNotBlank()) {
                            Text(n.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (n.text.isNotBlank()) {
                            Text(n.text, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            dateFormat.format(Date(n.updated)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GoalsList(goals: List<Goal>, onOpen: (Long?) -> Unit) {
    if (goals.isEmpty()) {
        EmptyHint("Поставь себе цель.\nНажми «+», опиши путь к ней по шагам —\nи я буду напоминать о ней каждый день.")
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 88.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(goals, key = { it.id }) { g ->
            Card(Modifier.fillMaxWidth().clickable { onOpen(g.id) }) {
                Column(Modifier.padding(16.dp)) {
                    Text("🎯 ${g.title}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    val next = g.nextStep
                    if (g.steps.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { g.progress }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (next == null) "Все шаги выполнены 🎉" else "Шагов: ${g.doneCount} из ${g.steps.size} · дальше: ${next.text}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (g.remind) {
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Notifications, null, Modifier.padding(end = 4.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("Каждый день в ${hm(g.hour, g.minute)}", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyHint(text: String) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
    }
}

fun hm(h: Int, m: Int) = String.format(Locale.US, "%02d:%02d", h, m)

// ---------------------------------------------------------------- Заметка

@Composable
fun NoteEditor(note: Note?, onSave: (Note?) -> Unit, onDelete: (Long) -> Unit) {
    var title by remember { mutableStateOf(note?.title ?: "") }
    var text by remember { mutableStateOf(note?.text ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }

    fun finish() {
        if (title.isBlank() && text.isBlank()) {
            if (note != null) onDelete(note.id) else onSave(null)
        } else if (note != null && note.title == title.trim() && note.text == text) {
            onSave(null)
        } else {
            onSave(Note(note?.id ?: System.currentTimeMillis(), title.trim(), text, System.currentTimeMillis()))
        }
    }
    BackHandler { finish() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (note == null) "Новая заметка" else "Заметка") },
                navigationIcon = { IconButton(onClick = { finish() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Сохранить и выйти") } },
                actions = {
                    if (note != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Удалить") }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).imePadding().padding(16.dp).fillMaxSize()) {
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("Заголовок") }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                label = { Text("Текст") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }

    if (confirmDelete && note != null) {
        ConfirmDelete("Удалить заметку?", onConfirm = { onDelete(note.id) }, onDismiss = { confirmDelete = false })
    }
}

@Composable
fun ConfirmDelete(title: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text("Это действие нельзя отменить.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Удалить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

// ---------------------------------------------------------------- Цель

@Composable
fun GoalEditor(goal: Goal?, onSave: (Goal?) -> Unit, onDelete: (Long) -> Unit) {
    val ctx = LocalContext.current
    val id = remember { goal?.id ?: System.currentTimeMillis() }
    val created = remember { goal?.created ?: System.currentTimeMillis() }
    var title by remember { mutableStateOf(goal?.title ?: "") }
    var description by remember { mutableStateOf(goal?.description ?: "") }
    val steps = remember { mutableStateListOf<Step>().apply { addAll(goal?.steps ?: emptyList()) } }
    var newStep by remember { mutableStateOf("") }
    var remind by remember { mutableStateOf(goal?.remind ?: true) }
    var hour by remember { mutableIntStateOf(goal?.hour ?: 9) }
    var minute by remember { mutableIntStateOf(goal?.minute ?: 0) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showTitleError by remember { mutableStateOf(false) }

    fun current(): Goal {
        val allSteps = if (newStep.isNotBlank()) steps + Step(System.nanoTime(), newStep.trim(), false) else steps.toList()
        return Goal(id, title.trim(), description.trim(), allSteps, remind, hour, minute, created)
    }

    fun finish() {
        if (title.isBlank()) {
            val untouched = description.isBlank() && steps.isEmpty() && newStep.isBlank()
            if (goal == null && untouched) {
                onSave(null)
            } else {
                showTitleError = true
            }
        } else {
            val g = current()
            onSave(if (g == goal) null else g)
        }
    }
    BackHandler { finish() }

    fun addStep() {
        if (newStep.isNotBlank()) {
            steps.add(Step(System.nanoTime(), newStep.trim(), false))
            newStep = ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (goal == null) "Новая цель" else "Цель") },
                navigationIcon = { IconButton(onClick = { finish() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Сохранить и выйти") } },
                actions = {
                    if (goal != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Удалить") }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .imePadding()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            OutlinedTextField(
                value = title, onValueChange = { title = it; showTitleError = false },
                label = { Text("Моя цель") },
                placeholder = { Text("Например: выучить Kubernetes") },
                isError = showTitleError,
                supportingText = { if (showTitleError) Text("Напиши название цели") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = description, onValueChange = { description = it },
                label = { Text("Зачем мне это / описание") },
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(20.dp))
            Text("Путь к цели", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (steps.isNotEmpty()) {
                val done = steps.count { it.done }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(progress = { done.toFloat() / steps.size }, modifier = Modifier.fillMaxWidth())
                Text("Выполнено $done из ${steps.size}", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(4.dp))

            steps.forEachIndexed { i, s ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(checked = s.done, onCheckedChange = { steps[i] = s.copy(done = it) })
                    Text(
                        "${i + 1}. ${s.text}",
                        modifier = Modifier.weight(1f),
                        textDecoration = if (s.done) TextDecoration.LineThrough else null,
                        color = if (s.done) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                    )
                    IconButton(onClick = { steps.removeAt(i) }) { Icon(Icons.Filled.Close, "Удалить шаг") }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newStep, onValueChange = { newStep = it },
                    placeholder = { Text("Новый шаг") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { addStep() }),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { addStep() }) { Icon(Icons.Filled.Add, "Добавить шаг") }
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Напоминать каждый день", style = MaterialTheme.typography.titleMedium)
                    Text("Уведомление с целью и следующим шагом", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = remind, onCheckedChange = { remind = it })
            }
            if (remind) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = {
                    TimePickerDialog(ctx, { _, h, m -> hour = h; minute = m }, hour, minute, true).show()
                }) {
                    Icon(Icons.Filled.Notifications, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Время: ${hm(hour, minute)}")
                }
                ExactAlarmHint()
            }

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = {
                Reminders.createChannel(ctx)
                Reminders.notify(ctx, current().copy(title = title.trim().ifBlank { "Моя цель" }))
            }) { Text("Показать пробное уведомление") }
        }
    }

    if (confirmDelete && goal != null) {
        ConfirmDelete("Удалить цель?", onConfirm = { onDelete(goal.id) }, onDismiss = { confirmDelete = false })
    }
}

/** На Android 12+ точные будильники могут быть выключены — подсказываем, как включить. */
@Composable
fun ExactAlarmHint() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val ctx = LocalContext.current
    val am = ctx.getSystemService(AlarmManager::class.java)
    if (am.canScheduleExactAlarms()) return
    Spacer(Modifier.height(4.dp))
    Text(
        "Чтобы напоминание приходило минута в минуту, разреши приложению «Будильники и напоминания».",
        style = MaterialTheme.typography.bodySmall,
    )
    TextButton(onClick = {
        ctx.startActivity(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }) { Text("Открыть настройки") }
}
