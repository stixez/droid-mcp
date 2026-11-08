package io.droidmcp.app.feature.computers

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.droidmcp.app.ui.components.CodeBlock
import io.droidmcp.app.ui.components.RowDivider
import io.droidmcp.app.ui.components.ScreenScaffold
import io.droidmcp.app.ui.components.SettingsGroup
import io.droidmcp.app.ui.copyText
import io.droidmcp.app.ui.relativeTime

@Composable
fun ComputersRoute(viewModel: ComputersViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ComputersEffect.Copy -> if (context.copyText(effect.what, effect.text, sensitive = true)) {
                    snackbar.showSnackbar("${effect.what} copied")
                }
                is ComputersEffect.Message -> snackbar.showSnackbar(effect.text)
            }
        }
    }
    ScreenScaffold(
        title = "Computers",
        snackbarHostState = snackbar,
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back") }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
        ) {
            ComputersContent(state, viewModel::onIntent)
            ExtendedFloatingActionButton(
                onClick = { viewModel.onIntent(ComputersIntent.AskAdd) },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Add a computer") },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            )
        }
    }

    state.nameInput?.let { name -> AddDialog(name, state.nameError, viewModel::onIntent) }
    state.confirmRevoke?.let { name ->
        AlertDialog(
            onDismissRequest = { viewModel.onIntent(ComputersIntent.CancelRevoke) },
            title = { Text("Disconnect $name?") },
            text = { Text("Its token stops working right away. Other computers stay connected.") },
            confirmButton = { TextButton(onClick = { viewModel.onIntent(ComputersIntent.ConfirmRevoke) }) { Text("Disconnect") } },
            dismissButton = { TextButton(onClick = { viewModel.onIntent(ComputersIntent.CancelRevoke) }) { Text("Cancel") } },
        )
    }
    if (state.showing != null) CommandSheet(state, viewModel::onIntent)
}

@Composable
private fun ComputersContent(state: ComputersState, onIntent: (ComputersIntent) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp)) {
        item(key = "intro") {
            Text(
                "Give each computer its own token. You can then disconnect one, say a lost laptop, " +
                    "without re-pairing the others. The command on Home keeps working as the shared token.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 16.dp),
            )
        }
        if (state.computers.isEmpty()) {
            item(key = "empty") {
                Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(72.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Laptop, contentDescription = null, modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("No computers yet", style = MaterialTheme.typography.titleLarge)
                }
            }
        } else {
            item(key = "list") {
                SettingsGroup {
                    state.computers.forEachIndexed { index, computer ->
                        if (index > 0) RowDivider()
                        ComputerRow(computer, onIntent)
                    }
                }
            }
        }
    }
}

@Composable
private fun ComputerRow(computer: ComputerUi, onIntent: (ComputersIntent) -> Unit) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onIntent(ComputersIntent.Show(computer.name)) }
            .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Laptop, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(computer.name, style = MaterialTheme.typography.titleMedium)
            Text(
                computer.lastUsed?.let {
                    "Last used " + relativeTime(it)
                } ?: ("Added " + DateUtils.formatDateTime(context, computer.createdAt, DateUtils.FORMAT_SHOW_DATE) + " · not used yet"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { onIntent(ComputersIntent.AskRevoke(computer.name)) }) {
            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Disconnect ${computer.name}")
        }
    }
}

@Composable
private fun AddDialog(name: String, error: String?, onIntent: (ComputersIntent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onIntent(ComputersIntent.CancelAdd) },
        title = { Text("Add a computer") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { onIntent(ComputersIntent.EditName(it)) },
                label = { Text("Name") },
                placeholder = { Text("Work laptop") },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onIntent(ComputersIntent.ConfirmAdd) }),
            )
        },
        confirmButton = { TextButton(onClick = { onIntent(ComputersIntent.ConfirmAdd) }) { Text("Create token") } },
        dismissButton = { TextButton(onClick = { onIntent(ComputersIntent.CancelAdd) }) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommandSheet(state: ComputersState, onIntent: (ComputersIntent) -> Unit) {
    val computer = state.showing ?: return
    val connection = state.showingConnection ?: return
    ModalBottomSheet(
        onDismissRequest = { onIntent(ComputersIntent.HideCommand) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 24.dp),
        ) {
            Text("Connect ${computer.name}", style = MaterialTheme.typography.titleLarge)
            Text(
                "Run this on ${computer.name}. It uses a token only this computer has.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )
            CodeBlock(connection.claudeCodeCommand(), wrap = true)
            if (state.host == null) {
                Text(
                    "Replace PHONE_IP with the phone's address once it's on Wi-Fi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Row(Modifier.padding(top = 16.dp)) {
                Button(onClick = { onIntent(ComputersIntent.CopyCommand) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Copy command")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { onIntent(ComputersIntent.CopyJson) }) { Text("Copy JSON") }
            }
        }
    }
}
