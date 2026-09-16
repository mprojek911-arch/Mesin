package com.autoremix.djslow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autoremix.djslow.engine.music.MusicMode
import com.autoremix.djslow.engine.music.PitchClass
import com.autoremix.djslow.engine.synth.BassPatternType
import com.autoremix.djslow.engine.synth.ChordSynthPreset
import com.autoremix.djslow.state.UiState
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.StudioCardBg
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt

/**
 * Komponen UI Mesin Musik Tahap 3:
 * Menampilkan dan mengontrol BPM Engine, Key Engine, Chord Engine, Bass Engine,
 * dan sinkronisasi Master Timeline secara visual dan interaktif.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MusicEngineCard(
    uiState: UiState,
    onBpmIncrement: () -> Unit,
    onBpmDecrement: () -> Unit,
    onBpmPresetSelected: (Int) -> Unit,
    onKeySelected: (PitchClass, MusicMode) -> Unit,
    onChordPresetChanged: (ChordSynthPreset) -> Unit,
    onBassPatternChanged: (BassPatternType) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("music_engine_card")
            .border(1.dp, NeonPurple.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = StudioCardBg),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Kartu Mesin Musik
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = NeonPurple,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "🎵 MESIN MUSIK (TAHAP 3)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "BPM • Tangga Nada • Progresi Akor • Bass DJ Slow • Master Timeline",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }

            // ==========================================
            // 1. BPM ENGINE (TARGET BPM, MANUAL, PRESETS)
            // ==========================================
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bpm_section"),
                shape = RoundedCornerShape(10.dp),
                color = StudioSurfaceElevated
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "BPM ENGINE",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = NeonCyan
                            )
                        }

                        // Badge Status Perkiraan / Akurat
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (uiState.isBpmEstimated) Color(0xFF332712) else Color(0xFF0F3022))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (uiState.isBpmEstimated) "Perkiraan BPM" else "BPM Terkunci",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.isBpmEstimated) NeonAmber else Color(0xFF00E676)
                            )
                        }
                    }

                    // Tampilan BPM Terdeteksi Vokal & Beat
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = "BPM Vokal", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            Text(
                                text = uiState.vocalBpmText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                modifier = Modifier.testTag("vocal_bpm_text")
                            )
                        }

                        Column {
                            Text(text = "BPM Beat", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            Text(
                                text = uiState.beatBpmText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                modifier = Modifier.testTag("beat_bpm_text")
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = "Keyakinan", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            Text(
                                text = uiState.confidenceText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = NeonAmber,
                                modifier = Modifier.testTag("confidence_text")
                            )
                        }
                    }

                    // Stepper Target BPM [ - ] [ TARGET BPM ] [ + ]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TARGET BPM:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilledTonalButton(
                                onClick = onBpmDecrement,
                                modifier = Modifier
                                    .size(38.dp)
                                    .testTag("bpm_decrement_button"),
                                shape = CircleShape,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF262E40))
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Kurangi BPM", tint = Color.White)
                            }

                            Text(
                                text = "${uiState.targetBpm.roundToInt()} BPM",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = NeonCyan,
                                modifier = Modifier
                                    .padding(horizontal = 14.dp)
                                    .testTag("target_bpm_text")
                            )

                            FilledTonalButton(
                                onClick = onBpmIncrement,
                                modifier = Modifier
                                    .size(38.dp)
                                    .testTag("bpm_increment_button"),
                                shape = CircleShape,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF262E40))
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Tambah BPM", tint = Color.White)
                            }
                        }
                    }

                    // Presets BPM Cepat DJ Slow: 70, 75, 80, 85, 90, 95
                    Text(
                        text = "Preset Tempo DJ Slow:",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val presets = listOf(70, 75, 80, 85, 90, 95)
                        presets.forEach { preset ->
                            val isSelected = uiState.targetBpm.roundToInt() == preset
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) NeonCyan else Color(0xFF161B26))
                                    .clickable { onBpmPresetSelected(preset) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .testTag("bpm_preset_$preset")
                            ) {
                                Text(
                                    text = "$preset",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.Black else TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 2. KEY ENGINE & MANUAL KEY SELECTOR
            // ==========================================
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("key_section"),
                shape = RoundedCornerShape(10.dp),
                color = StudioSurfaceElevated
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TANGGA NADA (KEY ENGINE)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = NeonPink
                        )

                        Text(
                            text = uiState.keyText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = NeonPink,
                            modifier = Modifier.testTag("key_text")
                        )
                    }

                    Text(
                        text = "Pilih Tangga Nada Manual (Seleraskan Akor & Bass):",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )

                    // Pilihan Nada Dasar (Tonic)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PitchClass.values().forEach { pitch ->
                            val isSelected = uiState.detectedKey.tonic == pitch
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) NeonPink else Color(0xFF161B26))
                                    .clickable { onKeySelected(pitch, uiState.detectedKey.mode) }
                                    .padding(horizontal = 9.dp, vertical = 5.dp)
                                    .testTag("key_pitch_${pitch.noteName}")
                            ) {
                                Text(
                                    text = pitch.noteName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.Black else TextSecondary
                                )
                            }
                        }
                    }

                    // Pilihan Mode (Mayor / Minor)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MusicMode.values().forEach { mode ->
                            val isSelected = uiState.detectedKey.mode == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) NeonPink.copy(alpha = 0.25f) else Color(0xFF161B26))
                                    .border(
                                        1.dp,
                                        if (isSelected) NeonPink else Color(0xFF262E40),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onKeySelected(uiState.detectedKey.tonic, mode) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) NeonPink else TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 3. CHORD ENGINE & SYNTH PRESET
            // ==========================================
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("chord_section"),
                shape = RoundedCornerShape(10.dp),
                color = StudioSurfaceElevated
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PROGRESI AKOR (CHORD ENGINE)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = NeonPurple
                        )

                        Text(
                            text = if (uiState.isChordEstimated) "Perkiraan" else "Terkunci",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }

                    // Tampilan Akor
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF161B26))
                            .padding(vertical = 10.dp, horizontal = 12.dp)
                    ) {
                        Text(
                            text = uiState.chordText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = NeonPurple,
                            modifier = Modifier.testTag("chord_text")
                        )
                    }

                    // Pemilih Preset Instrumen Akor
                    Text(
                        text = "Instrumen Sintesis Akor:",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ChordSynthPreset.values().forEach { preset ->
                            val isSelected = uiState.chordPreset == preset
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) NeonPurple else Color(0xFF161B26))
                                    .clickable { onChordPresetChanged(preset) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("chord_preset_${preset.name}")
                            ) {
                                Text(
                                    text = preset.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 4. BASS ENGINE (POLA & SINTESIS)
            // ==========================================
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bass_section"),
                shape = RoundedCornerShape(10.dp),
                color = StudioSurfaceElevated
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "BASS ENGINE",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = NeonAmber
                        )

                        Text(
                            text = uiState.bassPattern.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = NeonAmber,
                            modifier = Modifier.testTag("bass_pattern_text")
                        )
                    }

                    Text(
                        text = "Pola Irama Bass:",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BassPatternType.values().forEach { pattern ->
                            val isSelected = uiState.bassPattern == pattern
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) NeonAmber else Color(0xFF161B26))
                                    .clickable { onBassPatternChanged(pattern) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("bass_pattern_${pattern.name}")
                            ) {
                                Text(
                                    text = pattern.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.Black else TextSecondary
                                )
                            }
                        }
                    }

                    Text(
                        text = uiState.bassPattern.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }

            // ==========================================
            // 5. MASTER TIMELINE SYNC INFO
            // ==========================================
            uiState.masterTimeline?.let { timeline ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("timeline_section"),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0F131D)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Master Timeline:",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }

                        Text(
                            text = "${timeline.bpm.roundToInt()} BPM • 4/4 • ${timeline.totalBars} Bar • 44.1 kHz",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan,
                            modifier = Modifier.testTag("timeline_info_text")
                        )
                    }
                }
            }
        }
    }
}
