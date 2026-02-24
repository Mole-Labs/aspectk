# AspectK Android/Multiplatform Sample - 실행 계획

## Phase 1: Gradle 프로젝트 기본 구조 생성

### 1-1. sample/ 루트 프로젝트 설정 파일 생성
- [ ] `settings.gradle.kts` 생성
  - `rootProject.name = "aspectk-sample"`
  - `includeBuild("..")` 로 AspectK 메인 프로젝트 참조
  - `include(":android")`, `include(":multiplatform")`
  - pluginManagement에 google(), mavenCentral(), gradlePluginPortal() 설정
- [ ] `build.gradle.kts` 생성 (루트)
  - 공통 설정 (allprojects 레벨)
- [ ] `gradle.properties` 생성
  - `android.useAndroidX=true`
  - JVM 메모리 설정
  - Kotlin/AGP 관련 속성
- [ ] `gradle/libs.versions.toml` 생성
  - kotlin = "2.2.21" (메인 프로젝트와 동일)
  - agp, compose-bom, aspectk 관련 버전 정의
- [ ] Gradle Wrapper 복사 (메인 프로젝트에서 symlink 또는 복사)
  - `gradlew`, `gradlew.bat`, `gradle/wrapper/`

### 1-2. AspectK includeBuild 연동 확인
- [ ] `includeBuild("..")`가 정상적으로 `:plugin`, `:runtime` 모듈을 resolve하는지 확인
- [ ] `com.mole.aspectK` 플러그인 ID가 composite build에서 사용 가능한지 확인

---

## Phase 2: Android 모듈 생성 (`:android`)

### 2-1. 빌드 설정
- [ ] `android/build.gradle.kts` 생성
  - plugins: `com.android.application`, `org.jetbrains.kotlin.android`, `com.mole.aspectK`, `org.jetbrains.kotlin.plugin.compose`
  - android 블록: compileSdk=35, minSdk=24, targetSdk=35
  - Compose 활성화 (`buildFeatures { compose = true }`)
  - dependencies: runtime, compose-bom, compose-ui, compose-material3, activity-compose

### 2-2. AndroidManifest.xml
- [ ] 최소 매니페스트 생성 (application, activity 선언)

### 2-3. Aspect 정의
- [ ] `@LogExecution` 커스텀 타겟 어노테이션 생성
- [ ] `LoggingAspect` 생성
  - `@Aspect object`
  - `@Before(LogExecution::class)` advice 함수
  - `JoinPoint`에서 함수명, 파라미터 정보를 로그로 출력

### 2-4. 비즈니스 로직
- [ ] `SampleService` 생성
  - `@LogExecution` 어노테이션이 적용된 메서드들
  - 예: `greet(name: String): String`, `calculate(a: Int, b: Int): Int`

### 2-5. Compose UI
- [ ] `MainActivity.kt` 생성
  - `setContent { SampleApp() }` 구조
  - 버튼 클릭 시 `SampleService` 함수 호출
  - Logcat에 Aspect 로그 출력 확인
  - 화면에 호출 결과와 Aspect 실행 여부 표시

### 2-6. 빌드 및 검증
- [ ] `./gradlew :android:assembleDebug` 성공 확인
- [ ] 에뮬레이터/기기에서 실행 가능 여부 확인

---

## Phase 3: Multiplatform 모듈 생성 (`:multiplatform`)

### 3-1. 빌드 설정
- [ ] `multiplatform/build.gradle.kts` 생성
  - plugins: `org.jetbrains.kotlin.multiplatform`, `com.mole.aspectK`
  - kotlin 타겟: `jvm()`, `iosArm64()`, `iosSimulatorArm64()`, `macosArm64()`
  - commonMain dependencies: runtime
  - commonTest dependencies: kotlin-test

### 3-2. 공유 Aspect 정의 (commonMain)
- [ ] `@LogExecution` 커스텀 타겟 어노테이션 생성
- [ ] `LoggingAspect` 생성
  - `@Aspect object`
  - `@Before(LogExecution::class)` advice 함수
  - 멀티플랫폼 로깅 (expect/actual 또는 println)

### 3-3. 공유 비즈니스 로직 (commonMain)
- [ ] `Greeting` 클래스 생성
  - `@LogExecution` 적용된 함수들
  - `greet(): String` - 플랫폼 정보 포함 인사말 반환

### 3-4. 플랫폼별 코드
- [ ] `jvmMain`: JVM 전용 구현 (필요 시)
- [ ] `iosMain`: iOS 전용 구현 (필요 시)
- [ ] `desktopMain`: Desktop 전용 구현 (필요 시)
- [ ] 공통 expect 함수: `expect fun platformName(): String`

### 3-5. 테스트
- [ ] `commonTest`에 Aspect 동작 검증 테스트 작성
  - Aspect가 정상 호출되는지 확인 (side-effect 검증)
  - `JoinPoint`의 args, signature 등 검증

### 3-6. 빌드 및 검증
- [ ] `./gradlew :multiplatform:jvmTest` 성공 확인
- [ ] `./gradlew :multiplatform:allTests` 성공 확인

---

## Phase 4: 메인 프로젝트 연동

### 4-1. 메인 settings.gradle.kts 수정 여부 결정
- 메인 프로젝트의 `settings.gradle.kts`에는 변경 없음 (sample이 독립 프로젝트)
- sample은 자체 `settings.gradle.kts`의 `includeBuild("..")`로 메인을 참조

### 4-2. .gitignore
- [ ] `sample/.gitignore` 생성 (빌드 산출물 제외)

---

## Phase 5: 최종 검증

- [ ] `cd sample && ./gradlew build` 전체 빌드 성공
- [ ] `./gradlew :android:assembleDebug` Android APK 생성 확인
- [ ] `./gradlew :multiplatform:jvmTest` 테스트 통과
- [ ] AspectK 플러그인이 두 모듈 모두에서 정상 동작 확인 (Aspect advice가 실제로 주입됨)

---

## 주의사항

1. **Kotlin 버전 일치**: sample의 Kotlin 버전(2.2.21)이 메인 프로젝트와 반드시 일치해야 함. 컴파일러 플러그인은 Kotlin 버전에 민감.
2. **includeBuild 경로**: sample이 `aspectk/sample/`에 위치하므로 `includeBuild("..")`가 `aspectk/`를 가리킴.
3. **AGP + KMP**: Android 모듈은 순수 Android 프로젝트 (kotlin-android), multiplatform 모듈은 KMP 프로젝트로 분리. Android 모듈에서 KMP를 사용하지 않는 이유는 단순성을 위해서임.
4. **Compose Compiler**: Kotlin 2.2.x에서는 Compose Compiler가 Kotlin 플러그인으로 번들됨 (`org.jetbrains.kotlin.plugin.compose`).
5. **Gradle Wrapper**: sample 프로젝트에 별도의 Gradle Wrapper를 두거나, 메인 프로젝트의 wrapper를 공유할지 결정 필요. 독립 프로젝트이므로 별도 wrapper 권장.