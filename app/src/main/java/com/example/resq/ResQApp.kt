package com.example.resq

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.os.Handler
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
import java.security.MessageDigest
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

private enum class AppLanguage(
    val code: String,
    val speechLocale: Locale,
    val recognizerTag: String
) {
    Korean("ko", Locale.KOREAN, "ko-KR"),
    English("en", Locale.US, "en-US"),
    Chinese("zh", Locale.SIMPLIFIED_CHINESE, "zh-CN");

    companion object {
        fun fromCode(code: String): AppLanguage {
            return entries.firstOrNull { it.code == code } ?: Korean
        }
    }
}

private enum class DisasterId(val id: String) {
    Earthquake("earthquake"),
    Fire("fire"),
    Flood("flood"),
    Typhoon("typhoon"),
    Landslide("landslide"),
    Tsunami("tsunami"),
    HeavySnow("heavy_snow"),
    HazardRelease("hazard_release"),
    Emergency("emergency");

    companion object {
        fun fromId(id: String): DisasterId {
            return entries.firstOrNull { it.id == id } ?: Emergency
        }
    }
}

private data class DisasterDefinition(
    val id: DisasterId,
    val label: String,
    val cardDescription: String,
    val iconRes: Int,
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
        iconRes = R.drawable.resq_ic_disaster_crack,
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
        iconRes = R.drawable.resq_ic_warning_red,
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
        iconRes = R.drawable.resq_ic_warning_red,
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
        iconRes = R.drawable.resq_ic_siren_red,
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
        iconRes = R.drawable.resq_ic_disaster_crack,
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
        iconRes = R.drawable.resq_ic_warning_red,
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
        iconRes = R.drawable.resq_ic_warning_red,
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
        iconRes = R.drawable.resq_ic_warning_red,
        headline = "방송 안내에 따라 실내 대피 또는 지정 방향으로 이동하세요.",
        steps = listOf(
            "공식 방송·재난 문자의 지시를 우선 따르세요.",
            "실내 대피 시 창문·문을 닫고 환기를 끄세요.",
            "현장 촬영·접근은 위험하니 삼가세요.",
            "대피 시 피부 노출을 최소화하고 안내된 세척 방법을 따르세요."
        )
    ),
    DisasterDefinition(
        id = DisasterId.Emergency,
        label = "119",
        cardDescription = "매뉴얼 외 상황\n119 연결",
        iconRes = R.drawable.resq_ic_quick_phone,
        headline = "매뉴얼에서 해당 상황을 찾지 못했습니다.",
        steps = listOf(
            "즉시 119에 전화하세요.",
            "안전한 장소로 이동한 뒤 현재 위치를 설명하세요.",
            "공식 구조대 안내를 우선 따르세요."
        )
    )
)

private data class DisasterText(
    val label: String,
    val cardDescription: String,
    val headline: String,
    val steps: List<String>
)

private val DisasterTranslations = mapOf(
    AppLanguage.English to mapOf(
        DisasterId.Earthquake to DisasterText(
            label = "Earthquake",
            cardDescription = "Earthquake response\nEvacuation steps",
            headline = "Evacuate to an open area when shaking stops.",
            steps = listOf(
                "When you feel shaking, get under a sturdy table and protect your head.",
                "After the shaking stops, use the stairs and move to an open outdoor area.",
                "Do not use elevators.",
                "Once outside, stay away from glass, signs, and falling objects."
            )
        ),
        DisasterId.Fire to DisasterText(
            label = "Fire",
            cardDescription = "Fire response\nSmoke safety",
            headline = "Stay low and evacuate away from smoke.",
            steps = listOf(
                "If you see fire, alert people nearby and call 119.",
                "Touch the door handle first, and only open the door if it is not hot.",
                "If there is smoke, cover your nose and mouth with a wet cloth and move low.",
                "Use emergency exits or stairs, not elevators."
            )
        ),
        DisasterId.Flood to DisasterText(
            label = "Flood",
            cardDescription = "Flooding response\nEvacuation guide",
            headline = "Move immediately to higher, safer ground.",
            steps = listOf(
                "Check flood alerts and evacuation orders, then move to a designated shelter.",
                "Turn off the circuit breaker and gas valve if it is safe to do so.",
                "Do not enter floodwater, and avoid currents and manholes.",
                "Do not drive through flooded roads."
            )
        ),
        DisasterId.Typhoon to DisasterText(
            label = "Typhoon",
            cardDescription = "Strong wind and rain\nIndoor safety",
            headline = "Stay indoors and keep away from windows.",
            steps = listOf(
                "Secure outdoor objects or move them indoors.",
                "Lock windows and doors, and close curtains or shutters if available.",
                "Avoid going outside during strong winds and stay in a safe indoor area.",
                "If flooding is possible, prepare to move to a higher floor or safer place."
            )
        ),
        DisasterId.Landslide to DisasterText(
            label = "Landslide",
            cardDescription = "Landslide signs\nEmergency evacuation",
            headline = "Move away from slopes, ditches, and retaining walls.",
            steps = listOf(
                "If you notice ground cracks, small rockfalls, or unusual sounds, evacuate immediately.",
                "Do not stay near valleys, retaining walls, or steep slopes.",
                "Use only safe evacuation routes and avoid driving if possible.",
                "After evacuating, follow official guidance because further collapse may occur."
            )
        ),
        DisasterId.Tsunami to DisasterText(
            label = "Tsunami",
            cardDescription = "Tsunami response\nMove to high ground",
            headline = "Leave beaches and low areas for higher ground.",
            steps = listOf(
                "After an earthquake, do not stay near coasts or river mouths.",
                "Check broadcasts and emergency alerts, then move to high ground immediately.",
                "If driving is difficult, continue on foot toward higher ground.",
                "Stay in a safe location until the tsunami warning is lifted."
            )
        ),
        DisasterId.HeavySnow to DisasterText(
            label = "Heavy Snow",
            cardDescription = "Snow and ice\nTraffic and heating safety",
            headline = "Limit travel and use heating safely with ventilation.",
            steps = listOf(
                "Go out only when necessary and avoid icy or snow-covered routes.",
                "Keep flammable items away from heaters and ventilate often.",
                "Watch for snow falling from roofs and balconies.",
                "Check snow removal and tire condition before driving."
            )
        ),
        DisasterId.HazardRelease to DisasterText(
            label = "Hazardous Material",
            cardDescription = "Chemical or radiation\nShelter and evacuation",
            headline = "Follow official instructions to shelter indoors or evacuate.",
            steps = listOf(
                "Prioritize instructions from official broadcasts and emergency alerts.",
                "When sheltering indoors, close windows and doors and turn off ventilation.",
                "Do not approach or record the scene because exposure may be dangerous.",
                "Minimize exposed skin and follow any decontamination instructions."
            )
        ),
        DisasterId.Emergency to DisasterText(
            label = "119",
            cardDescription = "Not in manual\nCall 119",
            headline = "This situation was not found in the manual.",
            steps = listOf(
                "Call 119 immediately.",
                "Move to a safe place and describe your current location.",
                "Follow official rescue instructions first."
            )
        )
    ),
    AppLanguage.Chinese to mapOf(
        DisasterId.Earthquake to DisasterText(
            label = "地震",
            cardDescription = "地震应对\n避难方法",
            headline = "摇晃停止后，请撤离到开阔地带。",
            steps = listOf(
                "感到摇晃时，躲到坚固桌子下并保护头部。",
                "摇晃停止后，走楼梯到建筑外的开阔地带。",
                "不要使用电梯。",
                "到室外后，远离玻璃、招牌和可能掉落的物体。"
            )
        ),
        DisasterId.Fire to DisasterText(
            label = "火灾",
            cardDescription = "火灾应对\n烟雾安全",
            headline = "避开烟雾，低姿势撤离。",
            steps = listOf(
                "发现火情时，立即提醒周围的人并拨打119。",
                "先触摸门把手，不烫时再开门撤离。",
                "有烟雾时，用湿布捂住口鼻并低姿势移动。",
                "不要乘坐电梯，请使用安全出口或楼梯。"
            )
        ),
        DisasterId.Flood to DisasterText(
            label = "洪水",
            cardDescription = "浸水和洪水\n避难指南",
            headline = "立即前往更高、更安全的地方。",
            steps = listOf(
                "确认洪水警报和避难指示后，前往指定避难所。",
                "在安全的情况下关闭电闸和燃气阀。",
                "不要进入积水，避开急流和井盖附近。",
                "不要开车通过被水淹没的道路。"
            )
        ),
        DisasterId.Typhoon to DisasterText(
            label = "台风",
            cardDescription = "强风暴雨\n室内安全",
            headline = "留在室内，并远离窗户。",
            steps = listOf(
                "固定室外物品，或将其移入室内。",
                "锁好门窗，有窗帘或百叶窗时请拉上。",
                "强风时避免外出，待在安全的室内。",
                "如有浸水风险，准备转移到较高楼层或安全地点。"
            )
        ),
        DisasterId.Landslide to DisasterText(
            label = "山体滑坡",
            cardDescription = "滑坡征兆\n紧急避难",
            headline = "远离坡地、沟渠和挡土墙。",
            steps = listOf(
                "发现地面裂缝、小落石或异常声响时，立即撤离。",
                "不要停留在山谷、挡土墙或陡坡附近。",
                "只使用安全的避难路线，尽量避免开车。",
                "撤离后仍可能再次崩塌，请遵循官方指示。"
            )
        ),
        DisasterId.Tsunami to DisasterText(
            label = "海啸",
            cardDescription = "海啸应对\n前往高处",
            headline = "离开海岸和低洼地，前往高处。",
            steps = listOf(
                "地震后不要停留在海岸或河口附近。",
                "确认广播和紧急警报后，立即前往高处。",
                "如果车辆难以通行，也要步行前往更高地点。",
                "在海啸警报解除前，请留在安全地点。"
            )
        ),
        DisasterId.HeavySnow to DisasterText(
            label = "大雪",
            cardDescription = "积雪结冰\n交通和取暖安全",
            headline = "减少外出，安全取暖并保持通风。",
            steps = listOf(
                "只在必要时外出，并避开结冰或积雪路段。",
                "让可燃物远离取暖设备，并经常通风。",
                "注意屋顶和阳台积雪坠落。",
                "开车前检查除雪情况和轮胎状态。"
            )
        ),
        DisasterId.HazardRelease to DisasterText(
            label = "有害物质",
            cardDescription = "化学或放射性物质\n室内避险和撤离",
            headline = "按照官方指示，室内避险或向指定方向撤离。",
            steps = listOf(
                "优先遵循官方广播和紧急警报的指示。",
                "室内避险时，关闭门窗并停止通风。",
                "不要靠近或拍摄现场，可能存在暴露风险。",
                "撤离时尽量减少皮肤暴露，并遵循去污指示。"
            )
        ),
        DisasterId.Emergency to DisasterText(
            label = "119",
            cardDescription = "手册外情况\n联系119",
            headline = "手册中未找到该情况。",
            steps = listOf(
                "请立即拨打119。",
                "转移到安全地点后说明当前位置。",
                "优先遵循官方救援人员的指示。"
            )
        )
    )
)

