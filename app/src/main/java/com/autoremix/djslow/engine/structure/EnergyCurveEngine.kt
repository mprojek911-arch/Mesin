package com.autoremix.djslow.engine.structure

import kotlin.math.cos
import kotlin.math.sin

/**
 * Representasi kurva energi yang dapat di-query pada sembarang sample atau bar.
 */
class EnergyCurve(
    val sections: List<SongSection>,
    val totalSamples: Long
) {
    /**
     * Mengembalikan nilai energi kontinu [0.0f .. 1.0f] pada frame sample tertentu.
     * Menggunakan interpolasi kurva halus (S-curve / cosine) antar seksi.
     */
    fun getEnergyAtSample(sample: Long): Float {
        if (sections.isEmpty() || totalSamples <= 0L) return 0.5f
        val clampedSample = sample.coerceIn(0L, totalSamples)

        val sectionIdx = sections.indexOfFirst { s -> clampedSample in s.startSample..s.endSample }
        if (sectionIdx < 0) {
            return sections.lastOrNull()?.targetEnergy ?: 0.5f
        }

        val currentSec = sections[sectionIdx]
        val secLen = maxOf(1L, currentSec.endSample - currentSec.startSample)
        val progress = (clampedSample - currentSec.startSample).toFloat() / secLen.toFloat()

        val nextSec = sections.getOrNull(sectionIdx + 1)
        val currentEnergy = currentSec.targetEnergy
        val nextEnergy = nextSec?.targetEnergy ?: currentSec.targetEnergy

        // Khusus OUTRO: meluruh bertahap dari 70% ke 20%
        if (currentSec.sectionType == SongSectionType.OUTRO) {
            return 0.70f - (0.50f * progress)
        }

        // Khusus BUILD UP: eskalasi naik progresif
        if (currentSec.sectionType == SongSectionType.BUILD_UP || currentSec.sectionType == SongSectionType.BUILD_UP_2 || currentSec.sectionType == SongSectionType.FINAL_BUILD) {
            val startE = currentSec.targetEnergy * 0.7f
            val endE = currentSec.targetEnergy * 1.3f
            return (startE + (endE - startE) * (progress * progress)).coerceIn(0.1f, 1.0f)
        }

        // Transisi halus di 15% akhir seksi menuju seksi berikutnya
        return if (progress > 0.85f && nextSec != null) {
            val blendT = (progress - 0.85f) / 0.15f
            val smoothBlend = (1.0f - cos(blendT * Math.PI.toFloat())) * 0.5f
            (currentEnergy + (nextEnergy - currentEnergy) * smoothBlend).coerceIn(0.0f, 1.0f)
        } else {
            currentEnergy
        }
    }

    /**
     * Mengembalikan nilai energi pada bar tertentu.
     */
    fun getEnergyAtBar(bar: Int): Float {
        val sec = sections.firstOrNull { bar in it.startBar until it.endBar }
        return sec?.targetEnergy ?: 0.5f
    }
}

/**
 * Energy Curve Engine:
 * Menghitung kurva energi multi-titik dan automasi parameter musik.
 */
object EnergyCurveEngine {

    fun buildEnergyCurve(sections: List<SongSection>, totalSamples: Long): EnergyCurve {
        return EnergyCurve(sections, totalSamples)
    }

    /**
     * Menghitung string representasi bar energi visual untuk UI:
     * Contoh: "████████░░ 80%"
     */
    fun getVisualEnergyBar(energy: Float): String {
        val percent = (energy * 100).toInt().coerceIn(0, 100)
        val totalBlocks = 10
        val filledBlocks = (energy * totalBlocks).toInt().coerceIn(0, totalBlocks)
        val sb = java.lang.StringBuilder()
        for (i in 0 until filledBlocks) sb.append("█")
        for (i in filledBlocks until totalBlocks) sb.append("░")
        sb.append(" ").append(percent).append("%")
        return sb.toString()
    }
}
