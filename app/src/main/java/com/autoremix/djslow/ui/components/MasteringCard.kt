package com.autoremix.djslow.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autoremix.djslow.engine.mastering.MasteringPreset
import com.autoremix.djslow.engine.mix.BusSettings
import com.autoremix.djslow.engine.mix.BusType
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

/**
 * Komponen UI Kartu Mastering & Mix Bus Studio (Tahap 5).
 * - Pemilihan Profil Mastering Otomatis (DJ Slow, Natural, Bass Strong, Vocal Clear, Loud Clean)
 * - Kontrol 5 Mix Bus (Vocal, Beat, Drum, Bass, Music)
 * - Monitor Loudness Nyata (LUFS, True Peak dBTP, RMS, Anti-Clipping)
 * - Tampilan Arsitektur DSP Audio Studio
 */
@Composable
fun MasteringCard(
    uiState: UiState,
    onMasteringPresetChanged: (MasteringPreset) -> Unit,
    onBusVolumeChange: (BusType, Float) -> Unit,
    onBusMuteToggle: (BusType) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("mastering_card")
            .border(1.5.dp, Brush.horizontalGradient(listOf(NeonCyan, NeonPurple)), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = StudioCardBg),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Kartu
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Brush.linearGradient(listOf(NeonCyan, NeonPink))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "🎛️ AUTO MASTERING & MIX BUS (TAHAP 5)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "ITU-R BS.1770-4 LUFS • Brickwall Limiter • True Peak Anti-Clipping • DSP Studio",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }

            // 1. Pemilihan Preset Mastering
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "PROFIL MASTERING STUDIO",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MasteringPreset.values().take(3).forEach { preset ->
                        PresetChip(
                            preset = preset,
                            isSelected = uiState.masteringPreset == preset,
                            onClick = { onMasteringPresetChanged(preset) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MasteringPreset.values().drop(3).forEach { preset ->
                        PresetChip(
                            preset = preset,
                            isSelected = uiState.masteringPreset == preset,
                            onClick = { onMasteringPresetChanged(preset) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Deskripsi Preset yang Aktif
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = StudioSurfaceElevated
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Karakter: ${uiState.masteringPreset.label}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = NeonPink
                            )
                            Text(
                                text = "Target: ${uiState.masteringPreset.targetLufs} LUFS | Ceiling: ${uiState.masteringPreset.peakCeilingDbtp} dBTP",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = NeonCyan
                            )
                        }
                        Text(
                            text = uiState.masteringPreset.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            // 2. Mix Bus Controls (Tahap 5)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "🎚️ KONTROL MIX BUS STUDIO",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = NeonAmber
                )

                BusControlRow(
                    label = "🎤 VOCAL BUS",
                    color = Color(0xFF00E5FF),
                    busSettings = uiState.vocalBusSettings,
                    onVolumeChange = { onBusVolumeChange(BusType.VOCAL_BUS, it) },
                    onMuteToggle = { onBusMuteToggle(BusType.VOCAL_BUS) },
                    testTag = "vocal_bus"
                )

                BusControlRow(
                    label = "🥁 BEAT BUS",
                    color = Color(0xFFFF9100),
                    busSettings = uiState.beatBusSettings,
                    onVolumeChange = { onBusVolumeChange(BusType.BEAT_BUS, it) },
                    onMuteToggle = { onBusMuteToggle(BusType.BEAT_BUS) },
                    testTag = "beat_bus"
                )

                BusControlRow(
                    label = "💥 DRUM BUS",
                    color = NeonAmber,
                    busSettings = uiState.drumBusSettings,
                    onVolumeChange = { onBusVolumeChange(BusType.DRUM_BUS, it) },
                    onMuteToggle = { onBusMuteToggle(BusType.DRUM_BUS) },
                    testTag = "drum_bus"
                )

                BusControlRow(
                    label = "🎸 BASS BUS",
                    color = Color(0xFFFF5252),
                    busSettings = uiState.bassBusSettings,
                    onVolumeChange = { onBusVolumeChange(BusType.BASS_BUS, it) },
                    onMuteToggle = { onBusMuteToggle(BusType.BASS_BUS) },
                    testTag = "bass_bus"
                )

                BusControlRow(
                    label = "🎹 MUSIC BUS (Akor+Melodi+Pad+FX)",
                    color = NeonPurple,
                    busSettings = uiState.musicBusSettings,
                    onVolumeChange = { onBusVolumeChange(BusType.MUSIC_BUS, it) },
                    onMuteToggle = { onBusMuteToggle(BusType.MUSIC_BUS) },
                    testTag = "music_bus"
                )

                BusControlRow(
                    label = "🎛️ MASTER BUS",
                    color = NeonPink,
                    busSettings = uiState.masterBusSettings,
                    onVolumeChange = { onBusVolumeChange(BusType.MASTER_BUS, it) },
                    onMuteToggle = { onBusMuteToggle(BusType.MASTER_BUS) },
                    testTag = "master_bus"
                )
            }

            // 3. Monitor Loudness & Anti-Clipping Nyata
            val report = uiState.loudnessReport ?: uiState.validationResult?.let {
                com.autoremix.djslow.engine.dsp.LoudnessMeter.LoudnessReport(
                    lufsIntegrated = it.lufsIntegrated,
                    truePeakDbtp = it.truePeakDbtp,
                    peakLinear = it.peakAmplitude,
                    peakDbfs = it.peakDbfs,
                    rmsLinear = (it.peakAmplitude * 0.707f).coerceIn(0f, 1f),
                    rmsDbfs = it.rmsDbfs,
                    dynamicRangeLu = (it.peakDbfs - it.rmsDbfs).coerceAtLeast(0f),
                    isClipping = it.isClipping
                )
            }

            if (report != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = if (report.isClipping) Color(0xFF261014) else Color(0xFF091F14),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (report.isClipping) Color(0xFFFF1744) else Color(0xFF00E676)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (report.isClipping) Icons.Default.Hearing else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (report.isClipping) Color(0xFFFF1744) else Color(0xFF00E676),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (report.isClipping) "TERDETEKSI CLIPPING" else "LOUDNESS STUDIO TERVERIFIKASI",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (report.isClipping) Color(0xFFFF1744) else Color(0xFF00E676)
                                )
                            }

                            Text(
                                text = if (report.isClipping) "Batas Terlampaui" else "100% Anti-Clipping",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (report.isClipping) Color(0xFFFF1744) else Color(0xFF00E676)
                            )
                        }

                        // Metrik 4 Kolom: LUFS, True Peak, RMS, Headroom
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            LoudnessMetricBox(
                                label = "INTEGRATED",
                                value = report.formattedLufs,
                                color = NeonCyan
                            )
                            LoudnessMetricBox(
                                label = "TRUE PEAK",
                                value = report.formattedTruePeak,
                                color = if (report.truePeakDbtp > -0.2f) Color(0xFFFF5252) else Color(0xFF00E676)
                            )
                            LoudnessMetricBox(
                                label = "RMS LEVEL",
                                value = report.formattedRms,
                                color = NeonAmber
                            )
                            LoudnessMetricBox(
                                label = "PEAK LEVEL",
                                value = report.formattedPeak,
                                color = NeonPink
                            )
                        }
                    }
                }
            }

            // 4. Ringkasan Arsitektur Audio DSP Studio
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF0E131E)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "⚡ ARSITEKTUR KUALITAS AUDIO STUDIO (TAHAP 5):",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = NeonPurple
                    )
                    Text(
                        text = "• Vocal: HPF 85Hz • 3-Band Parametric EQ • Light Compressor • De-Esser • Stereo Ambience\n" +
                                "• Ducking: Sidechain Vokal halus (-3 dB) ke Bus Musik saat vokal berbunyi\n" +
                                "• Kick/Bass: Pemisahan frekuensi (Kick punch 75Hz vs Bass sub 55Hz) • HPF 30Hz • Mono Sub <140Hz\n" +
                                "• Drum Bus: Punch Compression (attack 25ms) • Snare snap 2.4kHz • Limiter Transient\n" +
                                "• Mastering: Tonal EQ • Glue Comp • Analog Saturation • Mid-Side Stereo • Brickwall Limiter",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetChip(
    preset: MasteringPreset,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .testTag("preset_${preset.name.lowercase()}"),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) NeonPink.copy(alpha = 0.25f) else StudioSurfaceElevated,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) NeonPink else Color(0xFF2A3347)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = preset.label,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.White else TextSecondary,
                maxLines = 1
            )
            Text(
                text = "${preset.targetLufs} LUFS",
                fontSize = 9.sp,
                color = if (isSelected) NeonCyan else TextMuted
            )
        }
    }
}