private fun DisasterDefinition.localized(language: AppLanguage): DisasterDefinition {
    val translated = DisasterTranslations[language]?.get(id) ?: return this
    return copy(
        label = translated.label,
        cardDescription = translated.cardDescription,
        headline = translated.headline,
        steps = translated.steps
    )
}

private fun localizedDisasterCatalog(language: AppLanguage): List<DisasterDefinition> {
    return DisasterCatalog.map { it.localized(language) }
}

private fun getDisasterById(id: DisasterId, language: AppLanguage = AppLanguage.Korean): DisasterDefinition {
    return (DisasterCatalog.firstOrNull { it.id == id } ?: DisasterCatalog.first()).localized(language)
}

private val TextQuickTags = listOf(
    QuickTag("tag-earthquake", "지진", DisasterId.Earthquake.id),
    QuickTag("tag-fire", "화재", DisasterId.Fire.id),
    QuickTag("tag-blackout", "정전", DisasterId.HazardRelease.id),
    QuickTag("tag-emergency-1", "응급", "emergency"),
    QuickTag("tag-emergency-2", "응급", "emergency"),
    QuickTag("tag-emergency-3", "응급", "emergency")
)

private data class AppStrings(
    val offlineCoach: String,
    val offline: String,
    val online: String,
    val download: String,
    val ok: String,
    val homeDisasterPicker: String,
    val homeCamera: String,
    val homeTextQuestion: String,
    val homeSettings: String,
    val voiceStart: String,
    val disasterPickerTitle: String,
    val disasterQuestion: String,
    val disasterSubtitle: String,
    val noDisasterTitle: String,
    val noDisasterSubtitle: String,
    val guidanceSuffix: String,
    val call119: String,
    val flashlight: String,
    val turnOff: String,
    val siren: String,
    val actionSteps: String,
    val sourceText: String,
    val cameraTitle: String,
    val imageAnalyzing: String,
    val textQuestionTitle: String,
    val situationInput: String,
    val textPlaceholder: String,
    val aiAnalyzing: String,
    val getGuidance: String,
    val settingsTitle: String,
    val languageDefaultTitle: String,
    val languageDefaultSubtitle: String,
    val korean: String,
    val english: String,
    val chinese: String,
    val ttsTitle: String,
    val ttsSubtitle: String,
    val ttsTone: String,
    val voiceNatural: String,
    val voiceAssured: String,
    val voiceBrisk: String,
    val llmModelTitle: String,
    val llmModelSubtitle: String,
    val loading: String,
    val ready: String,
    val none: String,
    val status: String,
    val modelPath: String,
    val installMethod: String,
    val bundledModelFirst: String,
    val reloadModel: String,
    val deleteModel: String,
    val modelNote: String,
    val errorPrefix: String,
    val noticeTitle: String,
    val noticeBody: String,
    val cameraCaptureTitle: String,
    val voiceQuestionTitle: String,
    val modelNeededTitle: String,
    val modelNeededMessage: String,
    val modelLoadingTitle: String,
    val modelLoadingMessage: String,
    val modelReadyTitle: String,
    val modelReadyMessage: String,
    val downloadFailedTitle: String,
    val downloadFailedMessage: String,
    val modelDeletedTitle: String,
    val modelDeletedMessage: String,
    val deleteFailedTitle: String,
    val deleteFailedMessage: String,
    val inputNeededTitle: String,
    val inputNeededMessage: String,
    val analysisErrorTitle: String,
    val analysisErrorMessage: String,
    val voiceFailedTitle: String,
    val voiceFailedMessage: String,
    val noFlashTitle: String,
    val noFlashMessage: String,
    val torchErrorTitle: String,
    val torchErrorMessage: String,
    val captureFailedTitle: String,
    val captureFailedMessage: String,
    val permissionTitle: String,
    val cameraPermissionMessage: String,
    val micPermissionMessage: String
)

