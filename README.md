# Android LiteRT Super-Resolution Demo

첨부 프로젝트를 수정해 **Android에서 실제 LiteRT 모델을 열고, 입력 이미지를 4배로 확대한 뒤 화면에 출력하는 전체 예제**로 구성했습니다.

## 핵심 동작

```text
Android Bitmap
  -> 중앙 정사각형 crop + 50 x 50 resize
  -> NHWC RGB Float32 (0..255)
  -> LiteRT CompiledModel
  -> CPU 또는 GPU
  -> NHWC RGB Float32 (0..255), 200 x 200
  -> ARGB_8888 Bitmap
  -> Compose UI
```

앱 시작 시 `AUTO` backend로 모델을 compile/warm-up합니다. `AUTO`는 GPU를 먼저 시도하고, GPU에서 모델을 열거나 실행하지 못하면 CPU로 fallback합니다. 화면에서 `CPU`, `GPU`, `AUTO`를 직접 다시 선택할 수도 있습니다.

## 모델에 관한 중요한 안내

프로젝트에는 `app/src/main/assets/sr_x4.tflite`가 이미 들어 있으므로 **별도 Python 패키지나 모델 다운로드 없이도 모델 로딩과 추론 경로를 검증할 수 있습니다.**

기본 모델은 다음 그래프를 가진 작은 배포 검증용 TFLite 모델입니다.

```text
[1, 50, 50, 3] Float32
  -> Conv2D 1 x 1, 48 channels
  -> DepthToSpace, block size 4
  -> [1, 200, 200, 3] Float32
```

고정 가중치로 RGB 픽셀을 4배 복제하므로, **LiteRT 실행 여부를 검증하기 위한 모델이지 학습된 ESRGAN 품질 모델은 아닙니다.** 실제 perceptual SR 결과가 필요하면 아래 한 줄로 Google 공식 ESRGAN 모델로 교체하십시오.

```powershell
py -3 .\tools\download_official_esrgan.py
```

또는:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\download_official_esrgan.ps1
```

이 스크립트는 TensorFlow, TensorFlow Hub, `pkg_resources`, `setuptools`를 사용하지 않습니다. Python 표준 라이브러리만으로 공식 모델을 받아 같은 파일명인 `sr_x4.tflite`로 원자적으로 교체합니다.

## 개발 환경

프로젝트 설정은 첨부된 원본의 최신 구성에 맞췄습니다.

- Android Gradle Plugin: `9.3.1`
- Gradle wrapper: `9.5.0`
- Kotlin: `2.2.10`
- LiteRT Android: `com.google.ai.edge.litert:litert:2.1.0`
- `compileSdk`: Android API `36.1`
- `targetSdk`: `36`
- `minSdk`: `26`
- JDK: `17` 이상

처음 열 때 Android Studio에서 SDK Platform 36.1과 필요한 dependency를 설치/동기화해야 합니다.

## Windows에서 실행

1. ZIP을 원하는 폴더에 풉니다.
2. Android Studio에서 프로젝트 루트의 `settings.gradle.kts`를 엽니다.
3. Gradle Sync가 끝날 때까지 dependency를 받습니다.
4. API 26 이상 실제 기기 또는 emulator를 선택합니다.
5. `app`을 Run합니다.
6. 앱이 시작되면 backend 초기화가 끝난 후 `Run 4x SR`을 누릅니다.

CLI build:

```powershell
cd C:\path\to\srdemo_fixed
.\gradlew.bat :app:assembleDebug
```

생성 APK:

```text
app\build\outputs\apk\debug\app-debug.apk
```

설치:

```powershell
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

## 테스트

### 1. 인터넷과 Android SDK가 없어도 가능한 구조 검사

```powershell
py -3 .\tools\verify_project.py
py -3 .\tools\inspect_tflite.py --verify-sr-contract
```

확인 항목:

