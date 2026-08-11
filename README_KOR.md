# Android LiteRT Super-Resolution Demo

[English README](README.md)

Google **LiteRT `CompiledModel` API**를 이용해 Android 기기에서 **4× Super-Resolution TFLite 모델을 on-device로 실행하는 예제**입니다.

앱은 사용자가 선택한 Android `Bitmap`을 RGB `Float32` tensor로 변환하고, **GPU 또는 CPU**에서 ML 모델을 실행한 뒤, 출력 tensor를 다시 Bitmap으로 변환하여 Jetpack Compose 화면에 표시합니다.

> **모델 관련 중요 안내**
>
> 저장소에는 `app/src/main/assets/sr_x4.tflite`가 기본 포함되어 있어 별도의 모델 다운로드 없이 Android inference 전체 경로를 즉시 실행할 수 있습니다. 다만 기본 모델은 **학습된 ESRGAN 모델이 아니라 배포/inference 경로 검증용 test graph**입니다. 50×50 → 200×200의 동일한 x4 입출력 인터페이스를 사용하여 모델 로딩, tensor I/O, GPU/CPU 실행 및 결과 렌더링을 검증합니다. 실제 perceptual SR 품질을 확인하려면 프로젝트의 다운로드 스크립트로 Google 공식 ESRGAN TFLite 모델로 교체할 수 있습니다.

---

## 실행 결과

### Example 1

<img src="docs/images/result_dolphin.jpg" width="420" alt="LiteRT Super-Resolution 실행 결과 - dolphin">

테스트 기기에서 측정한 결과:

| 구간 | 시간 |
|---|---:|
| Backend | GPU |
| Compile + warm-up | 944.53 ms |
| Preprocess | 1.12 ms |
| Inference + readback | 45.08 ms |
| Postprocess | 12.38 ms |
| **이미지 1장 전체 처리** | **58.58 ms** |

### Example 2

<img src="docs/images/result_cyborg.jpg" width="420" alt="LiteRT Super-Resolution 실행 결과 - cyborg portrait">

동일 테스트 기기에서 측정한 결과:

| 구간 | 시간 |
|---|---:|
| Backend | GPU |
| Compile + warm-up | 944.53 ms |
| Preprocess | 1.64 ms |
| Inference + readback | 45.06 ms |
| Postprocess | 15.17 ms |
| **이미지 1장 전체 처리** | **61.86 ms** |

모델 초기화 비용인 `Compile + warm-up`은 이미지별 inference 전에 한 번 수행되므로, 이미지 한 장의 처리 시간과 별도로 표시했습니다. 실제 시간은 기기, GPU driver, thermal 상태, 이미지 및 모델에 따라 달라질 수 있습니다.

---

## 전체 Inference Pipeline

```text
원본 이미지 (Bitmap)
        |
        v
Center crop + 50 x 50 resize
        |
        v
RGB Float32 tensor
[1, 50, 50, 3]
        |
        v
TensorBuffer.writeFloat()
        |
        v
LiteRT CompiledModel
        |
        |  model.run(...)
        v
GPU / CPU inference
        |
        v
출력 Float32 tensor
[1, 200, 200, 3]
        |
        v
TensorBuffer.readFloat()
        |
        v
RGB Float32 -> ARGB_8888 Bitmap
        |
        v
200 x 200 출력 이미지
```

이 앱에서 사용하는 모델 contract는 다음과 같습니다.

- Input: `Float32 [1, 50, 50, 3]`
- Output: `Float32 [1, 200, 200, 3]`
- Layout: NHWC
- Channel: RGB
- Scale: 4×

---

## LiteRT 핵심 코드

ML 모델 실행의 핵심 코드는 `SrRunner.kt`에 있습니다.

### 1. 실행 backend 선택

```kotlin
val accelerator = when (backend) {
    ExecutionBackend.CPU -> Accelerator.CPU
    ExecutionBackend.GPU -> Accelerator.GPU
}
```

`AUTO` 모드에서는 GPU를 먼저 시도하고, GPU model 생성 또는 warm-up에 실패하면 CPU로 fallback합니다.

### 2. `.tflite` 모델 로딩

```kotlin
val model = CompiledModel.create(
    assetManager,
    "sr_x4.tflite",
    CompiledModel.Options(accelerator),
    null,
)
```

`CompiledModel.create()`가 APK의 assets에서 `.tflite` 모델을 읽고 선택된 CPU/GPU accelerator에서 실행할 수 있도록 준비합니다.

