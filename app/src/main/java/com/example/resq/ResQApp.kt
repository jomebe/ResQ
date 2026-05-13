package com.example.resq

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Anchor
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.resq.ui.theme.ResQFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.Locale
import org.json.JSONObject
import org.json.JSONArray

private const val MODEL_DOWNLOAD_URL = "https://huggingface.co/unsloth/gemma-4-E2B-it-GGUF/resolve/main/gemma-4-E2B-it-IQ4_XS.gguf?download=1"

private enum class Screen {
    Onboard,
    Home,
    Disaster,
    Guidance,
    CameraLoading,
    TextQuery,
    Settings
}

private enum class DisasterId(val id: String) {
    Earthquake("earthquake"),
    Fire("fire"),
    Flood("flood"),
    Typhoon("typhoon"),
    Landslide("landslide"),
    Tsunami("tsunami"),
    HeavySnow("heavy_snow"),
    HazardRelease("hazard_release");

    companion object {
        fun fromId(id: String): DisasterId {
            return entries.firstOrNull { it.id == id } ?: Earthquake
        }
    }
}

private data class DisasterDefinition(
    val id: DisasterId,
    val label: String,
    val cardDescription: String,
    val icon: ImageVector,
    val headline: String,
    val steps: List<String>
)

private data class QuickTag(val id: String, val label: String, val value: String)

private data class AnalysisResult(
    val disasterId: DisasterId,
    val warning: String? = null
)

private data class VoiceAnalysisResult(
    val recognizedText: String,
    val disasterId: DisasterId,
    val disasterLabel: String,
    val recommendation: String,
    val confidence: Double
)

private data class AlertState(val title: String, val message: String)

private val DisasterCatalog = listOf(
    DisasterDefinition(
        id = DisasterId.Earthquake,
        label = "지진",
        cardDescription = "지진 발생시 행동요령\n대피 방법 등",
        icon = Icons.Outlined.Waves,
        headline = "건물 밖으로 대피해주세요.",
        steps = listOf(
            "흔들림이 느껴지면 책상 아래로 들어가 머리를 보호하세요.",
            "흔들림이 멈추면 계단으로 건물 밖의 넓은 장소로 대피하세요.",
            "엘리베이터는 사용하지 마세요.",
            "밖으로 나온 뒤 유리·간판 등 낙하물을 피하세요."
        )
    ),
    DisasterDefinition(
        id = DisasterId.Fire,
        label = "화재",
        cardDescription = "화재 발생시 행동요령\n연기 대응 등",
        icon = Icons.Outlined.Warning,
        headline = "연기를 피해 낮은 자세로 대피하세요.",
        steps = listOf(
            "불을 발견하면 큰 소리로 알리고 119에 신고하세요.",
            "문 손잡이를 만져 보고 뜨겁지 않을 때만 문을 열고 나가세요.",
            "연기가 있으면 젖은 천으로 코와 입을 막고 낮은 자세로 이동하세요.",
            "엘리베이터는 타지 말고 비상구·계단을 이용하세요."
        )
    ),
    DisasterDefinition(
        id = DisasterId.Flood,
        label = "홍수",
        cardDescription = "침수·홍수 대비\n대피 요령",
        icon = Icons.Outlined.WaterDrop,
        headline = "높고 안전한 곳으로 즉시 대피하세요.",
        steps = listOf(
            "침수 경보·대피 명령을 확인하고 지정된 대피소로 이동하세요.",
            "전기 차단기를 내리고 가스를 잠그세요.",
            "물에 들어가지 말고 급류·맨홀 근처를 피하세요.",
            "차량으로 침수 도로를 통과하지 마세요."
        )
    ),
    DisasterDefinition(
        id = DisasterId.Typhoon,
        label = "태풍",
        cardDescription = "강풍·호우 대비\n창문·야외 정리",
        icon = Icons.Outlined.Air,
        headline = "실내에서 창문에서 멀리 떨어져 주세요.",
        steps = listOf(
            "야외 물건을 고정하거나 실내로 옮기세요.",
            "창문·문을 잠그고 커튼·셔터가 있으면 내려주세요.",
            "강풍 시 외출을 삼가고 안전한 실내에 머무르세요.",
            "침수 위험이 있으면 층고가 높은 곳으로 이동을 준비하세요."
        )
    ),
    DisasterDefinition(
        id = DisasterId.Landslide,
        label = "산사태",
        cardDescription = "산사태 징후\n긴급 대피",
        icon = Icons.Outlined.ReportProblem,
        headline = "비탈·도랑에서 멀리 떨어지세요.",
        steps = listOf(
            "땅 균열·작은 낙석·이상한 소리가 나면 즉시 대피하세요.",
            "계곡·옹벽·사면 인근에 머무르지 마세요.",
            "대피 시 안전한 경로만 이용하고 차량은 가능한 한 피하세요.",
            "대피 후에는 추가 붕괴 위험이 있으니 안내에 따르세요."
        )
    ),
    DisasterDefinition(
        id = DisasterId.Tsunami,
        label = "쓰나미",
        cardDescription = "지진해일 대비\n고지대 대피",
        icon = Icons.Outlined.Anchor,
        headline = "해변·저지대를 떠나 높은 곳으로 가세요.",
        steps = listOf(
            "지진 직후 해안·강 하구 근처에 있지 마세요.",
            "방송·재난 문자를 확인하고 즉시 고지대로 이동하세요.",
            "차량 이동이 어려우면 도보로라도 높은 곳을 향하세요.",
            "해일 경보가 해제될 때까지 안전한 곳에 머무르세요."
        )
    ),
    DisasterDefinition(
        id = DisasterId.HeavySnow,
        label = "대설",
        cardDescription = "폭설·빙판\n교통·난방 안전",
        icon = Icons.Outlined.Cloud,
        headline = "외출을 줄이고 난방·환기를 안전하게 하세요.",
        steps = listOf(
            "필수 외출만 하고 빙판길·적설 구간을 피하세요.",
            "난방기구 주변 가연물을 치우고 환기를 자주 하세요.",
            "지붕·발코니의 눈 무너짐에 주의하세요.",
            "차량은 미리 제설·타이어 상태를 점검하세요."
        )
    ),
    DisasterDefinition(
        id = DisasterId.HazardRelease,
        label = "유해물질",
        cardDescription = "화학·방사능 등\n대피·대기 지침",
        icon = Icons.Outlined.ReportProblem,
        headline = "방송 안내에 따라 실내 대피 또는 지정 방향으로 이동하세요.",
        steps = listOf(
            "공식 방송·재난 문자의 지시를 우선 따르세요.",
            "실내 대피 시 창문·문을 닫고 환기를 끄세요.",
            "현장 촬영·접근은 위험하니 삼가세요.",
            "대피 시 피부 노출을 최소화하고 안내된 세척 방법을 따르세요."
        )
    )
)

private val TextQuickTags = listOf(
    QuickTag("tag-earthquake", "지진", DisasterId.Earthquake.id),
    QuickTag("tag-fire", "화재", DisasterId.Fire.id),
    QuickTag("tag-blackout", "정전", DisasterId.HazardRelease.id),
    QuickTag("tag-emergency-1", "응급", "emergency"),
    QuickTag("tag-emergency-2", "응급", "emergency"),
    QuickTag("tag-emergency-3", "응급", "emergency")
)

