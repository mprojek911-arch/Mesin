package com.autoremix.djslow.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autoremix.djslow.engine.RenderedPlaybackStatus
import com.autoremix.djslow.state.UiState
import com.autoremix.djslow.ui.theme.NeonAmber
import com.autoremix.djslow.ui.theme.NeonCyan
import com.autoremix.djslow.ui.theme.NeonPink
import com.autoremix.djslow.ui.theme.StatusError
import com.autoremix.djslow.ui.theme.StatusSuccess
import com.autoremix.djslow.ui.theme.StudioCardBg
import com.autoremix.djslow.ui.theme.StudioCardBorder
import com.autoremix.djslow.ui.theme.StudioSurfaceElevated
import com.autoremix.djslow.ui.theme.TextMuted
import com.autoremix.djslow.ui.theme.TextPrimary
import com.autoremix.djslow.ui.theme.TextSecondary
import java.util.Locale

/**
 * Monitor Playback Utama untuk Hasil Remix Full (Single Master Output).
 * Mengatur pemutaran berkas WAV final, status pemutaran, posisi audio,
 * monitor mix volume nyata, serta monitor proses render berjenjang.
 */
@Composable
fun RemixPlaybackMonitor(
    uiState: UiState,
    onPlayRendered: () -> Unit,
    onPauseRendered: () -> Unit,
    onStopRendered: () -> Unit,
    onSeekRendered: (Float) -> Unit,
    onSeekRelative: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("remix_playback_monitor_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = StudioCardBg),
        border = BorderStroke(1.dp, if (uiState.isRenderedPlaying) Color(0xFF00E676) else StudioCardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ==============================================================
            // 1. HEADER MONITOR
            // ==============================================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (uiState.isRenderedPlaying) Color(0xFF00E676).copy(alpha = 0.2f) else StudioSurfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Headphones,
                            contentDescription = "Monitor Icon",
                            tint = if (uiState.isRenderedPlaying) Color(0xFF00E676) else NeonPink,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "🎧 MASTER REMIX",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "AUTO REMIX DJ SLOW",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = NeonCyan
                        )
                    }
                }

                // Status Badge Nyata (Aturan 1)
                val status = uiState.renderedPlaybackStatus
                val (badgeColor, textColor, statusPrefix) = when (status) {
                    RenderedPlaybackStatus.SEDANG_MEMUTAR -> Triple(Color(0xFF00E676), Color.Black, "🟢 ")
                    RenderedPlaybackStatus.DIJEDA -> Triple(NeonAmber, Color.Black, "🟡 ")
                    RenderedPlaybackStatus.SIAP -> Triple(NeonCyan, Color.Black, "🔵 ")
                    RenderedPlaybackStatus.SELESAI -> Triple(Color(0xFF818CF8), Color.White, "⏹ ")
                    RenderedPlaybackStatus.ERROR -> Triple(StatusError, Color.White, "🔴 ")
                    RenderedPlaybackStatus.BERHENTI -> Triple(StudioSurfaceElevated, TextSecondary, "⏹ ")
                    RenderedPlaybackStatus.BELUM_TERSEDIA -> Triple(StudioSurfaceElevated, TextMuted, "⚪ ")
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = badgeColor,
                    modifier = Modifier.testTag("rendered_playback_status")
                ) {
                    Text(
                        text = "$statusPrefix${status.label}",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }
            }

            // Label Berkas Master
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = StudioSurfaceElevated
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = Color(0xFF00E676),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "🔊 MASTER REMIX: ${uiState.renderedWavFile?.name ?: "DJ_SLOW_MASTER.wav"}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }

                    if (uiState.isRenderedAvailable) {
                        Text(
                            text = "RIFF WAV 16-bit",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF00E676)
                        )
                    }
                }
            }

            // ==============================================================
            // 2. MONITOR RENDER (SAAT SEDANG BERJALAN - ATURAN 11)
            // ==============================================================
            AnimatedVisibility(
                visible = uiState.isRendering,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                RenderProgressCard(
                    progress = uiState.renderProgressFraction,
                    stageText = uiState.renderStageText
                )
            }

            // ==============================================================
            // 3. PANEL INFORMASI MASTER (ATURAN 4 & 12)
            // ==============================================================
            if (uiState.isRenderedAvailable && !uiState.isRendering) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0B1914),
                    border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF00E676),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "🎧 MASTER REMIX SIAP",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00E676)
                                )
                            }

                            val isValid = uiState.validationResult?.isValid != false && uiState.renderedPlaybackStatus != RenderedPlaybackStatus.ERROR
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isValid) Color(0xFF00E676).copy(alpha = 0.2f) else StatusError.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, if (isValid) Color(0xFF00E676) else StatusError)
                            ) {
                                Text(
                                    text = if (isValid) "VALID" else "TIDAK VALID",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isValid) Color(0xFF00E676) else StatusError
                                )
                            }
                        }

                        // Rincian Metadata Master Audio Sesuai Aturan 4
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "File: ${uiState.renderedWavFile?.name ?: "DJ_SLOW_MASTER.wav"}", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                Text(text = "Format: WAV 16-bit PCM", fontSize = 11.sp, color = TextSecondary)
                                Text(text = "Sample Rate: 44.1 kHz • Stereo", fontSize = 11.sp, color = TextSecondary)
                            }
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                Text(text = "Durasi: ${formatTime(uiState.renderedDurationMs)}", fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                Text(text = "Loudness: ${uiState.validationResult?.formattedLufs ?: "-14.0 LUFS"}", fontSize = 11.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                                Text(text = "True Peak: ${uiState.validationResult?.formattedTruePeak ?: "-0.80 dBTP"}", fontSize = 11.sp, color = NeonAmber, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Error Notice jika Validasi Output Gagal / Fatal Clipping (Aturan 10)
            if (uiState.renderedPlaybackStatus == RenderedPlaybackStatus.ERROR || uiState.validationResult?.isValid == false) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF330C0C),
                    border = BorderStroke(1.dp, StatusError)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = StatusError,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "🔴 AUDIO TIDAK VALID",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = StatusError
                            )
                            val errorMsg = uiState.errorMessage ?: uiState.validationResult?.errorMessage ?: "Berkas audio rusak, durasi 0, atau terdeteksi fatal clipping."
                            Text(
                                text = errorMsg,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Text(
                                text = "Pemutar dinonaktifkan untuk keamanan output. Silakan render ulang.",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    }
                }
            }

            // ==============================================================
            // 4. TIME MONITOR & PROGRESS BAR (SEEK BAR)
            // ==============================================================
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTime(uiState.renderedPositionMs),
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.isRenderedPlaying) Color(0xFF00E676) else TextPrimary,
                        modifier = Modifier.testTag("rendered_time_display")
                    )
                    Text(
                        text = formatTime(uiState.renderedDurationMs),
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )
                }

                Slider(
                    value = uiState.renderedProgressFraction,
                    onValueChange = { frac -> onSeekRendered(frac) },
                    enabled = uiState.isRenderedAvailable && !uiState.isRendering,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF00E676),
                        activeTrackColor = Color(0xFF00E676),
                        inactiveTrackColor = Color(0xFF1B2F25),
                        disabledThumbColor = TextMuted,
                        disabledActiveTrackColor = TextMuted,
                        disabledInactiveTrackColor = StudioSurfaceElevated
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rendered_seek_slider")
                )
            }

            // ==============================================================
            // 5. KONTROL PLAYBACK (↶, ▶ PUTAR, ⏸ JEDA, ⏹ STOP, ↷)
            // ==============================================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tombol ↶ Mundur 10 detik
                IconButton(
                    onClick = { onSeekRelative(-10_000L) },
                    enabled = uiState.isRenderedAvailable && !uiState.isRendering,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("seek_backward_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FastRewind,
                        contentDescription = "Mundur 10 Detik",
                        tint = if (uiState.isRenderedAvailable) TextPrimary else TextMuted,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Tombol Utama: ▶ PUTAR / ⏸ JEDA (Aturan 10: dinonaktifkan jika clipping/invalid)
                val canPlay = uiState.isRenderedAvailable && !uiState.isRendering &&
                        uiState.renderedPlaybackStatus != RenderedPlaybackStatus.ERROR &&
                        (uiState.validationResult?.isValid != false)

                if (!uiState.isRenderedPlaying) {
                    Button(
                        onClick = { onPlayRendered() },
                        enabled = canPlay,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00E676),
                            contentColor = Color.Black,
                            disabledContainerColor = StudioSurfaceElevated,
                            disabledContentColor = TextMuted
                        ),
                        modifier = Modifier
                            .size(60.dp)
                            .testTag("play_rendered_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Putar Hasil Remix",
                            modifier = Modifier.size(34.dp)
                        )
                    }
                } else {
                    Button(
                        onClick = { onPauseRendered() },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonAmber,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier
                            .size(60.dp)
                            .testTag("pause_rendered_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = "Jeda Hasil Remix",
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                // Tombol ⏹ STOP
                IconButton(
                    onClick = { onStopRendered() },
                    enabled = uiState.isRenderedAvailable && (uiState.isRenderedPlaying || uiState.isRenderedPaused),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("stop_rendered_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop Hasil Remix",
                        tint = if (uiState.isRenderedPlaying || uiState.isRenderedPaused) Color(0xFFFF6B6B) else TextMuted,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Tombol ↷ Maju 10 detik
                IconButton(
                    onClick = { onSeekRelative(10_000L) },
                    enabled = canPlay,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("seek_forward_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "Maju 10 Detik",
                        tint = if (canPlay) TextPrimary else TextMuted,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // ==============================================================
            // 6. MASTER CONTENT CHECK (ATURAN 5 - LAPISAN ELEMEN NYATA)
            // ==============================================================
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF101624),
                border = BorderStroke(1.dp, StudioCardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🎛️ ISI MASTER REMIX",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan
                        )
                        Text(
                            text = "Status Mix Engine",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }

                    // Lapisan Elemen Nyata Master Audio (Aturan 5)
                    val layers = listOf(
                        Triple("🎤", "Vocal", uiState.isVocalSelected && !uiState.vocalBusSettings.isMuted && !uiState.vocalMixSettings.isMuted && uiState.vocalMixSettings.volume > 0.05f),
                        Triple("🥁", "Beat Sumber", uiState.isBeatSelected && !uiState.beatBusSettings.isMuted && !uiState.beatMixSettings.isMuted && uiState.beatMixSettings.volume > 0.05f),
                        Triple("⚡", "Drum Synth", !uiState.drumBusSettings.isMuted && !uiState.drumMixSettings.isMuted && uiState.drumMixSettings.volume > 0.05f),
                        Triple("🎸", "Sub-Bass", !uiState.bassBusSettings.isMuted && !uiState.bassMixSettings.isMuted && uiState.bassMixSettings.volume > 0.05f),
                        Triple("🎹", "Akor Synth", !uiState.musicBusSettings.isMuted && !uiState.chordMixSettings.isMuted && uiState.chordMixSettings.volume > 0.05f),
                        Triple("🎺", "Melodi Hook", !uiState.musicBusSettings.isMuted && !uiState.melodyMixSettings.isMuted && uiState.melodyMixSettings.volume > 0.05f),
                        Triple("🌊", "Pad Atmosfer", !uiState.musicBusSettings.isMuted && !uiState.padMixSettings.isMuted && uiState.padMixSettings.volume > 0.05f),
                        Triple("⚡", "FX Transisi", !uiState.musicBusSettings.isMuted && !uiState.fxMixSettings.isMuted && uiState.fxMixSettings.volume > 0.05f)
                    )

                    // Tampilkan Grid 2 Kolom untuk kejelasan visual
                    for (i in layers.indices step 2) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MasterLayerBadge(
                                icon = layers[i].first,
                                name = layers[i].second,
                                isActive = layers[i].third,
                                modifier = Modifier.weight(1f)
                            )
                            if (i + 1 < layers.size) {
                                MasterLayerBadge(
                                    icon = layers[i + 1].first,
                                    name = layers[i + 1].second,
                                    isActive = layers[i + 1].third,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // ==============================================================
            // 7. SKEMA AUDIO: MIX BUS KE MASTER (ATURAN 6)
            // ==============================================================
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF0C101A),
                border = BorderStroke(1.dp, Color(0xFF1E283D))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "🛣️ PERJALANAN AUDIO (MIX BUS ➔ MASTER)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = NeonPink
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF070A12)
                    ) {
                        Text(
                            text = """
VOCAL  ───────┐
BEAT   ───────┤
DRUM   ───────┤
BASS   ───────┤
AKOR   ───────┤ ──> [ MIX ENGINE ] ──> [ MASTER BUS ] ──> [ MASTER WAV ] ──> ▶ PLAY
MELODI ───────┤
PAD    ───────┤
FX     ───────┘
                            """.trimIndent(),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            lineHeight = 13.sp,
                            color = NeonCyan,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Baris monitor level audio visual dengan nilai nyata.
 */
@Composable
private fun MixLevelItem(
    icon: String,
    label: String,
    fraction: Float,
    percentageText: String,
    activeColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.width(72.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = icon, fontSize = 12.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }

        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = activeColor,
            trackColor = StudioCardBg
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = percentageText,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier.width(36.dp)
        )
    }
}

@Composable
private fun MasterLayerBadge(
    icon: String,
    name: String,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = if (isActive) Color(0xFF132A1C) else StudioSurfaceElevated,
        border = BorderStroke(1.dp, if (isActive) Color(0xFF00E676).copy(alpha = 0.5f) else StudioCardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = icon, fontSize = 12.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isActive) TextPrimary else TextMuted
                )
            }
            Text(
                text = if (isActive) "✓ AKTIF" else "○ NONAKTIF",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isActive) Color(0xFF00E676) else TextMuted
            )
        }
    }
}

