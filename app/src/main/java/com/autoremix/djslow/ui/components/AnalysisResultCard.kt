package com.autoremix.djslow.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autoremix.djslow.state.UiState
import com.autoremix.djslow.ui.theme.*
import kotlin.math.roundToInt

/**
 * Komponen Hasil Analisis Musik Nyata (FASE 1 - Bagian F).
 * Menampilkan:
 * SAYA MENEMUKAN
 *
 * BPM:
 * KEY:
 * CONFIDENCE:
 * STRUKTUR:
 * ENERGY:
 * BEAT:
 *
 * Jika data tidak tersedia atau analisis belum dilakukan, menampilkan secara jujur:
 * "DATA TIDAK TERSEDIA" tanpa nilai palsu atau buatan.
 */
@Composable
fun AnalysisResultCard(
    uiState: UiState,
    modifier: Modifier = Modifier
) {
    val analysis = uiState.musicAnalysis

    // Nilai BPM
    val bpmText = when {
        analysis != null -> "${analysis.bpm.roundToInt()} BPM" + if (analysis.isBpmEstimated) " (Estimasi)" else " (Akurat)"
        else -> "DATA TIDAK TERSEDIA"
    }

    // Nilai KEY
    val keyText = when {
        analysis != null -> analysis.key.displayName + if (analysis.isKeyEstimated) " (Estimasi)" else " (Terdeteksi)"
        else -> "DATA TIDAK TERSEDIA"
    }

    // Nilai CONFIDENCE
    val confidenceText = when {
        analysis != null -> "${(analysis.confidenceOverall * 100).toInt()}% (BPM: ${(analysis.bpmConfidence * 100).toInt()}%, Key: ${(analysis.keyConfidence * 100).toInt()}%)"
        else -> "DATA TIDAK TERSEDIA"
    }

    // Nilai STRUKTUR
    val strukturText = when {
        analysis != null && analysis.sections.isNotEmpty() -> {
            val count = analysis.sections.size
            val labels = analysis.sections.take(4).joinToString(" → ") { it.sectionType.label }
            val suffix = if (count > 4) "..." else ""
            "$count Seksi ($labels$suffix)"
        }
        else -> "DATA TIDAK TERSEDIA"
    }

    // Nilai ENERGY
    val energyText = when {
        analysis != null -> {
            val percent = (analysis.energyAverage * 100).toInt().coerceIn(0, 100)
            val desc = when {
                analysis.energyAverage > 0.70f -> "Tinggi"
                analysis.energyAverage > 0.40f -> "Sedang"
                else -> "Lembut"
            }
            "$percent% ($desc)"
        }
        else -> "DATA TIDAK TERSEDIA"
    }

    // Nilai BEAT
    val beatText = when {
        analysis != null && analysis.beatAnalysis != null -> {
            val beat = analysis.beatAnalysis
            "Drum: ${beat.recommendedDrumMode.label} • Bass: ${(beat.bassPresence * 100).toInt()}%"
        }
        analysis != null -> {
            "Grid 4/4 Sinkron • ${analysis.timeline.totalBars} Bar"
        }
        else -> "DATA TIDAK TERSEDIA"
    }

    val isAvailable = analysis != null

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("analysis_result_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = StudioCardBg),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(
                if (isAvailable) NeonCyan.copy(alpha = 0.6f) else StudioCardBorder
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = if (isAvailable) NeonCyan else TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SAYA MENEMUKAN",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = if (isAvailable) NeonCyan else TextSecondary,
                        letterSpacing = 1.sp
                    )
                }

                // Badge Status
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isAvailable) StatusSuccess.copy(alpha = 0.15f) else StudioSurfaceElevated
                ) {
                    Text(
                        text = if (isAvailable) "DSP TERVERIFIKASI" else "ANALISIS DIPERLUKAN",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isAvailable) StatusSuccess else TextMuted,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider(
                color = StudioCardBorder,
                thickness = 1.dp
            )

            // 6 Baris Hasil Analisis Sesuai Bagian F
            AnalysisResultRow(label = "BPM:", value = bpmText, isAvailable = isAvailable)
            AnalysisResultRow(label = "KEY:", value = keyText, isAvailable = isAvailable)
            AnalysisResultRow(label = "CONFIDENCE:", value = confidenceText, isAvailable = isAvailable)
            AnalysisResultRow(label = "STRUKTUR:", value = strukturText, isAvailable = isAvailable && analysis?.sections?.isNotEmpty() == true)
            AnalysisResultRow(label = "ENERGY:", value = energyText, isAvailable = isAvailable)
            AnalysisResultRow(label = "BEAT:", value = beatText, isAvailable = isAvailable)
        }
    }
}

@Composable
private fun AnalysisResultRow(
    label: String,
    value: String,
    isAvailable: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            modifier = Modifier.width(110.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isAvailable) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isAvailable) TextPrimary else TextMuted,
            modifier = Modifier.weight(1f)
        )
    }
}
