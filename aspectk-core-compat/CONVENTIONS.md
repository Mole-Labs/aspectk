# aspectk-core-compat 코딩 컨벤션

## 1. 패키지 구조

```
io.github.molelabs.aspectk.compat          # 베이스: 인터페이스, 어노테이션, 유틸
io.github.molelabs.aspectk.compat.k2220    # Kotlin 2.2.20 구현
io.github.molelabs.aspectk.compat.k2221    # Kotlin 2.2.21 구현
io.github.molelabs.aspectk.compat.k230     # Kotlin 2.3.0 구현
```

### 패키지명 생성 규칙

Kotlin 버전 → 패키지 접미사 변환:

| Kotlin 버전 | 패키지명 |
|-------------|---------|
| `2.2.20` | `k2220` |
| `2.2.21` | `k2221` |
| `2.3.0` | `k230` |
| `2.3.20-Beta1` | `k2320_beta1` |
| `2.3.20-dev-5706` | `k2320_dev_5706` |

규칙:
- 접두사 `k` + 메이저.마이너.패치에서 `.` 제거
- Pre-release classifier는 `_` 구분자로 소문자 변환
- `-` → `_` 변환

---

## 2. 파일 명명

### 베이스 모듈
| 파일 | 역할 |
|------|------|
| `CompatContext.kt` | 핵심 인터페이스 + Factory + ServiceLoader Companion |
| `CompatApi.kt` | `@CompatApi` 어노테이션 정의 |
| `KotlinToolingVersion.kt` | 버전 파싱/비교 `Comparable` 구현체 |

### 버전별 모듈
| 파일 | 역할 |
|------|------|
| `version.txt` | 모듈 루트. 타겟 Kotlin 버전 plain text (예: `2.2.20`) |
| `CompatContextImpl.kt` | `CompatContext` 구현체. **파일명 고정.** |
| `META-INF/services/io.github.molelabs.aspectk.compat.CompatContext$Factory` | ServiceLoader 등록 |

---

## 3. CompatContext 인터페이스 메서드 컨벤션

### 네이밍

```kotlin
// 패턴: 원본 함수명 + "Compat" 접미사
fun registerIrExtensionCompat(...)
fun addBackingFieldCompat(...)
fun createClassReferenceCompat(...)
fun isConcreteFunctionImplCompat(...)
```

- 원본 Kotlin 컴파일러 API 이름을 기반으로 하되, 접미사 `Compat`를 붙여 직접 호출과 구분.
- 단, 원본 이름이 너무 길면 의미를 유지하는 선에서 축약 가능.

### 어노테이션

모든 compat 메서드에는 `@CompatApi` 어노테이션 필수:

```kotlin
@CompatApi(
    since = "2.3.0",
    reason = ChangeReason.DELETED,
    message = "getContainingClassSymbol()이 fir.analysis.checkers에서 삭제됨",
)
fun getContainingClassSymbolCompat(symbol: FirBasedSymbol<*>): FirClassSymbol<*>?
```

### 파라미터

- 원본 API의 receiver를 **첫 번째 파라미터**로 변환:
  ```kotlin
  // 원본: IrClass.addFakeOverrides(typeSystem)
  // Compat: fun addFakeOverridesCompat(irClass: IrClass, typeSystem: IrTypeSystemContext)
  ```
- 확장 함수가 아닌 일반 함수로 선언 (인터페이스 제약).

---

## 4. 버전별 CompatContextImpl 작성 규칙

### 베이스 모듈 (가장 낮은 지원 버전)

```kotlin
// k2220/CompatContextImpl.kt
package io.github.molelabs.aspectk.compat.k2220

// 네이티브 API는 `as ...Native` 별칭으로 import
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension.Companion.registerExtension as registerExtensionNative

class CompatContextImpl : CompatContext {
    // 모든 메서드를 직접 구현
    override fun registerIrExtensionCompat(
        configuration: CompilerConfiguration,
        extension: IrGenerationExtension,
    ) {
        registerExtensionNative(configuration, extension)
    }

    class Factory : CompatContext.Factory {
        override val minVersion = "2.2.20"
        override fun create(): CompatContext = CompatContextImpl()
    }
}
```

### 후속 버전 모듈 (위임 + override)

```kotlin
// k230/CompatContextImpl.kt
package io.github.molelabs.aspectk.compat.k230

import io.github.molelabs.aspectk.compat.CompatContext
import io.github.molelabs.aspectk.compat.k2221.CompatContextImpl as DelegateType

class CompatContextImpl : CompatContext by DelegateType() {
    // 2.3.0에서 변경된 메서드만 override
    override fun registerIrExtensionCompat(...) {
        // 2.3.0의 새로운 API 사용
    }

    class Factory : CompatContext.Factory {
        override val minVersion = "2.3.0"
        override fun create(): CompatContext = CompatContextImpl()
    }
}
```

### 핵심 규칙

1. **`by DelegateType()`** 패턴 필수. 변경되지 않은 메서드는 이전 버전에 자동 위임.
2. **이전 버전의 `CompatContextImpl`을 `DelegateType`으로 import alias**. 모든 모듈에서 동일한 이름 사용.
3. **override는 변경된 메서드만**. 불필요한 override 금지.
4. **Factory.minVersion은 `version.txt`와 일치**해야 한다.

