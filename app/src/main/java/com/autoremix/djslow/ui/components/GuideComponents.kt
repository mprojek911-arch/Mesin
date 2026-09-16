package com.autoremix.djslow.ui.components

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.autoremix.djslow.guide.ContextualHelpTopic
import com.autoremix.djslow.guide.GuideStep
import com.autoremix.djslow.guide.RemixGuideManager
import com.autoremix.djslow.state.UiState
import com.autoremix.djslow.ui.theme.NeonAmber
import com.autoremix.djslow.ui.theme.NeonCyan
import com.autoremix.djslow.ui.theme.NeonPink
import com.autoremix.djslow.ui.theme.NeonPurple
import com.autoremix.djslow.ui.theme.StatusError
import com.autoremix.djslow.ui.theme.StatusSuccess
import com.autoremix.djslow.ui.theme.StatusWarning
import com.autoremix.djslow.ui.theme.StudioCardBg
import com.autoremix.djslow.ui.theme.StudioCardBorder
import com.autoremix.djslow.ui.theme.StudioDarkBg
import com.autoremix.djslow.ui.theme.StudioSurfaceElevated
import com.autoremix.djslow.ui.theme.TextMuted
import com.autoremix.djslow.ui.theme.TextPrimary
import com.autoremix.djslow.ui.theme.TextSecondary

/**
 * Tombol lencana kecil ⓘ PANDUAN pada setiap bagian fitur utama.
 */
@Composable
fun GuideBadgeButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "guide_badge_button"
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .testTag(testTag)
            .clip(RoundedCornerShape(6.dp)),
        color = StudioSurfaceElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, NeonPink.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "ⓘ",
                color = NeonPink,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "PANDUAN",
                color = NeonPink,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Dialog panduan selamat datang saat pertama kali membuka aplikasi.
 */
