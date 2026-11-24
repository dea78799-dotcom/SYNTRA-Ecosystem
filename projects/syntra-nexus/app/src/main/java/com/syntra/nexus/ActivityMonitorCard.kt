package com.syntra.nexus

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val MonitorSurface = Color(0xFF1B1D22)
private val MonitorGold = Color(0xFFD8B56D)
private val MonitorMuted = Color(0xFF9A9DA6)

@Composable
fun ActivityMonitorCard() {
    val context = LocalContext.current
    val repository = remember(context) { ActivityUsageRepository(context) }
    var permissionGranted by remember { mutableStateOf(repository.hasUsageAccess()) }
    var summary by remember { mutableStateOf<ScreenUsageSummary?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MonitorSurface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Activity Monitor", color = MonitorGold, fontWeight = FontWeight.Bold)
            Text(
                "Thống kê chỉ đọc trên điện thoại này. Chưa gửi dữ liệu sang thiết bị khác.",
                color = MonitorMuted,
                fontSize = 12.sp
            )

            if (!permissionGranted) {
                Text(
                    "Chưa cấp quyền Usage Access. NEXUS không thể đọc thời gian sử dụng ứng dụng.",
                    fontSize = 13.sp
                )
            } else {
                Text("Đã cấp Usage Access trên thiết bị này.", color = MonitorGold, fontSize = 12.sp)
            }

            summary?.let { data ->
                Text(
                    "Thời gian màn hình bật hôm nay: " +
                        (data.totalScreenOnMillis?.let(::formatDuration) ?: "Không có dữ liệu hệ thống"),
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (data.screenTimelineAvailable) {
                        "Dữ liệu sự kiện màn hình có sẵn; tổng thời gian có thể thiếu sau khi khởi động lại hoặc do giới hạn Android."
                    } else {
                        "Android không cung cấp sự kiện bật/tắt màn hình cho khoảng thời gian này."
                    },
                    color = MonitorMuted,
                    fontSize = 11.sp
                )
                Text("Ứng dụng sử dụng nhiều nhất hôm nay", fontWeight = FontWeight.SemiBold)
                if (data.appUsage.isEmpty()) {
                    Text("Chưa có dữ liệu ứng dụng.", color = MonitorMuted, fontSize = 12.sp)
                } else {
                    data.appUsage.take(6).forEach { app ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(app.appLabel, modifier = Modifier.weight(1f), fontSize = 12.sp)
                            Text(formatDuration(app.foregroundMillis), color = MonitorGold, fontSize = 12.sp)
                        }
                    }
                }
            }

            error?.let { Text(it, color = Color(0xFFFFB4AB), fontSize = 12.sp) }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Mở quyền")
                }
                Button(
                    onClick = {
                        permissionGranted = repository.hasUsageAccess()
                        summary = null
                        error = null
                        if (permissionGranted) {
                            try {
                                summary = repository.loadToday()
                            } catch (_: Exception) {
                                error = "Không đọc được thống kê. Hãy kiểm tra quyền Usage Access."
                            }
                        } else {
                            error = "Quyền chưa được cấp. Hãy bật quyền cho SYNTRA NEXUS trong Cài đặt."
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cập nhật")
                }
            }
        }
    }
}
