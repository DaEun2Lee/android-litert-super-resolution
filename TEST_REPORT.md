# Test Report

검증 일자: 2026-08-11

## 결론

소스와 모델은 정적/host 수준 검증을 통과했고, 실제 Android 기기에서 LiteRT inference를 검증할 instrumentation test까지 포함했습니다.

다만 현재 검증 컨테이너에는 Android SDK가 없고 외부 DNS가 차단되어 있어, 이 환경에서 Gradle distribution 및 Android/Maven dependency를 내려받아 APK를 끝까지 생성하거나 Android 기기에 설치하는 단계는 수행할 수 없었습니다. 이 제한은 아래 Gradle 시도 로그에 그대로 기록했습니다.

## 통과한 검사

### 1. TFLite 구조 검사: PASS

명령:

```bash
python3 tools/inspect_tflite.py --verify-sr-contract
```

확인 결과:

- File identifier: `TFL3`
- Schema version: `3`
- Subgraph: `1`
- Operator: `Conv2D`, `DepthToSpace`로 구성된 2개 operator
- Input: `Float32 [1,50,50,3]`
- Output: `Float32 [1,200,200,3]`

### 2. 프로젝트 오프라인 구조 검사: PASS

명령:

```bash
python3 tools/verify_project.py
```

확인 결과:

- 필수 파일 존재
- 모든 Android XML well-formed
- LiteRT `2.1.0` dependency
- `.tflite` no-compress 설정
- pre-build TFLite identifier check
- `CompiledModel.create()` / input-output buffer / write-run-read / close lifecycle
- assets에 Python script가 들어가지 않음

### 3. 순수 Kotlin RGB codec host test: PASS

실제 `ArgbTensorCodec.kt`를 Kotlin/JVM으로 컴파일해 다음을 확인했습니다.

- ARGB -> RGB Float32 channel 순서
- RGB Float32 -> ARGB round/clamp
- alpha channel 고정
- round-trip 값

출력:

```text
PASS: ArgbTensorCodec host test
```

### 4. SR core Kotlin compile check: PASS

실제 아래 파일을 Android 및 LiteRT 공개 API signature와 호환되는 최소 stub에 대해 Kotlin compiler로 컴파일했습니다.

- `ArgbTensorCodec.kt`
- `BitmapSrCodec.kt`
- `SrRunner.kt`

이 검사에서 기존 `Dispatchers.IO.limitedParallelism(1)` 의존성을 발견해, 명시적으로 닫을 수 있는 `newSingleThreadExecutor().asCoroutineDispatcher()`로 교체했습니다.

### 5. Activity orchestration compile check: PASS

실제 아래 파일을 최소 Android/Activity/lifecycle stub과 함께 컴파일했습니다.

- `MainActivity.kt`
- `SampleImageFactory.kt`
- `SrUiState.kt`
- SR core 전체

### 6. Compose screen compile check: PASS

실제 `SrScreen.kt`를 Compose API 호환 stub과 함께 컴파일해 UI 코드의 문법과 호출 구조를 검사했습니다.

### 7. Android instrumentation test source compile check: PASS

실제 `SrModelInstrumentedTest.kt`를 AndroidX Test/JUnit/LiteRT API 호환 stub과 함께 컴파일해 test source의 문법과 호출 구조를 확인했습니다. 이 검사는 test 코드의 컴파일 가능성을 점검하는 host-side 검사이며, 실제 native LiteRT 실행은 아래의 device/emulator 명령으로 확인해야 합니다.

## Android에서 실행하도록 포함한 test

파일:

```text
app/src/androidTest/java/com/delee/srdemo/SrModelInstrumentedTest.kt
```

실제 Android device/emulator에서 다음 명령으로 실행합니다.

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

이 테스트는 CPU backend로 모델 compile/warm-up, `CompiledModel.run()`, output readback, 200 x 200 bitmap 생성을 실제로 수행합니다.

## Gradle/APK 시도 결과

시도 명령:

```bash
./gradlew :app:assembleDebug --offline
```

환경 제한으로 wrapper가 필요한 Gradle 9.5.0 distribution을 찾지 못했고, 외부 DNS도 사용할 수 없어 다음 단계로 진행하지 못했습니다.

```text
java.net.UnknownHostException: services.gradle.org
```

또한 현재 컨테이너에는 `ANDROID_HOME`/`ANDROID_SDK_ROOT`와 Android SDK가 없습니다. 따라서 여기에서 APK 생성 성공을 주장하지 않습니다.

상세 로그:

```text
verification/gradle-wrapper-attempt.log
```

사용자 PC의 Android Studio에는 원본 프로젝트를 만들 때 사용한 SDK/Gradle 환경이 있으므로, 첫 Gradle Sync 후 아래 두 명령으로 최종 확인하면 됩니다.

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```
