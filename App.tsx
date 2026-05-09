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
    'Pretendard-Regular': require('pretendard/dist/public/static/Pretendard-Regular.otf'),
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
          <Animated.View style={{ transform: [{ scale: micScale }] }}>
            <View style={styles.micShadow} />
            <View style={styles.micButton}>
              <Animated.View
                style={[
                  styles.micGlow,
                  { opacity: micGlow, transform: [{ scale: micGlow }] },
                ]}
              />
              <Feather name="mic" size={88} color="#fff" />
            </View>
          </Animated.View>
          <Text style={styles.micLabel}>음성 질문 시작하기</Text>
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
});