private fun getDisasterById(id: DisasterId): DisasterDefinition {
    return DisasterCatalog.firstOrNull { it.id == id } ?: DisasterCatalog.first()
}

private fun detectDisasterFromKeywords(text: String): DisasterDetectResult {
    val normalized = text.lowercase(Locale.getDefault())
    val types = listOf(
        DisasterDetectResult(DisasterId.Earthquake, "지진", 0.7, listOf("지진", "진동", "흔들", "흔들림", "진동이", "earthquake", "shake")),
        DisasterDetectResult(DisasterId.Fire, "화재", 0.7, listOf("불", "화재", "불이", "불나", "불이야", "연기", "화염", "불꽃", "fire", "smoke")),
        DisasterDetectResult(DisasterId.Flood, "홍수", 0.7, listOf("물", "홍수", "침수", "물이", "물이차", "물이들어와", "flood", "water")),
        DisasterDetectResult(DisasterId.Typhoon, "태풍", 0.7, listOf("바람", "태풍", "강풍", "폭풍", "호우", "heavy rain", "typhoon", "wind")),
        DisasterDetectResult(DisasterId.Landslide, "산사태", 0.7, listOf("산사태", "붕괴", "무너지", "흙", "땅", "landslide")),
        DisasterDetectResult(DisasterId.Tsunami, "쓰나미", 0.7, listOf("쓰나미", "해일", "해일경보", "tsunami")),
        DisasterDetectResult(DisasterId.HeavySnow, "대설", 0.7, listOf("눈", "대설", "폭설", "눈사태", "빙판", "snow")),
        DisasterDetectResult(DisasterId.HazardRelease, "유해물질", 0.7, listOf("유해", "독", "가스", "가스누출", "가스냄새", "화학", "유출", "누출", "폭발", "hazard", "gas"))
    )

    for (type in types) {
        if (type.keywords.any { normalized.contains(it) }) {
            return type
        }
    }

    return DisasterDetectResult(DisasterId.Earthquake, "지진", 0.3, emptyList())
}

private data class DisasterDetectResult(
    val id: DisasterId,
    val label: String,
    val confidence: Double,
    val keywords: List<String>
)

private fun analyzeVoiceInputLocally(userInput: String): AnalysisResult {
    val normalized = userInput.lowercase(Locale.getDefault())

    if (listOf("지진", "진동", "흔들", "흔들림", "진동이", "지진왔", "earthquake", "quake", "shake").any { normalized.contains(it) }) {
        return AnalysisResult(
            DisasterId.Earthquake,
            "책상 아래로 숨고 문을 열어두세요. 흔들림이 멈출 때까지 자세를 유지하세요."
        )
    }

    if (listOf("화재", "불", "불나", "불이야", "연기", "화염", "불꽃", "타고", "불타", "fire", "smoke").any { normalized.contains(it) }) {
        return AnalysisResult(
            DisasterId.Fire,
            "연기 유입을 막고 낮은 자세로 비상구 방향으로 이동하세요."
        )
    }

    if (listOf("홍수", "침수", "물이", "물이차", "물이들어와", "물에잠겨", "물불어", "내려", "flood", "water").any { normalized.contains(it) }) {
        return AnalysisResult(
            DisasterId.Flood,
            "급류 구간과 맨홀 주변을 피하고 높은 위치로 이동하세요."
        )
    }

    if (listOf("정전", "정전됐", "전기끌림", "정격", "blackout", "power", "전기나감").any { normalized.contains(it) }) {
        return AnalysisResult(
            DisasterId.HazardRelease,
            "보안등과 휴대폰 손전등을 켜고 천천히 움직이세요."
        )
    }

    if (listOf("쓰나미", "해일", "해일경보", "밀려온다", "tsunami", "wave").any { normalized.contains(it) }) {
        return AnalysisResult(
            DisasterId.Tsunami,
            "해안 주민은 즉시 높은 지대로 대피하세요."
        )
    }

    return AnalysisResult(
        DisasterId.Earthquake,
        "상황이 위험하다고 판단되면 즉시 신고하세요."
    )
}

private fun analyzeCapturedImage(uri: String): AnalysisResult {
    val normalizedUri = uri.lowercase(Locale.getDefault())
    val rules = listOf(
        Pair(listOf("fire", "flame", "smoke", "화재", "불"), DisasterId.Fire),
        Pair(listOf("flood", "rain", "water", "침수", "홍수"), DisasterId.Flood),
        Pair(listOf("quake", "earth", "crack", "지진", "붕괴"), DisasterId.Earthquake)
    )

    for ((keywords, disasterId) in rules) {
        if (keywords.any { normalizedUri.contains(it) }) {
            val warning = when (disasterId) {
                DisasterId.Fire -> "연기 흡입 위험이 있으니 젖은 천으로 호흡기를 보호하세요."
                DisasterId.Flood -> "급류와 맨홀 근처 접근을 피하고 높은 곳으로 이동하세요."
                else -> "지진 심한 경우 건물이 붕괴할 수 있어요!"
            }
            return AnalysisResult(disasterId, warning)
        }
    }

    return AnalysisResult(DisasterId.Earthquake, "구조물 균열과 낙하물 위험에 주의하세요.")
}

private fun analyzeTextQuery(inputText: String, selectedTag: String): AnalysisResult {
    val normalized = inputText.lowercase(Locale.getDefault())
    if (selectedTag != "emergency") {
        return AnalysisResult(
            DisasterId.fromId(selectedTag),
            "강한 흔들림·연기·침수 등 2차 위험이 없는지 주변을 먼저 확인하세요."
        )
    }

    val rules = listOf(
        Pair(listOf("화재", "불", "불나", "불이야", "연기", "화염", "불꽃", "smoke", "fire"), DisasterId.Fire),
        Pair(listOf("홍수", "침수", "물이", "물이차", "물이들어와", "flood", "water"), DisasterId.Flood),
        Pair(listOf("태풍", "강풍", "폭풍", "호우", "typhoon", "wind"), DisasterId.Typhoon),
        Pair(listOf("지진", "진동", "흔들", "붕괴", "quake", "earth", "shake"), DisasterId.Earthquake)
    )

    for ((keywords, disasterId) in rules) {
        if (keywords.any { normalized.contains(it) }) {
            val warning = when (disasterId) {
                DisasterId.Fire -> "연기 유입을 막고 낮은 자세로 비상구 방향으로 이동하세요."
                DisasterId.Flood -> "급류 구간과 맨홀 주변을 피하고 높은 위치로 이동하세요."
                DisasterId.Typhoon -> "창문 주변에서 떨어지고 낙하물 위험 지역을 피하세요."
                DisasterId.Earthquake -> "지진 심한 경우 건물이 무너질수 있어요!"
                else -> "정확한 위치와 주변 위험요소를 계속 공유해 주세요."
            }
            return AnalysisResult(disasterId, warning)
        }
    }

    return AnalysisResult(DisasterId.Earthquake, "정확한 위치와 주변 위험요소를 계속 공유해 주세요.")
}

