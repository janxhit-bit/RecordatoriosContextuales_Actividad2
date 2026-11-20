package cl.ipp.recordatorios

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

private val Brand = Color(0xFF234477)
private val Ink = Color(0xFF17243A)
private val Muted = Color(0xFF64748B)
private val Canvas = Color(0xFFF5F7FB)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Brand, background = Canvas)) {
                val repository = remember { InMemoryReminderRepository() }
                val vm: ReminderViewModel = viewModel(factory = ReminderViewModel.Factory(repository))
                ReminderApp(vm)
            }
        }
    }
}

@Composable
fun ReminderApp(vm: ReminderViewModel) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "list") {
        composable("list") {
            val state by vm.listState.collectAsStateWithLifecycle()
            ReminderListScreen(state, onCreate = { vm.resetForm(); nav.navigate("create") },
                onOpen = { id -> nav.navigate("detail/$id") },
                onRetry = vm::loadReminders, onEmpty = vm::simulateEmpty, onError = vm::simulateError)
        }
        composable("create") {
            val form by vm.formState.collectAsStateWithLifecycle()
            CreateReminderScreen(form, vm::updateTitle, vm::updateDescription, vm::updateCategory,
                vm::updatePriority, vm::toggleSaveError, onSave = vm::saveReminder,
                onCancel = { vm.resetForm(); nav.popBackStack() },
                onFinish = { vm.resetForm(); nav.popBackStack() })
        }
        composable("detail/{id}", arguments = listOf(navArgument("id") { type = NavType.IntType })) { entry ->
            val id = entry.arguments?.getInt("id") ?: -1
            var reminder by remember { mutableStateOf<Reminder?>(null) }
            LaunchedEffect(id) { vm.reminderById(id) { reminder = it } }
            DetailScreen(reminder, onBack = { nav.popBackStack() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderListScreen(
    state: ReminderListUiState, onCreate: () -> Unit, onOpen: (Int) -> Unit,
    onRetry: () -> Unit, onEmpty: () -> Unit, onError: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Mis recordatorios", fontWeight = FontWeight.Bold) }) },
        floatingActionButton = { FloatingActionButton(onClick = onCreate) { Text("+") } }
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding).padding(18.dp)) {
            Text("Organiza tus tareas y mantén el control de tu día.", color = Muted)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onEmpty) { Text("Probar vacío") }
                TextButton(onClick = onError) { Text("Probar error") }
                TextButton(onClick = onRetry) { Text("Recargar") }
            }
            when (state) {
                ReminderListUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                ReminderListUiState.Empty -> StateMessage("Todavía no tienes recordatorios.", "Crear recordatorio", onCreate)
                is ReminderListUiState.Error -> StateMessage(state.message, "Reintentar", onRetry)
                is ReminderListUiState.Content -> {
                    Text("${state.reminders.size} recordatorios", color = Muted, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(state.reminders, key = { it.id }) { item ->
                            ReminderCard(item = item, onClick = { onOpen(item.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StateMessage(message: String, action: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, color = Ink, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onAction) { Text(action) }
    }
}

@Composable
fun ReminderCard(item: Reminder, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp)) {
            Text(item.title, color = Ink, fontWeight = FontWeight.Bold)
            item.description?.let { Text(it, color = Muted, modifier = Modifier.padding(top = 4.dp)) }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text(item.category.label) })
                AssistChip(onClick = {}, label = { Text("Prioridad ${item.priority.label}") })
            }
            Text(item.status.label, color = Muted, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateReminderScreen(
    form: ReminderFormUiState, onTitle: (String) -> Unit, onDescription: (String) -> Unit,
    onCategory: (ReminderCategory) -> Unit, onPriority: (Priority) -> Unit,
    onToggleSaveError: (Boolean) -> Unit, onSave: () -> Unit, onCancel: () -> Unit,
    onFinish: () -> Unit, modifier: Modifier = Modifier
) {
    var categoryExpanded by remember { mutableStateOf(false) }
    var priorityExpanded by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text("Nuevo recordatorio") }, navigationIcon = {
        TextButton(onClick = onCancel) { Text("Volver") }
    }) }) { innerPadding ->
        if (form.saved) {
            Column(Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Recordatorio guardado correctamente.", fontWeight = FontWeight.Bold, color = Ink)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onFinish) { Text("Volver al listado") }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(innerPadding).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = form.title, onValueChange = onTitle, label = { Text("Título") },
                    isError = form.titleError != null, supportingText = { form.titleError?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = form.description, onValueChange = onDescription, label = { Text("Descripción") },
                    isError = form.descriptionError != null, supportingText = { Text("${form.description.length}/200 ${form.descriptionError.orEmpty()}") },
                    modifier = Modifier.fillMaxWidth(), minLines = 3)
                ExposedDropdownMenuBox(expanded = categoryExpanded, onExpandedChange = { categoryExpanded = !categoryExpanded }) {
                    OutlinedTextField(value = form.category?.label.orEmpty(), onValueChange = {}, readOnly = true,
                        label = { Text("Categoría") }, isError = form.categoryError != null,
                        supportingText = { form.categoryError?.let { Text(it) } },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth())
                    ExposedDropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                        ReminderCategory.entries.forEach { option -> DropdownMenuItem(text = { Text(option.label) },
                            onClick = { onCategory(option); categoryExpanded = false }) }
                    }
                }
                ExposedDropdownMenuBox(expanded = priorityExpanded, onExpandedChange = { priorityExpanded = !priorityExpanded }) {
                    OutlinedTextField(value = form.priority?.label.orEmpty(), onValueChange = {}, readOnly = true,
                        label = { Text("Prioridad") }, isError = form.priorityError != null,
                        supportingText = { form.priorityError?.let { Text(it) } },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(priorityExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth())
                    ExposedDropdownMenu(expanded = priorityExpanded, onDismissRequest = { priorityExpanded = false }) {
                        Priority.entries.forEach { option -> DropdownMenuItem(text = { Text(option.label) },
                            onClick = { onPriority(option); priorityExpanded = false }) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = form.simulateSaveError, onCheckedChange = onToggleSaveError)
                    Text("Simular error al guardar")
                }
                form.generalError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancelar") }
                    Button(onClick = onSave, enabled = form.isValid && !form.isSaving,
                        modifier = Modifier.weight(1f)) {
                        if (form.isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text("Guardar")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(reminder: Reminder?, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(topBar = { TopAppBar(title = { Text("Detalle del recordatorio") },
        navigationIcon = { TextButton(onClick = onBack) { Text("Volver") } }) }) { innerPadding ->
        if (reminder == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            Column(Modifier.fillMaxSize().padding(innerPadding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(reminder.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Ink)
                DetailRow("Descripción", reminder.description ?: "Sin descripción")
                DetailRow("Categoría", reminder.category.label)
                DetailRow("Prioridad", reminder.priority.label)
                DetailRow("Estado", reminder.status.label)
                DetailRow("Identificador", reminder.id.toString())
                reminder.location?.let { DetailRow("Ubicación", it) }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String, modifier: Modifier = Modifier) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(label, color = Muted, style = MaterialTheme.typography.labelMedium)
            Text(value, color = Ink, fontWeight = FontWeight.Medium)
        }
    }
}