private fun appStrings(language: AppLanguage): AppStrings {
    return when (language) {
        AppLanguage.Korean -> AppStrings(
            offlineCoach = "오프라인 재난코치",
            offline = "오프라인",
            online = "온라인",
            download = "다운로드",
            ok = "확인",
            homeDisasterPicker = "재난 유형 선택",
            homeCamera = "안내문 촬영",
            homeTextQuestion = "텍스트 질문",
            homeSettings = "설정",
            voiceStart = "음성 질문 시작하기",
            disasterPickerTitle = "재난 유형 선택",
            disasterQuestion = "어떤 재난에 대한 정보가 필요하신가요?",
            disasterSubtitle = "재난 유형을 선택해주세요.",
            noDisasterTitle = "원하는 항목이 없나요?",
            noDisasterSubtitle = "음성이나 텍스트로 직접 질문해보세요",
            guidanceSuffix = "안내",
            call119 = "119",
            flashlight = "손전등",
            turnOff = "끄기",
            siren = "사이렌",
            actionSteps = "행동 단계",
            sourceText = "출처: 행정안전부 국민재난안전포털·소방청 119 안전 안내 요약",
            cameraTitle = "안내문 촬영",
            imageAnalyzing = "이미지 분석중...",
            textQuestionTitle = "텍스트 질문",
            situationInput = "상황입력",
            textPlaceholder = "지금 상황을 입력해주세요.",
            aiAnalyzing = "AI 분석 중...",
            getGuidance = "안내받기",
            settingsTitle = "설정",
            languageDefaultTitle = "언어 기본값",
            languageDefaultSubtitle = "현재 기본 언어를 선택하세요.",
            korean = "한국어",
            english = "영어",
            chinese = "중국어",
            ttsTitle = "음성 TTS",
            ttsSubtitle = "음성 안내",
            ttsTone = "음성 안내 톤을 선택합니다.",
            voiceNatural = "느낌",
            voiceAssured = "보증",
            voiceBrisk = "빠름",
            llmModelTitle = "LLM 모델",
            llmModelSubtitle = "버튼 한 번으로 HuggingFace GGUF를 내려받아 바로 적용합니다.",
            loading = "불러오는 중",
            ready = "준비됨",
            none = "없음",
            status = "상태",
            modelPath = "모델 경로",
            installMethod = "설치 방법",
            bundledModelFirst = "앱 내장 모델 우선",
            reloadModel = "모델 다시 다운로드",
            deleteModel = "모델 삭제",
            modelNote = "텍스트 질문은 앱에 포함된 오프라인 모델을 우선 사용합니다. 문제가 있으면 모델을 다시 다운로드하세요.",
            errorPrefix = "오류",
            noticeTitle = "안내",
            noticeBody = "오프라인 상태에서도 기본 재난 안내와 내장 LLM 기능을 사용할 수 있습니다.",
            cameraCaptureTitle = "카메라 촬영",
            voiceQuestionTitle = "음성 질문",
            modelNeededTitle = "LLM 모델 필요",
            modelNeededMessage = "내장 LLM 모델을 준비하지 못했습니다. 다운로드 버튼으로 모델을 다시 받아 적용할 수 있습니다.",
            modelLoadingTitle = "모델 준비 중",
            modelLoadingMessage = "내장 LLM 모델을 준비하고 있습니다. 잠시 후 다시 시도해 주세요.",
            modelReadyTitle = "모델 준비 완료",
            modelReadyMessage = "LLM 모델을 자동으로 내려받아 적용했습니다.",
            downloadFailedTitle = "다운로드 실패",
            downloadFailedMessage = "모델을 내려받지 못했습니다.",
            modelDeletedTitle = "모델 삭제",
            modelDeletedMessage = "LLM 모델을 삭제했습니다.",
            deleteFailedTitle = "삭제 실패",
            deleteFailedMessage = "모델을 삭제하지 못했습니다.",
            inputNeededTitle = "입력 필요",
            inputNeededMessage = "상황을 입력해 주세요.",
            analysisErrorTitle = "분석 오류",
            analysisErrorMessage = "분석 중 오류가 발생했습니다.",
            voiceFailedTitle = "음성 인식 실패",
            voiceFailedMessage = "다시 한 번 천천히 말씀해 주세요.",
            noFlashTitle = "손전등 없음",
            noFlashMessage = "이 기기에서 손전등을 사용할 수 없습니다.",
            torchErrorTitle = "손전등 오류",
            torchErrorMessage = "손전등을 전환하지 못했습니다.",
            captureFailedTitle = "촬영 실패",
            captureFailedMessage = "카메라 촬영이 취소되었습니다.",
            permissionTitle = "권한 필요",
            cameraPermissionMessage = "카메라 권한이 필요합니다.",
            micPermissionMessage = "음성 녹음을 위해 마이크 권한이 필요합니다."
        )
        AppLanguage.English -> AppStrings(
            offlineCoach = "Offline Disaster Coach",
            offline = "Offline",
            online = "Online",
            download = "Download",
            ok = "OK",
            homeDisasterPicker = "Choose Disaster Type",
            homeCamera = "Scan Notice",
            homeTextQuestion = "Text Question",
            homeSettings = "Settings",
            voiceStart = "Start Voice Question",
            disasterPickerTitle = "Choose Disaster Type",
            disasterQuestion = "What disaster information do you need?",
            disasterSubtitle = "Select a disaster type.",
            noDisasterTitle = "Need something else?",
            noDisasterSubtitle = "Ask directly by voice or text",
            guidanceSuffix = "Guide",
            call119 = "119",
            flashlight = "Flashlight",
            turnOff = "Off",
            siren = "Siren",
            actionSteps = "Action Steps",
            sourceText = "Source: Korean public disaster and 119 safety guidance summary",
            cameraTitle = "Scan Notice",
            imageAnalyzing = "Analyzing image...",
            textQuestionTitle = "Text Question",
            situationInput = "Situation",
            textPlaceholder = "Describe the current situation.",
            aiAnalyzing = "AI analyzing...",
            getGuidance = "Get Guidance",
            settingsTitle = "Settings",
            languageDefaultTitle = "Default Language",
            languageDefaultSubtitle = "Choose the app language.",
            korean = "Korean",
            english = "English",
            chinese = "Chinese",
            ttsTitle = "Voice TTS",
            ttsSubtitle = "Voice guidance",
            ttsTone = "Choose the voice guidance tone.",
            voiceNatural = "Natural",
            voiceAssured = "Assured",
            voiceBrisk = "Brisk",
            llmModelTitle = "LLM Model",
            llmModelSubtitle = "Download and apply the HuggingFace GGUF model in one tap.",
            loading = "Loading",
            ready = "Ready",
            none = "None",
            status = "Status",
            modelPath = "Model path",
            installMethod = "Install method",
            bundledModelFirst = "Bundled model first",
            reloadModel = "Reload model",
            deleteModel = "Delete model",
            modelNote = "Text questions use the offline model included in the app first. Reload the model if there is a problem.",
            errorPrefix = "Error",
            noticeTitle = "Notice",
            noticeBody = "Basic disaster guidance and the bundled offline LLM can be used without a network connection.",
            cameraCaptureTitle = "Camera Scan",
            voiceQuestionTitle = "Voice Question",
            modelNeededTitle = "LLM Model Required",
            modelNeededMessage = "The bundled LLM model could not be prepared. Use Download to fetch and apply the model again.",
            modelLoadingTitle = "Preparing Model",
            modelLoadingMessage = "The bundled LLM model is being prepared. Please try again shortly.",
            modelReadyTitle = "Model Ready",
            modelReadyMessage = "The LLM model has been downloaded and applied.",
            downloadFailedTitle = "Download Failed",
            downloadFailedMessage = "Could not download the model.",
            modelDeletedTitle = "Model Deleted",
            modelDeletedMessage = "The LLM model has been deleted.",
            deleteFailedTitle = "Delete Failed",
            deleteFailedMessage = "Could not delete the model.",
            inputNeededTitle = "Input Required",
            inputNeededMessage = "Please enter the situation.",
            analysisErrorTitle = "Analysis Error",
            analysisErrorMessage = "An error occurred during analysis.",
            voiceFailedTitle = "Voice Recognition Failed",
            voiceFailedMessage = "Please speak slowly one more time.",
            noFlashTitle = "No Flashlight",
            noFlashMessage = "This device does not support flashlight control.",
            torchErrorTitle = "Flashlight Error",
            torchErrorMessage = "Could not toggle the flashlight.",
            captureFailedTitle = "Capture Failed",
            captureFailedMessage = "Camera capture was canceled.",
            permissionTitle = "Permission Required",
            cameraPermissionMessage = "Camera permission is required.",
            micPermissionMessage = "Microphone permission is required for voice input."
        )
        AppLanguage.Chinese -> AppStrings(
            offlineCoach = "离线灾害应对教练",
            offline = "离线",
            online = "在线",
            download = "下载",
            ok = "确认",
            homeDisasterPicker = "选择灾害类型",
            homeCamera = "拍摄通知",
            homeTextQuestion = "文字提问",
            homeSettings = "设置",
            voiceStart = "开始语音提问",
            disasterPickerTitle = "选择灾害类型",
            disasterQuestion = "您需要哪种灾害信息？",
            disasterSubtitle = "请选择灾害类型。",
            noDisasterTitle = "没有需要的项目？",
            noDisasterSubtitle = "可用语音或文字直接提问",
            guidanceSuffix = "指南",
            call119 = "119",
            flashlight = "手电筒",
            turnOff = "关闭",
            siren = "警报",
            actionSteps = "行动步骤",
            sourceText = "来源：韩国公共灾害与119安全指南摘要",
            cameraTitle = "拍摄通知",
            imageAnalyzing = "正在分析图像...",
            textQuestionTitle = "文字提问",
            situationInput = "情况输入",
            textPlaceholder = "请输入当前情况。",
            aiAnalyzing = "AI分析中...",
            getGuidance = "获取指南",
            settingsTitle = "设置",
            languageDefaultTitle = "默认语言",
            languageDefaultSubtitle = "请选择应用语言。",
            korean = "韩语",
            english = "英语",
            chinese = "中文",
            ttsTitle = "语音TTS",
            ttsSubtitle = "语音指南",
            ttsTone = "选择语音指南语气。",
            voiceNatural = "自然",
            voiceAssured = "稳重",
            voiceBrisk = "快速",
            llmModelTitle = "LLM模型",
            llmModelSubtitle = "一键下载并应用HuggingFace GGUF模型。",
            loading = "加载中",
            ready = "就绪",
            none = "无",
            status = "状态",
            modelPath = "模型路径",
            installMethod = "安装方式",
            bundledModelFirst = "优先使用内置模型",
            reloadModel = "重新下载模型",
            deleteModel = "删除模型",
            modelNote = "文字提问优先使用应用内置的离线模型。如有问题，请重新下载模型。",
            errorPrefix = "错误",
            noticeTitle = "提示",
            noticeBody = "离线状态下也可使用基本灾害指南和内置LLM功能。",
            cameraCaptureTitle = "拍摄",
            voiceQuestionTitle = "语音提问",
            modelNeededTitle = "需要LLM模型",
            modelNeededMessage = "未能准备内置LLM模型。可通过下载按钮重新获取并应用。",
            modelLoadingTitle = "模型准备中",
            modelLoadingMessage = "正在准备内置LLM模型，请稍后再试。",
            modelReadyTitle = "模型已就绪",
            modelReadyMessage = "LLM模型已下载并应用。",
            downloadFailedTitle = "下载失败",
            downloadFailedMessage = "无法下载模型。",
            modelDeletedTitle = "模型已删除",
            modelDeletedMessage = "LLM模型已删除。",
            deleteFailedTitle = "删除失败",
            deleteFailedMessage = "无法删除模型。",
            inputNeededTitle = "需要输入",
            inputNeededMessage = "请输入情况。",
            analysisErrorTitle = "分析错误",
            analysisErrorMessage = "分析过程中发生错误。",
            voiceFailedTitle = "语音识别失败",
            voiceFailedMessage = "请再慢慢说一遍。",
            noFlashTitle = "无手电筒",
            noFlashMessage = "此设备不支持手电筒控制。",
            torchErrorTitle = "手电筒错误",
            torchErrorMessage = "无法切换手电筒。",
            captureFailedTitle = "拍摄失败",
            captureFailedMessage = "相机拍摄已取消。",
            permissionTitle = "需要权限",
            cameraPermissionMessage = "需要相机权限。",
            micPermissionMessage = "语音输入需要麦克风权限。"
        )
    }
}

