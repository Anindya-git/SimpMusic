package com.maxrave.simpmusic.viewModel

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Represents a single audio file on the device.
 */
data class LocalAudioFile(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,       // milliseconds
    val uri: Uri,             // content URI for ExoPlayer
    val albumArtUri: Uri?,    // content URI for album art (may be null)
    val dateAdded: Long,      // seconds since epoch
    val sizeBytes: Long,
)

sealed class LocalMusicState {
    data object Loading : LocalMusicState()
    data class Success(val files: List<LocalAudioFile>) : LocalMusicState()
    data class Error(val message: String) : LocalMusicState()
    data object PermissionRequired : LocalMusicState()
    data object Empty : LocalMusicState()
}

/**
 * ViewModel for the Local Music screen.
 *
 * Scans the device MediaStore for audio files and exposes them as a StateFlow.
 * Deliberately kept Android-only (androidMain source set) because local-file
 * scanning is entirely platform-specific.
 */
class LocalMusicViewModel(
    private val context: Context,
) : ViewModel() {

    private val _localMusicState = MutableStateFlow<LocalMusicState>(LocalMusicState.Loading)
    val localMusicState: StateFlow<LocalMusicState> = _localMusicState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private var _allFiles: List<LocalAudioFile> = emptyList()

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    fun loadLocalMusic() {
        viewModelScope.launch {
            _localMusicState.value = LocalMusicState.Loading
            val files = scanMediaStore()
            _allFiles = files
            _localMusicState.value = when {
                files.isEmpty() -> LocalMusicState.Empty
                else -> LocalMusicState.Success(files)
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        val filtered = if (query.isBlank()) {
            _allFiles
        } else {
            val q = query.lowercase()
            _allFiles.filter {
                it.title.lowercase().contains(q) ||
                    it.artist.lowercase().contains(q) ||
                    it.album.lowercase().contains(q)
            }
        }
        _localMusicState.value = if (filtered.isEmpty()) {
            LocalMusicState.Empty
        } else {
            LocalMusicState.Success(filtered)
        }
    }

    fun setPermissionDenied() {
        _localMusicState.value = LocalMusicState.PermissionRequired
    }

    // -----------------------------------------------------------------------
    // MediaStore scanning
    // -----------------------------------------------------------------------

    private suspend fun scanMediaStore(): List<LocalAudioFile> = withContext(Dispatchers.IO) {
        val files = mutableListOf<LocalAudioFile>()

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.ALBUM_ID,
        )

        // Only include files longer than 30 s to exclude notification sounds, etc.
        val selection = "${MediaStore.Audio.Media.DURATION} >= ?"
        val selectionArgs = arrayOf("30000")
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            sortOrder,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val albumId = cursor.getLong(albumIdCol)

                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id,
                )
                val albumArtUri = Uri.parse("content://media/external/audio/albumart/$albumId")

                files += LocalAudioFile(
                    id = id,
                    title = cursor.getString(titleCol) ?: "Unknown",
                    artist = cursor.getString(artistCol) ?: "Unknown Artist",
                    album = cursor.getString(albumCol) ?: "Unknown Album",
                    duration = cursor.getLong(durationCol),
                    uri = contentUri,
                    albumArtUri = albumArtUri,
                    dateAdded = cursor.getLong(dateAddedCol),
                    sizeBytes = cursor.getLong(sizeCol),
                )
            }
        }

        files
    }
}
