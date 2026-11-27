package com.mangareminder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.mangareminder.R
import com.mangareminder.releases.KeyTestResult
import com.mangareminder.releases.normalizeApiKey
import kotlinx.coroutines.launch

/** Page de la console Google Cloud où activer l'API Books (point de départ pour créer la clé). */
private const val BOOKS_API_CONSOLE = "https://console.cloud.google.com/apis/library/books.googleapis.com"

/**
 * Saisie de la clé d'API Google Books de l'utilisateur. La clé tapée n'est jamais réaffichée en
 * entier une fois enregistrée : seulement ses 4 derniers caractères ([savedMask]).
 */
@Composable
fun ApiKeyDialog(
    savedMask: String?,
    onSave: suspend (String) -> Boolean,
    onClear: suspend () -> Unit,
    /** Teste la clé tapée, ou l'enregistrée si le champ est vide ; null : aucune clé à tester. */
    onTest: suspend (String) -> KeyTestResult?,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var input by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Int?>(null) }
    var isError by remember { mutableStateOf(false) }

    fun show(text: Int, error: Boolean) {
        message = text
        isError = error
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.api_key_title)) },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.api_key_intro), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.api_key_steps), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { uriHandler.openUri(BOOKS_API_CONSOLE) }, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(R.string.api_key_open_console))
                }
                Text(
                    savedMask?.let { stringResource(R.string.api_key_saved, it) } ?: stringResource(R.string.api_key_none),
                    style = MaterialTheme.typography.titleSmall,
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it.take(120)
                        message = null
                    },
                    label = { Text(stringResource(if (savedMask == null) R.string.api_key_field else R.string.api_key_field_replace)) },
                    singleLine = true,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    trailingIcon = {
                        TextButton(onClick = { visible = !visible }) {
                            Text(stringResource(if (visible) R.string.api_key_hide else R.string.api_key_show))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        enabled = !busy && (input.isNotBlank() || savedMask != null),
                        onClick = {
                            if (input.isNotBlank() && normalizeApiKey(input) == null) {
                                show(R.string.api_key_invalid, true)
                                return@TextButton
                            }
                            busy = true
                            scope.launch {
                                when (onTest(input)) {
                                    KeyTestResult.OK -> show(R.string.api_key_test_ok, false)
                                    KeyTestResult.REFUSED -> show(R.string.api_key_test_refused, true)
                                    KeyTestResult.QUOTA -> show(R.string.api_key_test_quota, true)
                                    KeyTestResult.NETWORK -> show(R.string.api_key_test_network, true)
                                    null -> show(R.string.api_key_invalid, true)
                                }
                                busy = false
                            }
                        },
                    ) { Text(stringResource(R.string.api_key_test)) }
                    if (savedMask != null) {
                        TextButton(
                            enabled = !busy,
                            onClick = {
                                scope.launch {
                                    onClear()
                                    input = ""
                                    show(R.string.api_key_cleared, false)
                                }
                            },
                        ) { Text(stringResource(R.string.api_key_clear)) }
                    }
                    if (busy) CircularProgressIndicator(Modifier.heightIn(max = 20.dp))
                }
                message?.let {
                    Text(
                        stringResource(it),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
                Text(stringResource(R.string.api_key_privacy), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && input.isNotBlank(),
                onClick = {
                    busy = true
                    scope.launch {
                        if (onSave(input)) {
                            input = ""
                            visible = false
                            show(R.string.api_key_saved_ok, false)
                        } else {
                            show(R.string.api_key_invalid, true)
                        }
                        busy = false
                    }
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}