private fun downloadProgressText(language: AppLanguage, progress: Int): String {
    return when (language) {
        AppLanguage.Korean -> "다운로드 $progress%"
        AppLanguage.English -> "Download $progress%"
        AppLanguage.Chinese -> "下载 $progress%"
    }
}

private fun loadingProgressText(language: AppLanguage, progress: Int): String {
    return when (language) {
        AppLanguage.Korean -> "진행률: $progress%"
        AppLanguage.English -> "Progress: $progress%"
        AppLanguage.Chinese -> "进度: $progress%"
    }
}

private fun localizedStatusText(statusKey: String, language: AppLanguage): String {
    val text = appStrings(language)
    return when (statusKey) {
        "online", "온라인", "Online", "オンライン" -> text.online
        else -> text.offline
    }
}

private fun localizedGuidanceTitle(titleKey: String?, language: AppLanguage): String? {
    val text = appStrings(language)
    return when (titleKey) {
        "camera" -> text.cameraCaptureTitle
        "voice" -> text.voiceQuestionTitle
        "text" -> text.textQuestionTitle
        null -> null
        else -> titleKey
    }
}

private fun localizedRecommendation(disasterId: DisasterId, language: AppLanguage): String {
    return when (language) {
        AppLanguage.Korean -> when (disasterId) {
            DisasterId.Earthquake -> "구조물 균열과 낙하물 위험에 주의하세요."
            DisasterId.Fire -> "연기를 피해 낮은 자세로 빠르게 대피하세요."
            DisasterId.Flood -> "높은 곳으로 이동하고 전기 제품에 주의하세요."
            DisasterId.Typhoon -> "창문에서 떨어져 안전한 실내로 이동하세요."
            DisasterId.Landslide -> "산 기슭이나 하천 근처를 피하세요."
            DisasterId.Tsunami -> "즉시 높은 지대로 대피하세요."
            DisasterId.HeavySnow -> "외출을 자제하고 보온에 신경쓰세요."
            DisasterId.HazardRelease -> "즉시 환기하고 안전거리 확보하세요."
            DisasterId.Emergency -> "매뉴얼에서 해당 상황을 찾지 못했습니다. 즉시 119에 전화하세요."
        }
        AppLanguage.English -> when (disasterId) {
            DisasterId.Earthquake -> "Watch for structural cracks and falling objects."
            DisasterId.Fire -> "Stay low and evacuate quickly away from smoke."
            DisasterId.Flood -> "Move to higher ground and avoid electrical hazards."
            DisasterId.Typhoon -> "Move away from windows and stay in a safe indoor area."
            DisasterId.Landslide -> "Avoid mountain slopes, valleys, and river areas."
            DisasterId.Tsunami -> "Evacuate to higher ground immediately."
            DisasterId.HeavySnow -> "Avoid unnecessary travel and keep warm safely."
            DisasterId.HazardRelease -> "Move upwind, keep distance, and call 119 if needed."
            DisasterId.Emergency -> "Not in manual. Call 119 immediately."
        }
        AppLanguage.Chinese -> when (disasterId) {
            DisasterId.Earthquake -> "注意建筑裂缝和坠落物。"
            DisasterId.Fire -> "避开烟雾，低姿势快速撤离。"
            DisasterId.Flood -> "前往高处，避开电气危险。"
            DisasterId.Typhoon -> "远离窗户，待在安全室内。"
            DisasterId.Landslide -> "避开山坡、山谷和河流附近。"
            DisasterId.Tsunami -> "立即撤离到高处。"
            DisasterId.HeavySnow -> "避免不必要外出，并安全保暖。"
            DisasterId.HazardRelease -> "向上风方向移动，保持距离，必要时拨打119。"
            DisasterId.Emergency -> "手册中未找到。请立即拨打119。"
        }
    }
}

