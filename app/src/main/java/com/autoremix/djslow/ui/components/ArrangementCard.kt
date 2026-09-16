package com.autoremix.djslow.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import com.autoremix.djslow.engine.arrangement.AutoDjPreset
import com.autoremix.djslow.engine.structure.SongSection
import com.autoremix.djslow.engine.structure.SongSectionType
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

@Composable
fun ArrangementCard(
    uiState: UiState,
    onPresetChanged: (AutoDjPreset) -> Unit,
    onRegenerateMelodySeed: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("arrangement_card")
            .border(1.dp, NeonPurple.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = StudioCardBg),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = null,
                    tint = NeonPurple,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "🎶 TAHAP 4 — ARANSEMEN & ENERGI AUTO DJ",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Drum • Melodi Hook • Pad Lush • Struktur Seksi & Transisi",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }

            // 1. Pemilihan Preset Auto DJ
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "PRESET ARANSEMEN AUTO DJ",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AutoDjPreset.entries.forEach { preset ->
                        val isSelected = uiState.currentPreset == preset
                        FilterChip(
                            selected = isSelected,
                            onClick = { onPresetChanged(preset) },
                            label = {
                                Text(
                                    text = "${preset.label} (${preset.defaultBpm.roundToInt()} BPM)",
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonPurple.copy(alpha = 0.3f),
                                selectedLabelColor = NeonCyan,
                                containerColor = StudioSurfaceElevated,
                                labelColor = TextSecondary
                            ),
                            modifier = Modifier.testTag("preset_chip_${preset.name.lowercase()}")
                        )
                    }
                }

                Text(
                    text = uiState.currentPreset.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }

            // 2. Visualisasi Seksi Lagu (Song Structure & Energy)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "STRUKTUR SEKSI & KURVA ENERGI",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = NeonAmber
                    )
                    Text(
                        text = if (uiState.songSections.isEmpty()) "Analisis untuk memetakan" else "${uiState.songSections.size} Seksi Terkunci Grid",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }

                if (uiState.songSections.isNotEmpty()) {
                    // Timeline bar visualizer
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        uiState.songSections.forEach { section ->
                            val sectionColor = getSectionColor(section.sectionType)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = sectionColor.copy(alpha = 0.18f),
                                modifier = Modifier
                                    .border(1.dp, sectionColor.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                                    .testTag("section_chip_${section.sectionType.name.lowercase()}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = section.sectionType.label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = sectionColor
                                    )
                                    Text(
                                        text = "${section.barCount} Bar (${(section.targetEnergy * 100).toInt()}%)",
                                        fontSize = 10.sp,
                                        color = TextMuted
                                    )
                                }
                            }
                        }
                    }

                    // Perlindungan Frasa Vokal Info
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = StudioSurfaceElevated,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Vocal Phrase Protection: Batas transisi drop diselaraskan dengan kelipatan 4/8 bar vokal tanpa memotong lirik.",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = StudioSurfaceElevated,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "💡 Klik tombol [ 🔍 ANALISIS ] di atas untuk mendeteksi struktur seksi lagu dan kurva energi otomatis.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            // 3. Kontrol Melody Engine (Random Seed & Generator)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = StudioSurfaceElevated,
                modifier = Modifier.fillMaxWidth()
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Piano,
                                contentDescription = null,
                                tint = NeonPink,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "MELODY ENGINE (Algorithmic Hook)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }

                        Text(
                            text = "Seed: #${uiState.melodySeed}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = NeonPink
                        )
                    }

                    Text(
                        text = "Melodi deterministik mengikuti Tangga Nada (${uiState.detectedKey.displayName}), Progresi Akor, dan Seksi Lagu.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )

                    Button(
                        onClick = onRegenerateMelodySeed,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("generate_melody_button"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonPink.copy(alpha = 0.25f),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "🔀 BUAT MELODI BARU",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 4. Drum Engine & Sidechain Status
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = StudioSurfaceElevated,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ElectricBolt,
                                contentDescription = null,
                                tint = NeonAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "DRUM SYNTH & KICK-BASS SYNC",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }

                        val beatModeText = uiState.beatAnalysis?.recommendedDrumMode?.label ?: "Auto DJ Slow"
                        Text(
                            text = "Mode: $beatModeText",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = NeonAmber
                        )
                    }

                    Text(
                        text = "Sidechain Compression aktif: Bass sub ducking otomatis saat Kick memukul, mencegah tabrakan frekuensi 30-120 Hz.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }
        }
    }
}

private fun getSectionColor(type: SongSectionType): Color {
    return when (type) {
        SongSectionType.INTRO -> Color(0xFF64B5F6)
        SongSectionType.BUILD_UP, SongSectionType.BUILD_UP_2, SongSectionType.FINAL_BUILD -> NeonAmber
        SongSectionType.GROOVE, SongSectionType.VERSE -> Color(0xFF81C784)
        SongSectionType.PRE_DROP -> Color(0xFFFFB74D)
        SongSectionType.DROP, SongSectionType.MAIN_DROP -> NeonPink
        SongSectionType.BREAK, SongSectionType.BREAKDOWN -> NeonPurple
        SongSectionType.PEAK, SongSectionType.FINAL_DROP -> Color(0xFFFF3366)
        SongSectionType.OUTRO -> Color(0xFF90A4AE)
    }
}
