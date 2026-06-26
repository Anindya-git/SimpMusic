package com.maxrave.simpmusic.ui.screen.library

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.maxrave.domain.data.player.GenericMediaItem
import com.maxrave.simpmusic.extension.isScrollingUp
import com.maxrave.simpmusic.ui.theme.typo
import com.maxrave.simpmusic.viewModel.LocalAudioFile
import com.maxrave.simpmusic.viewModel.LocalMusicState
import com.maxrave.simpmusic.viewModel.LocalMusicViewModel
import com.maxrave.simpmusic.viewModel.SharedViewModel
import org.koin.compose.viewmodel.koinViewModel

// ─── Helper: format ms duration as "m:ss" ───────────────────────────────────
private fun Long.formatDuration(): String {
    val totalSeconds = this / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

// ─── Permission helper ───────────────────────────────────────────────────────
private fun audioPermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

/**
 * Screen that lists local audio files from the device's MediaStore.
 *
 * Placement: `androidMain` source set only, referenced from the navigation graph
 * and LibraryScreen chip row exclusively on Android.
 *
 * Zero changes to existing screens, ViewModels, or navigation routes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalMusicScreen(
    innerPadding: PaddingValues,
    navController: NavController,
    sharedViewModel: SharedViewModel,
    viewModel: LocalMusicViewModel = koinViewModel(),
    onScrolling: (onTop: Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val state by viewModel.localMusicState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    // ── Permission launcher ─────────────────────────────────────────────────
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.loadLocalMusic()
        } else {
            viewModel.setPermissionDenied()
        }
    }

    // ── Auto-request permission on first composition ───────────────────────
    LaunchedEffect(Unit) {
        permissionLauncher.launch(audioPermission())
    }

    val listState = rememberLazyListState()
    val isScrollingUp by listState.isScrollingUp()
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { idx ->
                onScrolling(if (idx <= 1) true else isScrollingUp)
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
    ) {
        // ── Search bar ─────────────────────────────────────────────────────
        OutlinedTextField(
            value = searchQuery,
            onValueChange = viewModel::onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search local music…", style = typo().bodyMedium) },
            leadingIcon = {
                Icon(Icons.Rounded.Search, contentDescription = null)
            },
            singleLine = true,
            shape = RoundedCornerShape(50),
        )

        // ── Content ────────────────────────────────────────────────────────
        when (val s = state) {
            is LocalMusicState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is LocalMusicState.PermissionRequired -> {
                PermissionRequiredContent {
                    permissionLauncher.launch(audioPermission())
                }
            }

            is LocalMusicState.Empty -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No audio files found on device.",
                        style = typo().bodyMedium,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }
            }

            is LocalMusicState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = s.message,
                        style = typo().bodyMedium,
                        color = Color.Red.copy(alpha = 0.8f),
                    )
                }
            }

            is LocalMusicState.Success -> {
                LocalMusicList(
                    files = s.files,
                    listState = listState,
                    onPlayFile = { file, allFiles ->
                        // Build a queue from all visible files, starting at the tapped one
                        val queue = allFiles.map { f ->
                            GenericMediaItem(
                                mediaId = "local:${f.id}",
                                title = f.title,
                                artist = f.artist,
                                thumbnailUrl = f.albumArtUri?.toString(),
                                mediaUri = f.uri.toString(),
                                isLocal = true,
                            )
                        }
                        val startIndex = allFiles.indexOfFirst { it.id == file.id }
                            .coerceAtLeast(0)
                        sharedViewModel.playLocalQueue(queue, startIndex)
                    },
                )
            }
        }
    }
}

// ─── List of local tracks ────────────────────────────────────────────────────
@Composable
private fun LocalMusicList(
    files: List<LocalAudioFile>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onPlayFile: (LocalAudioFile, List<LocalAudioFile>) -> Unit,
) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        items(files, key = { it.id }) { file ->
            LocalAudioFileRow(
                file = file,
                onClick = { onPlayFile(file, files) },
            )
        }
    }
}

// ─── Single row ─────────────────────────────────────────────────────────────
@Composable
private fun LocalAudioFileRow(
    file: LocalAudioFile,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Album art
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF2A2A2A)),
            contentAlignment = Alignment.Center,
        ) {
            if (file.albumArtUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(file.albumArtUri)
                        .crossfade(300)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    error = null, // fall through to the icon fallback below
                )
            }
            // Fallback icon is always composited below the image
            // (only visible when image is absent or fails)
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(24.dp),
            )
        }

        Spacer(Modifier.width(12.dp))

        // Title / artist
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = file.title,
                style = typo().bodyMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = file.artist,
                style = typo().bodySmall,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(8.dp))

        // Duration
        Text(
            text = file.duration.formatDuration(),
            style = typo().bodySmall,
            color = Color.White.copy(alpha = 0.5f),
        )
    }
}

// ─── Permission-required placeholder ────────────────────────────────────────
@Composable
private fun PermissionRequiredContent(onRequest: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(64.dp),
            )
            Text(
                text = "Storage permission needed to\nbrowse local music files.",
                style = typo().bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
            )
            Button(onClick = onRequest) {
                Text("Grant Permission")
            }
        }
    }
}
