package com.syntra.nexus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun CapabilityGrantsCard(
    registry: CapabilityGrantRegistry?,
    loadError: String?
) {
    var grants by remember(registry) { mutableStateOf(registry?.snapshot().orEmpty()) }
    var selectedForRevocation by remember { mutableStateOf<CapabilityGrant?>(null) }
    var operationError by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        if (registry == null) return
        try {
            grants = registry.snapshot()
            operationError = null
        } catch (_: Exception) {
            operationError = "Không thể đọc danh sách quyền đã cấp."
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Quyền truy cập thiết bị", color = Color(0xFFF5F2EA))
                    Text(
                        if (loadError == null) grants.size.toString() + " grant đã lưu" else "Kho quyền chưa xác minh được",
                        color = Color(0xFF9A9DA6),
                        fontSize = 11.sp
                    )
                }
                TextButton(onClick = { refresh() }, enabled = registry != null && loadError == null) {
                    Text("Làm mới")
                }
            }

            if (loadError != null) {
                Text(
                    "Không thể mở kho quyền mã hóa. NEXUS khóa việc cấp và sử dụng quyền thay vì coi lỗi này là danh sách trống.",
                    color = Color(0xFFFF8A80),
                    fontSize = 12.sp
                )
            } else if (registry == null) {
                Text("Kho quyền chưa sẵn sàng.", color = Color(0xFF9A9DA6), fontSize = 12.sp)
            } else if (grants.isEmpty()) {
                Text(
                    "Chưa có quyền nào được cấp. Yêu cầu từ thiết bị khác chưa thể đến đây vì kết nối xác thực chưa được triển khai.",
                    color = Color(0xFF9A9DA6),
                    fontSize = 12.sp
                )
            } else {
                grants.forEachIndexed { index, grant ->
                    val request = grant.request
                    val expired = System.currentTimeMillis() >= request.expiresAtMillis
                    val status = when {
                        grant.revokedAtMillis != null -> "Đã thu hồi"
                        expired -> "Đã hết hạn"
                        else -> "Đã cấp · thời hạn ngắn"
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(request.capability.wireName, color = Color(0xFFD8B56D))
                        Text(
                            "Thiết bị: " + request.peerFingerprint.take(16) + "…",
                            color = Color(0xFF9A9DA6),
                            fontSize = 11.sp
                        )
                        Text(
                            status + " · hết hạn " + SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()).format(Date(request.expiresAtMillis)),
                            color = if (grant.revokedAtMillis != null) Color(0xFFFF8A80) else Color(0xFF9A9DA6),
                            fontSize = 11.sp
                        )
                        if (grant.revokedAtMillis == null && !expired) {
                            TextButton(onClick = { selectedForRevocation = grant }) {
                                Text("Thu hồi grant", color = Color(0xFFFF8A80))
                            }
                        }
                    }
                    if (index != grants.lastIndex) {
                        androidx.compose.material3.HorizontalDivider(color = Color(0xFF34363D))
                    }
                }
            }

            operationError?.let {
                Text(it, color = Color(0xFFFF8A80), fontSize = 11.sp)
            }
            Text(
                "Mỗi grant chỉ áp dụng cho một capability, một phiên và đúng transcript ghép đôi. Thu hồi thiết bị tin cậy cũng làm grant mất hiệu lực.",
                color = Color(0xFF9A9DA6),
                fontSize = 11.sp
            )
        }
    }

    selectedForRevocation?.let { grant ->
        AlertDialog(
            onDismissRequest = { selectedForRevocation = null },
            title = { Text("Thu hồi quyền này?") },
            text = {
                Text("Grant " + grant.request.capability.wireName + " sẽ bị thu hồi trên thiết bị này. Thao tác được lưu trước khi giao diện cập nhật.")
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        val changed = registry?.revoke(
                            grant.request.grantId,
                            System.currentTimeMillis()
                        ) == true
                        if (!changed) {
                            operationError = "Grant không còn hiệu lực hoặc đã được thu hồi."
                        } else {
                            refresh()
                        }
                    } catch (_: Exception) {
                        operationError = "Không thể lưu trạng thái thu hồi. Dữ liệu cũ vẫn được giữ."
                    }
                    selectedForRevocation = null
                }) {
                    Text("Xác nhận thu hồi", color = Color(0xFFFF8A80))
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedForRevocation = null }) { Text("Hủy") }
            }
        )
    }
}
