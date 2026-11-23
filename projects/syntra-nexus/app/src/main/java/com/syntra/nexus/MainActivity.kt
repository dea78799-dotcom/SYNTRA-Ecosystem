package com.syntra.nexus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val NexusBackground = Color(0xFF101114)
private val NexusSurface = Color(0xFF1B1D22)
private val NexusGold = Color(0xFFD8B56D)
private val NexusMuted = Color(0xFF9A9DA6)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = NexusGold,
                    background = NexusBackground,
                    surface = NexusSurface,
                    onPrimary = Color(0xFF17130B),
                    onBackground = Color(0xFFF5F2EA),
                    onSurface = Color(0xFFF5F2EA)
                )
            ) {
                NexusDashboard()
            }
        }
    }
}

@Composable
private fun NexusDashboard() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = NexusBackground
    ) {
        LazyColumn(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        text = "SYNTRA NEXUS",
                        color = NexusGold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "Device Center",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Quản lý thiết bị đã được ghép nối",
                        color = NexusMuted,
                        fontSize = 14.sp
                    )
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = NexusSurface),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Thiết bị đã kết nối", color = NexusMuted, fontSize = 13.sp)
                            Text("0 thiết bị", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                            Text("Chưa có thiết bị nào được ghép nối", color = NexusMuted, fontSize = 12.sp)
                        }
                        Icon(
                            imageVector = Icons.Rounded.Devices,
                            contentDescription = null,
                            tint = NexusGold,
                            modifier = Modifier.size(42.dp)
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = { /* Pairing flow will be implemented in the next milestone. */ },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Thêm thiết bị")
                }
            }

            item {
                Text(
                    text = "Thiết bị của tôi",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            item {
                DevicePlaceholder(
                    icon = { Icon(Icons.Rounded.PhoneAndroid, contentDescription = null, tint = NexusGold) },
                    title = "Điện thoại Android",
                    subtitle = "Thiết bị này · Bảng điều khiển",
                    status = "Đang sử dụng"
                )
            }

            item {
                DevicePlaceholder(
                    icon = { Icon(Icons.Rounded.Tv, contentDescription = null, tint = NexusMuted) },
                    title = "TV",
                    subtitle = "Chưa chọn nền tảng TV",
                    status = "Chưa thiết lập"
                )
            }

            item {
                Text(
                    text = "Bản prototype 0.1.0 · Chưa có kết nối thiết bị thực",
                    color = NexusMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun DevicePlaceholder(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    status: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = NexusSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color(0xFF29251C), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = NexusMuted, fontSize = 12.sp)
            }
            Text(status, color = NexusGold, fontSize = 11.sp)
        }
    }
}
