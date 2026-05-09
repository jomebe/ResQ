import { StatusBar } from 'expo-status-bar';
import { LinearGradient } from 'expo-linear-gradient';
import { Feather } from '@expo/vector-icons';
import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useFonts } from 'expo-font';
import {
  Animated,
  Platform,
  Pressable,
  SafeAreaView,
  StyleSheet,
  Text,
  View,
} from 'react-native';

type Screen = 'onboard' | 'home';

export default function App() {
  const [fontsLoaded] = useFonts({
type Screen = 'onboard' | 'home' | 'disaster';
    'Pretendard-SemiBold': require('pretendard/dist/public/static/Pretendard-SemiBold.otf'),
    'Pretendard-Bold': require('pretendard/dist/public/static/Pretendard-Bold.otf'),
  });

  // Keep font hook at the top to preserve hook call order across renders.
  // Declare other hooks immediately after so the hook call order is stable
  // across renders even during font loading.
  const [screen, setScreen] = useState<Screen>('onboard');

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
      ) : (
        <HomeScreen />
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

function HomeScreen() {
  const micScale = useRef(new Animated.Value(0.92)).current;
  const micGlow = useRef(new Animated.Value(0)).current;
  const [listening, setListening] = useState(false);
  const items = useMemo(
    () => [
function HomeScreen({ onNavigate }: { onNavigate?: (s: Screen) => void }) {
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
                <Pressable onPress={() => {}}>
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

function DisasterScreen({ onBack }: { onBack: () => void }) {
  const types = Array.from({ length: 8 }).map((_, i) => ({
    key: `t${i}`,
    label: '지진',
    desc: '지진 발생시 행동요령\n대피 방법 등',
  }));

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

        <Text style={styles.disasterIntroTitle}>어떤 재난에 대한 정보가 필요하신가요?</Text>
        <Text style={styles.disasterIntroSubtitle}>재난 유형을 선택해주세요.</Text>

        <View style={styles.disasterGrid}>
          {types.map((t) => (
            <Pressable key={t.key} style={styles.disasterCard} onPress={() => {}}>
              <View style={styles.disasterCardIcon}>
                <Feather name="home" size={18} color="#fff" />
              </View>
              <Text style={styles.disasterCardTitle}>{t.label}</Text>
              <Text style={styles.disasterCardDesc}>{t.desc}</Text>
            </Pressable>
          ))}
        </View>

        <Pressable style={styles.bottomBanner} onPress={() => {}}>
          <View style={styles.bottomBannerIcon}>
            <Feather name="volume-2" size={18} color="#ff5252" />
          </View>
          <View style={{ flex: 1 }}>
            <Text style={styles.bottomBannerTitle}>원하는 항목이 없나요?</Text>
            <Text style={styles.bottomBannerSubtitle}>음성이나 텍스트로 직접 질문해보세요</Text>
          </View>
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
});