@Composable
fun WelcomeGuideDialog(
    onStartGuideClicked: () -> Unit,
    onDismissRequest: (dontShowAgain: Boolean) -> Unit
) {
    var dontShowAgain by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { onDismissRequest(dontShowAgain) },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .testTag("welcome_guide_dialog")
                .clip(RoundedCornerShape(20.dp)),
            color = StudioCardBg,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, NeonPink)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(NeonPink, NeonPurple)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🎧", fontSize = 32.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "SELAMAT DATANG DI AUTO REMIX DJ SLOW",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Aplikasi remix musik DJ Slow cerdas berkecepatan 80 BPM dengan pemisahan trek, sinkronisasi tempo, dan mastering audio lossless.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(StudioSurfaceElevated)
                        .border(1.dp, StudioCardBorder, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "💡 Cara Kerja Panduan Interaktif:",
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "• 13 langkah terpandu dari memilih lagu sampai export\n• Tombol besar & bahasa Indonesia sederhana\n• Membaca kondisi riil audio engine Anda secara langsung",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Checkbox Jangan Tampilkan Lagi
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { dontShowAgain = !dontShowAgain }
                ) {
                    Checkbox(
                        checked = dontShowAgain,
                        onCheckedChange = { dontShowAgain = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = NeonPink,
                            checkmarkColor = Color.White,
                            uncheckedColor = TextMuted
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Jangan tampilkan lagi saat membuka aplikasi",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Tombol Aksi
                Button(
                    onClick = { onStartGuideClicked() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("welcome_start_guide_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonPink,
                        contentColor = Color.White
                    )
                ) {
                    Text("MULAI PANDUAN (13 LANGKAH)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { onDismissRequest(dontShowAgain) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("welcome_later_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = TextSecondary
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StudioCardBorder)
                ) {
                    Text("NANTI SAJA", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                }
            }
        }
    }
}

/**
 * Modal panduan 13 langkah utama yang terhubung langsung ke UiState dan fitur riil.
 */
@Composable
fun RemixInteractiveGuideModal(
    uiState: UiState,
    initialStep: Int = 1,
    onDismissRequest: () -> Unit,
    onStepActionClicked: (GuideStep) -> Unit
) {
    val context = LocalContext.current
    var currentStepNumber by remember { mutableIntStateOf(initialStep.coerceIn(1, 13)) }
    val currentStep = GuideStep.fromStepNumber(currentStepNumber)

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .testTag("remix_guide_modal")
                .clip(RoundedCornerShape(20.dp)),
            color = StudioDarkBg,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, NeonPink)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Baris Atas: Judul & Tombol Tutup
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "🎧 PANDUAN REMIX MUSIK",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = NeonPink
                        )
                        Text(
                            text = "Langkah $currentStepNumber dari 13",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                    }

                    Surface(
                        onClick = onDismissRequest,
                        shape = CircleShape,
                        color = StudioSurfaceElevated,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("close_guide_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = "✕", color = TextSecondary, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Progress Bar 13 Langkah
                LinearProgressIndicator(
                    progress = { currentStepNumber / 13f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = NeonPink,
                    trackColor = StudioSurfaceElevated
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Judul Langkah
                Text(
                    text = currentStep.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Status Deteksi Nyata dari UI State
                val (statusText, statusColor) = when (currentStep) {
                    GuideStep.STEP_01_PILIH_AUDIO -> {
                        when {
                            uiState.isVocalSelected && uiState.isBeatSelected -> "✅ Audio vokal & beat siap dimuat." to StatusSuccess
                            uiState.isVocalSelected -> "✅ Audio vokal dimuat. Silakan pilih beat pengiring." to NeonAmber
                            uiState.isBeatSelected -> "✅ Audio beat dimuat. Silakan pilih vokal penyanyi." to NeonAmber
                            else -> "⚠️ Silakan pilih audio vokal atau lagu terlebih dahulu." to StatusWarning
                        }
                    }
                    GuideStep.STEP_02_ANALISIS_MUSIK -> {
                        when {
                            uiState.isAnalyzing -> "⏳ Sedang menganalisis musik (${(uiState.analysisProgressFraction * 100).toInt()}%)..." to NeonAmber
                            uiState.vocalBpm != null || uiState.beatBpm != null -> "✅ Analisis musik selesai (BPM: ${uiState.targetBpm.toInt()}, Nada: ${uiState.detectedKey})." to StatusSuccess
                            else -> "⚠️ Musik belum dianalisis. Tekan tombol analisis di bawah." to StatusWarning
                        }
                    }
                    GuideStep.STEP_03_PISAHKAN_VOCAL_BEAT -> {
                        when {
                            uiState.isVocalSelected && uiState.isBeatSelected -> "✅ Vokal & Beat terpisah dan siap dikontrol independen." to StatusSuccess
                            else -> "ℹ️ Anda dapat memasukkan trek vokal dan trek beat terpisah." to NeonCyan
                        }
                    }
                    GuideStep.STEP_04_ATUR_BPM_PITCH -> {
                        "✅ Target BPM terkunci di ${uiState.targetBpm.toInt()} BPM (Gaya Slow Remix)." to StatusSuccess
                    }
                    GuideStep.STEP_05_PILIH_GAYA_REMIX -> {
                        "✅ Gaya remix aktif: ${uiState.currentPreset.label}." to StatusSuccess
                    }
                    GuideStep.STEP_06_ATUR_STRUKTUR_REMIX -> {
                        val count = uiState.songSections.size
                        if (count > 0) "✅ Struktur tersusun: $count bagian (Intro ➔ Drop ➔ Outro)." to StatusSuccess
                        else "ℹ️ Struktur standar DJ Slow: Intro ➔ Drop ➔ Break ➔ Drop ➔ Outro." to NeonCyan
                    }
                    GuideStep.STEP_07_ATUR_MUSIK_LAYERS -> {
                        "✅ 8 Fader trek aktif (Vokal, Beat, Drum, Bass, Chord, dsb)." to StatusSuccess
                    }
                    GuideStep.STEP_08_ATUR_VOCAL -> {
                        if (uiState.isAutoMixEnabled) "✅ Auto Vocal Mix & Sidechain Ducking aktif." to StatusSuccess
                        else "ℹ️ Auto Vocal Mix dinonaktifkan." to StatusWarning
                    }
                    GuideStep.STEP_09_ATUR_FX_TRANSITION -> {
                        "✅ FX Transitions (Sweep, Riser, Impact) terintegrasi ke timeline." to StatusSuccess
                    }
                    GuideStep.STEP_10_PREVIEW_MONITOR -> {
                        if (uiState.isPlaying) "▶️ Preview sedang memutar audio nyata." to StatusSuccess
                        else if (uiState.isPaused) "⏸ Preview dijeda." to NeonAmber
                        else "■ Preview siap diputar." to TextSecondary
                    }
                    GuideStep.STEP_11_RENDER_REMIX -> {
                        when {
                            uiState.isRendering -> "⏳ Sedang membuat hasil remix (${(uiState.renderProgressFraction * 100).toInt()}%)..." to NeonAmber
                            uiState.isRenderedAvailable -> "✅ Remix berhasil dirender (${uiState.renderedWavFile?.name})." to StatusSuccess
                            uiState.errorMessage != null -> "❌ Remix gagal: ${uiState.errorMessage}" to StatusError
                            else -> "ℹ️ Siap dirender menjadi WAV master resolusi tinggi." to TextSecondary
                        }
                    }
                    GuideStep.STEP_12_PUTAR_HASIL_REMIX -> {
                        when {
                            uiState.isRenderedPlaying -> "🎧 Memutar file WAV hasil render asli." to StatusSuccess
                            uiState.isRenderedAvailable -> "✅ File hasil render siap diputar." to NeonCyan
                            else -> "⚠️ File remix belum dirender. Selesaikan langkah 11 terlebih dahulu." to StatusWarning
                        }
                    }
                    GuideStep.STEP_13_SIMPAN_EXPORT -> {
                        when {
                            uiState.exportedWavFile != null -> "✅ Hasil remix tersimpan di MediaStore perangkat!" to StatusSuccess
                            uiState.isRenderedAvailable -> "ℹ️ Siap disimpan ke folder Musik perangkat." to NeonCyan
                            else -> "⚠️ Belum ada file audio yang dirender untuk disimpan." to StatusWarning
                        }
                    }
                }

                // Box Status Riil
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(StudioSurfaceElevated)
                        .border(1.dp, statusColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Penjelasan Langkah
                Text(
                    text = currentStep.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    lineHeight = 20.sp
                )

                // Khusus Langkah 6: Tampilkan visualisasi alur struktur
                if (currentStep == GuideStep.STEP_06_ATUR_STRUKTUR_REMIX) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(StudioCardBg)
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("INTRO  (Bagian pembuka santai)", fontSize = 11.sp, color = NeonCyan)
                            Text("↓", fontSize = 11.sp, color = TextMuted)
                            Text("BUILD UP  (Energi & snare roll mulai naik)", fontSize = 11.sp, color = NeonAmber)
                            Text("↓", fontSize = 11.sp, color = TextMuted)
                            Text("DROP  (Klimaks bass DJ Slow & beat bertenaga)", fontSize = 11.sp, color = NeonPink, fontWeight = FontWeight.Bold)
                            Text("↓", fontSize = 11.sp, color = TextMuted)
                            Text("BREAK  (Jeda tenang memberi ruang vokal)", fontSize = 11.sp, color = NeonPurple)
                            Text("↓", fontSize = 11.sp, color = TextMuted)
                            Text("OUTRO  (Penutup halus lagu)", fontSize = 11.sp, color = TextSecondary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Tombol Aksi Nyata Langkah Ini
                Button(
                    onClick = {
                        onStepActionClicked(currentStep)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("guide_action_button_${currentStep.stepNumber}"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonPink,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = currentStep.actionButtonText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Baris Navigasi: [ KEMBALI ] [ LEWATI ] [ BERIKUTNYA / SELESAI ]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tombol KEMBALI
                    OutlinedButton(
                        onClick = {
                            if (currentStepNumber > 1) {
                                currentStepNumber--
                                RemixGuideManager.saveLastStep(context, currentStepNumber)
                            }
                        },
                        enabled = currentStepNumber > 1,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("guide_previous_button"),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (currentStepNumber > 1) StudioCardBorder else Color.Transparent
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                    ) {
                        Text("◀ KEMBALI", fontSize = 11.sp)
                    }

                    // Tombol LEWATI
                    TextButton(
                        onClick = {
                            if (currentStepNumber < 13) {
                                currentStepNumber++
                                RemixGuideManager.saveLastStep(context, currentStepNumber)
                            } else {
                                onDismissRequest()
                            }
                        },
                        modifier = Modifier.testTag("guide_skip_button")
                    ) {
                        Text("LEWATI", fontSize = 11.sp, color = TextMuted)
                    }

                    // Tombol BERIKUTNYA / SELESAI
                    Button(
                        onClick = {
                            if (currentStepNumber < 13) {
                                currentStepNumber++
                                RemixGuideManager.saveLastStep(context, currentStepNumber)
                            } else {
                                onDismissRequest()
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("guide_next_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentStepNumber == 13) StatusSuccess else NeonCyan,
                            contentColor = Color.Black
                        )
                    ) {
                        Text(
                            text = if (currentStepNumber == 13) "SELESAI ✓" else "BERIKUTNYA ▶",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dialog bantuan kontekstual untuk tombol ⓘ pada masing-masing fitur.
 */
@Composable
fun ContextualFeatureGuideDialog(
    topic: ContextualHelpTopic,
    onDismissRequest: () -> Unit,
    onOpenFullGuideStep: (GuideStep) -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .testTag("contextual_guide_dialog")
                .clip(RoundedCornerShape(16.dp)),
            color = StudioCardBg,
            border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = topic.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan
                    )

                    Surface(
                        onClick = onDismissRequest,
                        shape = CircleShape,
                        color = StudioSurfaceElevated,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("✕", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = topic.explanation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = { onOpenFullGuideStep(topic.relatedStep) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("open_related_guide_step_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonPink,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "BUKA DI PANDUAN LENGKAP (LANGKAH ${topic.relatedStep.stepNumber})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
