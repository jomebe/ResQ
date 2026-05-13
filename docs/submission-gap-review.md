# ResQ submission gap review

Reviewed on 2026-05-13.

## Fixed in this pass

- Korean/English/Japanese mismatch corrected to Korean/English/Chinese.
- Unknown and out-of-domain text no longer falls through to earthquake guidance.
  It routes to a fixed 119 fallback card.
- Added an explicit emergency fallback disaster card.
- Reduced the app disaster taxonomy to earthquake, fire, blackout, flood, and
  first aid, with unclear input routed to the fallback classifier/119 path.
- Wired the quick 119 button to `ACTION_DIAL tel:119`.
- Wired the siren button to a local alarm tone.
- Reworked model download to discover Gemma 4 E2B GGUF files through the
  Hugging Face model API before downloading.
- Kept SHA-256 verification for the bundled model and size validation for
  Hugging Face downloads.
- Added public-repo basics: `README.md` and `LICENSE`.

## Do not miss before submission

- **Runtime claim:** this branch uses `llama.cpp` with a GGUF model. Do not
  claim a LiteRT special-tech implementation unless a real LiteRT build is
  added and shown in the video.
- **Official facts:** re-check the Kaggle overview, rules, prizes, evaluation,
  and track-stacking language on the submission day.
- **Data license:** final guidance text needs source-level attribution. Keep
  Red Cross copyrighted text out of the bundled database unless permission is
  clear.
- **Model packaging:** the current repo has a bundled GGUF under assets. If the
  final story says first-run download plus offline afterwards, the APK and video
  must show that exact flow.
- **Integrity:** if the bundled model artifact changes, regenerate the SHA-256
  and update `EXPECTED_BUNDLED_MODEL_SHA256`.
- **Demo assets:** public writeup, public code repo, live demo URL, public video,
  and cover image all need to be reachable without private VPN or login.
- **Privacy:** the README/writeup should state that voice, text, and captured
  images stay on device in the offline phone demo.
- **Device proof:** record airplane-mode use, model-ready state, and at least one
  non-disaster query returning the fixed 119 fallback.