/**
 * Monitor status render berjenjang sesuai Aturan 11.
 * Tahapan: 1. MIXING, 2. MASTERING, 3. RENDER WAV, 4. VALIDASI, 5. SIAP DIPUTAR
 */
@Composable
private fun RenderProgressCard(
    progress: Float,
    stageText: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF09141D),
        border = BorderStroke(1.dp, NeonAmber.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🟡 MERENDER MASTER...",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = NeonAmber
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00E676)
                )
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = Color(0xFF00E676),
                trackColor = Color(0xFF132A20)
            )

            // 5 Tahap Nyata sesuai Aturan 11
            val stages = listOf(
                "1. MIXING",
                "2. MASTERING",
                "3. RENDER WAV",
                "4. VALIDASI",
                "5. SIAP DIPUTAR"
            )

            // Kalkulasi tahap aktif berdasarkan fraksi progres (0..1)
            val currentStageIndex = when {
                progress < 0.25f -> 0
                progress < 0.50f -> 1
                progress < 0.80f -> 2
                progress < 0.95f -> 3
                else -> 4
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                stages.forEachIndexed { index, _ ->
                    val isDone = index < currentStageIndex
                    val isCurrent = index == currentStageIndex
                    val dotColor = when {
                        isDone -> Color(0xFF00E676)
                        isCurrent -> NeonCyan
                        else -> TextMuted.copy(alpha = 0.4f)
                    }
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                }
            }

            Text(
                text = "Tahap: ${stages.getOrElse(currentStageIndex) { "PROSES" }} • $stageText",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format(Locale.US, "%02d:%02d", min, sec)
}
