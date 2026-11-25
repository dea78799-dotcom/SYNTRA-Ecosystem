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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
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
    val clipboardManager = LocalClipboardManager.current
    val tokenRegistry = remember(context) {
        OneTimePairingTokenRegistry(persistence = EncryptedPairingTokenPersistence(context))
    }
    val pairingTransactionLoad = remember(context) {
        runCatching {
            PairingTransactionCoordinator(EncryptedPairingTransactionPersistence(context))
        }
    }
    val trustedDeviceLoad = remember(context) {
        runCatching { TrustedDeviceRegistry(EncryptedTrustedDevicePersistence(context)) }
    }
    val trustedDeviceRegistry = trustedDeviceLoad.getOrNull()
    val capabilityGrantLoad = remember(context) {
        runCatching { CapabilityGrantRegistry(EncryptedCapabilityGrantPersistence(context)) }
    }

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
                        text = "Prototype 0.11.35-dev · quản lý thiết bị và quyền cục bộ",
                        color = NexusMuted,
                        fontSize = 14.sp
                    )
                }
            }

            item {
                PairingTransactionStatusCard(
                    coordinator = pairingTransactionLoad.getOrNull(),
                    loadError = pairingTransactionLoad.exceptionOrNull()?.message
                )
            }

            item {
                TrustedDevicesCard(
                    registry = trustedDeviceRegistry,
                    loadError = trustedDeviceLoad.exceptionOrNull()?.message
                )
            }

            item {
                CapabilityGrantsCard(
                    registry = capabilityGrantLoad.getOrNull(),
                    loadError = capabilityGrantLoad.exceptionOrNull()?.message
                )
            }

            item {
                Button(
                    onClick = {
                        try {
                            val request = PairingRequest.create()
                            val parsed = PairingPayloadParser.parse(request.payload)
                            check(tokenRegistry.register(parsed)) {
                                "Invitation token already exists in the local registry."
                            }
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
                ActivityMonitorCard()
            }

            item {
                ScreenShareConsentCard()
            }

            item {
                LanDiscoveryCard()
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
                    text = "Prototype 0.11.34-dev · quản lý phiên PING/PONG; chưa mở kết nối LAN",
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
                    Text(PairingIdentityCrypto.formatFingerprint(identity.fingerprint), color = NexusGold, fontSize = 12.sp)
                    Text(
                        "Đây chỉ là danh tính cục bộ. Chưa xác thực thiết bị khác, chưa ghép đôi và chưa có kết nối.",
                        color = NexusMuted,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { deviceIdentity = null }) { Text("Đóng") }
            },
            dismissButton = {
                TextButton(onClick = {
                    clipboardManager.setText(AnnotatedString(identity.fingerprint))
                }) { Text("Sao chép mã SHA-256") }
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
private fun PairingTransactionStatusCard(
    coordinator: PairingTransactionCoordinator?,
    loadError: String?
) {
    var revision by remember(coordinator) { mutableStateOf(0) }
    val snapshot = remember(coordinator, revision) { coordinator?.snapshot() }
    var operationError by remember { mutableStateOf<String?>(null) }

    Card(
        colors = CardDefaults.cardColors(containerColor = NexusSurface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Trạng thái ghép đôi", fontWeight = FontWeight.SemiBold)
            when {
                loadError != null -> Text(
                    "Không thể mở kho giao dịch mã hóa. NEXUS đã khóa thao tác khôi phục để tránh bỏ qua lỗi dữ liệu.",
                    color = Color(0xFFFF8A80),
                    fontSize = 12.sp
                )
                coordinator == null -> Text(
                    "Kho giao dịch chưa sẵn sàng.",
                    color = NexusMuted,
                    fontSize = 12.sp
                )
                snapshot == null -> Text(
                    "Không có giao dịch ghép đôi đang lưu.",
                    color = NexusMuted,
                    fontSize = 12.sp
                )
                snapshot.state == PairingTransactionState.RECOVERY_REQUIRED -> {
                    Text(
                        "Có giao dịch cũ cần xử lý. Phiên trước không được tiếp tục tự động; hãy tạo lời mời QR mới.",
                        color = Color(0xFFFFC66D),
                        fontSize = 12.sp
                    )
                    TextButton(onClick = {
                        val result = coordinator.clearTerminal()
                        if (result == PairingTransactionResult.UPDATED) {
                            operationError = null
                            revision++
                        } else {
                            operationError = "Không thể xóa giao dịch cũ. Dữ liệu vẫn được giữ nguyên."
                        }
                    }) { Text("Xóa giao dịch gián đoạn") }
                }
                else -> Text(
                    "Trạng thái: ${snapshot.state.name}",
                    color = NexusMuted,
                    fontSize = 12.sp
                )
            }
            operationError?.let {
                Text(it, color = Color(0xFFFF8A80), fontSize = 12.sp)
            }
            Text(
                "Giao dịch chỉ lưu metadata đã mã hóa; không lưu token QR hoặc khóa riêng.",
                color = NexusMuted,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun TrustedDevicesCard(
    registry: TrustedDeviceRegistry?,
    loadError: String?
) {
    var records by remember(registry) {
        mutableStateOf(registry?.allRecords().orEmpty())
    }
    var filter by remember { mutableStateOf("ACTIVE") }
    var selectedForRevocation by remember { mutableStateOf<TrustedDeviceRecord?>(null) }
    var selectedForDetails by remember { mutableStateOf<TrustedDeviceRecord?>(null) }
    var selectedForRename by remember { mutableStateOf<TrustedDeviceRecord?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var operationError by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    val activeCount = records.count { !it.isRevoked }
    val visibleRecords = when (filter) {
        "REVOKED" -> records.filter { it.isRevoked }
        "ALL" -> records
        else -> records.filterNot { it.isRevoked }
    }

    fun refresh() {
        if (registry == null) return
        try {
            records = registry.allRecords()
            operationError = null
        } catch (_: Exception) {
            operationError = "Không thể tải danh sách thiết bị."
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = NexusSurface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Thiết bị đáng tin cậy", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (loadError != null) "Không thể xác minh dữ liệu lưu trữ"
                        else "$activeCount đang được tin cậy · ${records.size} bản ghi",
                        color = NexusMuted,
                        fontSize = 12.sp
                    )
                }
                TextButton(onClick = { refresh() }, enabled = registry != null) {
                    Text("Làm mới")
                }
            }

            if (loadError != null) {
                Text(
                    "Registry mã hóa không đọc được. Dữ liệu không được coi là danh sách trống. Chi tiết: ${loadError.take(140)}",
                    color = Color(0xFFFF8A80),
                    fontSize = 12.sp
                )
            } else if (registry == null) {
                Text("Registry chưa sẵn sàng.", color = NexusMuted, fontSize = 13.sp)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("ACTIVE" to "Đang tin cậy", "REVOKED" to "Đã thu hồi", "ALL" to "Tất cả").forEach { (value, label) ->
                        TextButton(onClick = { filter = value }) {
                            Text(
                                label,
                                color = if (filter == value) NexusGold else NexusMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                if (visibleRecords.isEmpty()) {
                    Text(
                        when (filter) {
                            "REVOKED" -> "Chưa có thiết bị nào bị thu hồi."
                            "ALL" -> "Registry chưa có bản ghi thiết bị."
                            else -> "Chưa có thiết bị nào được xác minh. Quét QR hoặc tìm thấy thiết bị trên LAN không tự động thêm thiết bị vào đây."
                        },
                        color = NexusMuted,
                        fontSize = 13.sp
                    )
                } else {
                    visibleRecords.forEachIndexed { index, device ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(device.displayName, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "Đã xác minh: " + SimpleDateFormat(
                                            "dd/MM/yyyy HH:mm",
                                            Locale.getDefault()
                                        ).format(Date(device.trustedAtMillis)),
                                        color = NexusMuted,
                                        fontSize = 11.sp
                                    )
                                    if (device.isRevoked) {
                                        Text("Đã thu hồi", color = Color(0xFFFF8A80), fontSize = 11.sp)
                                    }
                                }
                                TextButton(onClick = { selectedForDetails = device }) {
                                    Text("Chi tiết")
                                }
                            }
                            Text(
                                PairingIdentityCrypto.formatFingerprint(device.fingerprint),
                                color = NexusGold,
                                fontSize = 11.sp
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(device.fingerprint))
                                    }
                                ) { Text("Sao chép mã") }
                                TextButton(
                                    onClick = {
                                        selectedForRename = device
                                        renameInput = device.displayName
                                        operationError = null
                                    }
                                ) { Text("Đổi tên") }
                                if (!device.isRevoked) {
                                    TextButton(onClick = { selectedForRevocation = device }) {
                                        Text("Thu hồi", color = Color(0xFFFF8A80))
                                    }
                                }
                            }
                            if (index != visibleRecords.lastIndex) {
                                androidx.compose.material3.HorizontalDivider(color = Color(0xFF34363D))
                            }
                        }
                    }
                }
            }

            operationError?.let {
                Text(it, color = Color(0xFFFF8A80), fontSize = 12.sp)
            }
            Text(
                "Thu hồi chỉ cập nhật registry trên điện thoại này. Chưa có lệnh thu hồi từ xa vì NEXUS chưa có kết nối mạng xác thực.",
                color = NexusMuted,
                fontSize = 11.sp
            )
        }
    }

    selectedForDetails?.let { device ->
        AlertDialog(
            onDismissRequest = { selectedForDetails = null },
            title = { Text("Chi tiết thiết bị") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(device.displayName, fontWeight = FontWeight.SemiBold)
                    Text("Fingerprint SHA-256", color = NexusMuted, fontSize = 11.sp)
                    Text(PairingIdentityCrypto.formatFingerprint(device.fingerprint), color = NexusGold, fontSize = 12.sp)
                    Text("Ngày tin cậy: " + SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(device.trustedAtMillis)), fontSize = 12.sp)
                    Text(
                        device.revokedAtMillis?.let {
                            "Ngày thu hồi: " + SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(it))
                        } ?: "Trạng thái: đang được tin cậy",
                        color = if (device.isRevoked) Color(0xFFFF8A80) else NexusMuted,
                        fontSize = 12.sp
                    )
                    Text("Khóa công khai: ${device.publicKeyEncoded.size} byte. Không hiển thị khóa riêng.", color = NexusMuted, fontSize = 11.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(device.fingerprint))
                }) { Text("Sao chép fingerprint") }
            },
            dismissButton = {
                TextButton(onClick = { selectedForDetails = null }) { Text("Đóng") }
            }
        )
    }

    selectedForRename?.let { device ->
        AlertDialog(
            onDismissRequest = { selectedForRename = null },
            title = { Text("Đổi tên thiết bị") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tên chỉ dùng để hiển thị trên điện thoại này. Giới hạn 48 ký tự.")
                    OutlinedTextField(
                        value = renameInput,
                        onValueChange = { renameInput = it.take(48) },
                        singleLine = true,
                        label = { Text("Tên thiết bị") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = renameInput.isNotBlank(),
                    onClick = {
                        try {
                            val changed = registry?.renameDevice(device.fingerprint, renameInput) == true
                            if (!changed) {
                                operationError = "Không tìm thấy thiết bị để đổi tên."
                            } else {
                                refresh()
                            }
                        } catch (_: Exception) {
                            operationError = "Không thể lưu tên mới. Dữ liệu cũ được giữ nguyên."
                        }
                        selectedForRename = null
                    }
                ) { Text("Lưu tên") }
            },
            dismissButton = {
                TextButton(onClick = { selectedForRename = null }) { Text("Hủy") }
            }
        )
    }

    selectedForRevocation?.let { device ->
        AlertDialog(
            onDismissRequest = { selectedForRevocation = null },
            title = { Text("Thu hồi thiết bị?") },
            text = {
                Text(
                    "Thiết bị \"${device.displayName}\" sẽ bị đánh dấu đã thu hồi trên máy này. Thao tác không thể thay thế việc thu hồi từ xa."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        try {
                            val changed = registry?.revoke(device.fingerprint, System.currentTimeMillis()) == true
                            if (!changed) {
                                operationError = "Không tìm thấy thiết bị hoặc thiết bị đã được thu hồi."
                            } else {
                                refresh()
                            }
                        } catch (_: Exception) {
                            operationError = "Không thể lưu trạng thái thu hồi. Danh sách chưa được thay đổi."
                        }
                        selectedForRevocation = null
                    }
                ) {
                    Text("Xác nhận thu hồi", color = Color(0xFFFF8A80))
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedForRevocation = null }) { Text("Hủy") }
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
