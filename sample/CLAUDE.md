# CLAUDE.md - AspectK Sample Project

## Overview

AspectK 컴파일러 플러그인의 샘플 프로젝트. `includeBuild`를 통해 AspectK 메인 프로젝트를 참조하는 **별도의 독립 Gradle 프로젝트**로 구성된다.

## 프로젝트 구조

```
sample/                              # 독립 Gradle 프로젝트 루트
├── CLAUDE.md
├── PLAN.md                          # 실행 계획
├── settings.gradle.kts              # includeBuild("..") 로 AspectK 참조
├── build.gradle.kts                 # 루트 빌드 파일 (공통 설정)
├── gradle.properties
├── gradle/
│   └── libs.versions.toml           # 버전 카탈로그
│
├── android/                         # Android 앱 샘플 (Compose)
│   ├── build.gradle.kts             # AGP + com.mole.aspectK 플러그인 적용
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           └── kotlin/com/mole/sample/android/
│               ├── MainActivity.kt          # Compose UI
│               ├── aspects/                 # Aspect 정의
│               │   └── LoggingAspect.kt     # @Aspect + @Before
│               └── service/                 # 비즈니스 로직
│                   └── SampleService.kt     # Aspect 적용 대상 함수들
│
└── multiplatform/                   # KMP 샘플 (JVM + iOS + Desktop)
    ├── build.gradle.kts             # KMP + com.mole.aspectK 플러그인 적용
    └── src/
        ├── commonMain/kotlin/com/mole/sample/shared/
        │   ├── aspects/             # 공유 Aspect 정의
        │   │   └── LoggingAspect.kt
        │   └── service/             # 공유 비즈니스 로직
        │       └── Greeting.kt
        ├── commonTest/kotlin/       # 공유 테스트
        ├── jvmMain/kotlin/          # JVM 플랫폼 코드
        ├── iosMain/kotlin/          # iOS 플랫폼 코드
        └── desktopMain/kotlin/      # Desktop 플랫폼 코드
```

## 핵심 설계 결정

### includeBuild 구성
- `settings.gradle.kts`에서 `includeBuild("..")`로 AspectK 루트 프로젝트를 composite build로 포함
- `:plugin` (Gradle 플러그인)과 `:runtime` 모듈을 로컬 소스에서 직접 참조
- Maven publish 없이 로컬 개발/테스트 가능

### 플러그인 적용 방식
- 각 모듈에 `com.mole.aspectK` Gradle 플러그인 적용
- AspectK 컴파일러 플러그인(:core)이 Kotlin 컴파일러 클래스패스에 자동 등록됨
- `:runtime` 모듈을 dependency로 추가하여 `@Aspect`, `@Before`, `JoinPoint` 등 사용

### android 모듈
- 단일 Android 타겟 (Jetpack Compose 앱)
- AGP + Kotlin Android 플러그인 + AspectK 플러그인
- Compose UI에서 버튼 클릭 시 Aspect가 적용된 함수를 호출하고 결과를 화면에 표시

### multiplatform 모듈
- KMP 타겟: JVM, iOS (iosArm64, iosSimulatorArm64), Desktop (macosArm64)
- `commonMain`에 Aspect 정의와 비즈니스 로직
- 각 플랫폼별 expect/actual 패턴 활용

## 빌드 명령어

```bash
# 전체 빌드
./gradlew build

# Android 앱 설치
./gradlew :android:installDebug

# Multiplatform 테스트
./gradlew :multiplatform:allTests

# JVM 전용 테스트
./gradlew :multiplatform:jvmTest
```

## 샘플 시나리오

1. **Logging Aspect**: `@LogExecution` 커스텀 어노테이션이 붙은 함수 호출 시 로그를 자동 출력
2. **UI 확인 (Android)**: Compose UI에서 버튼 클릭 → Aspect 적용 함수 호출 → 로그 결과 화면 표시

## 주요 의존성

| Component | Version / 비고 |
|---|---|
| Kotlin | 2.2.21 (메인 프로젝트와 동일) |
| AspectK plugin | `com.mole.aspectK` (includeBuild 로컬 참조) |
| AspectK runtime | `io.github.oungsi2000:runtime` (includeBuild 대체) |
| AGP | 최신 안정 버전 |
| Jetpack Compose (BOM) | 최신 안정 버전 |
| minSdk / targetSdk / compileSdk | 24 / 35 / 35 |