package com.syntra.nexus

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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LanDiscoveryCard() {
    val context = LocalContext.current
    val peers = remember { mutableStateListOf<DiscoveredNexusPeer>() }
    var status by remember { mutableStateOf("Chưa bắt đầu quét LAN.") }
    var scanning by remember { mutableStateOf(false) }
    val controller = remember(context) {
        NsdLanDiscovery(
            context = context,
            onPeersChanged = { snapshot ->
                peers.clear()
                peers.addAll(snapshot)
            },
            onStatusChanged = { status = it }
        )
    }

    DisposableEffect(controller) {
        onDispose { controller.release() }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1D22)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("NEXUS LAN Link · 0.11.2", color = Color(0xFFD8B56D), fontWeight = FontWeight.Bold)
            Text(
                "Quét dịch vụ NEXUS trong cùng mạng Wi-Fi. Chỉ thiết bị quảng bá đúng loại dịch vụ mới được liệt kê.",
                fontSize = 12.sp,
                color = Color(0xFF9A9DA6)
            )
            Text(status, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !scanning,
                    onClick = {
                        scanning = true
                        controller.start()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Quét LAN") }
                Button(
                    enabled = scanning,
                    onClick = {
                        controller.stop()
                        scanning = false
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Dừng quét") }
            }
            if (peers.isEmpty()) {
                Text("Chưa tìm thấy dịch vụ NEXUS nào.", color = Color(0xFF9A9DA6), fontSize = 12.sp)
            } else {
                peers.forEach { peer ->
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(peer.serviceName, fontWeight = FontWeight.SemiBold)
                        Text("${peer.hostAddress}:${peer.port}", fontSize = 12.sp, color = Color(0xFFD8B56D))
                        Text("Chưa xác minh · Không tự kết nối", fontSize = 11.sp, color = Color(0xFF9A9DA6))
                    }
                }
            }
            Text(
                "Bảo mật: kết quả NSD có thể bị giả mạo. Bản này không mở socket, không gửi token, không ghép đôi và không truyền màn hình.",
                fontSize = 11.sp,
                color = Color(0xFF9A9DA6)
            )
        }
    }
}