- 필수 프로젝트 파일
- Android XML parse
- LiteRT dependency와 `noCompress` 설정
- 모델의 `TFL3` identifier
- TFLite schema version 3
- 입력 `Float32 [1,50,50,3]`
- 출력 `Float32 [1,200,200,3]`
- `CompiledModel` / `TensorBuffer` lifecycle API 사용
- APK assets 안에 불필요한 Python 파일이 없는지

### 2. JVM unit test

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

`ArgbTensorCodecTest`가 ARGB/RGB channel 순서와 float clamp/rounding을 검증합니다.

### 3. 실제 Android LiteRT 추론 instrumentation test

실제 기기 또는 emulator가 연결된 상태에서:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

`SrModelInstrumentedTest`는 다음을 실제 Android runtime에서 수행합니다.

- assets의 `sr_x4.tflite` 열기
- CPU backend compile 및 warm-up
- 실제 `CompiledModel.run()` 호출
- 출력 readback
- 50 x 50 input preview 확인
- 200 x 200 output bitmap 확인

## 주요 코드

### `SrRunner.kt`

LiteRT 모델과 native resource를 관리하는 핵심 코드입니다.

```kotlin
val model = CompiledModel.create(
    assetManager,
    "sr_x4.tflite",
    CompiledModel.Options(Accelerator.GPU),
    null,
)

val inputs = model.createInputBuffers()
val outputs = model.createOutputBuffers()

inputs.single().writeFloat(rgbInput)
model.run(inputs, outputs)
val rgbOutput = outputs.single().readFloat()
```

구현에서는 이 객체들을 매 추론마다 만들지 않고 session 동안 재사용합니다. create/run/read/close를 한 전용 dispatcher에 가두고, Activity 종료 시 input buffer, output buffer, model, dispatcher를 닫습니다.

### `BitmapSrCodec.kt`

- 원본의 중앙 정사각형 영역 crop
- 50 x 50 resize
- Android ARGB -> 모델 RGB Float32 변환
- 모델 RGB Float32 -> Android ARGB 변환

### `MainActivity.kt`

- Compose UI state 관리
- 시스템 image picker
- 큰 이미지 downsample decode
- lifecycle coroutine에서 backend 초기화와 추론 실행
- cancellation을 일반 오류로 오인하지 않도록 처리

### `SrScreen.kt`

- AUTO/CPU/GPU 선택
- 원본, 실제 50 x 50 모델 입력, 200 x 200 출력 표시
- compile/warm-up 및 전처리/추론/readback/후처리 시간 표시

## 전체 파일 구조

```text
srdemo_fixed/
├── README.md
├── TEST_REPORT.md
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── gradle/
├── tools/
│   ├── download_official_esrgan.py
│   ├── download_official_esrgan.ps1
│   ├── download_official_esrgan.sh
│   ├── generate_demo_model.py
│   ├── inspect_tflite.py
│   └── verify_project.py
└── app/
    ├── build.gradle.kts
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── assets/sr_x4.tflite
        │   ├── java/com/delee/srdemo/
        │   │   ├── MainActivity.kt
        │   │   ├── SampleImageFactory.kt
        │   │   ├── SrUiState.kt
        │   │   ├── sr/
        │   │   │   ├── ArgbTensorCodec.kt
        │   │   │   ├── BitmapSrCodec.kt
        │   │   │   └── SrRunner.kt
        │   │   └── ui/SrScreen.kt
        │   └── res/
        ├── test/
        │   └── .../ArgbTensorCodecTest.kt
        └── androidTest/
            └── .../SrModelInstrumentedTest.kt
```

## 공식 참고 자료

- LiteRT CompiledModel Android/Kotlin: https://ai.google.dev/edge/litert/next/android_kotlin
- LiteRT sample lifecycle: https://github.com/google-ai-edge/litert-samples
- Google TensorFlow SR Android example: https://github.com/tensorflow/examples/tree/master/lite/examples/super_resolution/android
- 공식 ESRGAN 모델 다운로드 정의: https://github.com/tensorflow/examples/blob/master/lite/examples/super_resolution/android/app/download.gradle
