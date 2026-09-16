package com.autoremix.djslow.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.autoremix.djslow.ui.components.AnalysisResultCard
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.autoremix.djslow.engine.core.RemixBrain
import com.autoremix.djslow.state.UiState
import com.autoremix.djslow.ui.theme.NeonAmber
import com.autoremix.djslow.ui.theme.NeonCyan
import com.autoremix.djslow.ui.theme.NeonPink
import com.autoremix.djslow.ui.theme.NeonPurple
import com.autoremix.djslow.ui.theme.StudioCardBg
import com.autoremix.djslow.ui.theme.StudioSurfaceElevated
import com.autoremix.djslow.ui.theme.TextMuted
import com.autoremix.djslow.ui.theme.TextPrimary
import com.autoremix.djslow.ui.theme.TextSecondary
import com.autoremix.djslow.viewmodel.MainViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickModeCard(
    uiState: UiState,
    viewModel: MainViewModel,
    context: Context
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("quick_mode_container"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ==========================================
        // 1. STYLE SELECTOR (GAYA REMIX)
        // ==========================================
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("style_selector_card"),
            colors = CardDefaults.cardColors(containerColor = StudioCardBg),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "✨ PILIH GAYA REMIX",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan
                    )
                    Text(
                        text = "${uiState.remixStyle.recommendedBpmRange.start.roundToInt()} - ${uiState.remixStyle.recommendedBpmRange.endInclusive.roundToInt()} BPM",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RemixBrain.RemixStyle.values().forEach { style ->
                        val isSelected = uiState.remixStyle == style
                        val bg = if (isSelected) NeonCyan.copy(alpha = 0.25f) else StudioSurfaceElevated
                        val borderCol = if (isSelected) NeonCyan else Color.Transparent
                        val textCol = if (isSelected) NeonCyan else TextPrimary

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(bg)
                                .border(1.dp, borderCol, RoundedCornerShape(8.dp))
                                .clickable { viewModel.onStyleSelected(style) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag("style_chip_${style.name.lowercase()}")
                        ) {
                            Column {
                                Text(
                                    text = style.label,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = textCol
                                )
                                Text(
                                    text = style.description,
                                    fontSize = 10.sp,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // 2. TOMBOL UTAMA: AUTO REMIX (ONE-CLICK)
        // ==========================================
        Button(
            onClick = { viewModel.onAutoRemix(context) },
            enabled = uiState.hasAnyAudio && !uiState.isRendering,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("auto_remix_button"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = NeonPink,
                contentColor = Color.White,
                disabledContainerColor = Color(0xFF1E2433),
                disabledContentColor = Color(0xFF6C7A9C)
            )
        ) {
            if (uiState.isRendering) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Color.White,
                    strokeWidth = 2.5.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "SEDANG MEREMIX DENGAN 10 ENGINE NYATA...",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            } else {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "⚡ AUTO REMIX (ONE-CLICK)",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp
                )
            }
        }

        // Progress Bar Auto Remix
        if (uiState.isRendering) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LinearProgressIndicator(
                    progress = { uiState.renderProgressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
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
                        .height(38.dp)
                        .testTag("quick_cancel_render_button"),
                    border = BorderStroke(1.dp, Color(0xFFFF5252)),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252))
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "BATALKAN PROSES", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ==========================================
        // 3. HASIL ANALISIS & RENCANA REMIX SINGKAT
        // ==========================================
        if (uiState.musicAnalysis != null || uiState.remixPlan != null || uiState.isRenderedAvailable) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("analysis_and_plan_card")
                    .border(1.dp, NeonPurple.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131726)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // FASE 1: Bagian F - SAYA MENEMUKAN (Hasil Analisis Musik Nyata)
                    AnalysisResultCard(uiState = uiState)

                    Spacer(modifier = Modifier.height(4.dp))

                    // RENCANA REMIX
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(NeonPink)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "📋 RENCANA REMIX:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = NeonPink
                        )
                    }

                    val targetBpm = uiState.remixPlan?.targetBpm?.roundToInt() ?: uiState.targetBpm.roundToInt()
                    val styleLabel = uiState.remixPlan?.style?.label ?: uiState.remixStyle.label
                    val sectionCount = uiState.songSections.size.takeIf { it > 0 } ?: 8

                    Text(
                        text = "• Target Remix: $targetBpm BPM\n• Gaya Produksi: $styleLabel\n• Susunan Aransemen: $sectionCount Seksi (Intro, Build, Pre-Drop, Drop, Break, Build 2, Drop 2, Outro)",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                        lineHeight = 20.sp
                    )
                }
            }
        }

        // ==========================================
        // 4. ACTION ROW: 30s PREVIEW & BUAT VERSI LAIN
        // ==========================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Tombol Generate Preview (30 Detik)
            Button(
                onClick = { viewModel.onGenerate30sPreview(context) },
                enabled = uiState.hasAnyAudio && !uiState.isRendering && !uiState.isGeneratingPreview30s,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .testTag("preview_30s_button"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StudioSurfaceElevated,
                    contentColor = NeonCyan
                )
            ) {
                if (uiState.isGeneratingPreview30s) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = NeonCyan, strokeWidth = 2.dp)
                } else {
                    Icon(imageVector = Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "PREVIEW 30s", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            // Tombol Buat Versi Lain (Seed System)
            Button(
                onClick = { viewModel.onRegenerateVersion(context) },
                enabled = uiState.hasAnyAudio && !uiState.isRendering,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .testTag("regenerate_version_button"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StudioSurfaceElevated,
                    contentColor = NeonAmber
                )
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "VERSI LAIN", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        // ==========================================
        // 5. PLAYER HASIL MASTER / PREVIEW
        // ==========================================
        if (uiState.isRenderedAvailable) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("quick_rendered_player_card")
                    .border(1.5.dp, Color(0xFF00E676).copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1B15)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🎧 HASIL REMIX SIAP DIPUTAR",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E676)
                        )
                        uiState.validationResult?.let { v ->
                            Text(
                                text = "${v.formattedLufs} • ${v.formattedTruePeak}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    }

                    // Banner Khusus Pratinjau 30 Detik (Intro -> Build -> Pre-Drop -> Drop)
                    val is30sPreview = uiState.preview30sFile != null && uiState.renderedWavFile == uiState.preview30sFile
                    if (is30sPreview) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = NeonCyan.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "⚡ PRATINJAU 30 DETIK (Intro → Build → Pre-Drop → Drop)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonCyan
                                )
                                Text(
                                    text = "Jika puas: tekan RENDER FULL untuk menghasilkan seluruh lagu.\nJika tidak: tekan BUAT VERSI LAIN untuk variasi aransemen baru.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { viewModel.onAutoRemix(context) },
                                        modifier = Modifier
                                            .weight(1.2f)
                                            .height(40.dp)
                                            .testTag("quick_preview_render_full_button"),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = NeonPink, contentColor = Color.White)
                                    ) {
                                        Icon(imageVector = Icons.Default.FastForward, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("RENDER FULL", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = { viewModel.onRegenerateVersion(context) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(40.dp)
                                            .testTag("quick_preview_regen_button"),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = StudioSurfaceElevated, contentColor = NeonAmber)
                                    ) {
                                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("VERSI LAIN", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Progress Slider
                    Slider(
                        value = uiState.renderedProgressFraction,
                        onValueChange = { frac -> viewModel.onSeekRendered(frac) },
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00E676),
                            activeTrackColor = Color(0xFF00E676),
                            inactiveTrackColor = StudioSurfaceElevated
                        ),
                        modifier = Modifier.testTag("quick_rendered_seek_slider")
                    )

                    // Tombol Kontrol Playback
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.onPlayRendered() },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("quick_play_rendered_button"),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676), contentColor = Color.Black)
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PUTAR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.onPauseRendered() },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("quick_pause_rendered_button"),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = StudioSurfaceElevated, contentColor = TextPrimary)
                        ) {
                            Icon(imageVector = Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("JEDA", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.onStopRendered() },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("quick_stop_rendered_button"),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = StudioSurfaceElevated, contentColor = Color(0xFFFF6B6B))
                        ) {
                            Icon(imageVector = Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("STOP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // ==========================================
                    // EKSPOR REAL BUTTONS
                    // ==========================================
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.exportWavToMediaStore(context) },
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .testTag("quick_export_wav_button"),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color.Black)
                        ) {
                            Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("SIMPAN WAV", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.exportMp3(context) },
                            enabled = uiState.isMp3Supported,
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .testTag("quick_export_mp3_button"),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonPurple, contentColor = Color.White)
                        ) {
                            Text(
                                text = if (uiState.isMp3Supported) "EKSPOR MP3" else "MP3 T/A",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = { viewModel.shareAudio(context) },
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .testTag("quick_share_button"),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = StudioSurfaceElevated, contentColor = TextPrimary)
                        ) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("BAGIKAN", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
