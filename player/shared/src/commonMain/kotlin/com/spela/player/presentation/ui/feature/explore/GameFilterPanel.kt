package com.spela.player.presentation.ui.feature.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import com.spela.player.domain.model.GameFilters
import com.spela.player.domain.model.SavedSearch
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.components.SpChip
import com.spela.player.presentation.ui.components.SpTextField
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

private val CONSOLE_OPTIONS = listOf("NES", "SNES", "N64", "GB", "GBC", "GBA", "SMS", "GEN", "GG", "PSX", "SATURN")

private val GENRE_OPTIONS = listOf(
    "Action", "Adventure", "RPG", "Platform", "Puzzle",
    "Racing", "Shooter", "Sports", "Strategy", "Fighting",
    "Simulation", "Horror",
)

private val PLAY_STATUS_OPTIONS = listOf(
    "unplayed" to "Unplayed",
    "played" to "Played",
    "favorited" to "Favorited",
    "play-later" to "Play Later",
)

private val SORT_OPTIONS = listOf(
    "title" to "Title",
    "rating" to "Rating",
    "release_date" to "Release Date",
    "created_at" to "Date Added",
    "file_size" to "File Size",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameFilterPanel(
    filters: GameFilters,
    savedSearches: List<SavedSearch>,
    isLoading: Boolean,
    isSaving: Boolean,
    onFiltersChanged: (GameFilters) -> Unit,
    onApplyFilters: () -> Unit,
    onClearFilters: () -> Unit,
    onSaveSearch: (String) -> Unit,
    onDeleteSavedSearch: (String) -> Unit,
    onApplySavedSearch: (SavedSearch) -> Unit,
    modifier: Modifier = Modifier,
) {
    var saveSearchName by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .testTag("game_filter_panel"),
    ) {
        // --- Saved Searches Section ---
        if (savedSearches.isNotEmpty()) {
            Text(
                text = "Saved Searches",
                style = SpTypography.TitleSmall,
                color = SpColor.OnBackground,
                modifier = Modifier.testTag("saved_searches_title"),
            )
            Spacer(Modifier.height(SpSpacing.Small))
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("saved_searches_list"),
                horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
                verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
            ) {
                savedSearches.forEach { search ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SpChip(
                            text = search.name,
                            onClick = { onApplySavedSearch(search) },
                            modifier = Modifier.testTag("saved_search_chip_${search.id}"),
                        )
                        IconButton(
                            onClick = { onDeleteSavedSearch(search.id) },
                            modifier = Modifier.testTag("delete_saved_search_${search.id}"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Delete saved search",
                                tint = SpColor.OnBackgroundSecondary,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(SpSpacing.Medium))
        }

        // --- Search text ---
        Text(
            text = "Search",
            style = SpTypography.TitleSmall,
            color = SpColor.OnBackground,
        )
        Spacer(Modifier.height(SpSpacing.XSmall))
        SpTextField(
            value = filters.search,
            onValueChange = { onFiltersChanged(filters.copy(search = it)) },
            placeholder = "Search games...",
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Search",
                    tint = SpColor.OnBackgroundSecondary,
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("filter_search_field"),
        )
        Spacer(Modifier.height(SpSpacing.Medium))

        // --- Console chips ---
        Text(
            text = "Consoles",
            style = SpTypography.TitleSmall,
            color = SpColor.OnBackground,
        )
        Spacer(Modifier.height(SpSpacing.XSmall))
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("filter_consoles_chips"),
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        ) {
            CONSOLE_OPTIONS.forEach { console ->
                SpChip(
                    text = console,
                    isSelected = console in filters.consoles,
                    onClick = {
                        val updated = if (console in filters.consoles) {
                            filters.consoles - console
                        } else {
                            filters.consoles + console
                        }
                        onFiltersChanged(filters.copy(consoles = updated))
                    },
                    modifier = Modifier.testTag("filter_console_chip_$console"),
                )
            }
        }
        Spacer(Modifier.height(SpSpacing.Medium))

        // --- Genre chips ---
        Text(
            text = "Genres",
            style = SpTypography.TitleSmall,
            color = SpColor.OnBackground,
        )
        Spacer(Modifier.height(SpSpacing.XSmall))
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("filter_genres_chips"),
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        ) {
            GENRE_OPTIONS.forEach { genre ->
                SpChip(
                    text = genre,
                    isSelected = genre in filters.genres,
                    onClick = {
                        val updated = if (genre in filters.genres) {
                            filters.genres - genre
                        } else {
                            filters.genres + genre
                        }
                        onFiltersChanged(filters.copy(genres = updated))
                    },
                    modifier = Modifier.testTag("filter_genre_chip_$genre"),
                )
            }
        }
        Spacer(Modifier.height(SpSpacing.Medium))

        // --- Developer & Publisher text fields ---
        Text(
            text = "Developer",
            style = SpTypography.TitleSmall,
            color = SpColor.OnBackground,
        )
        Spacer(Modifier.height(SpSpacing.XSmall))
        SpTextField(
            value = filters.developer,
            onValueChange = { onFiltersChanged(filters.copy(developer = it)) },
            placeholder = "Developer name...",
            modifier = Modifier
                .fillMaxWidth()
                .testTag("filter_developer_field"),
        )
        Spacer(Modifier.height(SpSpacing.Small))
        Text(
            text = "Publisher",
            style = SpTypography.TitleSmall,
            color = SpColor.OnBackground,
        )
        Spacer(Modifier.height(SpSpacing.XSmall))
        SpTextField(
            value = filters.publisher,
            onValueChange = { onFiltersChanged(filters.copy(publisher = it)) },
            placeholder = "Publisher name...",
            modifier = Modifier
                .fillMaxWidth()
                .testTag("filter_publisher_field"),
        )
        Spacer(Modifier.height(SpSpacing.Medium))

        // --- Year range ---
        Text(
            text = "Release Year",
            style = SpTypography.TitleSmall,
            color = SpColor.OnBackground,
        )
        Spacer(Modifier.height(SpSpacing.XSmall))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        ) {
            SpTextField(
                value = filters.yearMin?.toString() ?: "",
                onValueChange = { onFiltersChanged(filters.copy(yearMin = it.toIntOrNull())) },
                placeholder = "From",
                keyboardType = KeyboardType.Number,
                modifier = Modifier
                    .weight(1f)
                    .testTag("filter_year_min_field"),
            )
            SpTextField(
                value = filters.yearMax?.toString() ?: "",
                onValueChange = { onFiltersChanged(filters.copy(yearMax = it.toIntOrNull())) },
                placeholder = "To",
                keyboardType = KeyboardType.Number,
                modifier = Modifier
                    .weight(1f)
                    .testTag("filter_year_max_field"),
            )
        }
        Spacer(Modifier.height(SpSpacing.Medium))

        // --- Rating range ---
        Text(
            text = "Rating",
            style = SpTypography.TitleSmall,
            color = SpColor.OnBackground,
        )
        Spacer(Modifier.height(SpSpacing.XSmall))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        ) {
            SpTextField(
                value = filters.ratingMin?.let { "%.0f".format(it) } ?: "",
                onValueChange = { onFiltersChanged(filters.copy(ratingMin = it.toDoubleOrNull())) },
                placeholder = "Min",
                keyboardType = KeyboardType.Number,
                modifier = Modifier
                    .weight(1f)
                    .testTag("filter_rating_min_field"),
            )
            SpTextField(
                value = filters.ratingMax?.let { "%.0f".format(it) } ?: "",
                onValueChange = { onFiltersChanged(filters.copy(ratingMax = it.toDoubleOrNull())) },
                placeholder = "Max",
                keyboardType = KeyboardType.Number,
                modifier = Modifier
                    .weight(1f)
                    .testTag("filter_rating_max_field"),
            )
        }
        Spacer(Modifier.height(SpSpacing.Medium))

        // --- Play status chips ---
        Text(
            text = "Play Status",
            style = SpTypography.TitleSmall,
            color = SpColor.OnBackground,
        )
        Spacer(Modifier.height(SpSpacing.XSmall))
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("filter_play_status_chips"),
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        ) {
            PLAY_STATUS_OPTIONS.forEach { (value, label) ->
                SpChip(
                    text = label,
                    isSelected = filters.playStatus == value,
                    onClick = {
                        val updated = if (filters.playStatus == value) "" else value
                        onFiltersChanged(filters.copy(playStatus = updated))
                    },
                    modifier = Modifier.testTag("filter_play_status_chip_$value"),
                )
            }
        }
        Spacer(Modifier.height(SpSpacing.Medium))

        // --- Sort ---
        Text(
            text = "Sort By",
            style = SpTypography.TitleSmall,
            color = SpColor.OnBackground,
        )
        Spacer(Modifier.height(SpSpacing.XSmall))
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("filter_sort_chips"),
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        ) {
            SORT_OPTIONS.forEach { (value, label) ->
                SpChip(
                    text = label,
                    isSelected = filters.sortBy == value,
                    onClick = {
                        if (filters.sortBy == value) {
                            // Toggle sort order or deselect
                            val newOrder = if (filters.sortOrder == "asc") "desc" else "asc"
                            onFiltersChanged(filters.copy(sortOrder = newOrder))
                        } else {
                            onFiltersChanged(filters.copy(sortBy = value, sortOrder = "asc"))
                        }
                    },
                    modifier = Modifier.testTag("filter_sort_chip_$value"),
                )
            }
        }
        Spacer(Modifier.height(SpSpacing.Large))

        // --- Action buttons ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        ) {
            SpButton(
                text = "Apply Filters",
                onClick = onApplyFilters,
                modifier = Modifier
                    .weight(1f)
                    .testTag("apply_filters_button"),
                isLoading = isLoading,
            )
            SpButton(
                text = "Clear",
                onClick = onClearFilters,
                modifier = Modifier
                    .weight(1f)
                    .testTag("clear_filters_button"),
                style = SpButtonStyle.Secondary,
            )
        }
        Spacer(Modifier.height(SpSpacing.Medium))

        // --- Save search ---
        if (!filters.isEmpty) {
            Text(
                text = "Save This Search",
                style = SpTypography.TitleSmall,
                color = SpColor.OnBackground,
            )
            Spacer(Modifier.height(SpSpacing.XSmall))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SpTextField(
                    value = saveSearchName,
                    onValueChange = { saveSearchName = it },
                    placeholder = "Search name...",
                    modifier = Modifier
                        .weight(1f)
                        .testTag("save_search_name_field"),
                )
                SpButton(
                    text = "Save",
                    onClick = {
                        if (saveSearchName.isNotBlank()) {
                            onSaveSearch(saveSearchName)
                            saveSearchName = ""
                        }
                    },
                    modifier = Modifier.testTag("save_search_button"),
                    isLoading = isSaving,
                    enabled = saveSearchName.isNotBlank(),
                )
            }
        }
        Spacer(Modifier.height(SpSpacing.Medium))
    }
}
