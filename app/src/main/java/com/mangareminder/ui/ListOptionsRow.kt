package com.mangareminder.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mangareminder.R
import com.mangareminder.data.ListFilter
import com.mangareminder.data.ListSort

private fun ListFilter.label(): Int = when (this) {
    ListFilter.ALL -> R.string.filter_all
    ListFilter.INCOMPLETE -> R.string.filter_incomplete
    ListFilter.COMPLETE -> R.string.filter_complete
    ListFilter.WATCHED -> R.string.filter_watched
    ListFilter.NEW_RELEASE -> R.string.filter_new_release
    ListFilter.LENT -> R.string.filter_lent
}

private fun ListSort.label(): Int = when (this) {
    ListSort.TITLE -> R.string.sort_title
    ListSort.ADDED -> R.string.sort_added
    ListSort.MISSING -> R.string.sort_missing
}

/** Filtres de la liste (un seul à la fois) et choix du tri, sur une ligne qui défile. */
@Composable
fun ListOptionsRow(
    filter: ListFilter,
    sort: ListSort,
    showLent: Boolean,
    onFilter: (ListFilter) -> Unit,
    onSort: (ListSort) -> Unit,
) {
    var sortMenu by remember { mutableStateOf(false) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(PaddingValues(horizontal = 16.dp)),
    ) {
        Box {
            AssistChip(
                onClick = { sortMenu = true },
                label = { Text(stringResource(R.string.sort_label, stringResource(sort.label()))) },
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            )
            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                ListSort.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(option.label())) },
                        onClick = {
                            sortMenu = false
                            onSort(option)
                        },
                    )
                }
            }
        }
        ListFilter.entries
            .filter { it != ListFilter.LENT || showLent }
            .forEach { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { onFilter(option) },
                    label = { Text(stringResource(option.label())) },
                )
            }
    }
}
