# Live ISL

On-device Android app for real-time **voice → Indian Sign Language (ISL)** conversation, with architecture reserved for **camera ISL → voice**.

## Features (Phase 1)

- Google Translate–style live conversation UI (Jetpack Compose)
- Streaming speech recognition (Android SpeechRecognizer offline-preferred; Sherpa-ONNX when models are present)
- Open-vocabulary English/Hindi → ISL gloss pipeline (reorder + dictionary + fingerspell fallback)
- Pluggable `SignRenderer` with `VideoClipSignRenderer` (ExoPlayer queue, barge-in, prefetch cache)
- Latency debug overlay (ASR / gloss map / clip start / queue depth)
- Hybrid assets: placeholder clips now; swap real ISL videos via `scripts/import_clip.sh`
- Avatar-ready: implement `AvatarSignRenderer` later without changing the NLP pipeline

## Phase 2 (stub)

- Camera permission + CameraX preview
- MediaPipe Tasks Vision hook (`IslLandmarkStub`)
- LiteRT classifier + on-device TTS — to be trained/wired after Phase 1

## Requirements

- Android Studio Ladybug+ / JDK 17
- Android SDK 35, minSdk 26
- Optional: `ffmpeg` for clip generation/import scripts

## Build

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew :app:assembleDebug
```

Install:

```bash
./gradlew :app:installDebug
```

## Usage

1. Grant microphone permission
2. Tap the mic and speak English or Hindi (toggle language chip)
3. Watch the ISL stage + gloss strip update with low-latency streaming
4. Tap **Demo** for a scripted mock ASR pass without speaking
5. Switch to **Camera ISL** to preview the Phase 2 stub

## Adding real ISL clips

```bash
./scripts/generate_placeholder_clips.sh   # solid-color placeholders
./scripts/import_clip.sh HELLO ~/Videos/hello_isl.mp4
python3 ./scripts/validate_dictionary.py --check
```

Dictionary: `app/src/main/assets/dictionary/glosses.json`

## Sherpa-ONNX models (optional)

Place under `app/src/main/assets/asr/` (unpacked to `filesDir/asr` on first launch):

- `tokens.txt`
- `encoder.onnx` / `decoder.onnx` / `joiner.onnx` (or `model.onnx`)

See `app/src/main/assets/asr/README.txt`.

## Package layout

```
com.liveisl.app
  asr/          Streaming ASR interfaces + Android/Sherpa/Mock
  nlp/          IslGlossPipeline
  sign/         SignRenderer + VideoClipSignRenderer (+ Avatar stub)
  data/         GlossDictionary
  session/      ConversationViewModel
  bootstrap/    ModelBootstrap
  phase2/       MediaPipe landmark stub
  ui/           Conversation + Phase 2 screens
```

## CISLR video pack (~4.7k words)

The large CISLR corpus is **not** bundled in the APK. Download it from Settings:

1. Open the app → Settings → Sign output **Video** → Video source **CISLR**
2. Tap **Download pack** (~800 MB)
3. The app downloads the zip, extracts clips on-device, then deletes the zip

Hosted release asset:

https://github.com/hellakursed/live-isl/releases/download/cislr-v1/cislr_pack.zip

Rebuild the pack locally (requires a Hugging Face token after accepting CISLR terms):

```bash
HF_TOKEN=hf_xxx ./scripts/fetch_cislr_pack.sh
./scripts/install_cislr_pack_adb.sh
```

## Latency targets

| Stage | Target |
|-------|--------|
| Partial ASR | 200–400 ms |
| Gloss map | &lt; 50 ms |
| First clip start | &lt; 150 ms |
| End-to-end first sign | &lt; 700 ms |
