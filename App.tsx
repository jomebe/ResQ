import { StatusBar } from 'expo-status-bar';
import { LinearGradient } from 'expo-linear-gradient';
import { Feather } from '@expo/vector-icons';
import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useFonts } from 'expo-font';
import {
  ActivityIndicator,
  Animated,
  Alert,
  Pressable,
  SafeAreaView,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import * as ImagePicker from 'expo-image-picker';

type DisasterId =
  | 'earthquake'
  | 'fire'
  | 'flood'
  | 'typhoon'
  | 'landslide'
  | 'tsunami'
  | 'heavy_snow'
  | 'hazard_release';

type FeatherIconName = React.ComponentProps<typeof Feather>['name'];

type DisasterDefinition = {
  id: DisasterId;
  label: string;
  cardDescription: string;
  icon: FeatherIconName;
  headline: string;
  steps: string[];
};

const DISASTER_CATALOG: DisasterDefinition[] = [
  {
    id: 'earthquake',
    label: '지진',
    cardDescription: '지진 발생시 행동요령\n대피 방법 등',
    icon: 'activity',
    headline: '건물 밖으로 대피해주세요.',
    steps: [
      '흔들림이 느껴지면 책상 아래로 들어가 머리를 보호하세요.',
      '흔들림이 멈추면 계단으로 건물 밖의 넓은 장소로 대피하세요.',
      '엘리베이터는 사용하지 마세요.',
      '밖으로 나온 뒤 유리·간판 등 낙하물을 피하세요.',
    ],
  },
  {
    id: 'fire',
    label: '화재',
    cardDescription: '화재 발생시 행동요령\n연기 대응 등',
    icon: 'zap',
    headline: '연기를 피해 낮은 자세로 대피하세요.',
    steps: [
      '불을 발견하면 큰 소리로 알리고 119에 신고하세요.',
      '문 손잡이를 만져 보고 뜨겁지 않을 때만 문을 열고 나가세요.',
      '연기가 있으면 젖은 천으로 코와 입을 막고 낮은 자세로 이동하세요.',
      '엘리베이터는 타지 말고 비상구·계단을 이용하세요.',
    ],
  },
  {
    id: 'flood',
    label: '홍수',
    cardDescription: '침수·홍수 대비\n대피 요령',
    icon: 'droplet',
    headline: '높고 안전한 곳으로 즉시 대피하세요.',
    steps: [
      '침수 경보·대피 명령을 확인하고 지정된 대피소로 이동하세요.',
      '전기 차단기를 내리고 가스를 잠그세요.',
      '물에 들어가지 말고 급류·맨홀 근처를 피하세요.',
      '차량으로 침수 도로를 통과하지 마세요.',
    ],
  },
  {
    id: 'typhoon',
    label: '태풍',
    cardDescription: '강풍·호우 대비\n창문·야외 정리',
    icon: 'wind',
    headline: '실내에서 창문에서 멀리 떨어져 주세요.',
    steps: [
      '야외 물건을 고정하거나 실내로 옮기세요.',
      '창문·문을 잠그고 커튼·셔터가 있으면 내려주세요.',
      '강풍 시 외출을 삼가고 안전한 실내에 머무르세요.',
      '침수 위험이 있으면 층고가 높은 곳으로 이동을 준비하세요.',
    ],
  },
  {
    id: 'landslide',
    label: '산사태',
    cardDescription: '산사태 징후\n긴급 대피',
    icon: 'alert-triangle',
    headline: '비탈·도랑에서 멀리 떨어지세요.',
    steps: [
      '땅 균열·작은 낙석·이상한 소리가 나면 즉시 대피하세요.',
      '계곡·옹벽·사면 인근에 머무르지 마세요.',
      '대피 시 안전한 경로만 이용하고 차량은 가능한 한 피하세요.',
      '대피 후에는 추가 붕괴 위험이 있으니 안내에 따르세요.',
    ],
  },
  {
    id: 'tsunami',
    label: '쓰나미',
    cardDescription: '지진해일 대비\n고지대 대피',
    icon: 'anchor',
    headline: '해변·저지대를 떠나 높은 곳으로 가세요.',
    steps: [
      '지진 직후 해안·강 하구 근처에 있지 마세요.',
      '방송·재난 문자를 확인하고 즉시 고지대로 이동하세요.',
      '차량 이동이 어려우면 도보로라도 높은 곳을 향하세요.',
      '해일 경보가 해제될 때까지 안전한 곳에 머무르세요.',
    ],
  },
  {
    id: 'heavy_snow',
    label: '대설',
    cardDescription: '폭설·빙판\n교통·난방 안전',
    icon: 'cloud',
    headline: '외출을 줄이고 난방·환기를 안전하게 하세요.',
    steps: [
      '필수 외출만 하고 빙판길·적설 구간을 피하세요.',
      '난방기구 주변 가연물을 치우고 환기를 자주 하세요.',
      '지붕·발코니의 눈 무너짐에 주의하세요.',
      '차량은 미리 제설·타이어 상태를 점검하세요.',
    ],
  },
  {
    id: 'hazard_release',
    label: '유해물질',
    cardDescription: '화학·방사능 등\n대피·대기 지침',
    icon: 'alert-octagon',
    headline: '방송 안내에 따라 실내 대피 또는 지정 방향으로 이동하세요.',
    steps: [
      '공식 방송·재난 문자의 지시를 우선 따르세요.',
      '실내 대피 시 창문·문을 닫고 환기를 끄세요.',
      '현장 촬영·접근은 위험하니 삼가세요.',
      '대피 시 피부 노출을 최소화하고 안내된 세척 방법을 따르세요.',
    ],
  },
];

function getDisasterById(id: DisasterId): DisasterDefinition {
  const found = DISASTER_CATALOG.find((d) => d.id === id);
  return found ?? DISASTER_CATALOG[0];
}

type Screen =
  | 'onboard'
  | 'home'
  | 'disaster'
  | 'guidance'
  | 'camera_loading'
  | 'text_query';
type DisasterPickerSource = 'home' | 'guidance';
type GuidanceBackTarget = 'home' | 'text_query';
type QuickTagValue = DisasterId | 'emergency';
type QuickTag = { id: string; label: string; value: QuickTagValue };

type AnalysisResult = {
  disasterId: DisasterId;
  warning?: string;
};

async function analyzeCapturedImage(uri: string): Promise<AnalysisResult> {
  const normalizedUri = uri.toLowerCase();
  const matchingRules: Array<{ keywords: string[]; disasterId: DisasterId; warning?: string }> = [
    {
      keywords: ['fire', 'flame', 'smoke', '화재', '불'],
      disasterId: 'fire',
      warning: '연기 흡입 위험이 있으니 젖은 천으로 호흡기를 보호하세요.',
    },
    {
      keywords: ['flood', 'rain', 'water', '침수', '홍수'],
      disasterId: 'flood',
      warning: '급류와 맨홀 근처 접근을 피하고 높은 곳으로 이동하세요.',
    },
    {
      keywords: ['quake', 'earth', 'crack', '지진', '붕괴'],
      disasterId: 'earthquake',
      warning: '지진 심한 경우 건물이 붕괴할 수 있어요!',
    },
  ];

  const matched = matchingRules.find((rule) =>
    rule.keywords.some((keyword) => normalizedUri.includes(keyword))
  );
  if (matched) {
    return { disasterId: matched.disasterId, warning: matched.warning };
  }
  return { disasterId: 'earthquake', warning: '구조물 균열과 낙하물 위험에 주의하세요.' };
}

async function openDefaultCamera(): Promise<string | null> {
  const permission = await ImagePicker.requestCameraPermissionsAsync();
  if (!permission.granted) {
    Alert.alert('카메라 권한 필요', '안내문 촬영을 위해 카메라 권한이 필요합니다.');
    return null;
  }

  const result = await ImagePicker.launchCameraAsync({
    mediaTypes: ['images'],
    quality: 0.8,
  });

  if (result.canceled || result.assets.length === 0) {
    return null;
  }
  return result.assets[0].uri;
}

const TEXT_QUICK_TAGS: QuickTag[] = [
  { id: 'tag-earthquake', label: '지진', value: 'earthquake' },
  { id: 'tag-fire', label: '화재', value: 'fire' },
  { id: 'tag-blackout', label: '정전', value: 'hazard_release' },
  { id: 'tag-emergency-1', label: '응급', value: 'emergency' },
  { id: 'tag-emergency-2', label: '응급', value: 'emergency' },
  { id: 'tag-emergency-3', label: '응급', value: 'emergency' },
];

function analyzeTextQuery(inputText: string, selectedTag: QuickTagValue): AnalysisResult {
  const normalized = inputText.toLowerCase();

  if (selectedTag !== 'emergency') {
    return {
      disasterId: selectedTag,
      warning: '강한 흔들림·연기·침수 등 2차 위험이 없는지 주변을 먼저 확인하세요.',
    };
  }

  const rules: Array<{ keywords: string[]; result: AnalysisResult }> = [
    {
      keywords: ['화재', '불', '연기', 'smoke', 'fire'],
      result: {
        disasterId: 'fire',
        warning: '연기 유입을 막고 낮은 자세로 비상구 방향으로 이동하세요.',
      },
    },
    {
      keywords: ['홍수', '침수', '물', 'flood', 'water'],
      result: {
        disasterId: 'flood',
        warning: '급류 구간과 맨홀 주변을 피하고 높은 위치로 이동하세요.',
      },
    },
    {
      keywords: ['태풍', '강풍', 'typhoon', 'wind'],
      result: {
        disasterId: 'typhoon',
        warning: '창문 주변에서 떨어지고 낙하물 위험 지역을 피하세요.',
      },
    },
    {
      keywords: ['지진', '흔들', '붕괴', 'quake', 'earth'],
      result: {
        disasterId: 'earthquake',
        warning: '지진 심한 경우 건물이 무너질수 있어요!',
      },
    },
  ];

  const matched = rules.find((rule) => rule.keywords.some((keyword) => normalized.includes(keyword)));
  return matched?.result ?? {
    disasterId: 'earthquake',
    warning: '정확한 위치와 주변 위험요소를 계속 공유해 주세요.',
  };
}

export default function App() {
  const [fontsLoaded] = useFonts({
    'Pretendard-Regular': require('pretendard/dist/public/static/Pretendard-Regular.otf'),
    'Pretendard-SemiBold': require('pretendard/dist/public/static/Pretendard-SemiBold.otf'),
    'Pretendard-Bold': require('pretendard/dist/public/static/Pretendard-Bold.otf'),
  });

  // Keep font hook at the top to preserve hook call order across renders.
  // Declare other hooks immediately after so the hook call order is stable
  // across renders even during font loading.
  const [screen, setScreen] = useState<Screen>('onboard');
  const [selectedDisasterId, setSelectedDisasterId] = useState<DisasterId>('earthquake');
  const [disasterPickerSource, setDisasterPickerSource] =
    useState<DisasterPickerSource>('home');
  const [guidanceTitle, setGuidanceTitle] = useState<string | undefined>(undefined);
  const [statusText, setStatusText] = useState<'오프라인' | '온라인'>('오프라인');
  const [analysisWarning, setAnalysisWarning] = useState<string | undefined>(undefined);
  const [guidanceBackTarget, setGuidanceBackTarget] = useState<GuidanceBackTarget>('home');
  const [textQuestion, setTextQuestion] = useState('');
  const [selectedQuickTagId, setSelectedQuickTagId] = useState<string>(TEXT_QUICK_TAGS[0].id);

  useEffect(() => {
    if (screen !== 'onboard') return;
    const timer = setTimeout(() => setScreen('home'), 1600);
    return () => clearTimeout(timer);
  }, [screen]);

  // Show a simple dark loading view until fonts are ready to avoid visual
  // flashes. Hooks are already called above so order is stable.
  if (!fontsLoaded) {
    return <View style={{ flex: 1, backgroundColor: '#0a0a0a' }} />;
  }

  return (
    <View style={styles.appRoot}>
      <StatusBar style="light" />
      {screen === 'onboard' ? (
        <OnboardingScreen onDone={() => setScreen('home')} />
      ) : screen === 'home' ? (
        <HomeScreen
          onOpenDisasterPicker={() => {
            setDisasterPickerSource('home');
            setScreen('disaster');
          }}
          onOpenCameraCapture={async () => {
            const capturedUri = await openDefaultCamera();
            if (!capturedUri) {
              return;
            }
            setGuidanceTitle(undefined);
            setAnalysisWarning(undefined);
            setStatusText('온라인');
            setScreen('camera_loading');

            const result = await analyzeCapturedImage(capturedUri);
            setSelectedDisasterId(result.disasterId);
            setGuidanceTitle('카메라 촬영');
            setAnalysisWarning(result.warning);
            setGuidanceBackTarget('home');
            setScreen('guidance');
          }}
          onOpenTextQuestion={() => {
            setStatusText('온라인');
            setScreen('text_query');
          }}
        />
      ) : screen === 'disaster' ? (
        <DisasterScreen
          catalog={DISASTER_CATALOG}
          onBack={() =>
            setScreen(disasterPickerSource === 'guidance' ? 'guidance' : 'home')
          }
          onSelectType={(id) => {
            setSelectedDisasterId(id);
            setScreen('guidance');
          }}
        />
      ) : screen === 'camera_loading' ? (
        <CameraAnalyzingScreen />
      ) : screen === 'text_query' ? (
        <TextQuestionScreen
          textValue={textQuestion}
          selectedTagId={selectedQuickTagId}
          onChangeText={setTextQuestion}
          onSelectTag={setSelectedQuickTagId}
          onBack={() => {
            setStatusText('오프라인');
            setScreen('home');
          }}
          onSubmit={() => {
            const trimmed = textQuestion.trim();
            if (!trimmed) {
              Alert.alert('입력 필요', '상황을 입력해 주세요.');
              return;
            }
            const selectedTag = TEXT_QUICK_TAGS.find((tag) => tag.id === selectedQuickTagId);
            const result = analyzeTextQuery(trimmed, selectedTag?.value ?? 'emergency');
            setSelectedDisasterId(result.disasterId);
            setGuidanceTitle('텍스트 질문');
            setAnalysisWarning(result.warning);
            setGuidanceBackTarget('text_query');
            setScreen('guidance');
          }}
        />
      ) : (
        <GuidanceScreen
          disaster={getDisasterById(selectedDisasterId)}
          title={guidanceTitle}
          statusText={statusText}
          warning={analysisWarning}
          onBack={() => {
            if (guidanceBackTarget === 'text_query') {
              setScreen('text_query');
              return;
            }
            setStatusText('오프라인');
            setGuidanceTitle(undefined);
            setAnalysisWarning(undefined);
            setScreen('home');
          }}
          onOpenDisasterPicker={() => {
            setDisasterPickerSource('guidance');
            setStatusText('오프라인');
            setGuidanceTitle(undefined);
            setAnalysisWarning(undefined);
            setScreen('disaster');
          }}
        />
      )}
    </View>
  );
}

function OnboardingScreen({ onDone }: { onDone: () => void }) {
  const fade = useRef(new Animated.Value(0)).current;
  const lift = useRef(new Animated.Value(10)).current;

  useEffect(() => {
    Animated.parallel([
      Animated.timing(fade, {
        toValue: 1,
        duration: 700,
        useNativeDriver: true,
      }),
      Animated.timing(lift, {
        toValue: 0,
        duration: 700,
        useNativeDriver: true,
      }),
    ]).start();
  }, [fade, lift]);

  return (
    <Pressable style={styles.flex} onPress={onDone}>
      <LinearGradient
        colors={['#0a0a0a', '#141414', '#0b0b0b']}
        style={styles.screen}
      >
        <Animated.View
          style={[
            styles.onboardContent,
            { opacity: fade, transform: [{ translateY: lift }] },
          ]}
        >
          <Text style={styles.brandTitle}>ResQ</Text>
          <Text style={styles.brandSubtitle}>오프라인 재난코치</Text>
        </Animated.View>
      </LinearGradient>
    </Pressable>
  );
}

function HomeScreen({
  onOpenDisasterPicker,
  onOpenCameraCapture,
  onOpenTextQuestion,
}: {
  onOpenDisasterPicker: () => void;
  onOpenCameraCapture: () => void;
  onOpenTextQuestion: () => void;
}) {
  const micScale = useRef(new Animated.Value(0.92)).current;
  const micGlow = useRef(new Animated.Value(0)).current;
  const [listening, setListening] = useState(false);
  const items = useMemo(
    () => [
      { icon: 'grid', label: '재난 유형 선택' },
      { icon: 'camera', label: '안내문 촬영' },
      { icon: 'edit-3', label: '텍스트 질문' },
      { icon: 'settings', label: '설정' },
    ],
    []
  );
  const itemAnim = useRef(items.map(() => new Animated.Value(0))).current;

  useEffect(() => {
    Animated.parallel([
      Animated.spring(micScale, {
        toValue: 1,
        useNativeDriver: true,
        friction: 5,
      }),
      Animated.timing(micGlow, {
        toValue: 1,
        duration: 1200,
        useNativeDriver: true,
      }),
    ]).start();
    Animated.stagger(
      110,
      itemAnim.map((v) =>
        Animated.timing(v, {
          toValue: 1,
          duration: 420,
          useNativeDriver: true,
        })
      )
    ).start();
  }, [itemAnim, micGlow, micScale]);

  useEffect(() => {
    Animated.spring(micScale, {
      toValue: listening ? 1.08 : 1,
      useNativeDriver: true,
      friction: 5,
    }).start();
  }, [listening, micScale]);

  return (
    <LinearGradient
      colors={['#0a0a0a', '#121212', '#0d0d0d']}
      style={styles.screen}
    >
      <SafeAreaView style={styles.safe}>
        <View style={styles.topRow}>
          <Text style={styles.brandSmall}>ResQ</Text>
          <View style={styles.badge}>
            <Text style={styles.badgeText}>오프라인</Text>
          </View>
        </View>

        <View style={styles.centerStack}>
          <Pressable onPress={() => setListening((v) => !v)} style={{ alignItems: 'center' }}>
            <Animated.View style={{ transform: [{ scale: micScale }] }}>
              <View style={styles.micShadow} />
              <View style={styles.micButton}>
                <Animated.View
                  style={[
                    styles.micGlow,
                    { opacity: micGlow, transform: [{ scale: micGlow }] },
                  ]}
                />
                {listening ? (
                  <Feather name="activity" size={88} color="#fff" />
                ) : (
                  <Feather name="mic" size={88} color="#fff" />
                )}
              </View>
            </Animated.View>
          </Pressable>
          <Text style={styles.micLabel}>{listening ? '듣는중..' : '음성 질문 시작하기'}</Text>
        </View>

        <View style={styles.actions}>
          {items.map((item, index) => {
            const anim = itemAnim[index];
            return (
              <Animated.View
                key={item.label}
                style={{
                  opacity: anim,
                  transform: [
                    {
                      translateY: anim.interpolate({
                        inputRange: [0, 1],
                        outputRange: [10, 0],
                      }),
                    },
                  ],
                }}
              >
                <Pressable
                  onPress={() => {
                    if (item.label === '재난 유형 선택') {
                      onOpenDisasterPicker();
                    }
                    if (item.label === '안내문 촬영') {
                      onOpenCameraCapture();
                    }
                    if (item.label === '텍스트 질문') {
                      onOpenTextQuestion();
                    }
                  }}
                >
                  {({ pressed }) => (
                    <View style={styles.actionButton}>
                      <View style={styles.actionIcon}>
                        <Feather
                          name={item.icon as never}
                          size={18}
                          color={pressed ? '#ffffff' : '#d4d4d4'}
                        />
                      </View>
                      <Text style={[styles.actionLabel, pressed && styles.actionLabelActive]}>
                        {item.label}
                      </Text>
                    </View>
                  )}
                </Pressable>
              </Animated.View>
            );
          })}
        </View>
      </SafeAreaView>
    </LinearGradient>
  );
}

function DisasterScreen({
  catalog,
  onBack,
  onSelectType,
}: {
  catalog: DisasterDefinition[];
  onBack: () => void;
  onSelectType: (id: DisasterId) => void;
}) {
  return (
    <LinearGradient colors={['#0a0a0a', '#141414', '#0b0b0b']} style={styles.screen}>
      <SafeAreaView style={styles.safe}>
        <View style={styles.header}>
          <Pressable onPress={onBack} style={styles.headerBack}>
            <Feather name="arrow-left" size={20} color="#fff" />
          </Pressable>
          <Text style={styles.headerTitle}>재난 유형 선택</Text>
          <View style={styles.badge}>
            <Text style={styles.badgeText}>오프라인</Text>
          </View>
        </View>

        <ScrollView
          style={styles.disasterScroll}
          contentContainerStyle={styles.disasterScrollContent}
          showsVerticalScrollIndicator={false}
        >
          <Text style={styles.disasterIntroTitle}>어떤 재난에 대한 정보가 필요하신가요?</Text>
          <Text style={styles.disasterIntroSubtitle}>재난 유형을 선택해주세요.</Text>

          <View style={styles.disasterGrid}>
            {catalog.map((t) => (
              <Pressable
                key={t.id}
                style={styles.disasterCard}
                onPress={() => onSelectType(t.id)}
              >
                <View style={styles.disasterCardIcon}>
                  <Feather name={t.icon} size={18} color="#fff" />
                </View>
                <Text style={styles.disasterCardTitle}>{t.label}</Text>
                <Text style={styles.disasterCardDesc}>{t.cardDescription}</Text>
              </Pressable>
            ))}
          </View>

          <Pressable style={styles.bottomBanner} onPress={onBack}>
            <View style={styles.bottomBannerIcon}>
              <Feather name="volume-2" size={18} color="#ff5252" />
            </View>
            <View style={{ flex: 1 }}>
              <Text style={styles.bottomBannerTitle}>원하는 항목이 없나요?</Text>
              <Text style={styles.bottomBannerSubtitle}>음성이나 텍스트로 직접 질문해보세요</Text>
            </View>
          </Pressable>
        </ScrollView>
      </SafeAreaView>
    </LinearGradient>
  );
}

function GuidanceScreen({
  disaster,
  title,
  statusText,
  warning,
  onBack,
  onOpenDisasterPicker,
}: {
  disaster: DisasterDefinition;
  title?: string;
  statusText: '오프라인' | '온라인';
  warning?: string;
  onBack: () => void;
  onOpenDisasterPicker: () => void;
}) {
  const guidanceTitle = title ?? `${disaster.label} 안내`;

  return (
    <LinearGradient colors={['#0a0a0a', '#141414', '#0b0b0b']} style={styles.screen}>
      <SafeAreaView style={styles.safe}>
        <View style={styles.header}>
          <Pressable onPress={onBack} style={styles.headerBack}>
            <Feather name="arrow-left" size={20} color="#fff" />
          </Pressable>
          <Text style={styles.headerTitle}>{guidanceTitle}</Text>
          <View style={styles.badge}>
            <Text style={styles.badgeText}>{statusText}</Text>
          </View>
        </View>

        <ScrollView
          style={styles.guidanceScroll}
          contentContainerStyle={styles.guidanceScrollContent}
          showsVerticalScrollIndicator={false}
        >
          <View style={styles.quickActions}>
            <Pressable style={styles.quickActionBtn}>
              <Feather name="phone" size={22} color="#fff" />
              <Text style={styles.quickActionLabel}>119</Text>
            </Pressable>
            <Pressable style={styles.quickActionBtn}>
              <Feather name="sun" size={22} color="#fff" />
              <Text style={styles.quickActionLabel}>손전등</Text>
            </Pressable>
            <Pressable style={styles.quickActionBtn}>
              <Feather name="bell" size={22} color="#fff" />
              <Text style={styles.quickActionLabel}>사이렌</Text>
            </Pressable>
          </View>

          <View style={styles.guidanceMessageRow}>
            <Pressable style={styles.typePicker} onPress={onOpenDisasterPicker}>
              <View style={styles.typePickerIconWrap}>
                <Feather name={disaster.icon} size={22} color="#e53935" />
              </View>
              <Text style={styles.typePickerLabel}>{disaster.label}</Text>
            </Pressable>
            <Text style={styles.guidanceHeadline}>{disaster.headline}</Text>
          </View>

          <Text style={styles.stepsSectionTitle}>행동 단계</Text>
          <View style={styles.stepsList}>
            {disaster.steps.map((step, index) => (
              <View key={`${disaster.id}-step-${index}`} style={styles.stepRow}>
                <View style={styles.stepTimeline}>
                  <View style={styles.stepNumber}>
                    <Text style={styles.stepNumberText}>{index + 1}</Text>
                  </View>
                  {index < disaster.steps.length - 1 ? (
                    <View style={styles.stepLine} />
                  ) : null}
                </View>
                <View style={styles.stepCard}>
                  <Text style={styles.stepCardText}>{step}</Text>
                </View>
              </View>
            ))}
          </View>

          {warning ? (
            <View style={styles.warningCard}>
              <Feather name="alert-triangle" size={20} color="#ff4d4d" />
              <Text style={styles.warningText}>{warning}</Text>
            </View>
          ) : null}

          <Text style={styles.sourceFootnote}>출처: 공공 안전 안내 요약 (앱 내 참고용)</Text>
        </ScrollView>
      </SafeAreaView>
    </LinearGradient>
  );
}

function CameraAnalyzingScreen() {
  return (
    <LinearGradient colors={['#0a0a0a', '#141414', '#0b0b0b']} style={styles.screen}>
      <SafeAreaView style={styles.safe}>
        <View style={styles.header}>
          <Pressable style={styles.headerBack}>
            <Feather name="arrow-left" size={20} color="#fff" />
          </Pressable>
          <Text style={styles.headerTitle}>안내문 촬영</Text>
          <View style={styles.badge}>
            <Text style={styles.badgeText}>온라인</Text>
          </View>
        </View>
        <View style={styles.analysisCenter}>
          <ActivityIndicator size="large" color="#bfc5cb" />
          <Text style={styles.analysisText}>이미지 분석중...</Text>
        </View>
      </SafeAreaView>
    </LinearGradient>
  );
}

function TextQuestionScreen({
  textValue,
  selectedTagId,
  onChangeText,
  onSelectTag,
  onBack,
  onSubmit,
}: {
  textValue: string;
  selectedTagId: string;
  onChangeText: (text: string) => void;
  onSelectTag: (tagId: string) => void;
  onBack: () => void;
  onSubmit: () => void;
}) {
  return (
    <LinearGradient colors={['#0a0a0a', '#141414', '#0b0b0b']} style={styles.screen}>
      <SafeAreaView style={styles.safe}>
        <View style={styles.header}>
          <Pressable onPress={onBack} style={styles.headerBack}>
            <Feather name="arrow-left" size={20} color="#fff" />
          </Pressable>
          <Text style={styles.headerTitle}>텍스트 질문</Text>
          <View style={styles.badge}>
            <Text style={styles.badgeText}>온라인</Text>
          </View>
        </View>

        <View style={styles.textQuestionBody}>
          <Text style={styles.textSectionTitle}>상황입력</Text>
          <TextInput
            value={textValue}
            onChangeText={onChangeText}
            placeholder="지금 상황을 입력해주세요."
            placeholderTextColor="#777"
            style={styles.textInput}
          />

          <Text style={styles.textSectionTitle}>빠른 태그</Text>
          <View style={styles.quickTagWrap}>
            {TEXT_QUICK_TAGS.map((tag) => {
              const isSelected = selectedTagId === tag.id;
              return (
                <Pressable
                  key={tag.id}
                  style={[styles.quickTagButton, isSelected && styles.quickTagButtonActive]}
                  onPress={() => onSelectTag(tag.id)}
                >
                  <Text style={[styles.quickTagText, isSelected && styles.quickTagTextActive]}>
                    {tag.label}
                  </Text>
                </Pressable>
              );
            })}
          </View>
        </View>

        <Pressable style={styles.submitButton} onPress={onSubmit}>
          <Feather name="send" size={18} color="#fff" />
          <Text style={styles.submitButtonText}>안내받기</Text>
        </Pressable>
      </SafeAreaView>
    </LinearGradient>
  );
}
const styles = StyleSheet.create({
  appRoot: {
    flex: 1,
    backgroundColor: '#0a0a0a',
  },
  flex: {
    flex: 1,
  },
  screen: {
    flex: 1,
    justifyContent: 'center',
  },
  safe: {
    flex: 1,
    paddingHorizontal: 20,
    paddingTop: 10,
  },
  glow: {
    position: 'absolute',
    width: 320,
    height: 320,
    borderRadius: 160,
    backgroundColor: '#e53935',
    opacity: 0.12,
    alignSelf: 'center',
    top: '32%',
  },
  onboardContent: {
    alignItems: 'center',
  },
  brandTitle: {
    fontSize: 44,
    fontFamily: 'Pretendard-Bold',
    letterSpacing: 1.2,
    color: '#E53935',
  },
  brandSubtitle: {
    marginTop: 8,
    fontSize: 16,
    color: '#c7c7c7',
    letterSpacing: 0.3,
    fontFamily: 'Pretendard-Regular',
  },
  topRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  brandSmall: {
    fontSize: 22,
    fontFamily: 'Pretendard-SemiBold',
    color: '#E53935',
  },
  badge: {
    borderRadius: 999,
    borderWidth: 1,
    borderColor: '#2a2a2a',
    backgroundColor: '#161616',
    paddingHorizontal: 12,
    paddingVertical: 5,
  },
  badgeText: {
    fontSize: 12,
    color: '#9c9c9c',
    fontFamily: 'Pretendard-Regular',
  },
  centerStack: {
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 40,
  },
  micShadow: {
    position: 'absolute',
    width: 200,
    height: 200,
    borderRadius: 100,
    backgroundColor: '#000',
    opacity: 0.3,
    alignSelf: 'center',
    top: 12,
  },
  micButton: {
    width: 190,
    height: 190,
    borderRadius: 95,
    backgroundColor: '#e53935',
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#e53935',
    shadowOpacity: 0.45,
    shadowRadius: 20,
    shadowOffset: { width: 0, height: 10 },
    elevation: 16,
    overflow: 'hidden',
  },
  micGlow: {
    position: 'absolute',
    width: 160,
    height: 160,
    borderRadius: 80,
    backgroundColor: '#ff6b63',
    opacity: 0.3,
  },
  micLabel: {
    marginTop: 18,
    fontSize: 16,
    fontFamily: 'Pretendard-Regular',
    color: '#cfcfcf',
  },
  actions: {
    marginTop: 32,
    gap: 12,
  },
  actionButton: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#1b1b1b',
    borderRadius: 14,
    paddingVertical: 14,
    paddingHorizontal: 16,
    borderWidth: 1,
    borderColor: '#242424',
  },
  actionIcon: {
    width: 28,
    height: 28,
    borderRadius: 10,
    backgroundColor: '#262626',
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 12,
  },
  actionLabel: {
    fontSize: 15,
    color: '#e6e6e6',
    fontFamily: 'Pretendard-Regular',
  },
  actionLabelActive: {
    color: '#ffffff',
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 12,
  },
  headerBack: {
    padding: 8,
  },
  headerTitle: {
    flex: 1,
    textAlign: 'center',
    fontSize: 16,
    fontFamily: 'Pretendard-SemiBold',
    color: '#fff',
  },
  disasterIntroTitle: {
    fontSize: 20,
    fontFamily: 'Pretendard-Bold',
    color: '#fff',
    marginBottom: 6,
  },
  disasterIntroSubtitle: {
    fontSize: 13,
    fontFamily: 'Pretendard-Regular',
    color: '#9c9c9c',
    marginBottom: 12,
  },
  disasterGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'space-between',
  },
  disasterCard: {
    width: '48%',
    backgroundColor: '#1b1b1b',
    borderRadius: 12,
    padding: 12,
    marginBottom: 12,
    borderWidth: 1,
    borderColor: '#242424',
  },
  disasterCardIcon: {
    width: 40,
    height: 40,
    borderRadius: 10,
    backgroundColor: '#262626',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 8,
  },
  disasterCardTitle: {
    fontSize: 16,
    fontFamily: 'Pretendard-SemiBold',
    color: '#e6e6e6',
  },
  disasterCardDesc: {
    marginTop: 6,
    fontSize: 12,
    color: '#9c9c9c',
    fontFamily: 'Pretendard-Regular',
  },
  bottomBanner: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#1b1b1b',
    borderRadius: 12,
    padding: 12,
    marginTop: 12,
  },
  bottomBannerIcon: {
    width: 44,
    height: 44,
    borderRadius: 10,
    backgroundColor: '#2b2b2b',
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 12,
  },
  bottomBannerTitle: {
    color: '#fff',
    fontFamily: 'Pretendard-SemiBold',
  },
  bottomBannerSubtitle: {
    color: '#c7c7c7',
    fontFamily: 'Pretendard-Regular',
  },
  disasterScroll: {
    flex: 1,
  },
  disasterScrollContent: {
    paddingBottom: 24,
  },
  guidanceScroll: {
    flex: 1,
  },
  guidanceScrollContent: {
    paddingBottom: 28,
  },
  quickActions: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    gap: 10,
    marginBottom: 16,
  },
  quickActionBtn: {
    flex: 1,
    backgroundColor: '#e53935',
    borderRadius: 12,
    paddingVertical: 14,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 6,
  },
  quickActionLabel: {
    color: '#fff',
    fontFamily: 'Pretendard-SemiBold',
    fontSize: 14,
  },
  guidanceMessageRow: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#1b1b1b',
    borderRadius: 14,
    padding: 14,
    borderWidth: 1,
    borderColor: '#242424',
    marginBottom: 20,
    gap: 14,
  },
  typePicker: {
    alignItems: 'center',
    width: 72,
  },
  typePickerIconWrap: {
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: '#121212',
    borderWidth: 1,
    borderColor: '#2a2a2a',
    alignItems: 'center',
    justifyContent: 'center',
  },
  typePickerLabel: {
    marginTop: 6,
    fontSize: 12,
    color: '#e6e6e6',
    fontFamily: 'Pretendard-SemiBold',
  },
  guidanceHeadline: {
    flex: 1,
    fontSize: 18,
    fontFamily: 'Pretendard-Bold',
    color: '#fff',
    lineHeight: 26,
  },
  stepsSectionTitle: {
    fontSize: 16,
    fontFamily: 'Pretendard-SemiBold',
    color: '#e6e6e6',
    marginBottom: 12,
  },
  stepsList: {
    marginBottom: 16,
  },
  stepRow: {
    flexDirection: 'row',
    alignItems: 'stretch',
  },
  stepTimeline: {
    width: 36,
    alignItems: 'center',
  },
  stepNumber: {
    width: 28,
    height: 28,
    borderRadius: 14,
    backgroundColor: '#262626',
    borderWidth: 1,
    borderColor: '#3a3a3a',
    alignItems: 'center',
    justifyContent: 'center',
  },
  stepNumberText: {
    color: '#fff',
    fontSize: 13,
    fontFamily: 'Pretendard-SemiBold',
  },
  stepLine: {
    width: 2,
    flex: 1,
    minHeight: 12,
    marginVertical: 4,
    backgroundColor: '#333',
    borderStyle: 'dashed',
    opacity: 0.85,
  },
  stepCard: {
    flex: 1,
    backgroundColor: '#1b1b1b',
    borderRadius: 12,
    padding: 12,
    borderWidth: 1,
    borderColor: '#242424',
    marginBottom: 12,
    marginLeft: 8,
  },
  stepCardText: {
    color: '#d4d4d4',
    fontSize: 14,
    fontFamily: 'Pretendard-Regular',
    lineHeight: 21,
  },
  sourceFootnote: {
    fontSize: 11,
    color: '#6a6a6a',
    fontFamily: 'Pretendard-Regular',
    textAlign: 'center',
    marginTop: 8,
  },
  warningCard: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    backgroundColor: '#1b1b1b',
    borderColor: '#ff4d4d',
    borderWidth: 1,
    borderRadius: 12,
    paddingHorizontal: 12,
    paddingVertical: 14,
    marginBottom: 12,
  },
  warningText: {
    flex: 1,
    color: '#d8d8d8',
    fontFamily: 'Pretendard-SemiBold',
    fontSize: 15,
  },
  analysisCenter: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 120,
  },
  analysisText: {
    marginTop: 24,
    color: '#fff',
    fontSize: 36,
    fontFamily: 'Pretendard-Bold',
    textAlign: 'center',
  },
  textQuestionBody: {
    flex: 1,
    marginTop: 8,
  },
  textSectionTitle: {
    fontSize: 34,
    color: '#fff',
    fontFamily: 'Pretendard-Bold',
    marginBottom: 10,
  },
  textInput: {
    borderWidth: 1,
    borderColor: '#2b2b2b',
    borderRadius: 12,
    backgroundColor: '#1b1b1b',
    color: '#fff',
    fontFamily: 'Pretendard-Regular',
    fontSize: 18,
    paddingHorizontal: 16,
    paddingVertical: 14,
    marginBottom: 22,
  },
  quickTagWrap: {
    flexDirection: 'row',
    alignItems: 'center',
    flexWrap: 'wrap',
    gap: 10,
  },
  quickTagButton: {
    borderWidth: 1,
    borderColor: '#2a2a2a',
    backgroundColor: '#1b1b1b',
    borderRadius: 10,
    paddingHorizontal: 18,
    paddingVertical: 8,
  },
  quickTagButtonActive: {
    backgroundColor: '#e53935',
    borderColor: '#e53935',
  },
  quickTagText: {
    color: '#a4a4a4',
    fontSize: 14,
    fontFamily: 'Pretendard-SemiBold',
  },
  quickTagTextActive: {
    color: '#fff',
  },
  submitButton: {
    backgroundColor: '#e53935',
    borderRadius: 12,
    height: 64,
    alignItems: 'center',
    justifyContent: 'center',
    flexDirection: 'row',
    gap: 8,
    marginBottom: 18,
  },
  submitButtonText: {
    color: '#fff',
    fontSize: 30,
    fontFamily: 'Pretendard-Bold',
  },
});
