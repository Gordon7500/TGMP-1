package com.tuned.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerSheet(
    equalizerEnabled: Boolean,
    bands: List<EqualizerBand>,
    bandRangeMb: IntRange,
    bassBoostEnabled: Boolean,
    bassBoostStrength: Int,
    onDismiss: () -> Unit,
    onEqualizerEnabledChange: (Boolean) -> Unit,
    onBandChange: (index: Int, levelMb: Int) -> Unit,
    onBassBoostEnabledChange: (Boolean) -> Unit,
    onBassBoostStrengthChange: (Int) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Surface) {
        Column(Modifier.padding(20.dp).padding(bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Equalizer", color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextPrimary)
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                "Turning these on processes the audio digitally — that means it's no longer a raw, " +
                    "bit-perfect signal, even with direct output enabled. Leave both off for full-fidelity playback.",
                color = TextMuted, fontSize = 11.5.sp
            )

            Spacer(Modifier.height(20.dp))
            SettingRow(
                title = "Equalizer",
                subtitle = null,
                checked = equalizerEnabled,
                onCheckedChange = onEqualizerEnabledChange,
                enabled = bands.isNotEmpty()
            )

            if (bands.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(bands, key = { it.index }) { band ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                band.label, color = TextMuted, fontSize = 11.sp,
                                modifier = Modifier.width(46.dp)
                            )
                            Slider(
                                value = band.levelMb.toFloat(),
                                onValueChange = { onBandChange(band.index, it.toInt()) },
                                valueRange = bandRangeMb.first.toFloat()..bandRangeMb.last.toFloat(),
                                enabled = equalizerEnabled,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = Cyan, activeTrackColor = Cyan, inactiveTrackColor = Line
                                )
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            SettingRow(
                title = "Bass boost",
                subtitle = null,
                checked = bassBoostEnabled,
                onCheckedChange = onBassBoostEnabledChange,
                enabled = true
            )
            Spacer(Modifier.height(8.dp))
            Slider(
                value = bassBoostStrength.toFloat(),
                onValueChange = { onBassBoostStrengthChange(it.toInt()) },
                valueRange = 0f..1000f,
                enabled = bassBoostEnabled,
                colors = SliderDefaults.colors(
                    thumbColor = Amber, activeTrackColor = Amber, inactiveTrackColor = Line
                )
            )
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            subtitle?.let {
                Text(it, color = TextMuted, fontSize = 11.5.sp, modifier = Modifier.padding(top = 3.dp))
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = Violet)
        )
    }
}