@Composable
fun ResQApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val offlineLlm = remember { OfflineLlmManager(context) }
    val offlineState by offlineLlm.state.collectAsState()
    val voiceController = remember { VoiceInputController(context) }
    val torchController = remember { TorchController(context) }

    var screen by remember { mutableStateOf(Screen.Onboard) }
    var selectedDisasterId by remember { mutableStateOf(DisasterId.Earthquake) }
    var disasterPickerSource by remember { mutableStateOf("home") }
    var guidanceTitle by remember { mutableStateOf<String?>(null) }
    var statusText by remember { mutableStateOf("오프라인") }
    var analysisWarning by remember { mutableStateOf<String?>(null) }
    var guidanceBackTarget by remember { mutableStateOf("home") }
    var textQuestion by remember { mutableStateOf("") }
    var selectedQuickTagId by remember { mutableStateOf(TextQuickTags.first().id) }
    var language by remember { mutableStateOf("ko") }
    var ttsEnabled by remember { mutableStateOf(true) }
    var voiceType by remember { mutableStateOf("natural") }
    var isListening by remember { mutableStateOf(false) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var alertState by remember { mutableStateOf<AlertState?>(null) }
    var initAttempted by remember { mutableStateOf(false) }
    var modelPromptShown by remember { mutableStateOf(false) }

    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCameraLaunch by remember { mutableStateOf(false) }
    var pendingTorchToggle by remember { mutableStateOf(false) }
    var pendingMicLaunch by remember { mutableStateOf(false) }
    var isTorchOn by remember { mutableStateOf(false) }

    fun toggleTorch() {
        try {
            if (!torchController.hasFlashlight()) {
                alertState = AlertState("손전등 없음", "이 기기에서 손전등을 사용할 수 없습니다.")
                return
            }
            val next = !isTorchOn
            torchController.setTorchEnabled(next)
            isTorchOn = next
        } catch (e: Exception) {
            alertState = AlertState("손전등 오류", e.message ?: "손전등을 전환하지 못했습니다.")
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val capturedUri = pendingCameraUri
        pendingCameraUri = null
        if (!success || capturedUri == null) {
            if (pendingCameraLaunch) {
                alertState = AlertState("촬영 실패", "카메라 촬영이 취소되었습니다.")
            }
            pendingCameraLaunch = false
            return@rememberLauncherForActivityResult
        }

        pendingCameraLaunch = false
        scope.launch {
            guidanceTitle = null
            analysisWarning = null
            statusText = "온라인"
            screen = Screen.CameraLoading
            delay(600)
            val result = analyzeCapturedImage(capturedUri.toString())
            selectedDisasterId = result.disasterId
            guidanceTitle = "카메라 촬영"
            analysisWarning = result.warning
            guidanceBackTarget = "home"
            screen = Screen.Guidance
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            alertState = AlertState("권한 필요", "카메라 권한이 필요합니다.")
            pendingCameraLaunch = false
            pendingTorchToggle = false
            return@rememberLauncherForActivityResult
        }
        if (pendingTorchToggle) {
            pendingTorchToggle = false
            toggleTorch()
        }
        if (pendingCameraLaunch) {
            pendingCameraLaunch = false
            val uri = createCameraUri(context)
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        }
    }

    fun requestTorchToggle() {
        val permissionGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (permissionGranted) {
            toggleTorch()
        } else {
            pendingTorchToggle = true
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            alertState = AlertState("권한 필요", "음성 녹음을 위해 마이크 권한이 필요합니다.")
            pendingMicLaunch = false
            return@rememberLauncherForActivityResult
        }
        if (pendingMicLaunch) {
            pendingMicLaunch = false
            startVoiceFlow(
                voiceController = voiceController,
                offlineLlm = offlineLlm,
                onStart = { isListening = true },
                onStop = { isListening = false },
                onAlert = { title, message -> alertState = AlertState(title, message) },
                onResult = { result ->
                    selectedDisasterId = result.disasterId
                    guidanceTitle = "음성 질문"
                    analysisWarning = result.recommendation
                    guidanceBackTarget = "home"
                    statusText = "오프라인"
                    screen = Screen.Guidance
                }
            )
        }
    }

    LaunchedEffect(screen) {
        if (screen == Screen.Onboard) {
            delay(300)
            screen = Screen.Home
        }
    }

    LaunchedEffect(offlineState.isInitialized, offlineState.isLoading, initAttempted) {
        if (!initAttempted && !offlineState.isInitialized && !offlineState.isLoading) {
            initAttempted = true
            scope.launch {
                offlineLlm.initializeModel()
            }
        }
    }

    LaunchedEffect(offlineState.error, offlineState.isInitialized, offlineState.isLoading) {
        if (!modelPromptShown && !offlineState.isInitialized && !offlineState.isLoading) {
            val error = offlineState.error
            if (!error.isNullOrBlank()) {
                modelPromptShown = true
                alertState = AlertState(
                    "LLM 모델 필요",
                    "내장 LLM 모델을 준비하지 못했습니다. 다운로드 버튼으로 모델을 다시 받아 적용할 수 있습니다."
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceController.destroy()
            runCatching { torchController.setTorchEnabled(false) }
            offlineLlm.close()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0A0A0A)) {
        when (screen) {
            Screen.Onboard -> OnboardingScreen(onDone = { screen = Screen.Home })
            Screen.Home -> HomeScreen(
                offlineState = offlineState,
                isListening = isListening,
                onStartVoice = {
                    if (offlineState.isLoading) {
                        alertState = AlertState(
                            "모델 준비 중",
                            "LLM 모델을 불러오는 중입니다.\n진행률: ${offlineState.downloadProgress}%"
                        )
                        return@HomeScreen
                    }

                    val permissionGranted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED

                    if (permissionGranted) {
                        startVoiceFlow(
                            voiceController = voiceController,
                            offlineLlm = offlineLlm,
                            onStart = { isListening = true },
                            onStop = { isListening = false },
                            onAlert = { title, message -> alertState = AlertState(title, message) },
                            onResult = { result ->
                                selectedDisasterId = result.disasterId
                                guidanceTitle = "음성 질문"
                                analysisWarning = result.recommendation
                                guidanceBackTarget = "home"
                                statusText = "오프라인"
                                screen = Screen.Guidance
                            }
                        )
                    } else {
                        pendingMicLaunch = true
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onOpenDisasterPicker = {
                    disasterPickerSource = "home"
                    screen = Screen.Disaster
                },
                onOpenCameraCapture = {
                    val permissionGranted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED

                    if (permissionGranted) {
                        val uri = createCameraUri(context)
                        pendingCameraUri = uri
                        cameraLauncher.launch(uri)
                    } else {
                        pendingCameraLaunch = true
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                onOpenTextQuestion = {
                    statusText = "오프라인"
                    screen = Screen.TextQuery
                },
                onOpenSettings = {
                    statusText = "오프라인"
                    screen = Screen.Settings
                }
            )
            Screen.Disaster -> DisasterScreen(
                catalog = DisasterCatalog,
                onBack = {
                    screen = if (disasterPickerSource == "guidance") {
                        Screen.Guidance
                    } else {
                        Screen.Home
                    }
                },
                onSelectType = { id ->
                    selectedDisasterId = id
                    screen = Screen.Guidance
                }
            )
            Screen.CameraLoading -> CameraAnalyzingScreen()
            Screen.TextQuery -> TextQuestionScreen(
                textValue = textQuestion,
                onChangeText = { textQuestion = it },
                onBack = {
                    statusText = "오프라인"
                    screen = Screen.Home
                },
                isAnalyzing = isAnalyzing,
                onSubmit = {
                    val trimmed = textQuestion.trim()
                    if (trimmed.isEmpty()) {
                        alertState = AlertState("입력 필요", "상황을 입력해 주세요.")
                        return@TextQuestionScreen
                    }
                    if (offlineLlm.state.value.isLoading) {
                        alertState = AlertState("모델 준비 중", "내장 LLM 모델을 준비하고 있습니다. 잠시 후 다시 시도해 주세요.")
                        return@TextQuestionScreen
                    }
                    isAnalyzing = true
                    scope.launch {
                        try {
                            if (!offlineLlm.state.value.isInitialized) {
                                offlineLlm.initializeModel()
                            }
                            val result = analyzeTextQueryWithLLM(
                                trimmed,
                                offlineLlm
                            )
                            selectedDisasterId = result.disasterId
                            guidanceTitle = "텍스트 질문"
                            analysisWarning = result.warning
                            guidanceBackTarget = "text_query"
                            statusText = "오프라인"
                            screen = Screen.Guidance
                        } catch (e: Exception) {
                            alertState = AlertState("분석 오류", e.message ?: "분석 중 오류가 발생했습니다.")
                        } finally {
                            isAnalyzing = false
                        }
                    }
                }
            )
            Screen.Settings -> SettingsScreen(
                language = language,
                ttsEnabled = ttsEnabled,
                voiceType = voiceType,
                offlineState = offlineState,
                modelPath = offlineLlm.modelPath(),
                onLanguageChange = { language = it },
                onTtsToggle = { ttsEnabled = it },
                onVoiceChange = { voiceType = it },
                onDownloadModel = {
                    scope.launch {
                        try {
                            offlineLlm.downloadModel()
                            initAttempted = false
                            modelPromptShown = false
                            alertState = AlertState("모델 준비 완료", "LLM 모델을 자동으로 내려받아 적용했습니다.")
                        } catch (e: Exception) {
                            alertState = AlertState("다운로드 실패", e.message ?: "모델을 내려받지 못했습니다.")
                        }
                    }
                },
                onDeleteModel = {
                    scope.launch {
                        try {
                            offlineLlm.deleteModel()
                            initAttempted = false
                            modelPromptShown = false
                            alertState = AlertState("모델 삭제", "LLM 모델을 삭제했습니다.")
                        } catch (e: Exception) {
                            alertState = AlertState("삭제 실패", e.message ?: "모델을 삭제하지 못했습니다.")
                        }
                    }
                },
                onBack = {
                    statusText = "오프라인"
                    screen = Screen.Home
                }
            )
            Screen.Guidance -> GuidanceScreen(
                disaster = getDisasterById(selectedDisasterId),
                title = guidanceTitle,
                statusText = statusText,
                warning = analysisWarning,
                isTorchOn = isTorchOn,
                onBack = {
                    if (guidanceBackTarget == "text_query") {
                        screen = Screen.TextQuery
                    } else {
                        statusText = "오프라인"
                        guidanceTitle = null
                        analysisWarning = null
                        screen = Screen.Home
                    }
                },
                onOpenDisasterPicker = {
                    disasterPickerSource = "guidance"
                    statusText = "오프라인"
                    guidanceTitle = null
                    analysisWarning = null
                    screen = Screen.Disaster
                },
                onToggleTorch = { requestTorchToggle() }
            )
        }

        alertState?.let { alert ->
            AlertDialog(
                onDismissRequest = { alertState = null },
                confirmButton = {
                    val confirmLabel = if (alert.title == "LLM 모델 필요") "다운로드" else "확인"
                    Text(
                        text = confirmLabel,
                        modifier = Modifier
                            .padding(12.dp)
                            .clickable {
                                if (alert.title == "LLM 모델 필요") {
                                    scope.launch {
                                        try {
                                            alertState = null
                                            offlineLlm.downloadModel()
                                            initAttempted = false
                                            modelPromptShown = false
                                            alertState = AlertState("모델 준비 완료", "LLM 모델을 자동으로 내려받아 적용했습니다.")
                                        } catch (e: Exception) {
                                            alertState = AlertState("다운로드 실패", e.message ?: "모델을 내려받지 못했습니다.")
                                        }
                                    }
                                } else {
                                    alertState = null
                                }
                            },
                        color = Color.White
                    )
                },
                title = { Text(text = alert.title, color = Color.White) },
                text = { Text(text = alert.message, color = Color(0xFFBDBDBD)) },
                containerColor = Color(0xFF1B1B1B)
            )
        }
    }
}

private fun startVoiceFlow(
    voiceController: VoiceInputController,
    offlineLlm: OfflineLlmManager,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onAlert: (String, String) -> Unit,
    onResult: (VoiceAnalysisResult) -> Unit
) {
    val scope = voiceController.scope
    scope.launch {
        onStart()
        try {
            val transcript = voiceController.recordAndAnalyze()
            if (transcript.isNullOrBlank()) {
                onAlert("음성 인식 실패", "다시 한 번 천천히 말씀해 주세요.")
                return@launch
            }

            val analysis = offlineLlm.analyzeDisaster(transcript)
            val quickDetection = detectDisasterFromKeywords(transcript)
            val chosenId = analysis?.disasterId ?: quickDetection.id
            val chosen = getDisasterById(chosenId)

            onResult(
                VoiceAnalysisResult(
                    recognizedText = transcript,
                    disasterId = chosenId,
                    disasterLabel = chosen.label,
                    recommendation = analysis?.recommendation ?: "상황이 위험하면 즉시 119에 신고하세요.",
                    confidence = analysis?.confidence ?: quickDetection.confidence
                )
            )
        } finally {
            onStop()
        }
    }
}

private fun createCameraUri(context: Context): Uri {
    val imagesDir = File(context.cacheDir, "captures")
    if (!imagesDir.exists()) {
        imagesDir.mkdirs()
    }
    val imageFile = File(imagesDir, "capture_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        imageFile
    )
}

private class TorchController(private val context: Context) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    fun hasFlashlight(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH) &&
            findTorchCameraId() != null
    }

    fun setTorchEnabled(enabled: Boolean) {
        val cameraId = findTorchCameraId()
            ?: throw IllegalStateException("사용 가능한 손전등 카메라가 없습니다.")

        try {
            cameraManager.setTorchMode(cameraId, enabled)
        } catch (e: CameraAccessException) {
            throw IllegalStateException("카메라 손전등에 접근할 수 없습니다.", e)
        } catch (e: SecurityException) {
            throw IllegalStateException("카메라 권한이 필요합니다.", e)
        }
    }

    private fun findTorchCameraId(): String? {
        return cameraManager.cameraIdList.firstOrNull { cameraId ->
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            characteristics.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }
}

private data class OfflineAnalysisResult(
    val disasterId: DisasterId,
    val confidence: Double,
    val recommendation: String
)

private data class OfflineLlmState(
    val isInitialized: Boolean,
    val isLoading: Boolean,
    val error: String?,
    val downloadProgress: Int
)

private class OfflineLlmManager(private val context: Context) {
    private val _state = MutableStateFlow(
        OfflineLlmState(
            isInitialized = false,
            isLoading = false,
            error = null,
            downloadProgress = 0
        )
    )
    val state: StateFlow<OfflineLlmState> = _state

    private val modelFile = File(context.filesDir, MODEL_NAME)
    private val llama = LlamaNative()
    private val textCategories = listOf(
        Pair("지진", "튼튼한 책상 아래로 숨어 머리를 보호하세요."),
        Pair("화재", "낮은 자세로 연기를 피해 빠르게 대피하세요."),
        Pair("홍수", "높은 곳으로 즉시 이동하세요."),
        Pair("태풍", "창문에서 떨어져 안전한 실내로 이동하세요."),
        Pair("산사태", "산 기슭을 피하고 높은 곳으로 이동하세요."),
        Pair("쓰나미", "즉시 높은 지대(30m 이상)로 대피하세요."),
        Pair("대설", "외출을 자제하고 실내에서 대기하세요."),
        Pair("위험물", "바람 방향 반대로 대피하고 119에 신고하세요.")
    )

    fun hasModel(): Boolean = modelFile.exists() && modelFile.length() > 0L

    fun modelPath(): String = modelFile.absolutePath

    suspend fun downloadModel() {
        _state.update { it.copy(isInitialized = false, isLoading = true, error = null, downloadProgress = 0) }
        try {
            withContext(Dispatchers.IO) {
                llama.close()
                val downloadUrl = URL(MODEL_DOWNLOAD_URL)
                val connection = downloadUrl.openConnection() as java.net.HttpURLConnection
                try {
                    connection.instanceFollowRedirects = true
                    connection.connectTimeout = 30000
                    connection.readTimeout = 30000
                    connection.setRequestProperty("User-Agent", "ResQ/1.0")
                    connection.connect()

                    if (connection.responseCode !in 200..299) {
                        throw IllegalStateException("모델 다운로드 실패: ${connection.responseCode}")
                    }

                    val total = connection.contentLengthLong
                    val tempFile = File(context.filesDir, "$MODEL_NAME.part")
                    if (tempFile.exists()) {
                        tempFile.delete()
                    }

                    connection.inputStream.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var read = input.read(buffer)
                            var written = 0L
                            while (read >= 0) {
                                output.write(buffer, 0, read)
                                written += read
                                if (total > 0) {
                                    val percent = ((written * 100) / total).toInt().coerceIn(0, 100)
                                    _state.update { it.copy(downloadProgress = percent) }
                                }
                                read = input.read(buffer)
                            }
                        }
                    }

                    if (modelFile.exists()) {
                        modelFile.delete()
                    }
                    if (!tempFile.renameTo(modelFile)) {
                        throw IllegalStateException("모델 파일 저장에 실패했습니다.")
                    }
                } finally {
                    connection.disconnect()
                }
            }

            loadModelFromDisk()
            _state.update { it.copy(isInitialized = true, isLoading = false, downloadProgress = 100) }
        } catch (err: Exception) {
            _state.update {
                it.copy(
                    isInitialized = false,
                    error = err.message ?: "모델 가져오기 실패",
                    isLoading = false
                )
            }
            throw err
        }
    }

    suspend fun deleteModel() {
        withContext(Dispatchers.IO) {
            if (modelFile.exists()) {
                modelFile.delete()
            }
        }
        llama.close()
        _state.update { it.copy(isInitialized = false, isLoading = false, downloadProgress = 0) }
    }

    fun close() {
        llama.close()
    }

    suspend fun initializeModel() {
        if (_state.value.isInitialized || _state.value.isLoading) return

        _state.update { it.copy(isLoading = true, error = null, downloadProgress = 0) }

        try {
            withContext(Dispatchers.IO) {
                copyBundledModelIfNeeded()
            }

            Log.d("ResQApp-LLM", "모델 로드: ${modelFile.absolutePath}")
            loadModelFromDisk()
            _state.update { it.copy(isInitialized = true, isLoading = false, downloadProgress = 100) }
        } catch (err: Exception) {
            _state.update { it.copy(error = err.message ?: "초기화 실패", isLoading = false) }
        }
    }

    private fun copyBundledModelIfNeeded() {
        if (hasModel()) return

        val tempFile = File(context.filesDir, "$MODEL_NAME.part")
        if (tempFile.exists()) {
            tempFile.delete()
        }

        val total = bundledModelSize()
        try {
            context.assets.open(BUNDLED_MODEL_ASSET).use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read = input.read(buffer)
                    var written = 0L
                    while (read >= 0) {
                        output.write(buffer, 0, read)
                        written += read
                        if (total > 0L) {
                            val percent = ((written * 95) / total).toInt().coerceIn(1, 95)
                            _state.update { it.copy(downloadProgress = percent) }
                        }
                        read = input.read(buffer)
                    }
                }
            }

            if (modelFile.exists()) {
                modelFile.delete()
            }
            if (!tempFile.renameTo(modelFile)) {
                throw IllegalStateException("내장 LLM 모델 파일 저장에 실패했습니다.")
            }
        } catch (err: Exception) {
            if (tempFile.exists()) {
                tempFile.delete()
            }
            throw IllegalStateException(
                "내장 LLM 모델을 준비하지 못했습니다. 설정에서 '모델 다시 다운로드'를 시도해 주세요.",
                err
            )
        }
    }

    private fun bundledModelSize(): Long {
        return try {
            context.assets.openFd(BUNDLED_MODEL_ASSET).use { descriptor ->
                descriptor.length.takeIf { it > 0L } ?: -1L
            }
        } catch (_: Exception) {
            -1L
        }
    }

    private suspend fun loadModelFromDisk() {
        val threads = Runtime.getRuntime().availableProcessors()
            .coerceAtMost(4)
            .coerceAtLeast(1)
        val ok = withContext(Dispatchers.Default) {
            llama.init(modelFile.absolutePath, 256, threads)
        }
        if (!ok) {
            throw IllegalStateException("LLM 초기화 실패")
        }
    }

    fun analyzeDisaster(text: String): OfflineAnalysisResult? {
        val quick = detectDisasterFromKeywords(text)
        val recommendations = mapOf(
            DisasterId.Earthquake to "구조물 균열과 낙하물 위험에 주의하세요.",
            DisasterId.Fire to "연기를 피해 낮은 자세로 빠르게 대피하세요.",
            DisasterId.Flood to "높은 곳으로 이동하고 전기 제품에 주의하세요.",
            DisasterId.Typhoon to "창문에서 떨어져 안전한 실내로 이동하세요.",
            DisasterId.Landslide to "산 기슭이나 하천 근처를 피하세요.",
            DisasterId.Tsunami to "즉시 높은 지대로 대피하세요.",
            DisasterId.HeavySnow to "외출을 자제하고 보온에 신경쓰세요.",
            DisasterId.HazardRelease to "즉시 환기하고 안전거리 확보하세요."
        )

        return OfflineAnalysisResult(
            disasterId = quick.id,
            confidence = quick.confidence,
            recommendation = recommendations[quick.id] ?: "상황이 위험하면 즉시 신고하세요."
        )
    }

    suspend fun analyzeForTextQuery(prompt: String): Pair<String, String> {
        return withContext(Dispatchers.Default) {
            try {
                // LLM 프롬프트로 재난 카테고리 판단
                val classificationPrompt = """다음 중 하나의 숫자만 답하세요.

입력: "$prompt"

1=지진(earthquake)
2=화재(fire, smoke, flames)
3=홍수(flood)
4=태풍(typhoon, storm)
5=산사태(landslide)
6=쓰나미(tsunami)
7=대설(heavy snow, blizzard)
8=위험물(chemical, gas)
""".trimIndent()

                if (!_state.value.isInitialized) {
                    throw IllegalStateException("LLM이 초기화되지 않았습니다.")
                }

                val startedAt = System.currentTimeMillis()
                val classification = withTimeoutOrNull(10000L) {
                    llama.generate(classificationPrompt, 2, 0.0f).trim()
                }.orEmpty()
                val elapsedMs = System.currentTimeMillis() - startedAt
                
                // 로그 출력
                Log.d("ResQApp-LLM", "사용자 입력: $prompt")
                Log.d("ResQApp-LLM", "분류 결과(원본): $classification")
                Log.d("ResQApp-LLM", "분류 시간: ${elapsedMs}ms")

                if (classification.isBlank()) {
                    val fallback = fallbackTextCategory(prompt)
                    Log.d("ResQApp-LLM", "분류 실패, 로컬 폴백 사용: ${fallback.first}")
                    return@withContext fallback
                }
                
                // 분류 결과 파싱
                val normalized = classification.lowercase(Locale.getDefault())
                val categoryNum = classification.firstOrNull { it.isDigit() }?.digitToInt()
                    ?: when {
                        normalized.contains("지진") || normalized.contains("earthquake") -> 1
                        normalized.contains("화재") || normalized.contains("fire") -> 2
                        normalized.contains("홍수") || normalized.contains("flood") -> 3
                        normalized.contains("태풍") || normalized.contains("typhoon") -> 4
                        normalized.contains("산사태") || normalized.contains("landslide") -> 5
                        normalized.contains("쓰나미") || normalized.contains("tsunami") || normalized.contains("해일") -> 6
                        normalized.contains("대설") || normalized.contains("폭설") || normalized.contains("heavy snow") -> 7
                        normalized.contains("위험물") || normalized.contains("hazard") || normalized.contains("chemical") -> 8
                        else -> 1
                    }
                Log.d("ResQApp-LLM", "파싱된 카테고리 번호: $categoryNum")

                val selectedCategory = if (categoryNum in 1..8) {
                    textCategories[categoryNum - 1]
                } else {
                    fallbackTextCategory(prompt)
                }
                
                Log.d("ResQApp-LLM", "최종 선택 카테고리: ${selectedCategory.first}")
                
                return@withContext selectedCategory
            } catch (e: Exception) {
                Log.e("ResQApp-LLM", "분류 오류: ${e.message}", e)
                // 분석 실패 시 기본값
                throw IllegalStateException("LLM 분류 실패: ${e.message}")
            }
        }
    }

    private fun fallbackTextCategory(prompt: String): Pair<String, String> {
        val quick = detectDisasterFromKeywords(prompt)
        val index = when (quick.id) {
            DisasterId.Earthquake -> 0
            DisasterId.Fire -> 1
            DisasterId.Flood -> 2
            DisasterId.Typhoon -> 3
            DisasterId.Landslide -> 4
            DisasterId.Tsunami -> 5
            DisasterId.HeavySnow -> 6
            DisasterId.HazardRelease -> 7
        }
        return textCategories[index]
    }


    suspend fun generateText(prompt: String): String {
        return withContext(Dispatchers.Default) {
            try {
                if (!_state.value.isInitialized) {
                    throw IllegalStateException("LLM이 초기화되지 않았습니다.")
                }

                return@withContext llama.generate(prompt, 256, 0.2f)
            } catch (e: Exception) {
                throw Exception("LLM 생성 실패: ${e.message}")
            }
        }
    }

    companion object {
        private const val MODEL_NAME = "gemma-4-E2B-it-IQ4_XS.gguf"
        private const val BUNDLED_MODEL_ASSET = "llm/gemma-4-E2B-it-IQ4_XS.gguf"
    }


}

private class VoiceInputController(private val context: Context) {
    val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Main)
    private val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

    private val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
    }

    init {
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) {
                _isListening.value = true
            }

            override fun onBeginningOfSpeech() = Unit

            override fun onRmsChanged(rmsdB: Float) = Unit

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                _isListening.value = false
            }

            override fun onError(error: Int) {
                _error.value = "음성 인식 오류: $error"
                _isListening.value = false
            }

            override fun onResults(results: android.os.Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    _recognizedText.value = matches[0]
                }
            }

            override fun onPartialResults(partialResults: android.os.Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    _recognizedText.value = matches[0]
                }
            }

            override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
        })
    }

    fun startListening() {
        _error.value = null
        _recognizedText.value = ""
        speechRecognizer.startListening(recognizerIntent)
        _isListening.value = true
    }

    fun stopListening() {
        speechRecognizer.stopListening()
        _isListening.value = false
    }

    suspend fun recordAndAnalyze(): String? {
        startListening()
        delay(3000)
        stopListening()
        delay(500)
        return _recognizedText.value.ifBlank { null }
    }

    fun destroy() {
        speechRecognizer.destroy()
        scope.cancel()
    }
}

