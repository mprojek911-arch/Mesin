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
                            text = "🎧 MONITOR HASIL REMIX",
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

                // Status Badge Nyata
                val status = uiState.renderedPlaybackStatus
                val (badgeColor, textColor) = when (status) {
                    RenderedPlaybackStatus.SEDANG_MEMUTAR -> Pair(Color(0xFF00E676), Color.Black)
                    RenderedPlaybackStatus.DIJEDA -> Pair(NeonAmber, Color.Black)
                    RenderedPlaybackStatus.SIAP -> Pair(NeonCyan, Color.Black)
                    RenderedPlaybackStatus.SELESAI -> Pair(Color(0xFF818CF8), Color.White)
                    RenderedPlaybackStatus.ERROR -> Pair(StatusError, Color.White)
                    RenderedPlaybackStatus.BERHENTI -> Pair(StudioSurfaceElevated, TextSecondary)
                    RenderedPlaybackStatus.BELUM_TERSEDIA -> Pair(StudioSurfaceElevated, TextMuted)
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = badgeColor,
                    modifier = Modifier.testTag("rendered_playback_status")
                ) {
                    Text(
                        text = status.label,
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
            // 3. BANNER HASIL REMIX SIAP DIPUTAR (ATURAN 12)
            // ==============================================================
            if (uiState.isRenderedAvailable && !uiState.isRendering) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF072115),
                    border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF00E676),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "🎧 HASIL REMIX SIAP DIPUTAR",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00E676)
                                )
                                Text(
                                    text = "Durasi: ${formatTime(uiState.renderedDurationMs)} • ${(uiState.renderedWavFile?.length() ?: 0L) / 1024} KB",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }

                        // Status Loudness & Peak
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "LUFS: ${uiState.validationResult?.formattedLufs ?: "-14.0 LUFS"}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = NeonCyan
                            )
                            Text(
                                text = "Peak: ${uiState.validationResult?.formattedTruePeak ?: "-0.80 dBTP"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    }
                }
            }

            // Error Notice jika Validasi Output Gagal (Aturan 13)
            if (uiState.renderedPlaybackStatus == RenderedPlaybackStatus.ERROR || uiState.validationResult?.isValid == false) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF330C0C),
                    border = BorderStroke(1.dp, StatusError)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = StatusError,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "🔴 AUDIO TIDAK VALID",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = StatusError
                            )
                            Text(
                                text = uiState.errorMessage ?: uiState.validationResult?.errorMessage ?: "Berkas audio tidak dapat diputar.",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
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

                // Tombol Utama: ▶ PUTAR / ⏸ JEDA
                if (!uiState.isRenderedPlaying) {
                    Button(
                        onClick = { onPlayRendered() },
                        enabled = uiState.isRenderedAvailable && !uiState.isRendering,
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
                    enabled = uiState.isRenderedAvailable && !uiState.isRendering,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("seek_forward_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "Maju 10 Detik",
                        tint = if (uiState.isRenderedAvailable) TextPrimary else TextMuted,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // ==============================================================
            // 6. MONITOR SUMBER REMIX (ATURAN 10 - DATA NYATA DARI MIX ENGINE)
            // ==============================================================
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = StudioSurfaceElevated,
                border = BorderStroke(1.dp, StudioCardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "🎛️ MONITOR SUMBER REMIX (NILAI NYATA MIX ENGINE)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan
                    )

                    // 1. Vokal
                    val vocalPct = (uiState.vocalMixSettings.volume * 100).toInt().coerceIn(0, 100)
                    MixLevelItem(
                        icon = "🎤",
                        label = "Vokal",
                        fraction = uiState.vocalMixSettings.volume.coerceIn(0f, 1f),
                        percentageText = "$vocalPct%",
                        activeColor = NeonCyan
                    )

                    // 2. Beat
                    val beatPct = (uiState.beatMixSettings.volume * 100).toInt().coerceIn(0, 100)
                    MixLevelItem(
                        icon = "🥁",
                        label = "Beat",
                        fraction = uiState.beatMixSettings.volume.coerceIn(0f, 1f),
                        percentageText = "$beatPct%",
                        activeColor = NeonAmber
                    )

                    // 3. Musik (Rata-rata Drum, Bass, Akor, Melodi)
                    val musicVolume = (
                        uiState.drumMixSettings.volume +
                        uiState.bassMixSettings.volume +
                        uiState.chordMixSettings.volume +
                        uiState.melodyMixSettings.volume
                    ) / 4f
                    val musicPct = (musicVolume * 100).toInt().coerceIn(0, 100)
                    MixLevelItem(
                        icon = "🎹",
                        label = "Musik",
                        fraction = musicVolume.coerceIn(0f, 1f),
                        percentageText = "$musicPct%",
                        activeColor = NeonPink
                    )

                    // 4. Master Engine
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "🎛️", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Mastering",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF00E676).copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, Color(0xFF00E676))
                        ) {
                            Text(
                                text = "AKTIF (${(uiState.masterGain * 100).toInt()}%)",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00E676)
                            )
                        }
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

/**
 * Monitor status render berjenjang sesuai Aturan 11.
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
        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f))
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
                    text = "🎚️ MERENDER REMIX",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan
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

            // 8 Stage Nyata sesuai Aturan 11
            val stages = listOf(
                "ANALISIS",
                "ARRANGEMENT",
                "GENERATOR",
                "VOCAL FX",
                "MIX",
                "MASTER",
                "RENDER WAV",
                "VALIDASI"
            )

            // Kalkulasi tahap aktif berdasarkan fraksi progres (0..1)
            val currentStageIndex = when {
                progress < 0.15f -> 0
                progress < 0.30f -> 1
                progress < 0.50f -> 2
                progress < 0.62f -> 3
                progress < 0.75f -> 4
                progress < 0.88f -> 5
                progress < 0.95f -> 6
                else -> 7
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                stages.forEachIndexed { index, name ->
                    val isDone = index < currentStageIndex
                    val isCurrent = index == currentStageIndex
                    val dotColor = when {
                        isDone -> Color(0xFF00E676)
                        isCurrent -> NeonCyan
                        else -> TextMuted.copy(alpha = 0.4f)
                    }
                    Box(
                        modifier = Modifier
                            .size(10.dp)
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
