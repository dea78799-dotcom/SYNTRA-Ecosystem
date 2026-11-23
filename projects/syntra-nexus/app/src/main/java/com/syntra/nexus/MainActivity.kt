package com.syntra.nexus

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    var pairingRequest by remember { mutableStateOf<PairingRequest?>(null) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pairingError by remember { mutableStateOf<String?>(null) }
    var scannedInvitation by remember { mutableStateOf<ParsedPairingInvitation?>(null) }
    var deviceIdentity by remember { mutableStateOf<DeviceIdentityStore.DeviceIdentity?>(null) }
    val context = LocalContext.current

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
                    onClick = {
                        try {
                            val request = PairingRequest.create()
                            pairingRequest = request
                            qrBitmap = QrCodeRenderer.render(request.payload)
                            pairingError = null
                        } catch (_: Exception) {
                            pairingError = "Không thể tạo mã ghép đôi. Hãy thử lại."
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Tạo mã ghép đôi")
                }
            }

            item {
                Button(
                    onClick = {
                        val options = GmsBarcodeScannerOptions.Builder()
                            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                            .build()
                        GmsBarcodeScanning.getClient(context, options)
                            .startScan()
                            .addOnSuccessListener { barcode ->
                                val rawValue = barcode.rawValue
                                if (rawValue.isNullOrBlank()) {
                                    pairingError = "Mã QR không chứa dữ liệu."
                                } else {
                                    try {
                                        scannedInvitation = PairingPayloadParser.parse(rawValue)
                                        pairingError = null
                                    } catch (_: IllegalArgumentException) {
                                        pairingError = "Mã QR không hợp lệ hoặc đã hết hạn. Hãy yêu cầu tạo mã mới."
                                    }
                                }
                            }
                            .addOnFailureListener {
                                pairingError = "Không thể mở trình quét QR. Hãy kiểm tra Google Play services và thử lại."
                            }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Rounded.QrCodeScanner, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Quét mã QR")
                }
            }

            item {
                Button(
                    onClick = {
                        try {
                            deviceIdentity = DeviceIdentityStore().getOrCreateIdentity()
                            pairingError = null
                        } catch (_: Exception) {
                            pairingError = "Không thể tạo danh tính bảo mật trên thiết bị này."
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Tạo danh tính thiết bị")
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
                    text = "Prototype 0.7-dev · xác minh chữ ký hai chiều, chưa có ghép đôi LAN",
                    color = NexusMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
                )
            }
        }
    }

    pairingError?.let { message ->
        AlertDialog(
            onDismissRequest = { pairingError = null },
            title = { Text("Có lỗi") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { pairingError = null }) { Text("Đóng") }
            }
        )
    }

    deviceIdentity?.let { identity ->
        AlertDialog(
            onDismissRequest = { deviceIdentity = null },
            title = { Text("Danh tính thiết bị") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Khóa riêng đã được tạo trong Android Keystore và không được xuất ra ngoài.")
                    Text("SHA-256 fingerprint:", color = NexusMuted, fontSize = 12.sp)
                    Text(identity.fingerprint.chunked(4).joinToString(" "), color = NexusGold, fontSize = 12.sp)
                    Text(
                        "Đây chỉ là danh tính cục bộ. Chưa xác thực thiết bị khác, chưa ghép đôi và chưa có kết nối.",
                        color = NexusMuted,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { deviceIdentity = null }) { Text("Đóng") }
            }
        )
    }

    scannedInvitation?.let { invitation ->
        AlertDialog(
            onDismissRequest = { scannedInvitation = null },
            title = { Text("Mã mời hợp lệ") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Đã đọc được mã mời SYNTRA NEXUS.")
                    Text("Mã yêu cầu: ${invitation.requestId.take(8)}…", color = NexusMuted, fontSize = 12.sp)
                    Text("Mã còn hiệu lực và đúng định dạng.", color = NexusGold, fontSize = 13.sp)
                    Text(
                        "Chưa có kết nối hoặc ghép đôi nào được tạo. Bước xác nhận từ cả hai thiết bị sẽ được triển khai tiếp theo.",
                        color = NexusMuted,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { scannedInvitation = null }) { Text("Đóng") }
            }
        )
    }

    val request = pairingRequest
    val bitmap = qrBitmap
    if (request != null && bitmap != null) {
        val expiry = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            .format(Date(request.expiresAtMillis))
        AlertDialog(
            onDismissRequest = {
                pairingRequest = null
                qrBitmap = null
            },
            title = { Text("Mã mời ghép đôi") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Mã QR ghép đôi tạm thời",
                        modifier = Modifier.size(220.dp)
                    )
                    Text("Mã có hiệu lực trong 2 phút.", fontWeight = FontWeight.SemiBold)
                    Text("Chỉ hiển thị mã cho thiết bị bạn muốn ghép đôi. Không đăng ảnh QR công khai.", color = NexusMuted, fontSize = 12.sp)
                    Text("Hết hạn lúc $expiry", color = NexusMuted, fontSize = 12.sp)
                    Text(
                        "Đây mới là mã mời thử nghiệm. Thiết bị khác chưa thể quét và kết nối trong bản này.",
                        color = NexusMuted,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        val next = PairingRequest.create()
                        pairingRequest = next
                        qrBitmap = QrCodeRenderer.render(next.payload)
                    } catch (_: Exception) {
                        pairingRequest = null
                        qrBitmap = null
                        pairingError = "Không thể tạo mã mới. Hãy thử lại."
                    }
                }) {
                    Text("Tạo mã mới")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    pairingRequest = null
                    qrBitmap = null
                }) {
                    Text("Đóng")
                }
            }
        )
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