@Composable
private fun OnboardingScreen(onDone: () -> Unit) {
    val fade = remember { Animatable(0f) }
    val lift = remember { Animatable(10f) }

    LaunchedEffect(Unit) {
        launch { fade.animateTo(1f, tween(220)) }
        launch { lift.animateTo(0f, tween(220)) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onDone() }
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0A0A0A), Color(0xFF141414), Color(0xFF0B0B0B))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .alpha(fade.value)
                .padding(top = lift.value.dp)
        ) {
            Text(
                text = "ResQ",
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE53935),
                fontFamily = ResQFontFamily
            )
            Text(
                text = "오프라인 재난코치",
                fontSize = 16.sp,
                color = Color(0xFFC7C7C7),
                fontFamily = ResQFontFamily
            )
        }
    }
}

@Composable
private fun HomeScreen(
    offlineState: OfflineLlmState,
    isListening: Boolean,
    onStartVoice: () -> Unit,
    onOpenDisasterPicker: () -> Unit,
    onOpenCameraCapture: () -> Unit,
    onOpenTextQuestion: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val micScale by animateFloatAsState(
        targetValue = if (isListening) 1.08f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "mic-scale"
    )
    val micGlow = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        micGlow.animateTo(1f, tween(1200))
    }

    val items = listOf(
        HomeAction(Icons.Outlined.GridView, "재난 유형 선택", onOpenDisasterPicker),
        HomeAction(Icons.Outlined.CameraAlt, "안내문 촬영", onOpenCameraCapture),
        HomeAction(Icons.Outlined.Edit, "텍스트 질문", onOpenTextQuestion),
        HomeAction(Icons.Outlined.Settings, "설정", onOpenSettings)
    )

    val anims = remember { items.map { Animatable(0f) } }

    LaunchedEffect(Unit) {
        anims.forEachIndexed { index, anim ->
            launch {
                delay(30L * index)
                anim.animateTo(1f, tween(240))
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0A0A0A), Color(0xFF121212), Color(0xFF0D0D0D))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ResQ",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE53935)
                )
                Badge(label = if (offlineState.isInitialized) {
                    "오프라인"
                } else if (offlineState.isLoading) {
                    "다운로드 ${offlineState.downloadProgress}%"
                } else {
                    "오프라인"
                })
            }

            Spacer(modifier = Modifier.height(40.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(190.dp)
                        .graphicsLayer(scaleX = micScale, scaleY = micScale)
                        .shadow(12.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Color(0xFFE53935))
                        .clickable { onStartVoice() },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF6B63))
                            .alpha(0.3f * micGlow.value)
                    )
                    androidx.compose.material3.Icon(
                        imageVector = if (isListening) Icons.Outlined.GraphicEq else Icons.Outlined.Mic,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(88.dp)
                    )
                }
                Text(
                    text = "음성 질문 시작하기",
                    fontSize = 16.sp,
                    color = Color(0xFFCFCFCF),
                    modifier = Modifier.padding(top = 18.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items.forEachIndexed { index, item ->
                    val anim = anims[index]
                    val offset = (1f - anim.value) * 10f
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = offset.dp)
                            .alpha(anim.value)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF1B1B1B))
                            .clickable { item.onClick() }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF262626)),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = Color(0xFFD4D4D4),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = item.label,
                            fontSize = 15.sp,
                            color = Color(0xFFE6E6E6)
                        )
                    }
                }
            }
        }
    }
}

