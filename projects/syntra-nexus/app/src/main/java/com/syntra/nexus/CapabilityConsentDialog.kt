package com.syntra.nexus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Reusable consent prompt for a future authenticated incoming-request flow.
 * The caller must provide an already transcript-verified request and must
 * create signatures only inside onApprove, after the local user taps Allow.
 */
@Composable
internal fun CapabilityConsentDialog(
    request: CapabilityGrantRequest,
    peerDisplayName: String,
    onApprove: () -> Unit,
    onDeny: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDeny,
        title = { Text("Yêu cầu quyền truy cập") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Thiết bị yêu cầu: " + peerDisplayName)
                Text("Quyền: " + request.capability.wireName, color = Color(0xFFD8B56D))
                Text("Phiên: " + request.sessionId.take(10) + "…", color = Color(0xFF9A9DA6), fontSize = 12.sp)
                Text(
                    "Tự hết hạn: " + SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                        .format(Date(request.expiresAtMillis)),
                    color = Color(0xFF9A9DA6),
                    fontSize = 12.sp
                )
                Text("Chỉ cấp đúng quyền được nêu. Không cấp quyền truy cập toàn bộ thiết bị.", fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = onApprove) { Text("Cho phép") }
        },
        dismissButton = {
            TextButton(onClick = onDeny) { Text("Từ chối", color = Color(0xFFFF8A80)) }
        }
    )
}
