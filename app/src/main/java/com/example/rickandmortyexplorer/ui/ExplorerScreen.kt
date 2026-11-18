package com.example.rickandmortyexplorer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.example.rickandmortyexplorer.Category
import com.example.rickandmortyexplorer.DisplayItem
import com.example.rickandmortyexplorer.ExplorerViewModel
import com.example.rickandmortyexplorer.LocationPhoto
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerScreen(viewModel: ExplorerViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val current = state.current
    val error = state.error
    val saveMessage = state.saveMessage
    var showCamera by rememberSaveable { mutableStateOf(false) }

    if (showCamera) {
        CameraScreen(
            onPhotoTaken = { file, location ->
                viewModel.savePhoto(file, location?.latitude, location?.longitude)
                showCamera = false
            },
            onBack = { showCamera = false }
        )
        return
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Rick and Morty Explorer") }, colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )
    }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Select Category", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Category.entries.forEach { category ->
                    if (category == state.category) {
                        Button(onClick = {}, modifier = Modifier.weight(1f)) {
                            Text(
                                category.label,
                                maxLines = 1
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = { viewModel.selectCategory(category) },
                            modifier = Modifier.weight(1f)
                        ) { Text(category.label, maxLines = 1) }
                    }
                }
            }

            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                label = { Text("Filter ${state.category.label} by name...") },
                leadingIcon = { Text("🔍") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Firebase & API Stats", fontWeight = FontWeight.Bold)
                    Text("Total Rolls: ${state.rollCount}", fontWeight = FontWeight.Bold)
                    Text("Total ${state.category.label}s in API: ${state.totalCount}")
                    Text("Retrieved in current view: ${state.items.size} items")
                }
            }

            when {
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                error != null -> Text(error, color = MaterialTheme.colorScheme.error)
                current != null -> ItemCard(current, state.photo)
            }

            Button(
                onClick = viewModel::roll,
                enabled = state.items.size > 1 && !state.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) { Text("🎲 Roll Random Item") }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = viewModel::save,
                    enabled = current != null && !state.isLoading,
                    modifier = Modifier.weight(1f)
                ) { Text("🔖 Save Item") }
                Button(
                    onClick = viewModel::loadLast,
                    enabled = !state.isLoading,
                    modifier = Modifier.weight(1f)
                ) { Text("☁️ Load Last") }
            }

            if (current?.category == Category.LOCATION) {
                OutlinedButton(
                    onClick = { showCamera = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("📷 Take Photo Here") }
            }

            if (saveMessage != null) Text(saveMessage)
        }
    }
}

@Composable
private fun ItemCard(item: DisplayItem, photo: LocationPhoto?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val imageModifier = Modifier
                .size(200.dp)
                .clip(RoundedCornerShape(12.dp))
            // a foto tirada no local tem prioridade sobre a imagem da API
            val image = photo?.let { File(it.path) } ?: item.imageUrl
            if (image != null) {
                AsyncImage(
                    model = image,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = imageModifier
                )
            } else {
                // episódios e localizações não têm imagem na API
                Box(
                    modifier = imageModifier.background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) { Text("No image") }
            }
            Text(
                item.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            HorizontalDivider()
            FieldsRow(item.fields)
            if (photo != null) {
                FieldsRow(
                    listOf(
                        "Latitude" to photo.latitude?.let { "%.5f".format(it) }.orEmpty(),
                        "Longitude" to photo.longitude?.let { "%.5f".format(it) }.orEmpty()
                    )
                )
            }
        }
    }
}

@Composable
private fun FieldsRow(fields: List<Pair<String, String>>) {
    Row(modifier = Modifier.fillMaxWidth()) {
        fields.forEach { (label, value) ->
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall)
                Text(value.ifBlank { "-" }, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
