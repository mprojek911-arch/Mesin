package com.autoremix.djslow.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import com.autoremix.djslow.ui.components.AnalysisResultCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.PaddingValues
import com.autoremix.djslow.logchat.LogChatScreen
import androidx.compose.ui.Alignment
import com.autoremix.djslow.engine.structure.SongSection
import com.autoremix.djslow.engine.structure.SongSectionType
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autoremix.djslow.engine.AudioSource
import com.autoremix.djslow.engine.BeatTrackState
import com.autoremix.djslow.engine.PlaybackEngineState
import com.autoremix.djslow.engine.VocalTrackState
import com.autoremix.djslow.ui.components.ArrangementCard
import com.autoremix.djslow.ui.components.MasteringCard
import com.autoremix.djslow.ui.components.MusicEngineCard
import com.autoremix.djslow.ui.components.RemixPlaybackMonitor
import com.autoremix.djslow.viewmodel.MainViewModel
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPink
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.StatusError
import com.example.ui.theme.StudioCardBg
import com.example.ui.theme.StudioDarkBg
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TrackBeatColor
import com.example.ui.theme.TrackVocalColor

@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // SAF Audio Pickers (Mendukung MP3, WAV, M4A, AAC)
    val vocalPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.onVocalSelected(context, it) }
    }

    val beatPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.onBeatSelected(context, it) }
    }

    var showLogChat by remember { mutableStateOf(false) }

    if (showLogChat) {
        LogChatScreen(
            onDismiss = { showLogChat = false },
            modifier = modifier
        )
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(StudioDarkBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Banner Aplikasi
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("app_header_card"),
                colors = CardDefaults.cardColors(containerColor = StudioCardBg),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(NeonCyan, NeonPink)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "AUTO REMIX DJ SLOW",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "STUDIO PRODUKSI MUSIK & AUDIO NYATA",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NeonCyan,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Tombol 🛠️ LOGCHAT
                            OutlinedButton(
                                onClick = { showLogChat = true },
                                modifier = Modifier
                                    .height(36.dp)
                                    .testTag("open_logchat_button"),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.8f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan)
                            ) {
                                Text(
                                    text = "🛠️ LOGCHAT",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonCyan
                                )
                            }

                            // Badge Tahap 4
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(NeonPurple.copy(alpha = 0.2f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "TAHAP 4: ARANSEMEN",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonPurple
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Status Bar Engine Audio
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isEngineActive = uiState.hasAnyAudio
                        val engineBg = if (isEngineActive) Color(0xFF0F3022) else Color(0xFF1E2433)
                        val engineFg = if (isEngineActive) Color(0xFF00E676) else TextMuted

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(engineBg)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (isEngineActive) "AUDIO ENGINE AKTIF (44.1 kHz)" else "MENUNGGU FILE AUDIO",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = engineFg
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(engineBg)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = uiState.audioState.playbackState.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = engineFg
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Badge Vokal
                        val vocalIsReady = uiState.audioState.vocalState == VocalTrackState.VOKAL_DIPILIH
                        val vocalBg = if (vocalIsReady) TrackVocalColor.copy(alpha = 0.2f) else Color(0xFF22283A)
                        val vocalFg = if (vocalIsReady) TrackVocalColor else TextMuted
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(vocalBg)
                                .padding(vertical = 6.dp, horizontal = 10.dp)
                        ) {
                            Text(
                                text = "🎤 ${uiState.audioState.vocalState.label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = vocalFg,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Badge Beat
                        val beatIsReady = uiState.audioState.beatState == BeatTrackState.BEAT_DIPILIH
                        val beatBg = if (beatIsReady) TrackBeatColor.copy(alpha = 0.2f) else Color(0xFF22283A)
                        val beatFg = if (beatIsReady) TrackBeatColor else TextMuted
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(beatBg)
                                .padding(vertical = 6.dp, horizontal = 10.dp)
                        ) {
                            Text(
                                text = "🥁 ${uiState.audioState.beatState.label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = beatFg,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = uiState.statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.testTag("status_message_text")
                    )
                }
            }

            // Error Banner
            AnimatedVisibility(visible = uiState.errorMessage != null) {
                uiState.errorMessage?.let { errText ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("error_banner"),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF3B1217)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Peringatan",
                                tint = StatusError,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = errText,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFFFB4AB),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.clearError() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Tutup Error",
                                    tint = TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 1. [ 🎤 PILIH VOKAL ]
            Button(
                onClick = { vocalPickerLauncher.launch("audio/*") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("select_vocal_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TrackVocalColor,
                    contentColor = Color.Black
                )
            ) {
                Icon(imageVector = Icons.Default.Mic, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "🎤 PILIH VOKAL",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Tampilan Metadata Vokal
            if (uiState.vocalSource != null) {
                TrackMetadataCard(
                    title = "METADATA VOKAL",
                    source = uiState.vocalSource!!,
                    accentColor = TrackVocalColor,
                    volume = uiState.audioState.vocalVolume,
                    onVolumeChange = { viewModel.onVocalVolumeChange(it) },
                    testTagPrefix = "vocal"
                )
            }

            // 2. [ 🥁 PILIH BEAT ]
            Button(
                onClick = { beatPickerLauncher.launch("audio/*") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("select_beat_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TrackBeatColor,
                    contentColor = Color.White
                )
            ) {
                Icon(imageVector = Icons.Default.MusicNote, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "🥁 PILIH BEAT",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Tampilan Metadata Beat
            if (uiState.beatSource != null) {
                TrackMetadataCard(
                    title = "METADATA BEAT",
                    source = uiState.beatSource!!,
                    accentColor = TrackBeatColor,
                    volume = uiState.audioState.beatVolume,
                    onVolumeChange = { viewModel.onBeatVolumeChange(it) },
                    testTagPrefix = "beat"
                )
            }

            // KONTROL PLAYBACK SUMBER NYATA (Vokal & Beat Pratinjau)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("playback_controls_card"),
                colors = CardDefaults.cardColors(containerColor = StudioCardBg),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "KONTROL PEMUTARAN SUMBER NYATA",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(uiState.currentPositionMs),
                            fontSize = 12.sp,
                            color = NeonCyan
                        )
                        Text(
                            text = formatTime(uiState.maxDurationMs),
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }

                    Slider(
                        value = uiState.playbackProgress,
                        onValueChange = { frac -> viewModel.onSeek(frac) },
                        enabled = uiState.hasAnyAudio,
                        colors = SliderDefaults.colors(
                            thumbColor = NeonCyan,
                            activeTrackColor = NeonCyan,
                            inactiveTrackColor = StudioSurfaceElevated
                        ),
                        modifier = Modifier.testTag("playback_seek_slider")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.onPlay() },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("play_button"),
                            shape = RoundedCornerShape(10.dp),
                            enabled = uiState.hasAnyAudio,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonCyan,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "▶ PUTAR", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.onPause() },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("pause_button"),
                            shape = RoundedCornerShape(10.dp),
                            enabled = uiState.hasAnyAudio,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StudioSurfaceElevated,
                                contentColor = TextPrimary
                            )
                        ) {
                            Icon(imageVector = Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "⏸ JEDA", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.onStop() },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("stop_button"),
                            shape = RoundedCornerShape(10.dp),
                            enabled = uiState.hasAnyAudio,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StudioSurfaceElevated,
                                contentColor = Color(0xFFFF6B6B)
                            )
                        ) {
                            Icon(imageVector = Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "⏹ STOP", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ==========================================
            // SELECTOR MODE PENGGUNA: MODE CEPAT vs MODE STUDIO
            // ==========================================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("mode_selector_card"),
                colors = CardDefaults.cardColors(containerColor = StudioCardBg),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val isQuick = uiState.isQuickMode
                    Button(
                        onClick = { if (!isQuick) viewModel.onToggleQuickMode() },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("mode_cepat_tab"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isQuick) NeonCyan else Color.Transparent,
                            contentColor = if (isQuick) Color.Black else TextPrimary
                        )
                    ) {
                        Text("⚡ MODE CEPAT (INSTAN)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Button(
                        onClick = { if (isQuick) viewModel.onToggleQuickMode() },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("mode_studio_tab"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!isQuick) NeonPurple else Color.Transparent,
                            contentColor = if (!isQuick) Color.White else TextPrimary
                        )
                    ) {
                        Text("🎛️ MODE STUDIO (ADVANCED)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            if (uiState.isQuickMode) {
                // TAMPILAN MODE CEPAT (INSTAN)
                QuickModeCard(
                    uiState = uiState,
                    viewModel = viewModel,
                    context = context
                )
            } else {
                // TAMPILAN MODE STUDIO (DETAIL & ADVANCED)
            // ==========================================
            // 3. [ 🔍 ANALISIS AUDIO NYATA ] (Tahap 3)
            // ==========================================
            Button(
                onClick = { viewModel.onAnalyzeTracks(context) },
                enabled = uiState.hasAnyAudio && !uiState.isAnalyzing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("analysis_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonPurple,
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFF1E2433),
                    disabledContentColor = Color(0xFF6C7A9C)
                )
            ) {
                if (uiState.isAnalyzing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = "MENGANALISIS AUDIO...", fontWeight = FontWeight.Bold)
                } else {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "🔍 ANALISIS (BPM + KEY + CHORD)", fontWeight = FontWeight.Bold)
                }
            }

            // Indikator Progres Analisis
            if (uiState.isAnalyzing) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { uiState.analysisProgressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .testTag("analysis_progress_bar"),
                        color = NeonPurple,
                        trackColor = StudioSurfaceElevated
                    )
                    Text(
                        text = uiState.analysisMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonCyan
                    )
                }
            }

            // ==========================================
            // FASE 1: HASIL ANALISIS NYATA (SAYA MENEMUKAN)
            // ==========================================
            AnalysisResultCard(uiState = uiState)

            // ==========================================
            // MESIN MUSIK TAHAP 3 (BPM + KEY + CHORD + BASS)
            // ==========================================
            MusicEngineCard(
                uiState = uiState,
                onBpmIncrement = { viewModel.onBpmIncrement() },
                onBpmDecrement = { viewModel.onBpmDecrement() },
                onBpmPresetSelected = { viewModel.onBpmPresetSelected(it) },
                onKeySelected = { pitch, mode -> viewModel.onKeySelected(pitch, mode) },
                onChordPresetChanged = { viewModel.onChordPresetChanged(it) },
                onBassPatternChanged = { viewModel.onBassPatternChanged(it) }
            )

            // ==========================================
            // MESIN ARANSEMEN TAHAP 4 (DRUM + MELODY + PAD + DJ STRUCTURE)
            // ==========================================
            ArrangementCard(
                uiState = uiState,
                onPresetChanged = { viewModel.onPresetChanged(it) },
                onRegenerateMelodySeed = { viewModel.onRegenerateMelodySeed() }
            )

            // ==========================================
            // AUTO MASTERING & MIX BUS (TAHAP 5)
            // ==========================================
            MasteringCard(
                uiState = uiState,
                onMasteringPresetChanged = { viewModel.onMasteringPresetChanged(it) },
                onBusVolumeChange = { bus, vol -> viewModel.onBusVolumeChange(bus, vol) },
                onBusMuteToggle = { bus -> viewModel.onBusMuteToggle(bus) }
            )

            // ==========================================
            // MIX ENGINE & MASTER BUS (4-TRACK MIXING)
            // ==========================================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("mix_section")
                    .border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(14.dp)),
                colors = CardDefaults.cardColors(containerColor = StudioCardBg),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "🎚️ MIX ENGINE MULTI-TREK & MASTER BUS",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "PCM Float32 (44.1 kHz) • Vokal, Beat, Drum, Bass, Akor, Melodi, Pad • Auto Mix",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    }

                    // 1. Track Mixer: 🎤 VOKAL
                    TrackMixerRow(
                        trackName = "🎤 VOKAL",
                        accentColor = TrackVocalColor,
                        volume = uiState.vocalMixSettings.volume,
                        isMuted = uiState.vocalMixSettings.isMuted,
                        isSolo = uiState.vocalMixSettings.isSolo,
                        onVolumeChange = { viewModel.onVocalMixVolumeChange(it) },
                        onMuteToggle = { viewModel.onVocalMuteToggle() },
                        onSoloToggle = { viewModel.onVocalSoloToggle() },
                        testTagPrefix = "vocal"
                    )

                    // 2. Track Mixer: 🥁 BEAT SUMBER
                    TrackMixerRow(
                        trackName = "🥁 BEAT SUMBER",
                        accentColor = TrackBeatColor,
                        volume = uiState.beatMixSettings.volume,
                        isMuted = uiState.beatMixSettings.isMuted,
                        isSolo = uiState.beatMixSettings.isSolo,
                        onVolumeChange = { viewModel.onBeatMixVolumeChange(it) },
                        onMuteToggle = { viewModel.onBeatMuteToggle() },
                        onSoloToggle = { viewModel.onBeatSoloToggle() },
                        testTagPrefix = "beat"
                    )

                    // 3. Track Mixer: 💥 DRUM SYNTH
                    TrackMixerRow(
                        trackName = "💥 DRUM SYNTH (DJ Slow)",
                        accentColor = NeonAmber,
                        volume = uiState.drumMixSettings.volume,
                        isMuted = uiState.drumMixSettings.isMuted,
                        isSolo = uiState.drumMixSettings.isSolo,
                        onVolumeChange = { viewModel.onDrumMixVolumeChange(it) },
                        onMuteToggle = { viewModel.onDrumMuteToggle() },
                        onSoloToggle = { viewModel.onDrumSoloToggle() },
                        testTagPrefix = "drum"
                    )

                    // 4. Track Mixer: 🎸 SUB-BASS (Sidechained)
                    TrackMixerRow(
                        trackName = "🎸 SUB-BASS (Sidechained)",
                        accentColor = Color(0xFFFF5252),
                        volume = uiState.bassMixSettings.volume,
                        isMuted = uiState.bassMixSettings.isMuted,
                        isSolo = uiState.bassMixSettings.isSolo,
                        onVolumeChange = { viewModel.onBassMixVolumeChange(it) },
                        onMuteToggle = { viewModel.onBassMuteToggle() },
                        onSoloToggle = { viewModel.onBassSoloToggle() },
                        testTagPrefix = "bass"
                    )

                    // 5. Track Mixer: 🎹 AKOR SYNTH
                    TrackMixerRow(
                        trackName = "🎹 AKOR SYNTH",
                        accentColor = NeonPurple,
                        volume = uiState.chordMixSettings.volume,
                        isMuted = uiState.chordMixSettings.isMuted,
                        isSolo = uiState.chordMixSettings.isSolo,
                        onVolumeChange = { viewModel.onChordMixVolumeChange(it) },
                        onMuteToggle = { viewModel.onChordMuteToggle() },
                        onSoloToggle = { viewModel.onChordSoloToggle() },
                        testTagPrefix = "chord"
                    )

                    // 6. Track Mixer: 🎺 MELODI HOOK
                    TrackMixerRow(
                        trackName = "🎺 MELODI HOOK",
                        accentColor = NeonPink,
                        volume = uiState.melodyMixSettings.volume,
                        isMuted = uiState.melodyMixSettings.isMuted,
                        isSolo = uiState.melodyMixSettings.isSolo,
                        onVolumeChange = { viewModel.onMelodyMixVolumeChange(it) },
                        onMuteToggle = { viewModel.onMelodyMuteToggle() },
                        onSoloToggle = { viewModel.onMelodySoloToggle() },
                        testTagPrefix = "melody"
                    )

                    // 7. Track Mixer: 🌊 PAD ATMOSFIR
                    TrackMixerRow(
                        trackName = "🌊 PAD ATMOSFIR",
                        accentColor = NeonCyan,
                        volume = uiState.padMixSettings.volume,
                        isMuted = uiState.padMixSettings.isMuted,
                        isSolo = uiState.padMixSettings.isSolo,
                        onVolumeChange = { viewModel.onPadMixVolumeChange(it) },
                        onMuteToggle = { viewModel.onPadMuteToggle() },
                        onSoloToggle = { viewModel.onPadSoloToggle() },
                        testTagPrefix = "pad"
                    )

                    // 8. Track Mixer: ⚡ FX TRANSISI / RISER
                    TrackMixerRow(
                        trackName = "⚡ FX TRANSISI / RISER",
                        accentColor = NeonAmber,
                        volume = uiState.fxMixSettings.volume,
                        isMuted = uiState.fxMixSettings.isMuted,
                        isSolo = uiState.fxMixSettings.isSolo,
                        onVolumeChange = { viewModel.onFxMixVolumeChange(it) },
                        onMuteToggle = { viewModel.onFxMuteToggle() },
                        onSoloToggle = { viewModel.onFxSoloToggle() },
                        testTagPrefix = "fx"
                    )

                    // --- Master Bus Controls ---
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = StudioSurfaceElevated
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🎛️ MASTER GAIN",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${(uiState.masterGain * 100).toInt()}% (-0.5 dB Headroom Limiter)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonCyan
                                )
                            }

                            Slider(
                                value = uiState.masterGain,
                                onValueChange = { viewModel.onMasterGainChange(it) },
                                valueRange = 0.0f..1.5f,
                                colors = SliderDefaults.colors(
                                    thumbColor = NeonCyan,
                                    activeTrackColor = NeonCyan,
                                    inactiveTrackColor = Color(0xFF2C3446)
                                ),
                                modifier = Modifier.testTag("master_gain_slider")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Auto Mix (Vokal Dominan)",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Musik pengiring diskala otomatis agar vokal jernih & bebas clipping",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextMuted
                                    )
                                }

                                Switch(
                                    checked = uiState.isAutoMixEnabled,
                                    onCheckedChange = { viewModel.onAutoMixToggle() },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = NeonCyan,
                                        checkedTrackColor = NeonCyan.copy(alpha = 0.3f),
                                        uncheckedThumbColor = TextMuted,
                                        uncheckedTrackColor = Color(0xFF1E2433)
                                    ),
                                    modifier = Modifier.testTag("auto_mix_toggle")
                                )
                            }
                        }
                    }

                    // --- Tombol [ 💾 SIMPAN & RENDER MASTER WAV ] ---
                    Button(
                        onClick = { viewModel.startMixAndRender(context) },
                        enabled = uiState.hasAnyAudio && !uiState.isRendering,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("mix_and_render_button")
                            .testTag("save_wav_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (uiState.hasAnyAudio) NeonPink else Color(0xFF22283A),
                            contentColor = Color.White,
                            disabledContainerColor = Color(0xFF1E2433),
                            disabledContentColor = Color(0xFF6C7A9C)
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (uiState.isRendering) "SEDANG MASTERING & MERENDER WAV..." else "💾 SIMPAN & RENDER MASTER (WAV)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Progress Bar Rendering Latar Belakang Nyata
                    if (uiState.isRendering) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = { uiState.renderProgressFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .testTag("render_progress_bar"),
                                color = NeonPink,
                                trackColor = StudioSurfaceElevated
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = uiState.renderStageText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NeonCyan
                                )
                                Text(
                                    text = "${(uiState.renderProgressFraction * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                            OutlinedButton(
                                onClick = { viewModel.cancelRender(context) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .testTag("cancel_render_button"),
                                border = BorderStroke(1.dp, Color(0xFFFF5252)),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252))
                            ) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "BATALKAN RENDER & BERSIHKAN FILE SEMENTARA",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // ==========================================
            // MONITOR PLAYBACK HASIL REMIX FULL
            // ==========================================
            if (uiState.isRenderedAvailable || uiState.isRendering) {
                RemixPlaybackMonitor(
                    uiState = uiState,
                    onPlayRendered = { viewModel.onPlayRendered() },
                    onPauseRendered = { viewModel.onPauseRendered() },
                    onStopRendered = { viewModel.onStopRendered() },
                    onSeekRendered = { frac -> viewModel.onSeekRendered(frac) },
                    onSeekRelative = { deltaMs -> viewModel.onSeekRelative(deltaMs) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (uiState.isRenderedAvailable) {
                // ==========================================
                // TAHAP 6 FINAL: A/B PREVIEW & SECTION AUDITION
                // ==========================================
                AbPreviewCard(
                    uiState = uiState,
                    onSwitchAbMode = { viewModel.onSwitchAbMode(it) },
                    onToggleLoudnessMatching = { viewModel.onToggleLoudnessMatching() },
                    onPreviewSection = { sec, dur -> viewModel.onPreviewSection(sec, dur) }
                )

                // ==========================================
                // TAHAP 6 FINAL: EKSPOR MEDIASTORE WAV, MP3 & BAGIKAN
                // ==========================================
                ExportCard(
                    uiState = uiState,
                    onExportWav = { viewModel.exportWavToMediaStore(context) },
                    onExportMp3 = { viewModel.exportMp3(context) },
                    onShare = { viewModel.shareAudio(context) }
                )
            }

            // ==========================================
            // TAHAP 6 FINAL: SIMPAN & MUAT PROYEK OFFLINE
            // ==========================================
            ProjectPersistenceCard(
                uiState = uiState,
                onSaveProject = { viewModel.saveCurrentProject(context) },
                onLoadProject = { viewModel.loadSavedProject(context) }
            )

            // 4. [ 🎧 AUTO REMIX DJ SLOW ] (Tahap 3: Menjalankan seluruh alur remix otomatis)
            Button(
                onClick = { viewModel.startMixAndRender(context) },
                enabled = uiState.hasAnyAudio && !uiState.isRendering,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("auto_remix_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF007F),
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFF1E2433),
                    disabledContentColor = Color(0xFF6C7A9C)
                )
            ) {
                Icon(imageVector = Icons.Default.Headphones, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "🎧 AUTO REMIX DJ SLOW", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }

            // 5. [ 💾 HASIL ] (Status Berkas Render)
            Button(
                onClick = {
                    if (uiState.isRenderedAvailable) {
                        viewModel.onPlayRendered()
                    }
                },
                enabled = uiState.isRenderedAvailable,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("results_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E676),
                    contentColor = Color.Black,
                    disabledContainerColor = Color(0xFF1E2433),
                    disabledContentColor = Color(0xFF6C7A9C)
                )
            ) {
                Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (uiState.isRenderedAvailable) "💾 HASIL: SIAP DIPUTAR (${formatTime(uiState.renderedDurationMs)})" else "💾 HASIL",
                    fontWeight = FontWeight.SemiBold
                )
                if (!uiState.isRenderedAvailable) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "(Render WAV dahulu)", fontSize = 11.sp, color = TextMuted)
                }
            }

            // Catatan Disiplin Arsitektur Tahap 3
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF10131B)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Arsitektur TAHAP 3 — MUSIK NYATA (BPM + KEY + CHORD + BASS)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "• BPM Engine: Novelty curve & Autocorrelation DSP murni.\n" +
                                "• Key Engine: 12-semitone Chroma & Krumhansl-Schmuckler profile.\n" +
                                "• Chord Engine & Synth: Progresi diatonik 4-bar disintesis ke PCM Float32 (Piano/Pad/Synth) dengan ADSR envelope.\n" +
                                "• Bass Engine: Irama sub-bass punch & sinkopasi DJ Slow nyata.\n" +
                                "• Master Timeline: Mengunci detak ritme 44.1 kHz untuk mixing 4-trek dan render WAV 16-bit.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        lineHeight = 16.sp
                    )
                }
            }
            } // Tutup blok else untuk Mode Studio
        }
    }
}

