# ResQ

Offline, multilingual disaster-response coach for Android.

ResQ is positioned as a translator of government-verified disaster-response
manuals, not as an emergency-service replacement. When the app cannot map an
input to supported disaster guidance, it falls back to a fixed 119 instruction
instead of letting the model improvise.

## Current Demo Build

- Android app built with Kotlin and Jetpack Compose.
- On-device Gemma 4 E2B GGUF inference through `llama.cpp`.
- Supported UI/input languages: Korean, English, Chinese.
- Disaster cards: earthquake, fire, flood, typhoon, landslide, tsunami, heavy
  snow, hazardous material, and a 119 fallback card.
- Emergency controls: 119 dial intent, flashlight toggle, and siren tone.
- Voice input uses Android `SpeechRecognizer`; TTS uses Android
  `TextToSpeech`.

## Safety Guardrails

- Keyword routing runs before model classification.
- Unknown or out-of-domain inputs route to the 119 fallback card.
- The fallback copy is fixed:
  `Not in manual. Call 119 immediately.`
- The UI shows a source line for Korean public disaster and 119 safety guidance
  summaries.

## Important Submission Notes

- Do not claim LiteRT execution for this branch unless a LiteRT build is added
  and demonstrated separately. This code path currently uses `llama.cpp` and a
  GGUF model.
- Verify every source sentence against public Korean government or 119 safety
  guidance before final submission.
- Keep the demo video under 3 minutes and make the offline phone demo the main
  evidence.
- The live demo URL can be a convenience route, but the video should show the
  phone running locally.

## Build

Open the project in Android Studio, then build the debug APK.

```powershell
.\gradlew.bat assembleDebug
```

The app expects the Gemma GGUF model at:

```text
app/src/main/assets/llm/gemma-4-E2B-it-IQ4_XS.gguf
```

The settings screen can also attempt to download the same model into app-local
storage.

## License

Code in this repository is released under the MIT License. Model weights,
fonts, Android dependencies, and disaster-source materials retain their own
licenses and attribution requirements.
