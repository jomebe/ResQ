package app.resq.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flashlight
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(
    onMicTap: () -> Unit = {},
    onOcrTap: () -> Unit = {},
    onFlashlight: () -> Unit = {},
    onSiren: () -> Unit = {},
    onCall119: () -> Unit = {},
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0F0F0F)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("ResQ", fontSize = 28.sp, color = Color(0xFFEF5350))
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.DarkGray)) {
                    Text("오프라인", modifier = Modifier.padding(8.dp), color = Color.White)
                }
            }

            Spacer(Modifier.height(24.dp))

            Box(modifier = Modifier.size(180.dp), contentAlignment = Alignment.Center) {
                Button(
                    onClick = onMicTap,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                    modifier = Modifier.size(180.dp)
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "mic", tint = Color.White, modifier = Modifier.size(80.dp))
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("음성 질문 시작하기", color = Color.LightGray)

            Spacer(Modifier.height(24.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                SimpleCard("재난 유형 선택") { }
                SimpleCard("안내문 촬영") { onOcrTap() }
                SimpleCard("텍스트 질문") { }
                SimpleCard("설정") { }
            }
        }
    }
}

@Composable
private fun SimpleCard(label: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .height(56.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B1B))
    ) {
        Row(modifier = Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Color.White, fontSize = 16.sp)
        }
    }
}