/**
 * Baris kontrol mixer per-trek: Volume Slider, Mute, Solo.
 */
@Composable
fun TrackMixerRow(
    trackName: String,
    accentColor: Color,
    volume: Float,
    isMuted: Boolean,
    isSolo: Boolean,
    onVolumeChange: (Float) -> Unit,
    onMuteToggle: () -> Unit,
    onSoloToggle: () -> Unit,
    testTagPrefix: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = StudioSurfaceElevated
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(accentColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = trackName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Tombol MUTE
                    FilledTonalButton(
                        onClick = onMuteToggle,
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("${testTagPrefix}_mute_button"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isMuted) StatusError else Color(0xFF22283A),
                            contentColor = if (isMuted) Color.White else TextMuted
                        )
                    ) {
                        Text(
                            text = if (isMuted) "MUTED" else "MUTE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Tombol SOLO
                    FilledTonalButton(
                        onClick = onSoloToggle,
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("${testTagPrefix}_solo_button"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isSolo) Color(0xFFFFB300) else Color(0xFF22283A),
                            contentColor = if (isSolo) Color.Black else TextMuted
                        )
                    ) {
                        Text(
                            text = if (isSolo) "SOLO" else "SOLO",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                    contentDescription = null,
                    tint = if (isMuted) StatusError else accentColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Slider(
                    value = volume,
                    onValueChange = onVolumeChange,
                    valueRange = 0.0f..1.5f,
                    enabled = !isMuted,
                    colors = SliderDefaults.colors(
                        thumbColor = accentColor,
                        activeTrackColor = accentColor,
                        inactiveTrackColor = Color(0xFF2C3446)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("${testTagPrefix}_mix_volume_slider")
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${(volume * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isMuted) TextMuted else TextPrimary,
                    modifier = Modifier.width(42.dp)
                )
            }
        }
    }
}

/**
 * Kartu Penampil Metadata Audio Nyata:
 * Nama file, Format, Durasi, Sample rate jika tersedia, Channel jika tersedia, dan slider Volume dasar.
 */
@Composable
fun TrackMetadataCard(
    title: String,
    source: AudioSource,
    accentColor: Color,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    testTagPrefix: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("${testTagPrefix}_metadata_card")
            .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = StudioCardBg),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )

                // Badge Format Ekstensi
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(accentColor.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = source.formattedFormat,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        modifier = Modifier.testTag("${testTagPrefix}_format_badge")
                    )
                }
            }

            // Nama File Nyata
            Text(
                text = source.fileName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier.testTag("${testTagPrefix}_file_name_text")
            )

            // Grid Metadata: Durasi, Sample Rate, Channel, Ukuran
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = "DURASI", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text(
                        text = source.formattedDuration,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.testTag("${testTagPrefix}_duration_text")
                    )
                }

                Column {
                    Text(text = "SAMPLE RATE", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text(
                        text = source.formattedSampleRate,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.testTag("${testTagPrefix}_samplerate_text")
                    )
                }

                Column {
                    Text(text = "CHANNEL", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text(
                        text = source.formattedChannels,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.testTag("${testTagPrefix}_channels_text")
                    )
                }

                Column {
                    Text(text = "UKURAN", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Text(
                        text = source.formattedFileSize,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.testTag("${testTagPrefix}_filesize_text")
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Penggeser Volume Trek
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = "Volume",
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Slider(
                    value = volume,
                    onValueChange = onVolumeChange,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("${testTagPrefix}_volume_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = accentColor,
                        activeTrackColor = accentColor,
                        inactiveTrackColor = StudioSurfaceElevated
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${(volume * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.width(36.dp)
                )
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format("%02d:%02d", min, sec)
}

// ==========================================
// TAHAP 6: A/B PREVIEW & SECTION AUDITION
// ==========================================
@Composable
fun AbPreviewCard(
    uiState: com.autoremix.djslow.state.UiState,
    onSwitchAbMode: (com.autoremix.djslow.engine.preview.AbPreviewController.AbMode) -> Unit,
    onToggleLoudnessMatching: () -> Unit,
    onPreviewSection: (SongSection?, Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ab_preview_card")
            .border(1.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = StudioCardBg),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Compare,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "🎧 A/B PREVIEW & SECTION AUDITION",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Bandingkan kualitas audio secara instan dengan Loudness Matching & Section Audition",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }

            // Pilihan Mode A/B
            Text(
                text = "PILIHAN SUMBER AUDIO (A/B TESTING):",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = NeonCyan
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val currentMode = uiState.currentAbMode

                // 1. ORIGINAL
                Button(
                    onClick = { onSwitchAbMode(com.autoremix.djslow.engine.preview.AbPreviewController.AbMode.ORIGINAL) },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("ab_mode_original_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (currentMode == com.autoremix.djslow.engine.preview.AbPreviewController.AbMode.ORIGINAL) NeonPurple else StudioSurfaceElevated,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "ORIGINAL",
                        fontSize = 11.sp,
                        fontWeight = if (currentMode == com.autoremix.djslow.engine.preview.AbPreviewController.AbMode.ORIGINAL) FontWeight.Bold else FontWeight.Normal
                    )
                }

                // 2. REMIX (Pre-Master)
                Button(
                    onClick = { onSwitchAbMode(com.autoremix.djslow.engine.preview.AbPreviewController.AbMode.REMIX) },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("ab_mode_remix_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (currentMode == com.autoremix.djslow.engine.preview.AbPreviewController.AbMode.REMIX) NeonAmber else StudioSurfaceElevated,
                        contentColor = if (currentMode == com.autoremix.djslow.engine.preview.AbPreviewController.AbMode.REMIX) Color.Black else TextPrimary
                    )
                ) {
                    Text(
                        text = "REMIX",
                        fontSize = 11.sp,
                        fontWeight = if (currentMode == com.autoremix.djslow.engine.preview.AbPreviewController.AbMode.REMIX) FontWeight.Bold else FontWeight.Normal
                    )
                }

                // 3. MASTERED (Final)
                Button(
                    onClick = { onSwitchAbMode(com.autoremix.djslow.engine.preview.AbPreviewController.AbMode.MASTERED) },
                    modifier = Modifier
                        .weight(1.1f)
                        .height(44.dp)
                        .testTag("ab_mode_mastered_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (currentMode == com.autoremix.djslow.engine.preview.AbPreviewController.AbMode.MASTERED) Color(0xFF00E676) else StudioSurfaceElevated,
                        contentColor = if (currentMode == com.autoremix.djslow.engine.preview.AbPreviewController.AbMode.MASTERED) Color.Black else TextPrimary
                    )
                ) {
                    Text(
                        text = "MASTER FINAL",
                        fontSize = 11.sp,
                        fontWeight = if (currentMode == com.autoremix.djslow.engine.preview.AbPreviewController.AbMode.MASTERED) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }

            // Loudness Matching Switch
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = StudioSurfaceElevated
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.GraphicEq, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Loudness Matching (Kompensasi Gain)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        }
                        Text(
                            text = "Menyelaraskan volume RMS agar telinga tidak tertipu bias keras suara.",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }

                    Switch(
                        checked = uiState.isLoudnessMatchingEnabled,
                        onCheckedChange = { onToggleLoudnessMatching() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonCyan,
                            checkedTrackColor = NeonCyan.copy(alpha = 0.3f),
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = Color(0xFF1E2433)
                        ),
                        modifier = Modifier.testTag("loudness_matching_toggle")
                    )
                }
            }

            // Section Audition (15-30 Detik)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "AUDISI BAGIAN KHUSUS (SECTION PREVIEW 15-30s):",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = NeonPink
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val dropSection = uiState.songSections.firstOrNull { it.sectionType == SongSectionType.DROP || it.sectionType == SongSectionType.MAIN_DROP }
                    val chorusSection = uiState.songSections.firstOrNull { it.sectionType == SongSectionType.VERSE || it.sectionType == SongSectionType.GROOVE }
                    val introSection = uiState.songSections.firstOrNull { it.sectionType == SongSectionType.INTRO }

                    FilledTonalButton(
                        onClick = { onPreviewSection(dropSection, 15) },
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .testTag("section_preview_drop_button"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = NeonPink.copy(alpha = 0.2f),
                            contentColor = NeonPink
                        )
                    ) {
                        Text(text = "💥 Drop (15s)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    FilledTonalButton(
                        onClick = { onPreviewSection(chorusSection, 30) },
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .testTag("section_preview_chorus_button"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = NeonCyan.copy(alpha = 0.2f),
                            contentColor = NeonCyan
                        )
                    ) {
                        Text(text = "🎤 Reff (30s)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    FilledTonalButton(
                        onClick = { onPreviewSection(introSection, 15) },
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .testTag("section_preview_intro_button"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = NeonPurple.copy(alpha = 0.2f),
                            contentColor = NeonPurple
                        )
                    ) {
                        Text(text = "🎵 Intro (15s)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ==========================================
// TAHAP 6: EKSPOR MEDIASTORE & MP3 & SHARE
// ==========================================
@Composable
fun ExportCard(
    uiState: com.autoremix.djslow.state.UiState,
    onExportWav: () -> Unit,
    onExportMp3: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("export_card")
            .border(1.5.dp, Color(0xFF00E676).copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = StudioCardBg),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = Color(0xFF00E676),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "📁 EKSPOR & DISTRIBUSI AUDIO (FINAL)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Simpan permanen ke MediaStore Android (Music/Auto Remix/) & bagikan",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }

            // Notifikasi Berhasil Ekspor
            if (uiState.exportSuccessMessage != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0B2418),
                    border = BorderStroke(1.dp, Color(0xFF00E676))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = uiState.exportSuccessMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF00E676),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Tombol Ekspor MediaStore WAV
            Button(
                onClick = onExportWav,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("export_mediastore_wav_button"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E676),
                    contentColor = Color.Black
                )
            ) {
                Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "EKSPOR MEDIASTORE WAV (Music/Auto Remix/)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "RIFF WAV 44.1 kHz 16-bit PCM • Anti-Clipping Terjamin",
                        fontSize = 10.sp,
                        color = Color.Black.copy(alpha = 0.8f)
                    )
                }
            }

            // Baris Tombol MP3 dan Share
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Tombol Ekspor MP3
                Button(
                    onClick = onExportMp3,
                    enabled = uiState.isMp3Supported,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("export_mp3_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = Color.Black,
                        disabledContainerColor = StudioSurfaceElevated,
                        disabledContentColor = TextMuted
                    )
                ) {
                    Icon(imageVector = Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (uiState.isMp3Supported) "EKSPOR MP3" else "MP3 N/A",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Tombol Bagikan Audio (Share Sheet)
                Button(
                    onClick = onShare,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("share_audio_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonPurple,
                        contentColor = Color.White
                    )
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "BAGIKAN AUDIO",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ==========================================
// TAHAP 6: PENYIMPANAN PROYEK LOKAL OFFLINE
// ==========================================
@Composable
fun ProjectPersistenceCard(
    uiState: com.autoremix.djslow.state.UiState,
    onSaveProject: () -> Unit,
    onLoadProject: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("project_persistence_card")
            .border(1.dp, StudioSurfaceElevated, RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = StudioCardBg),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = null,
                    tint = NeonAmber,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "💾 MANAJEMEN PROYEK OFFLINE",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Simpan seluruh setelan tempo, akor, pola bass & mix bus secara lokal",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onSaveProject,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("save_project_button"),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, NeonAmber),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonAmber)
                ) {
                    Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "SIMPAN PROYEK", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                FilledTonalButton(
                    onClick = onLoadProject,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("load_project_button"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = StudioSurfaceElevated,
                        contentColor = NeonCyan
                    )
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "PULIHKAN SESI", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
