package com.autoremix.djslow.logchat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogChatScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val logs by LogChatManager.logs.collectAsState()
    val pipelineMap by LogChatManager.pipelineStatus.collectAsState()
    val audioDiag by LogChatManager.audioDiagnostic.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedLevel by remember { mutableStateOf<LogLevel?>(null) }
    var selectedModule by remember { mutableStateOf<LogModule?>(null) }
    var detailEntry by remember { mutableStateOf<LogEntry?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }

    // Evaluasi Status Sistem Keseluruhan Berdasarkan Log Nyata
    val hasCritical = logs.any { it.level == LogLevel.CRITICAL }
    val hasError = logs.any { it.level == LogLevel.ERROR }
    val hasWarning = logs.any { it.level == LogLevel.WARNING }

    val statusColor = when {
        hasCritical || hasError -> Color(0xFFFF4081)
        hasWarning -> Color(0xFFFFD54F)
        else -> Color(0xFF00E676)
    }

    val statusText = when {
        hasCritical -> "🔴 Ada kesalahan kritis dalam aplikasi"
        hasError -> "🔴 Ada kesalahan yang tercatat"
        hasWarning -> "🟡 Ada peringatan pada sistem atau audio"
        else -> "🟢 Aplikasi berjalan normal"
    }

    // Filter log
    val filteredLogs = remember(logs, searchQuery, selectedLevel, selectedModule) {
        logs.filter { entry ->
            val matchLevel = selectedLevel == null || entry.level == selectedLevel
            val matchModule = selectedModule == null || entry.module == selectedModule
            val matchQuery = if (searchQuery.isBlank()) {
                true
            } else {
                val q = searchQuery.trim().lowercase()
                entry.message.lowercase().contains(q) ||
                        (entry.detail?.lowercase()?.contains(q) == true) ||
                        entry.module.name.lowercase().contains(q) ||
                        (entry.associatedFile?.lowercase()?.contains(q) == true) ||
                        entry.formattedTime.contains(q)
            }
            matchLevel && matchModule && matchQuery
        }.reversed() // Log terbaru di atas
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(StudioDarkBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("logchat_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali ke Studio",
                            tint = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "LogChat",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Catatan kesalahan dan aktivitas aplikasi",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                // Badge Status Nyata
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .border(1.dp, statusColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (hasCritical || hasError) "KESALAHAN" else if (hasWarning) "PERINGATAN" else "NORMAL",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
            }

            // Status Bar Deskripsi
            Surface(
                color = StudioCardBg,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor
                    )
                }
            }

            // PIPELINE MONITOR HORIZONTAL
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioCardBg),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "MONITOR PIPELINE AUDIO",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PipelineStage.entries.forEachIndexed { index, stage ->
                            val node = pipelineMap[stage]
                            val nodeStatus = node?.status ?: StepStatus.PENDING
                            val nodeColor = when (nodeStatus) {
                                StepStatus.SUCCESS -> Color(0xFF00E676)
                                StepStatus.WARNING -> Color(0xFFFFD54F)
                                StepStatus.FAILED -> Color(0xFFFF4081)
                                StepStatus.PENDING -> Color(0xFF757575)
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(StudioSurfaceElevated)
                                    .border(1.dp, nodeColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "${nodeStatus.symbol} ${stage.label}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = nodeColor,
                                    fontSize = 10.sp
                                )
                                Text(
                                    text = nodeStatus.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted,
                                    fontSize = 9.sp
                                )
                            }

                            if (index < PipelineStage.entries.size - 1) {
                                Text(
                                    text = "→",
                                    color = TextMuted,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // DIAGNOSTIK AUDIO (Jika ada audio aktif)
            audioDiag?.let { diag ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = StudioCardBg),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "DIAGNOSTIK AUDIO AKTIF",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = NeonAmber
                            )
                            Text(
                                text = "Langkah sukses: ${diag.lastSuccessfulStep}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF00E676)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Berkas: ${diag.fileName} (${diag.format}) | Ukuran: ${diag.fileSizeBytes / 1024} KB",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary
                        )
                        Text(
                            text = "Durasi: ${diag.durationMs / 1000}s | Sample Rate: ${diag.sampleRate} Hz | Saluran: ${diag.channels} | BPM: ${diag.bpm ?: "-"} | Key: ${diag.key ?: "-"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            // TOOLBAR AKSI: Uji Error, Salin, Bagikan, Hapus
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tombol Uji Error Terkontrol
                Button(
                    onClick = {
                        LogChatManager.triggerControlledTestError(context)
                        Toast.makeText(context, "Uji error terkontrol dijalankan & dicatat.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E1A47)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("logchat_test_error_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = null,
                        tint = NeonPurple,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Uji Error", fontSize = 12.sp, color = TextPrimary)
                }

                // Salin Log
                OutlinedButton(
                    onClick = {
                        val text = LogChatManager.exportLogsAsText()
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("LogChat Diagnostic", text)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Log berhasil disalin ke clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("logchat_copy_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Salin", fontSize = 12.sp, color = TextPrimary)
                }

                // Bagikan Log
                OutlinedButton(
                    onClick = {
                        val text = LogChatManager.exportLogsAsText()
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, text)
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, "Bagikan Catatan LogChat")
                        context.startActivity(shareIntent)
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("logchat_share_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = NeonAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Bagikan", fontSize = 12.sp, color = TextPrimary)
                }

                // Hapus Log
                IconButton(
                    onClick = { showClearConfirm = true },
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("logchat_clear_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Hapus Log",
                        tint = StatusError
                    )
                }
            }

            // PENCARIAN LOG
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("logchat_search_input"),
                placeholder = { Text("🔎 Cari log (pesan, module, berkas, error, waktu)...", color = TextMuted, fontSize = 13.sp) },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = StudioSurfaceElevated,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = StudioCardBg,
                    unfocusedContainerColor = StudioCardBg
                ),
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Hapus Pencarian", tint = TextMuted)
                        }
                    }
                }
            )

            // FILTER LEVEL & MODULE CHIPS (Horizontally Scrollable)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Filter SEMUA
                FilterChip(
                    selected = selectedLevel == null && selectedModule == null,
                    onClick = {
                        selectedLevel = null
                        selectedModule = null
                    },
                    label = { Text("SEMUA (${logs.size})", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonCyan,
                        selectedLabelColor = Color.Black
                    )
                )

                // Level Filters
                LogLevel.entries.forEach { level ->
                    val count = logs.count { it.level == level }
                    FilterChip(
                        selected = selectedLevel == level,
                        onClick = { selectedLevel = if (selectedLevel == level) null else level },
                        label = { Text("${level.name} ($count)", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = when (level) {
                                LogLevel.ERROR, LogLevel.CRITICAL -> StatusError
                                LogLevel.WARNING -> NeonAmber
                                LogLevel.INFO -> Color(0xFF00E676)
                                LogLevel.DEBUG -> NeonCyan
                            },
                            selectedLabelColor = Color.Black
                        )
                    )
                }

                // Module Filters
                LogModule.entries.forEach { mod ->
                    FilterChip(
                        selected = selectedModule == mod,
                        onClick = { selectedModule = if (selectedModule == mod) null else mod },
                        label = { Text(mod.name, fontSize = 11.sp) }
                    )
                }
            }

            // DAFTAR LOG LIST
            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (logs.isEmpty()) "Belum ada catatan log aplikasi." else "Tidak ada log yang cocok dengan filter.",
                            color = TextMuted,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredLogs, key = { it.id }) { entry ->
                        val itemColor = when (entry.level) {
                            LogLevel.CRITICAL -> Color(0xFFFF1744)
                            LogLevel.ERROR -> Color(0xFFFF5252)
                            LogLevel.WARNING -> Color(0xFFFFD740)
                            LogLevel.INFO -> Color(0xFF00E676)
                            LogLevel.DEBUG -> Color(0xFF40C4FF)
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { detailEntry = entry }
                                .testTag("log_item_${entry.id}"),
                            colors = CardDefaults.cardColors(containerColor = StudioCardBg),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(itemColor.copy(alpha = 0.2f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = entry.level.name,
                                                color = itemColor,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(StudioSurfaceElevated)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = entry.module.name,
                                                color = TextSecondary,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }

                                    Text(
                                        text = entry.formattedTime,
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = entry.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = TextPrimary
                                )

                                if (!entry.detail.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = entry.detail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (!entry.stackTrace.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "⚡ Stack trace tersedia (Tekan untuk detail)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NeonAmber,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // DIALOG KONFIRMASI HAPUS LOG
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Hapus Semua Log?") },
            text = { Text("Apakah Anda yakin ingin menghapus seluruh catatan log dan riwayat diagnostik? Tindakan ini tidak dapat dibatalkan.") },
            confirmButton = {
                Button(
                    onClick = {
                        LogChatManager.clearLogs()
                        showClearConfirm = false
                        Toast.makeText(context, "Log berhasil dihapus.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusError)
                ) {
                    Text("Hapus")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Batal")
                }
            }
        )
    }

    // DIALOG DETAIL ERROR / LOG
    detailEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { detailEntry = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (entry.level == LogLevel.ERROR || entry.level == LogLevel.CRITICAL) Icons.Default.Error else Icons.Default.Info,
                        contentDescription = null,
                        tint = when (entry.level) {
                            LogLevel.CRITICAL, LogLevel.ERROR -> StatusError
                            LogLevel.WARNING -> NeonAmber
                            else -> Color(0xFF00E676)
                        }
                    )
                    Text("Detail Log [${entry.level.name}]")
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Waktu: ${entry.formattedDateTime}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextPrimary)
                    Text("Module: ${entry.module.name}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = NeonCyan)
                    if (!entry.pipelineStage.isNullOrBlank()) {
                        Text("Tahap Pipeline: ${entry.pipelineStage}", fontSize = 12.sp, color = NeonPink)
                    }
                    if (!entry.associatedFile.isNullOrBlank()) {
                        Text("File Terkait: ${entry.associatedFile}", fontSize = 12.sp, color = TextSecondary)
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Pesan:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextPrimary)
                    Text(entry.message, fontSize = 13.sp, color = TextPrimary)

                    if (!entry.detail.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Detail Kesalahan:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextPrimary)
                        Text(
                            text = entry.detail,
                            fontSize = 12.sp,
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(StudioSurfaceElevated)
                                .padding(8.dp)
                        )
                    }

                    if (!entry.stackTrace.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Stack Trace:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = StatusError)
                        Text(
                            text = entry.stackTrace,
                            fontSize = 10.sp,
                            color = TextMuted,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(StudioDarkBg)
                                .padding(8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val formatted = """
                            WAKTU: ${entry.formattedDateTime}
                            LEVEL: ${entry.level.name}
                            MODULE: ${entry.module.name}
                            PESAN: ${entry.message}
                            DETAIL: ${entry.detail ?: "-"}
                            STAGE: ${entry.pipelineStage ?: "-"}
                            FILE: ${entry.associatedFile ?: "-"}
                            STACK TRACE:
                            ${entry.stackTrace ?: "None"}
                        """.trimIndent()
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Log Detail", formatted)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Detail disalin ke clipboard!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Salin Detail")
                }
            },
            dismissButton = {
                TextButton(onClick = { detailEntry = null }) {
                    Text("Tutup")
                }
            }
        )
    }
}