private data class HomeAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit
)

@Composable
private fun Badge(label: String) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color(0xFF161616))
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(text = label, fontSize = 12.sp, color = Color(0xFF9C9C9C))
    }
}

@Composable
private fun DisasterScreen(
    catalog: List<DisasterDefinition>,
    onBack: () -> Unit,
    onSelectType: (DisasterId) -> Unit
) {
    ResQGradientScreen {
        Column(modifier = Modifier.fillMaxSize()) {
            Header(title = "재난 유형 선택", trailing = { Badge(label = "오프라인") }, onBack = onBack)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "어떤 재난에 대한 정보가 필요하신가요?",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
                )
                Text(
                    text = "재난 유형을 선택해주세요.",
                    fontSize = 13.sp,
                    color = Color(0xFF9C9C9C),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Column {
                    catalog.chunked(2).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            row.forEach { item ->
                                DisasterCard(item, onSelectType)
                            }
                            if (row.size == 1) {
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1B1B1B))
                        .clickable { onBack() }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF2B2B2B)),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Outlined.WbSunny,
                            contentDescription = null,
                            tint = Color(0xFFFF5252)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(text = "원하는 항목이 없나요?", color = Color.White)
                        Text(text = "음성이나 텍스트로 직접 질문해보세요", color = Color(0xFFC7C7C7))
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.DisasterCard(item: DisasterDefinition, onSelectType: (DisasterId) -> Unit) {
    Column(
        modifier = Modifier
            .width(0.dp)
            .weight(1f)
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1B1B1B))
            .clickable { onSelectType(item.id) }
            .padding(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF262626)),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = item.label,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFE6E6E6),
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = item.cardDescription,
            fontSize = 12.sp,
            color = Color(0xFF9C9C9C),
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun GuidanceScreen(
    disaster: DisasterDefinition,
    title: String?,
    statusText: String,
    warning: String?,
    isTorchOn: Boolean,
    onBack: () -> Unit,
    onOpenDisasterPicker: () -> Unit,
    onToggleTorch: () -> Unit
) {
    ResQGradientScreen {
        Column(modifier = Modifier.fillMaxSize()) {
            Header(title = title ?: "${disaster.label} 안내", trailing = { Badge(label = statusText) }, onBack = onBack)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickActionButton(Icons.Outlined.Phone, "119")
                    QuickActionButton(
                        icon = Icons.Outlined.WbSunny,
                        label = if (isTorchOn) "끄기" else "손전등",
                        active = isTorchOn,
                        onClick = onToggleTorch
                    )
                    QuickActionButton(Icons.Outlined.Notifications, "사이렌")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF1B1B1B))
                        .clickable { onOpenDisasterPicker() }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp)) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF121212))
                                .border(1.dp, Color(0xFF2A2A2A), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = disaster.icon,
                                contentDescription = null,
                                tint = Color(0xFFE53935),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Text(
                            text = disaster.label,
                            fontSize = 12.sp,
                            color = Color(0xFFE6E6E6),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = disaster.headline,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "행동 단계",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE6E6E6),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                disaster.steps.forEachIndexed { index, step ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(36.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF262626))
                                    .border(1.dp, Color(0xFF3A3A3A), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                            if (index < disaster.steps.lastIndex) {
                                Spacer(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .height(24.dp)
                                        .background(Color(0xFF333333))
                                )
                            }
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp, bottom = 12.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1B1B1B))
                                .border(1.dp, Color(0xFF242424), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = step,
                                fontSize = 14.sp,
                                color = Color(0xFFD4D4D4),
                                lineHeight = 21.sp
                            )
                        }
                    }
                }

                warning?.let {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFFFF4D4D), RoundedCornerShape(12.dp))
                            .background(Color(0xFF1B1B1B))
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = Color(0xFFFF4D4D),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = it,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFD8D8D8),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Text(
                    text = "출처: 공공 안전 안내 요약 (앱 내 참고용)",
                    fontSize = 11.sp,
                    color = Color(0xFF6A6A6A),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun RowScope.QuickActionButton(
    icon: ImageVector,
    label: String,
    active: Boolean = false,
    onClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) Color(0xFFFFB300) else Color(0xFFE53935))
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = label, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CameraAnalyzingScreen() {
    ResQGradientScreen {
        Column(modifier = Modifier.fillMaxSize()) {
            Header(title = "안내문 촬영", trailing = { Badge(label = "온라인") }, onBack = {})
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 120.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = Color(0xFFBFC5CB))
                Text(
                    text = "이미지 분석중...",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 24.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TextQuestionScreen(
    textValue: String,
    onChangeText: (String) -> Unit,
    onBack: () -> Unit,
    isAnalyzing: Boolean = false,
    onSubmit: () -> Unit
) {
    ResQGradientScreen {
        Column(modifier = Modifier.fillMaxSize()) {
            Header(title = "텍스트 질문", trailing = { Badge(label = "오프라인") }, onBack = onBack)

            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "상황입력",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                androidx.compose.material3.OutlinedTextField(
                    value = textValue,
                    onValueChange = onChangeText,
                    placeholder = { Text("지금 상황을 입력해주세요.", color = Color(0xFF777777)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 22.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = Color.White,
                        fontSize = 18.sp,
                        fontFamily = ResQFontFamily
                    ),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF2B2B2B),
                        unfocusedBorderColor = Color(0xFF2B2B2B),
                        cursorColor = Color.White,
                        focusedContainerColor = Color(0xFF1B1B1B),
                        unfocusedContainerColor = Color(0xFF1B1B1B)
                    )
                )

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isAnalyzing) Color(0xFF999999) else Color(0xFFE53935))
                        .clickable(enabled = !isAnalyzing) { onSubmit() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (isAnalyzing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    } else {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Send,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = if (isAnalyzing) "AI 분석 중..." else "안내받기",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
            }
        }
    }
}

private suspend fun analyzeTextQueryWithLLM(
    inputText: String,
    offlineLlm: OfflineLlmManager
): AnalysisResult {
    // 항상 LLM으로 분석 (태그는 무시)
    return try {
        val (disasterType, advice) = offlineLlm.analyzeForTextQuery(inputText)
        val disasterId = when (disasterType) {
            "지진" -> DisasterId.Earthquake
            "화재" -> DisasterId.Fire
            "홍수" -> DisasterId.Flood
            "태풍" -> DisasterId.Typhoon
            "산사태" -> DisasterId.Landslide
            "쓰나미" -> DisasterId.Tsunami
            "대설" -> DisasterId.HeavySnow
            "위험물" -> DisasterId.HazardRelease
            else -> DisasterId.Earthquake
        }
        AnalysisResult(disasterId, advice)
    } catch (e: Exception) {
        throw IllegalStateException("LLM 분석 실패: ${e.message}")
    }
}

@Composable
private fun SettingsScreen(
    language: String,
    ttsEnabled: Boolean,
    voiceType: String,
    offlineState: OfflineLlmState,
    modelPath: String,
    onLanguageChange: (String) -> Unit,
    onTtsToggle: (Boolean) -> Unit,
    onVoiceChange: (String) -> Unit,
    onDownloadModel: () -> Unit,
    onDeleteModel: () -> Unit,
    onBack: () -> Unit
) {
    ResQGradientScreen {
        Column(modifier = Modifier.fillMaxSize()) {
            Header(title = "설정", trailing = { Spacer(modifier = Modifier.width(44.dp)) }, onBack = onBack)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                SettingsSection(title = "언어 기본값", subtitle = "현재 기본 언어를 선택하세요.") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsOption("한국어", language == "ko") { onLanguageChange("ko") }
                        SettingsOption("영어", language == "en") { onLanguageChange("en") }
                        SettingsOption("일본어", language == "ja") { onLanguageChange("ja") }
                    }
                }

                SettingsSection(title = "음성 TTS", subtitle = "음성 안내") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "음성 TTS", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(text = "음성 안내", color = Color(0xFF999999), fontSize = 12.sp)
                        }
                        ToggleSwitch(isOn = ttsEnabled, onToggle = onTtsToggle)
                    }

                    if (ttsEnabled) {
                        Text(
                            text = "음성 안내 톤을 선택합니다.",
                            color = Color(0xFF999999),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            SettingsOption("느낌", voiceType == "natural") { onVoiceChange("natural") }
                            SettingsOption("보증", voiceType == "assured") { onVoiceChange("assured") }
                            SettingsOption("뻐금", voiceType == "brisk") { onVoiceChange("brisk") }
                        }
                    }
                }

                SettingsSection(title = "LLM 모델", subtitle = "버튼 한 번으로 HuggingFace GGUF를 내려받아 바로 적용합니다.") {
                    val statusText = when {
                        offlineState.isLoading -> "불러오는 중 ${offlineState.downloadProgress}%"
                        offlineState.isInitialized -> "준비됨"
                        else -> "없음"
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1B1B1B))
                            .padding(12.dp)
                    ) {
                        SettingsRow("상태", statusText)
                        SettingsRow("모델 경로", if (offlineState.isInitialized) modelPath else "없음")
                        SettingsRow("설치 방법", "앱 내장 모델 우선")
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            SettingsActionButton(
                                label = "모델 다시 다운로드",
                                enabled = !offlineState.isLoading,
                                onClick = onDownloadModel
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            SettingsActionButton(
                                label = "모델 삭제",
                                enabled = !offlineState.isLoading && offlineState.isInitialized,
                                onClick = onDeleteModel
                            )
                        }
                    }

                    Text(
                        text = "텍스트 질문은 앱에 포함된 오프라인 모델을 우선 사용합니다. 문제가 있으면 모델을 다시 다운로드하세요.",
                        color = Color(0xFF999999),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 10.dp)
                    )

                    if (!offlineState.error.isNullOrBlank()) {
                        Text(
                            text = "오류: ${offlineState.error}",
                            color = Color(0xFFE53935),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFFE53935), RoundedCornerShape(8.dp))
                        .background(Color(0x1AE53935))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "안내",
                        color = Color(0xFFE53935),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "오프라인 상태에서도 모든 기능 사용 가능합니다. 앱에서는 시더 정 완 정보를 제공합니다 수 없습니다.",
                        color = Color(0xFFE53935),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, subtitle: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(text = title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        if (subtitle.isNotEmpty()) {
            Text(text = subtitle, color = Color(0xFF999999), fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun RowScope.SettingsOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) Color(0xFFE53935) else Color(0xFF1B1B1B))
            .border(1.dp, if (selected) Color(0xFFE53935) else Color(0xFF333333), RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = if (selected) Color.White else Color(0xFFCCCCCC),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun SettingsRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color(0xFFCCCCCC), fontSize = 13.sp)
        Text(text = value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SettingsActionButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val background = if (enabled) Color(0xFF1B1B1B) else Color(0xFF2A2A2A)
    val border = if (enabled) Color(0xFF333333) else Color(0xFF2A2A2A)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (enabled) Color(0xFFCCCCCC) else Color(0xFF777777),
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ToggleSwitch(isOn: Boolean, onToggle: (Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .width(48.dp)
            .height(28.dp)
            .clip(CircleShape)
            .background(if (isOn) Color(0xFFE53935) else Color(0xFF333333))
            .clickable { onToggle(!isOn) }
            .padding(2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (isOn) Color.White else Color(0xFF666666))
                .align(if (isOn) Alignment.CenterEnd else Alignment.CenterStart)
        )
    }
}

@Composable
private fun Header(title: String, trailing: @Composable () -> Unit, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        androidx.compose.material3.Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .size(36.dp)
                .clickable { onBack() }
                .padding(8.dp)
        )
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        Box(modifier = Modifier.wrapContentHeight()) {
            trailing()
        }
    }
}

@Composable
private fun ResQGradientScreen(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0A0A0A), Color(0xFF141414), Color(0xFF0B0B0B))
                )
            )
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        content()
    }
}