@Composable
private fun BusControlRow(
    label: String,
    color: Color,
    busSettings: BusSettings,
    onVolumeChange: (Float) -> Unit,
    onMuteToggle: () -> Unit,
    testTag: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF141A28)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color,
                modifier = Modifier.width(135.dp)
            )

            Slider(
                value = busSettings.volume,
                onValueChange = onVolumeChange,
                valueRange = 0.0f..1.5f,
                modifier = Modifier
                    .weight(1f)
                    .testTag("${testTag}_slider"),
                colors = SliderDefaults.colors(
                    thumbColor = color,
                    activeTrackColor = color,
                    inactiveTrackColor = Color(0xFF263044)
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onMuteToggle() }
                    .testTag("${testTag}_mute"),
                shape = RoundedCornerShape(4.dp),
                color = if (busSettings.isMuted) Color(0xFFFF5252).copy(alpha = 0.25f) else Color(0xFF1E2638),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (busSettings.isMuted) Color(0xFFFF5252) else Color(0xFF2D3850)
                )
            ) {
                Text(
                    text = if (busSettings.isMuted) "MUTED" else "ON",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (busSettings.isMuted) Color(0xFFFF5252) else TextSecondary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun LoudnessMetricBox(
    label: String,
    value: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMuted)
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = color)
    }
}