---

## 5. build.gradle.kts 컨벤션

### 베이스 모듈

```kotlin
plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.diffplug.spotless)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    compileOnly(libs.kotlin.compiler)
    compileOnly(libs.kotlin.stdlib)
}
```

### 버전별 모듈

```kotlin
plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.diffplug.spotless)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// version.txt에서 타겟 Kotlin 컴파일러 버전 읽기
val kotlinVersion = providers.fileContents(
    layout.projectDirectory.file("version.txt")
).asText.map { it.trim() }

dependencies {
    compileOnly(kotlinVersion.map { "org.jetbrains.kotlin:kotlin-compiler:$it" })
    compileOnly(libs.kotlin.stdlib)
    api(project(":aspectk-core-compat"))                          // 인터페이스
    implementation(project(":aspectk-core-compat:<이전 버전>"))    // 위임 대상
}
```

### 핵심 규칙

1. `kotlin-compiler`는 반드시 **`compileOnly`**. 런타임에는 호스트 컴파일러가 제공.
2. 각 모듈은 **정확히 하나의 Kotlin 컴파일러 버전**에 대해서만 컴파일.
3. 베이스 인터페이스 모듈은 `api`로, 위임 대상 모듈은 `implementation`으로 의존.

---

## 6. ServiceLoader 등록

### 파일 경로

```
src/main/resources/META-INF/services/io.github.molelabs.aspectk.compat.CompatContext$Factory
```

> **주의**: 파일명에 `$`가 포함됨 (Java inner class 표기).

### 파일 내용 (한 줄)

```
io.github.molelabs.aspectk.compat.k2220.CompatContextImpl$Factory
```

---

## 7. Import 컨벤션

### 네이티브 API import alias

컴파일러 API를 직접 호출할 때, 이름 충돌을 피하기 위해 `as ...Native` 별칭 사용:

```kotlin
// Good
import org.jetbrains.kotlin.ir.util.addSimpleDelegatingConstructor as addSimpleDelegatingConstructorNative

// Bad — 이름 충돌 위험
import org.jetbrains.kotlin.ir.util.addSimpleDelegatingConstructor
```

### 이전 버전 import alias

```kotlin
// Good — 모든 모듈에서 동일한 이름
import io.github.molelabs.aspectk.compat.k2220.CompatContextImpl as DelegateType

// Bad — 구체 이름 사용
import io.github.molelabs.aspectk.compat.k2220.CompatContextImpl
```

---

## 8. 문서화

### CompatContext 메서드

```kotlin
/**
 * [IrClass]에 fake override를 추가합니다.
 *
 * Kotlin 2.3.0에서 [IrTypeSystemContext] 파라미터 타입이 변경됨.
 */
@CompatApi(since = "2.3.0", reason = ChangeReason.ABI_CHANGE)
fun addFakeOverridesCompat(irClass: IrClass, typeSystem: IrTypeSystemContext)
```

### 버전별 override

```kotlin
// k230/CompatContextImpl.kt
/**
 * 2.3.0: IrTypeSystemContext가 새 인터페이스로 변경.
 * 이전 버전의 구현과 다르게 새 API를 직접 호출.
 */
override fun addFakeOverridesCompat(irClass: IrClass, typeSystem: IrTypeSystemContext) {
    irClass.addFakeOverrides(typeSystem) // 2.3.0 네이티브 API
}
```

---

## 9. 테스트 컨벤션

### KotlinToolingVersion 테스트

```kotlin
class KotlinToolingVersionTest {
    @Test
    fun `parse stable version`() { ... }

    @Test
    fun `parse dev version`() { ... }

    @Test
    fun `compare versions`() { ... }
}
```

### Factory 해석 테스트

```kotlin
class CompatContextFactoryTest {
    @Test
    fun `resolve factory for exact version`() { ... }

    @Test
    fun `resolve factory picks highest compatible`() { ... }

    @Test
    fun `resolve factory ignores higher versions`() { ... }
}
```

---

## 10. 코드 스타일

기존 AspectK 프로젝트 규칙을 따른다:

- **ktlint 1.8.0** via Spotless
- **indent**: 4 spaces
- **wildcard import 금지**
- **Apache 2.0 라이센스 헤더** 필수 (모든 `.kt`, `.kts` 파일)
- **`./gradlew spotlessApply`** 커밋 전 실행
- JVM target: 17

---

## 11. 새 Kotlin 버전 추가 체크리스트

1. [ ] `aspectk-core-compat/k<version>/` 디렉토리 생성
2. [ ] `version.txt` 작성 (예: `2.3.20`)
3. [ ] `build.gradle.kts` 작성 (이전 버전 모듈에 `implementation` 의존)
4. [ ] `CompatContextImpl.kt` 작성 (`by DelegateType()` + 변경된 API만 override)
5. [ ] `META-INF/services/` ServiceLoader 등록 파일 생성
6. [ ] `settings.gradle.kts`는 자동 탐색으로 별도 수정 불필요
7. [ ] `./gradlew spotlessApply` 실행
8. [ ] `./gradlew check` 전체 테스트 통과 확인
9. [ ] CI 매트릭스에 새 버전 추가