private fun localizedWarning(warning: String?, disasterId: DisasterId, language: AppLanguage): String? {
    if (warning.isNullOrBlank()) return null
    return if (language == AppLanguage.Korean) warning else localizedRecommendation(disasterId, language)
}

private fun guidanceSpeechText(
    language: AppLanguage,
    disaster: DisasterDefinition,
    warning: String?
): String {
    val text = appStrings(language)
    val steps = disaster.steps.take(3).joinToString(" ")
    return listOfNotNull(
        "${disaster.label} ${text.guidanceSuffix}.",
        disaster.headline,
        warning,
        steps
    ).joinToString(" ")
}

private fun detectDisasterFromKeywords(text: String): DisasterDetectResult {
    val normalized = text.lowercase(Locale.getDefault())
    val types = listOf(
        DisasterDetectResult(DisasterId.Earthquake, "지진", 0.7, listOf("지진", "진동", "흔들", "흔들림", "진동이", "earthquake", "quake", "shake", "地震", "摇晃", "震动")),
        DisasterDetectResult(DisasterId.Fire, "화재", 0.7, listOf("불", "화재", "불이", "불나", "불이야", "연기", "화염", "불꽃", "fire", "smoke", "flame", "火灾", "着火", "烟", "火焰")),
        DisasterDetectResult(DisasterId.Flood, "홍수", 0.7, listOf("물", "홍수", "침수", "물이", "물이차", "물이들어와", "flood", "water", "flooding", "洪水", "浸水", "积水")),
        DisasterDetectResult(DisasterId.Typhoon, "태풍", 0.7, listOf("바람", "태풍", "강풍", "폭풍", "호우", "heavy rain", "typhoon", "wind", "storm", "台风", "强风", "暴风", "暴雨")),
        DisasterDetectResult(DisasterId.Landslide, "산사태", 0.7, listOf("산사태", "붕괴", "무너지", "흙", "땅", "landslide", "山体滑坡", "滑坡", "塌方")),
        DisasterDetectResult(DisasterId.Tsunami, "쓰나미", 0.7, listOf("쓰나미", "해일", "해일경보", "tsunami", "海啸")),
        DisasterDetectResult(DisasterId.HeavySnow, "대설", 0.7, listOf("눈", "대설", "폭설", "눈사태", "빙판", "snow", "blizzard", "heavy snow", "大雪", "暴雪", "雪", "结冰")),
        DisasterDetectResult(DisasterId.HazardRelease, "유해물질", 0.7, listOf("유해", "독", "가스", "가스누출", "가스냄새", "화학", "유출", "누출", "폭발", "hazard", "gas", "chemical", "有害", "煤气", "燃气", "化学", "泄漏", "爆炸"))
    )

    for (type in types) {
        if (type.keywords.any { normalized.contains(it) }) {
            return type
        }
    }

    return DisasterDetectResult(DisasterId.Emergency, "119", 0.0, emptyList())
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

    return AnalysisResult(DisasterId.Emergency, localizedRecommendation(DisasterId.Emergency, AppLanguage.Korean))
}

private fun analyzeCapturedImage(uri: String, language: AppLanguage): AnalysisResult {
    val normalizedUri = uri.lowercase(Locale.getDefault())
    val rules = listOf(
        Pair(listOf("fire", "flame", "smoke", "화재", "불"), DisasterId.Fire),
        Pair(listOf("flood", "rain", "water", "침수", "홍수"), DisasterId.Flood),
        Pair(listOf("quake", "earth", "crack", "지진", "붕괴"), DisasterId.Earthquake)
    )

    for ((keywords, disasterId) in rules) {
        if (keywords.any { normalizedUri.contains(it) }) {
            val warning = localizedRecommendation(disasterId, language)
            return AnalysisResult(disasterId, warning)
        }
    }

    return AnalysisResult(DisasterId.Emergency, localizedRecommendation(DisasterId.Emergency, language))
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

    return AnalysisResult(DisasterId.Emergency, localizedRecommendation(DisasterId.Emergency, AppLanguage.Korean))
}

