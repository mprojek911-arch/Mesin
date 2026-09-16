package com.autoremix.djslow.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autoremix.djslow.model.AudioTrackInfo
import com.autoremix.djslow.model.PlaybackStatus
import com.autoremix.djslow.model.TrackPlaybackState
import com.autoremix.djslow.model.TrackType
import com.example.ui.theme.StudioCardBg
import com.example.ui.theme.StudioCardBorder
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun TrackControlCard(
    trackType: TrackType,
    accentColor: Color,
    trackInfo: AudioTrackInfo?,
    playbackState: TrackPlaybackState,
    onPickFile: () -> Unit,
    onAnalyze: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onSeek: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val tagPrefix = if (trackType == TrackType.VOCAL) "vocal" else "beat"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("${tagPrefix}_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = StudioCardBg),
        border = BorderStroke(1.dp, StudioCardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Bar Kartu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(accentColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = trackType.emoji,
                            fontSize = 20.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Trek ${trackType.label}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (trackInfo != null) "Berkas Siap Digunakan" else "Belum ada berkas",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (trackInfo != null) accentColor else TextMuted
                        )
                    }
                }

                // Tombol PILIH
                Button(
                    onClick = onPickFile,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("select_${tagPrefix}_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AudioFile,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "PILIH ${trackType.label.uppercase()}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Informasi Berkas
            if (trackInfo == null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = StudioSurfaceElevated
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Tekan tombol 'PILIH ${trackType.label.uppercase()}' untuk mengambil berkas audio nyata via Storage Access Framework.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                // Berkas terpilih: tampilkan metadata nyata
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = StudioSurfaceElevated,
                    border = BorderStroke(1.dp, StudioCardBorder)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = trackInfo.fileName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Format: ${trackInfo.formattedMimeType}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Text(
                                text = "Ukuran: ${trackInfo.formattedFileSize}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Durasi: ${trackInfo.formattedDuration}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            if (trackInfo.sampleRateHz != null) {
                                Text(
                                    text = "Sample: ${trackInfo.sampleRateHz} Hz",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }

                        // Status Wajib BPM & Key
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF232838),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "BPM: Belum dianalisis",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF232838),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "Key: Belum dianalisis",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }

                        // Tombol ANALISIS Dasar
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = onAnalyze,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("analyze_${tagPrefix}_button"),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "🔍 ANALISIS DASAR TEKNIS",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Detail Hasil Analisis
                        AnimatedVisibility(visible = trackInfo.isAnalyzed && trackInfo.analysisDetails != null) {
                            trackInfo.analysisDetails?.let { details ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF131722),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                ) {
                                    Text(
                                        text = details,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            lineHeight = 18.sp
                                        ),
                                        color = Color(0xFF90E0EF),
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Kontrol Audio Playback Nyata (Seek, Play, Pause, Stop, Volume)
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Slider Seek & Indikator Waktu
                    var isUserSeeking by remember { mutableStateOf(false) }
                    var seekSliderValue by remember { mutableFloatStateOf(0f) }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = playbackState.formattedCurrentPosition,
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = playbackState.formattedTotalDuration,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Slider(
                        value = if (isUserSeeking) seekSliderValue else playbackState.progressFraction,
                        onValueChange = { frac ->
                            isUserSeeking = true
                            seekSliderValue = frac
                        },
                        onValueChangeFinished = {
                            isUserSeeking = false
                            val targetMs = (seekSliderValue * playbackState.durationMs).toLong()
                            onSeek(targetMs)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("${tagPrefix}_seek_slider"),
                        colors = SliderDefaults.colors(
                            thumbColor = accentColor,
                            activeTrackColor = accentColor,
                            inactiveTrackColor = Color(0xFF2A3347)
                        )
                    )

                    // Tombol Kontrol Playback (PUTAR, JEDA, STOP)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // PUTAR (Play)
                        FilledTonalButton(
                            onClick = onPlay,
                            enabled = playbackState.status != PlaybackStatus.PLAYING,
                            modifier = Modifier.testTag("play_${tagPrefix}_button"),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (playbackState.status == PlaybackStatus.PLAYING) accentColor else Color(0xFF232A3B),
                                contentColor = if (playbackState.status == PlaybackStatus.PLAYING) Color.Black else accentColor
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Putar")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "▶ PUTAR", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // JEDA (Pause)
                        FilledTonalButton(
                            onClick = onPause,
                            enabled = playbackState.status == PlaybackStatus.PLAYING,
                            modifier = Modifier.testTag("pause_${tagPrefix}_button"),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFF232A3B),
                                contentColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Pause, contentDescription = "Jeda")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "⏸ JEDA", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // STOP
                        FilledTonalButton(
                            onClick = onStop,
                            enabled = playbackState.status == PlaybackStatus.PLAYING || playbackState.status == PlaybackStatus.PAUSED,
                            modifier = Modifier.testTag("stop_${tagPrefix}_button"),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFF232A3B),
                                contentColor = Color(0xFFFF6B6B)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Stop, contentDescription = "Stop")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "⏹ STOP", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Slider Volume
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeDown,
                            contentDescription = "Volume Rendah",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Slider(
                            value = playbackState.volume,
                            onValueChange = onVolumeChange,
                            valueRange = 0f..1f,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("${tagPrefix}_volume_slider"),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = accentColor.copy(alpha = 0.8f),
                                inactiveTrackColor = Color(0xFF2A3347)
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Volume Tinggi",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${(playbackState.volume * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.width(36.dp)
                        )
                    }
                }
            }
        }
    }
}
