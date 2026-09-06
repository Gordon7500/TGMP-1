package com.tuned.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuned.app.data.Track

@Composable
fun LibraryScreen(
    tracks: List<Track>,
    currentTrackId: String?,
    onTrackClick: (Track) -> Unit,
    onOpenSettings: () -> Unit,
    onConnectClick: () -> Unit,
    statusMessage: String?,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, tracks) {
        if (query.isBlank()) tracks
        else tracks.filter {
            it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true)
        }
    }

    Column(modifier = modifier.fillMaxSize().background(Black)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp, 20.dp, 12.dp, 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(12.dp).clip(RoundedCornerShape(3.dp))
                        .background(androidx.compose.ui.graphics.Brush.horizontalGradient(VisualizerColors))
                )
                Spacer(Modifier.width(10.dp))
                Text("Tuned", fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = TextPrimary)
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = TextPrimary)
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search your library", color = TextMuted) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextMuted) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Surface,
                unfocusedContainerColor = Surface,
                focusedBorderColor = Violet,
                unfocusedBorderColor = Line,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )

        statusMessage?.let {
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 10.dp)
                    .background(Surface, RoundedCornerShape(8.dp)).padding(12.dp)
            ) {
                Text(it, color = TextMuted, fontSize = 12.5.sp)
            }
        }

        if (tracks.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape)
                        .background(androidx.compose.ui.graphics.Brush.horizontalGradient(VisualizerColors))
                )
                Spacer(Modifier.height(20.dp))
                Text("No tracks yet", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Connect your bot and pull music from your Telegram channels.",
                    color = TextMuted, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(22.dp))
                Button(
                    onClick = onConnectClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Violet)
                ) { Text("Connect Telegram", color = Color_Black) }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                items(filtered, key = { it.fileUniqueId }) { track ->
                    TrackRow(
                        track = track,
                        isPlaying = track.fileUniqueId == currentTrackId,
                        onClick = { onTrackClick(track) }
                    )
                }
                item { Spacer(Modifier.height(140.dp)) }
            }
        }
    }
}

@Composable
private fun TrackRow(track: Track, isPlaying: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(8.dp))
                .background(Surface2)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                color = if (isPlaying) Cyan else TextPrimary,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${track.artist} · ${track.sourceChat}",
                color = TextMuted,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (track.durationSec > 0) {
            Text(
                "%d:%02d".format(track.durationSec / 60, track.durationSec % 60),
                color = TextMuted,
                fontSize = 12.sp
            )
        }
    }
}

private val Color_Black = androidx.compose.ui.graphics.Color.Black