### 3. Input/Output TensorBuffer 생성

```kotlin
val inputBuffers = model.createInputBuffers()
val outputBuffers = model.createOutputBuffers()
```

Buffer는 inference마다 새로 생성하지 않고 session 동안 재사용합니다.

### 4. 입력 tensor 쓰기

```kotlin
inputBuffers.single().writeFloat(prepared.tensor)
```

Android `Bitmap`을 모델에 직접 전달하지 않습니다. `BitmapSrCodec`가 이미지를 crop/resize한 후 `[1, 50, 50, 3]` 형태의 RGB `FloatArray`로 변환합니다.

### 5. 실제 ML 모델 실행

```kotlin
model.run(
    inputBuffers,
    outputBuffers,
)
```

**실제 ML inference가 수행되는 핵심 호출입니다.** LiteRT가 선택된 GPU 또는 CPU backend에서 TFLite graph를 실행합니다.

### 6. 출력 tensor 읽기

```kotlin
val output = outputBuffers.single().readFloat()
```

결과는 `[1, 200, 200, 3]` 크기의 RGB `FloatArray`입니다. 이후 Android 화면에 표시하기 위해 다시 Bitmap으로 변환합니다.

따라서 ML 실행의 핵심은 다음 3줄로 요약할 수 있습니다.

```kotlin
inputBuffers.single().writeFloat(rgbInput)
model.run(inputBuffers, outputBuffers)
val rgbOutput = outputBuffers.single().readFloat()
```

---

## Model 초기화와 Warm-up

앱은 UI thread를 막지 않도록 모델 관련 동작을 전용 single-thread coroutine dispatcher에서 수행합니다.

```text
AUTO
 |
 +--> GPU 시도
 |      |
 |      +--> CompiledModel 생성
 |      +--> TensorBuffer 생성
 |      +--> warm-up inference
 |      +--> 성공 -> GPU 사용
 |
 +--> GPU 실패 -> CPU model 생성 및 warm-up
```

Warm-up에서는 실제 이미지 처리 전에 zero-filled input으로 inference를 한 번 수행합니다. 이를 통해 GPU/backend 지원 문제, operator 실행 오류 및 output size mismatch를 초기 단계에서 확인할 수 있습니다.

화면의 **Compile + warm-up**은 이 초기화 시간을 의미하며, 이미지 한 장을 처리할 때의 inference 시간과는 구분됩니다.

---

## 기본 모델과 ESRGAN의 차이

### 저장소에 기본 포함된 모델

다음 파일이 APK assets에 포함됩니다.

```text
app/src/main/assets/sr_x4.tflite
```

기본 모델은 다음과 같은 작은 TFLite test graph입니다.

```text
Float32 [1, 50, 50, 3]
        |
        v
Conv2D 1 x 1, 48 channels
        |
        v
DepthToSpace, block size 4
        |
        v
Float32 [1, 200, 200, 3]
```

Android/LiteRT 배포 및 실행 경로를 검증하기 위한 모델이며 **학습된 ESRGAN 모델은 아닙니다.**

### Google 공식 ESRGAN TFLite 모델로 교체

프로젝트의 helper script를 실행하면 Google TensorFlow 공식 ESRGAN TFLite 모델을 다운로드하여 앱이 사용하는 동일한 asset 이름으로 교체할 수 있습니다.

Python:

```powershell
py -3 .\tools\download_official_esrgan.py
```

PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\download_official_esrgan.ps1
```

모델 교체 후 다시 빌드합니다.

```powershell
.\gradlew.bat clean :app:assembleDebug
```

입출력 contract가 동일하면 Android의 LiteRT inference 코드는 변경할 필요가 없습니다.

TensorFlow 공식 ESRGAN Super Resolution 설명:

- https://www.tensorflow.org/lite/models/super_resolution/overview?hl=ko

---

## 주요 코드 및 역할

### `SrRunner.kt`

LiteRT runtime 전체 lifecycle을 관리합니다.

- CPU/GPU backend 선택
- `CompiledModel` 생성
- input/output `TensorBuffer` 생성 및 재사용
- warm-up 수행
- `model.run()`으로 실제 inference 실행
- output tensor readback
- 실행시간 측정
- LiteRT native resource 해제

### `BitmapSrCodec.kt`

이미지 전처리/후처리를 담당합니다.

- 원본 이미지 center crop
- 50×50 resize
- Android ARGB → RGB `Float32`
- 모델 RGB `Float32` → Android ARGB Bitmap

### `ArgbTensorCodec.kt`

RGB/ARGB 변환 및 float 값 clamp/rounding을 담당합니다.

### `MainActivity.kt`

앱 lifecycle, 이미지 선택, backend 초기화, inference 요청을 연결합니다.

### `SrScreen.kt`

Jetpack Compose UI를 구성합니다.

- `AUTO`, `CPU`, `GPU` 선택
- 이미지 선택
- SR 실행
- 원본 이미지 표시
- 실제 50×50 모델 입력 표시
- 200×200 출력 표시
- compile/warm-up 및 각 처리 단계 시간 표시

---

## 개발 환경

프로젝트 설정:

- Android Gradle Plugin: `9.3.1`
- Gradle: `9.5.0`
- Kotlin: `2.2.10`
- LiteRT Android: `com.google.ai.edge.litert:litert:2.1.0`
- `compileSdk`: `37`
- `targetSdk`: `36`
- `minSdk`: `26`
- JDK: 17 이상

빌드 전 Android SDK Platform 37을 설치해야 합니다.

---

## 빌드 및 실행

### Android Studio

1. Android Studio에서 프로젝트 root를 엽니다.
2. Gradle Sync가 완료될 때까지 기다립니다.
3. Android API 26 이상 실제 기기 또는 emulator를 선택합니다.
4. `app`을 실행합니다.
5. 모델 초기화가 끝날 때까지 기다립니다.
6. **Choose image**로 이미지를 선택합니다.
7. **Run SR**을 눌러 inference를 실행합니다.

### Windows command line

```powershell
.\gradlew.bat :app:assembleDebug
```

APK 생성 위치:

```text
app\build\outputs\apk\debug\app-debug.apk
```

ADB 설치:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r `
  .\app\build\outputs\apk\debug\app-debug.apk
```

---

## 검증 및 테스트

### Offline/static 검사

```powershell
py -3 .\tools\verify_project.py
py -3 .\tools\inspect_tflite.py --verify-sr-contract
```

주요 확인 항목:

- TFLite `TFL3` identifier
- TFLite schema
- input/output tensor shape
- LiteRT dependency / model asset 설정
- `CompiledModel` / `TensorBuffer` lifecycle

### JVM unit test

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

### Android instrumentation test

실제 기기 또는 emulator 연결 후:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

Instrumentation test는 Android runtime에서 모델을 열고 실제 `CompiledModel.run()`을 수행한 뒤 50×50 input / 200×200 output 경로를 검증합니다.

---

## 프로젝트 구조

```text
.
├── README.md
├── README_KOR.md
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── docs/
│   └── images/
│       ├── result_dolphin.jpg
│       └── result_cyborg.jpg
├── tools/
│   ├── download_official_esrgan.py
│   ├── download_official_esrgan.ps1
│   ├── download_official_esrgan.sh
│   ├── generate_demo_model.py
│   ├── inspect_tflite.py
│   └── verify_project.py
└── app/
    └── src/
        ├── main/
        │   ├── assets/sr_x4.tflite
        │   └── java/.../
        │       ├── MainActivity.kt
        │       ├── SrUiState.kt
        │       ├── sr/
        │       │   ├── ArgbTensorCodec.kt
        │       │   ├── BitmapSrCodec.kt
        │       │   └── SrRunner.kt
        │       └── ui/SrScreen.kt
        ├── test/
        └── androidTest/
```

---

## 참고 자료

- LiteRT: https://ai.google.dev/edge/litert
- LiteRT Android/Kotlin `CompiledModel` API: https://ai.google.dev/edge/litert/next/android_kotlin
- Google AI Edge LiteRT samples: https://github.com/google-ai-edge/litert-samples
- TensorFlow Lite Super Resolution / ESRGAN: https://www.tensorflow.org/lite/models/super_resolution/overview?hl=ko
- TensorFlow Android Super Resolution sample: https://github.com/tensorflow/examples/tree/master/lite/examples/super_resolution/android

---

## 향후 확장

현재 프로젝트는 **Bitmap 기반 inference baseline**입니다. 실시간 video SR로 확장하려면 decoder output을 Bitmap/CPU memory로 반복 복사하지 않고 GPU buffer/texture 상태로 LiteRT에 전달하는 구조로 확장하는 것이 다음 단계입니다.
