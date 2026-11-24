package com.syntra.nexus

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat

@Composable
fun ScreenShareConsentCard() {
    val context = LocalContext.current
    val projectionManager = remember(context) {
        context.getSystemService(MediaProjectionManager::class.java)
    }
    var sessionActive by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val captureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            val serviceIntent = Intent(context, ScreenShareConsentService::class.java).apply {
                putExtra(ScreenShareConsentService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenShareConsentService.EXTRA_RESULT_DATA, data)
            }
            try {
                ContextCompat.startForegroundService(context, serviceIntent)
                sessionActive = true
                message = "Đã mở phiên cấp quyền. Chưa chụp khung hình, ghi hình hoặc truyền dữ liệu."
            } catch (_: Exception) {
                message = "Không thể khởi động phiên. Hãy thử lại."
            }
        } else {
            message = "Bạn đã từ chối hoặc hủy quyền chia sẻ màn hình."
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            captureLauncher.launch(projectionManager.createScreenCaptureIntent())
        } else {
            message = "Cần cho phép thông báo để hiển thị trạng thái phiên và nút dừng."
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1D22)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Screen Share · 0.11", color = Color(0xFFD8B56D), fontWeight = FontWeight.Bold)
            Text(
                "Thử nghiệm luồng xin quyền MediaProjection. Chỉ bắt đầu sau khi bạn nhấn nút và xác nhận hộp thoại Android.",
                fontSize = 12.sp,
                color = Color(0xFF9A9DA6)
            )
            Text(
                if (sessionActive) "Phiên quyền đang hoạt động trên thiết bị này."
                else "Chưa có phiên chia sẻ màn hình.",
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Giới hạn hiện tại: chưa có kết nối LAN nên NEXUS không chụp, lưu, phát trực tiếp hoặc gửi khung hình sang thiết bị khác.",
                fontSize = 11.sp,
                color = Color(0xFF9A9DA6)
            )
            message?.let { Text(it, fontSize = 12.sp, color = Color(0xFFD8B56D)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !sessionActive,
                    onClick = {
                        if (Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            captureLauncher.launch(projectionManager.createScreenCaptureIntent())
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Xin quyền")
                }
                Button(
                    enabled = sessionActive,
                    onClick = {
                        context.startService(
                            Intent(context, ScreenShareConsentService::class.java).apply {
                                action = ScreenShareConsentService.ACTION_STOP
                            }
                        )
                        sessionActive = false
                        message = "Đã gửi lệnh dừng phiên trên thiết bị này."
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Dừng phiên")
                }
            }
        }
    }
}