@Composable
fun ResQApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val offlineLlm = remember { OfflineLlmManager(context) }
    val offlineState by offlineLlm.state.collectAsState()
    val voiceController = remember { VoiceInputController(context) }
    val ttsController = remember { GuidanceTtsController(context) }
    val torchController = remember { TorchController(context) }
    val settingsPrefs = remember { context.getSharedPreferences("resq-settings", Context.MODE_PRIVATE) }

    var screen by remember { mutableStateOf(Screen.Onboard) }
    var selectedDisasterId by remember { mutableStateOf(DisasterId.Earthquake) }
    var disasterPickerSource by remember { mutableStateOf("home") }
    var guidanceTitle by remember { mutableStateOf<String?>(null) }
    var statusText by remember { mutableStateOf("offline") }
    var analysisWarning by remember { mutableStateOf<String?>(null) }
    var guidanceBackTarget by remember { mutableStateOf("home") }
    var textQuestion by remember { mutableStateOf("") }
    var selectedQuickTagId by remember { mutableStateOf(TextQuickTags.first().id) }
    var language by remember {
        mutableStateOf(AppLanguage.fromCode(settingsPrefs.getString("language", AppLanguage.Korean.code) ?: AppLanguage.Korean.code))
    }
    var ttsEnabled by remember { mutableStateOf(settingsPrefs.getBoolean("tts_enabled", true)) }
    var voiceType by remember { mutableStateOf(settingsPrefs.getString("voice_type", "natural") ?: "natural") }
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
    val strings = appStrings(language)

    fun toggleTorch() {
        try {
            if (!torchController.hasFlashlight()) {
                alertState = AlertState(strings.noFlashTitle, strings.noFlashMessage)
                return
            }
            val next = !isTorchOn
            torchController.setTorchEnabled(next)
            isTorchOn = next
        } catch (e: Exception) {
            alertState = AlertState(strings.torchErrorTitle, e.message ?: strings.torchErrorMessage)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val capturedUri = pendingCameraUri
        pendingCameraUri = null
        if (!success || capturedUri == null) {
            if (pendingCameraLaunch) {
                alertState = AlertState(strings.captureFailedTitle, strings.captureFailedMessage)
            }
            pendingCameraLaunch = false
            return@rememberLauncherForActivityResult
        }

        pendingCameraLaunch = false
        scope.launch {
            guidanceTitle = null
            analysisWarning = null
            statusText = "online"
            screen = Screen.CameraLoading
            delay(600)
            val result = analyzeCapturedImage(capturedUri.toString(), language)
            selectedDisasterId = result.disasterId
            guidanceTitle = "camera"
            analysisWarning = result.warning
            guidanceBackTarget = "home"
            screen = Screen.Guidance
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            alertState = AlertState(strings.permissionTitle, strings.cameraPermissionMessage)
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
            alertState = AlertState(strings.permissionTitle, strings.micPermissionMessage)
            pendingMicLaunch = false
            return@rememberLauncherForActivityResult
        }
        if (pendingMicLaunch) {
            pendingMicLaunch = false
            startVoiceFlow(
                voiceController = voiceController,
                offlineLlm = offlineLlm,
                language = language,
                onStart = { isListening = true },
                onStop = { isListening = false },
                onAlert = { title, message -> alertState = AlertState(title, message) },
                onResult = { result ->
                    selectedDisasterId = result.disasterId
                    guidanceTitle = "voice"
                    analysisWarning = result.recommendation
                    guidanceBackTarget = "home"
                    statusText = "offline"
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
                    strings.modelNeededTitle,
                    strings.modelNeededMessage
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceController.destroy()
            ttsController.shutdown()
            runCatching { torchController.setTorchEnabled(false) }
            offlineLlm.close()
        }
    }

    LaunchedEffect(screen, selectedDisasterId, analysisWarning, language, ttsEnabled, voiceType) {
        if (screen == Screen.Guidance && ttsEnabled) {
            val disaster = getDisasterById(selectedDisasterId, language)
            val warning = localizedWarning(analysisWarning, selectedDisasterId, language)
            ttsController.speak(guidanceSpeechText(language, disaster, warning), language, voiceType)
        } else if (!ttsEnabled) {
            ttsController.stop()
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        color = Color(0xFF0A0A0A)
    ) {
        when (screen) {
            Screen.Onboard -> OnboardingScreen(language = language, onDone = { screen = Screen.Home })
            Screen.Home -> HomeScreen(
                language = language,
                offlineState = offlineState,
                isListening = isListening,
                onStartVoice = {
                    if (offlineState.isLoading) {
                        alertState = AlertState(
                            strings.modelLoadingTitle,
                            "${strings.modelLoadingMessage}\n${loadingProgressText(language, offlineState.downloadProgress)}"
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
                            language = language,
                            onStart = { isListening = true },
                            onStop = { isListening = false },
                            onAlert = { title, message -> alertState = AlertState(title, message) },
                            onResult = { result ->
                                selectedDisasterId = result.disasterId
                                guidanceTitle = "voice"
                                analysisWarning = result.recommendation
                                guidanceBackTarget = "home"
                                statusText = "offline"
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
                    statusText = "offline"
                    screen = Screen.TextQuery
                },
                onOpenSettings = {
                    statusText = "offline"
                    screen = Screen.Settings
                }
            )
            Screen.Disaster -> DisasterScreen(
                language = language,
                catalog = localizedDisasterCatalog(language),
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
            Screen.CameraLoading -> CameraAnalyzingScreen(language = language)
            Screen.TextQuery -> TextQuestionScreen(
                language = language,
                textValue = textQuestion,
                onChangeText = { textQuestion = it },
                onBack = {
                    statusText = "offline"
                    screen = Screen.Home
                },
                isAnalyzing = isAnalyzing,
                onSubmit = {
                    val trimmed = textQuestion.trim()
                    if (trimmed.isEmpty()) {
                        alertState = AlertState(strings.inputNeededTitle, strings.inputNeededMessage)
                        return@TextQuestionScreen
                    }
                    if (offlineLlm.state.value.isLoading) {
                        alertState = AlertState(strings.modelLoadingTitle, strings.modelLoadingMessage)
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
                                offlineLlm,
                                language
                            )
                            selectedDisasterId = result.disasterId
                            guidanceTitle = "text"
                            analysisWarning = result.warning
                            guidanceBackTarget = "text_query"
                            statusText = "offline"
                            screen = Screen.Guidance
                        } catch (e: Exception) {
                            alertState = AlertState(strings.analysisErrorTitle, e.message ?: strings.analysisErrorMessage)
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
                onLanguageChange = {
                    language = it
                    settingsPrefs.edit().putString("language", it.code).apply()
                },
                onTtsToggle = {
                    ttsEnabled = it
                    settingsPrefs.edit().putBoolean("tts_enabled", it).apply()
                },
                onVoiceChange = {
                    voiceType = it
                    settingsPrefs.edit().putString("voice_type", it).apply()
                },
                onDownloadModel = {
                    scope.launch {
                        try {
                            offlineLlm.downloadModel()
                            initAttempted = false
                            modelPromptShown = false
                            alertState = AlertState(strings.modelReadyTitle, strings.modelReadyMessage)
                        } catch (e: Exception) {
                            alertState = AlertState(strings.downloadFailedTitle, e.message ?: strings.downloadFailedMessage)
                        }
                    }
                },
                onDeleteModel = {
                    scope.launch {
                        try {
                            offlineLlm.deleteModel()
                            initAttempted = false
                            modelPromptShown = false
                            alertState = AlertState(strings.modelDeletedTitle, strings.modelDeletedMessage)
                        } catch (e: Exception) {
                            alertState = AlertState(strings.deleteFailedTitle, e.message ?: strings.deleteFailedMessage)
                        }
                    }
                },
                onBack = {
                    statusText = "offline"
                    screen = Screen.Home
                }
            )
            Screen.Guidance -> GuidanceScreen(
                language = language,
                disaster = getDisasterById(selectedDisasterId, language),
                title = localizedGuidanceTitle(guidanceTitle, language),
                statusText = localizedStatusText(statusText, language),
                warning = localizedWarning(analysisWarning, selectedDisasterId, language),
                isTorchOn = isTorchOn,
                onBack = {
                    if (guidanceBackTarget == "text_query") {
                        screen = Screen.TextQuery
                    } else {
                        statusText = "offline"
                        guidanceTitle = null
                        analysisWarning = null
                        screen = Screen.Home
                    }
                },
                onOpenDisasterPicker = {
                    disasterPickerSource = "guidance"
                    statusText = "offline"
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
                    val isModelNeeded = alert.title == strings.modelNeededTitle
                    val confirmLabel = if (isModelNeeded) strings.download else strings.ok
                    Text(
                        text = confirmLabel,
                        modifier = Modifier
                            .padding(12.dp)
                            .clickable {
                                if (isModelNeeded) {
                                    scope.launch {
                                        try {
                                            alertState = null
                                            offlineLlm.downloadModel()
                                            initAttempted = false
                                            modelPromptShown = false
                                            alertState = AlertState(strings.modelReadyTitle, strings.modelReadyMessage)
                                        } catch (e: Exception) {
                                            alertState = AlertState(strings.downloadFailedTitle, e.message ?: strings.downloadFailedMessage)
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
    language: AppLanguage,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onAlert: (String, String) -> Unit,
    onResult: (VoiceAnalysisResult) -> Unit
) {
    val strings = appStrings(language)
    val scope = voiceController.scope
    scope.launch {
        onStart()
        try {
            val transcript = voiceController.recordAndAnalyze(language)
            if (transcript.isNullOrBlank()) {
                onAlert(strings.voiceFailedTitle, strings.voiceFailedMessage)
                return@launch
            }

            val analysis = offlineLlm.analyzeDisaster(transcript, language)
            val quickDetection = detectDisasterFromKeywords(transcript)
            val chosenId = analysis?.disasterId ?: quickDetection.id
            val chosen = getDisasterById(chosenId, language)

            onResult(
                VoiceAnalysisResult(
                    recognizedText = transcript,
                    disasterId = chosenId,
                    disasterLabel = chosen.label,
                    recommendation = analysis?.recommendation ?: localizedRecommendation(chosenId, language),
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

private fun open119Dialer(context: Context) {
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:119"))
    context.startActivity(intent)
}

private fun playSirenTone() {
    val tone = ToneGenerator(AudioManager.STREAM_ALARM, 100)
    tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1200)
    Handler(android.os.Looper.getMainLooper()).postDelayed({ tone.release() }, 1500)
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
        Pair("위험물", "바람 방향 반대로 대피하고 119에 신고하세요."),
        Pair("119", "매뉴얼에서 해당 상황을 찾지 못했습니다. 즉시 119에 전화하세요.")
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
                    verifyModelFile(modelFile)
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
            verifyModelFile(modelFile)
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

    private fun verifyModelFile(file: File) {
        val actual = sha256(file)
        if (!actual.equals(EXPECTED_MODEL_SHA256, ignoreCase = true)) {
            file.delete()
            throw IllegalStateException("모델 파일 검증에 실패했습니다. 다시 다운로드해 주세요.")
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            var read = input.read(buffer)
            while (read >= 0) {
                digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
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

    fun analyzeDisaster(text: String, language: AppLanguage): OfflineAnalysisResult? {
        val quick = detectDisasterFromKeywords(text)
        return OfflineAnalysisResult(
            disasterId = quick.id,
            confidence = quick.confidence,
            recommendation = localizedRecommendation(quick.id, language)
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
9=매뉴얼에 없는 비재난 질문(none, not disaster)
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
                        normalized.contains("지진") || normalized.contains("earthquake") || normalized.contains("地震") -> 1
                        normalized.contains("화재") || normalized.contains("fire") || normalized.contains("火灾") || normalized.contains("着火") -> 2
                        normalized.contains("홍수") || normalized.contains("flood") || normalized.contains("洪水") || normalized.contains("浸水") -> 3
                        normalized.contains("태풍") || normalized.contains("typhoon") || normalized.contains("台风") -> 4
                        normalized.contains("산사태") || normalized.contains("landslide") || normalized.contains("滑坡") || normalized.contains("塌方") -> 5
                        normalized.contains("쓰나미") || normalized.contains("tsunami") || normalized.contains("해일") || normalized.contains("海啸") -> 6
                        normalized.contains("대설") || normalized.contains("폭설") || normalized.contains("heavy snow") || normalized.contains("大雪") -> 7
                        normalized.contains("위험물") || normalized.contains("hazard") || normalized.contains("chemical") || normalized.contains("有害") || normalized.contains("燃气") || normalized.contains("煤气") -> 8
                        normalized.contains("none") || normalized.contains("not disaster") || normalized.contains("비재난") || normalized.contains("없음") -> 9
                        else -> fallbackTextCategory(prompt).let { fallback ->
                            return@withContext fallback
                        }
                    }
                Log.d("ResQApp-LLM", "파싱된 카테고리 번호: $categoryNum")

                val selectedCategory = if (categoryNum in 1..9) {
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
            DisasterId.Emergency -> 8
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
        private const val EXPECTED_MODEL_SHA256 = "d50db8b4573839fb4a3a5e66342bb9977da4e821992ad722974359504f1d4ed3"
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

    private fun recognizerIntent(language: AppLanguage): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.recognizerTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language.recognizerTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
    }

    fun startListening(language: AppLanguage) {
        _error.value = null
        _recognizedText.value = ""
        speechRecognizer.startListening(recognizerIntent(language))
        _isListening.value = true
    }

    fun stopListening() {
        speechRecognizer.stopListening()
        _isListening.value = false
    }

    suspend fun recordAndAnalyze(language: AppLanguage): String? {
        startListening(language)
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

private class GuidanceTtsController(context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var pendingLanguage = AppLanguage.Korean
    private var pendingVoiceType = "natural"
    private var pendingSpeech: String? = null

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            applyVoice()
            pendingSpeech?.let {
                pendingSpeech = null
                speak(it, pendingLanguage, pendingVoiceType)
            }
        }
    }

    fun speak(text: String, language: AppLanguage, voiceType: String) {
        pendingLanguage = language
        pendingVoiceType = voiceType
        if (!ready) {
            pendingSpeech = text
            return
        }

        applyVoice()
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "resq-guidance")
    }

    fun stop() {
        pendingSpeech = null
        if (ready) {
            tts.stop()
        }
    }

    fun shutdown() {
        pendingSpeech = null
        tts.stop()
        tts.shutdown()
    }

    private fun applyVoice() {
        val result = tts.setLanguage(pendingLanguage.speechLocale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.KOREAN)
        }

        when (pendingVoiceType) {
            "assured" -> {
                tts.setSpeechRate(0.9f)
                tts.setPitch(0.95f)
            }
            "brisk" -> {
                tts.setSpeechRate(1.08f)
                tts.setPitch(1.03f)
            }
            else -> {
                tts.setSpeechRate(0.96f)
                tts.setPitch(1.0f)
            }
        }
    }
}

@Composable
private fun OnboardingScreen(language: AppLanguage, onDone: () -> Unit) {
    val strings = appStrings(language)
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
                text = strings.offlineCoach,
                fontSize = 16.sp,
                color = Color(0xFFC7C7C7),
                fontFamily = ResQFontFamily
            )
        }
    }
}

@Composable
private fun HomeScreen(
    language: AppLanguage,
    offlineState: OfflineLlmState,
    isListening: Boolean,
    onStartVoice: () -> Unit,
    onOpenDisasterPicker: () -> Unit,
    onOpenCameraCapture: () -> Unit,
    onOpenTextQuestion: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val strings = appStrings(language)
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
        HomeAction(R.drawable.resq_ic_home_disaster_grid, strings.homeDisasterPicker, onOpenDisasterPicker),
        HomeAction(R.drawable.resq_ic_home_camera, strings.homeCamera, onOpenCameraCapture),
        HomeAction(R.drawable.resq_ic_home_text, strings.homeTextQuestion, onOpenTextQuestion),
        HomeAction(R.drawable.resq_ic_home_settings, strings.homeSettings, onOpenSettings)
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
                    strings.offline
                } else if (offlineState.isLoading) {
                    downloadProgressText(language, offlineState.downloadProgress)
                } else {
                    strings.offline
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
                    Image(
                        painter = painterResource(
                            if (isListening) R.drawable.resq_ic_voice_wave else R.drawable.resq_ic_voice_mic
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(96.dp)
                    )
                }
                Text(
                    text = strings.voiceStart,
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
                            Image(
                                painter = painterResource(item.iconRes),
                                contentDescription = null,
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
    val iconRes: Int,
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
    language: AppLanguage,
    catalog: List<DisasterDefinition>,
    onBack: () -> Unit,
    onSelectType: (DisasterId) -> Unit
) {
    val strings = appStrings(language)
    ResQGradientScreen {
        Column(modifier = Modifier.fillMaxSize()) {
            Header(title = strings.disasterPickerTitle, trailing = { Badge(label = strings.offline) }, onBack = onBack)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = strings.disasterQuestion,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
                )
                Text(
                    text = strings.disasterSubtitle,
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
                        Image(
                            painter = painterResource(R.drawable.resq_ic_warning_red),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(text = strings.noDisasterTitle, color = Color.White)
                        Text(text = strings.noDisasterSubtitle, color = Color(0xFFC7C7C7))
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
            Image(
                painter = painterResource(item.iconRes),
                contentDescription = null,
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
    language: AppLanguage,
    disaster: DisasterDefinition,
    title: String?,
    statusText: String,
    warning: String?,
    isTorchOn: Boolean,
    onBack: () -> Unit,
    onOpenDisasterPicker: () -> Unit,
    onToggleTorch: () -> Unit
) {
    val strings = appStrings(language)
    val context = LocalContext.current
    ResQGradientScreen {
        Column(modifier = Modifier.fillMaxSize()) {
            Header(title = title ?: "${disaster.label} ${strings.guidanceSuffix}", trailing = { Badge(label = statusText) }, onBack = onBack)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickActionButton(
                        iconRes = R.drawable.resq_ic_quick_phone,
                        label = strings.call119,
                        onClick = { open119Dialer(context) }
                    )
                    QuickActionButton(
                        iconRes = R.drawable.resq_ic_quick_flashlight,
                        label = if (isTorchOn) strings.turnOff else strings.flashlight,
                        active = isTorchOn,
                        onClick = onToggleTorch
                    )
                    QuickActionButton(
                        iconRes = R.drawable.resq_ic_quick_siren,
                        label = strings.siren,
                        onClick = { playSirenTone() }
                    )
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
                            Image(
                                painter = painterResource(disaster.iconRes),
                                contentDescription = null,
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
                    text = strings.actionSteps,
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
                        Image(
                            painter = painterResource(R.drawable.resq_ic_warning_red),
                            contentDescription = null,
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
                    text = strings.sourceText,
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
    iconRes: Int,
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
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = label, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CameraAnalyzingScreen(language: AppLanguage) {
    val strings = appStrings(language)
    ResQGradientScreen {
        Column(modifier = Modifier.fillMaxSize()) {
            Header(title = strings.cameraTitle, trailing = { Badge(label = strings.online) }, onBack = {})
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 120.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = Color(0xFFBFC5CB))
                Text(
                    text = strings.imageAnalyzing,
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
    language: AppLanguage,
    textValue: String,
    onChangeText: (String) -> Unit,
    onBack: () -> Unit,
    isAnalyzing: Boolean = false,
    onSubmit: () -> Unit
) {
    val strings = appStrings(language)
    ResQGradientScreen {
        Column(modifier = Modifier.fillMaxSize()) {
            Header(title = strings.textQuestionTitle, trailing = { Badge(label = strings.offline) }, onBack = onBack)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
            ) {
                Text(
                    text = strings.situationInput,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                androidx.compose.material3.OutlinedTextField(
                    value = textValue,
                    onValueChange = onChangeText,
                    placeholder = { Text(strings.textPlaceholder, color = Color(0xFF777777)) },
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
                        text = if (isAnalyzing) strings.aiAnalyzing else strings.getGuidance,
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
    offlineLlm: OfflineLlmManager,
    language: AppLanguage
): AnalysisResult {
    // 항상 LLM으로 분석 (태그는 무시)
    return try {
        val (disasterType, advice) = offlineLlm.analyzeForTextQuery(inputText)
        val disasterId = when (disasterType) {
            "지진", "Earthquake", "地震" -> DisasterId.Earthquake
            "화재", "Fire", "火灾" -> DisasterId.Fire
            "홍수", "Flood", "洪水" -> DisasterId.Flood
            "태풍", "Typhoon", "台风" -> DisasterId.Typhoon
            "산사태", "Landslide", "山体滑坡" -> DisasterId.Landslide
            "쓰나미", "Tsunami", "海啸" -> DisasterId.Tsunami
            "대설", "Heavy Snow", "大雪" -> DisasterId.HeavySnow
            "위험물", "Hazardous Material", "有害物质" -> DisasterId.HazardRelease
            "119", "Emergency" -> DisasterId.Emergency
            else -> DisasterId.Emergency
        }
        AnalysisResult(
            disasterId,
            if (language == AppLanguage.Korean) advice else localizedRecommendation(disasterId, language)
        )
    } catch (e: Exception) {
        throw IllegalStateException(e.message ?: "LLM analysis failed")
    }
}

@Composable
private fun SettingsScreen(
    language: AppLanguage,
    ttsEnabled: Boolean,
    voiceType: String,
    offlineState: OfflineLlmState,
    modelPath: String,
    onLanguageChange: (AppLanguage) -> Unit,
    onTtsToggle: (Boolean) -> Unit,
    onVoiceChange: (String) -> Unit,
    onDownloadModel: () -> Unit,
    onDeleteModel: () -> Unit,
    onBack: () -> Unit
) {
    val strings = appStrings(language)
    ResQGradientScreen {
        Column(modifier = Modifier.fillMaxSize()) {
            Header(title = strings.settingsTitle, trailing = { Spacer(modifier = Modifier.width(44.dp)) }, onBack = onBack)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                SettingsSection(title = strings.languageDefaultTitle, subtitle = strings.languageDefaultSubtitle) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsOption(strings.korean, language == AppLanguage.Korean) { onLanguageChange(AppLanguage.Korean) }
                        SettingsOption(strings.english, language == AppLanguage.English) { onLanguageChange(AppLanguage.English) }
                        SettingsOption(strings.chinese, language == AppLanguage.Chinese) { onLanguageChange(AppLanguage.Chinese) }
                    }
                }

                SettingsSection(title = strings.ttsTitle, subtitle = strings.ttsSubtitle) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = strings.ttsTitle, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(text = strings.ttsSubtitle, color = Color(0xFF999999), fontSize = 12.sp)
                        }
                        ToggleSwitch(isOn = ttsEnabled, onToggle = onTtsToggle)
                    }

                    if (ttsEnabled) {
                        Text(
                            text = strings.ttsTone,
                            color = Color(0xFF999999),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            SettingsOption(strings.voiceNatural, voiceType == "natural") { onVoiceChange("natural") }
                            SettingsOption(strings.voiceAssured, voiceType == "assured") { onVoiceChange("assured") }
                            SettingsOption(strings.voiceBrisk, voiceType == "brisk") { onVoiceChange("brisk") }
                        }
                    }
                }

                SettingsSection(title = strings.llmModelTitle, subtitle = strings.llmModelSubtitle) {
                    val statusText = when {
                        offlineState.isLoading -> "${strings.loading} ${offlineState.downloadProgress}%"
                        offlineState.isInitialized -> strings.ready
                        else -> strings.none
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1B1B1B))
                            .padding(12.dp)
                    ) {
                        SettingsRow(strings.status, statusText)
                        SettingsRow(strings.modelPath, if (offlineState.isInitialized) modelPath else strings.none)
                        SettingsRow(strings.installMethod, strings.bundledModelFirst)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            SettingsActionButton(
                                label = strings.reloadModel,
                                enabled = !offlineState.isLoading,
                                onClick = onDownloadModel
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            SettingsActionButton(
                                label = strings.deleteModel,
                                enabled = !offlineState.isLoading && offlineState.isInitialized,
                                onClick = onDeleteModel
                            )
                        }
                    }

                    Text(
                        text = strings.modelNote,
                        color = Color(0xFF999999),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 10.dp)
                    )

                    if (!offlineState.error.isNullOrBlank()) {
                        Text(
                            text = "${strings.errorPrefix}: ${offlineState.error}",
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
                        text = strings.noticeTitle,
                        color = Color(0xFFE53935),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = strings.noticeBody,
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
        Image(
            painter = painterResource(R.drawable.resq_ic_back_arrow),
            contentDescription = null,
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
