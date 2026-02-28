# aspectk-core-compat 실행 계획

## 배경

Kotlin 컴파일러 플러그인 API는 버전 간 안정성이 보장되지 않는다. 클래스 이름 변경, 메서드 시그니처 변경, API 삭제 등이 빈번하게 발생한다. AspectK가 여러 Kotlin 버전을 동시에 지원하려면, **컴파일러 API 호환성 레이어**가 필요하다.

Metro 프로젝트의 `compiler-compat` 모듈 설계를 참고하여, AspectK에 맞는 `aspectk-core-compat` 모듈을 구축한다.

## 참고 프로젝트

- [ZacSweers/metro/compiler-compat](https://github.com/ZacSweers/metro/tree/main/compiler-compat)
- 핵심 패턴: **Interface + ServiceLoader + Kotlin class delegation (`by`)**

---

## Phase 1: 모듈 구조 설계

### 1-1. 디렉토리 구조

```
aspectk-core-compat/                     # 베이스 모듈: 인터페이스 + 유틸리티
├── PLAN.md
├── CONVENTIONS.md
├── build.gradle.kts
├── src/main/kotlin/io/github/molelabs/aspectk/compat/
│   ├── CompatContext.kt                 # 핵심 인터페이스 + ServiceLoader Factory
│   ├── CompatApi.kt                     # 호환성 어노테이션 (@CompatApi)
│   └── KotlinToolingVersion.kt          # 버전 파싱/비교 유틸리티
│
├── k2220/                               # Kotlin 2.2.20 구현 (베이스)
│   ├── build.gradle.kts
│   ├── version.txt                      # "2.2.20"
│   └── src/
│       ├── main/kotlin/.../k2220/
│       │   └── CompatContextImpl.kt     # 모든 메서드 직접 구현
│       └── main/resources/META-INF/services/
│           └── io.github.molelabs.aspectk.compat.CompatContext$Factory
│
├── k2221/                               # Kotlin 2.2.21 구현
│   ├── build.gradle.kts
│   ├── version.txt                      # "2.2.21"
│   └── src/
│       ├── main/kotlin/.../k2221/
│       │   └── CompatContextImpl.kt     # k2220에 위임, 변경점만 override
│       └── main/resources/META-INF/services/
│           └── io.github.molelabs.aspectk.compat.CompatContext$Factory
│
└── k230/                                # Kotlin 2.3.0 구현 (향후)
    ├── build.gradle.kts
    ├── version.txt                      # "2.3.0"
    └── src/
        ├── main/kotlin/.../k230/
        │   └── CompatContextImpl.kt     # k2221에 위임, 변경점만 override
        └── main/resources/META-INF/services/
            └── io.github.molelabs.aspectk.compat.CompatContext$Factory
```

### 1-2. settings.gradle.kts 연동

```kotlin
// settings.gradle.kts에 동적 모듈 탐색 추가
include(":aspectk-core-compat")
file("aspectk-core-compat").listFiles()
    ?.filter { it.isDirectory && it.name.startsWith("k") }
    ?.forEach { include(":aspectk-core-compat:${it.name}") }
```

---

## Phase 2: CompatContext 인터페이스 설계

### 2-1. 래핑 대상 API 분석 (AspectK에서 사용 중인 컴파일러 API)

현재 `aspectk-core`가 사용하는 Kotlin 컴파일러 API를 분석한 결과, 다음 카테고리로 분류된다:

#### 카테고리 A — 플러그인 등록 (변경 가능성: 중)
| API | 사용 위치 | 비고 |
|-----|----------|------|
| `IrGenerationExtension.registerExtension()` | `AspectKCompilerPluginRegistrar` | 2.4.0에서 API 이동 (Metro 참고) |
| `CompilerPluginRegistrar` | `AspectKCompilerPluginRegistrar` | 비교적 안정 |

#### 카테고리 B — IR 선언 빌더 (변경 가능성: 높음)
| API | 사용 위치 | 비고 |
|-----|----------|------|
| `irFactory.buildClass {}` | `MethodSignatureGenerator` | ClassKind, visibility 등 |
| `irFactory.buildField {}` | `MethodSignatureGenerator` | isStatic, isFinal |
| `irFactory.buildProperty {}` | `MethodSignatureGenerator` | backingField 연결 |
| `buildValueParameter {}` | `MethodSignatureGenerator` | IrValueParameterBuilder |
| `addSimpleDelegatingConstructor()` | `MethodSignatureGenerator` | 생성자 위임 |
| `createExpressionBody()` | `MethodSignatureGenerator` | 표현식 바디 |

#### 카테고리 C — IR 빌더 DSL (변경 가능성: 중)
| API | 사용 위치 | 비고 |
|-----|----------|------|
| `irCall()` | 모든 Generator | 함수 호출 IR 생성 |
| `irBlock()` | `AdviceCallGenerator` | 블록 표현식 |
| `irGet()`, `irGetField()`, `irGetObject()` | `JoinPointGenerator` | 값/필드/객체 참조 |
| `irNull()`, `irString()`, `irBoolean()` | 여러 Generator | 리터럴 |
| `irVararg()` | `IrExtension` | 가변인자 |
| `DeclarationIrBuilder` | `IrExtension` | 빌더 컨텍스트 |

#### 카테고리 D — IR 트리 순회/변환 (변경 가능성: 중)
| API | 사용 위치 | 비고 |
|-----|----------|------|
| `IrVisitorVoid` | `AspectVisitor`, `InheritableVisitor` | IR 트리 방문자 |
| `IrElementTransformerVoidWithContext` | `AspectTransformer` | IR 트리 변환자 |
| `acceptChildrenVoid()` | `AdviceGenerationExtension` | 자식 순회 |
| `deepCopyWithSymbols()` | `AdviceCallGenerator` | IR 깊은 복사 |

#### 카테고리 E — 심볼 해석 (변경 가능성: 중)
| API | 사용 위치 | 비고 |
|-----|----------|------|
| `IrPluginContext.referenceClass()` | `AspectKIrCompilerContext` | ClassId → IrClassSymbol |
| `IrPluginContext.referenceFunctions()` | `IrExtension` | CallableId → IrFunctionSymbol |
| `IrPluginContext.irBuiltIns` | `MethodSignatureGenerator`, `IrExtension` | 기본 타입 |
| `IrPluginContext.irFactory` | `MethodSignatureGenerator` | IR 팩토리 |

#### 카테고리 F — 타입 시스템 (변경 가능성: 낮음~중)
| API | 사용 위치 | 비고 |
|-----|----------|------|
| `IrType.classFqName` | `AspectVisitor`, `MethodSignatureGenerator` | 타입 FqName |
| `IrType.classOrNull` | `IrExtension` | nullable 클래스 심볼 |
| `IrType.typeWith()` | `MethodSignatureGenerator` | 파라미터화된 타입 |
| `IrType.isNullable()` | `MethodSignatureGenerator` | nullable 체크 |

#### 카테고리 G — 구현체 의존 (변경 가능성: 높음)
| API | 사용 위치 | 비고 |
|-----|----------|------|
| `IrFunctionImpl` | `AspectTransformer`, `InheritableVisitor` | Fake Override 구분용 |
| `IrClassReferenceImpl` | `IrExtension` | KClass 표현식 직접 생성 |
| `allOverridden()` | `InheritableVisitor` | 오버라이드 체인 탐색 |

### 2-2. CompatContext 인터페이스 초안

```kotlin
interface CompatContext {
    // --- 카테고리 A: 플러그인 등록 ---
    fun registerIrExtension(
        configuration: CompilerConfiguration,
        extension: IrGenerationExtension,
    )

    // --- 카테고리 B: IR 선언 빌더 ---
    fun buildClassCompat(
        irFactory: IrFactory,
        builder: IrClassBuilder.() -> Unit,
    ): IrClass

    fun addSimpleDelegatingConstructorCompat(
        irClass: IrClass,
        superConstructor: IrConstructor,
        irBuiltIns: IrBuiltIns,
        isPrimary: Boolean,
    ): IrConstructor

    fun createExpressionBodyCompat(
        irFactory: IrFactory,
        expression: IrExpression,
    ): IrExpressionBody

    // --- 카테고리 D: IR 구현체 체크 ---
    fun isConcreteFunctionImpl(declaration: IrSimpleFunction): Boolean

    // --- 카테고리 G: KClass 표현식 ---
    fun createClassReference(
        startOffset: Int,
        endOffset: Int,
        classType: IrType,
        pluginContext: IrPluginContext,
    ): IrExpression

    // --- Factory ---
    interface Factory {
        val minVersion: String
        fun create(): CompatContext
    }

    companion object {
        fun create(): CompatContext { /* ServiceLoader */ }
    }
}
```

> **원칙**: 현재 API가 버전 간에 **실제로 변경될 때만** 메서드를 추가한다. 아직 변경되지 않은 API를 미리 래핑하지 않는다.

### 2-3. @CompatApi 어노테이션

```kotlin
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
annotation class CompatApi(
    val since: String,                    // 변경이 발생한 Kotlin 버전
    val reason: ChangeReason,             // DELETED, RENAMED, ABI_CHANGE, COMPAT
    val message: String = "",             // 설명
)

enum class ChangeReason { DELETED, RENAMED, ABI_CHANGE, COMPAT }
```

---

## Phase 3: 베이스 구현 (k2220)

### 3-1. k2220/CompatContextImpl.kt

- `CompatContext` 인터페이스의 **모든 메서드를 직접 구현**
- Kotlin 2.2.20의 네이티브 API를 직접 호출
- 가장 낮은 지원 버전이므로 다른 모듈에 의존하지 않음
- `import ... as ...Native` 패턴으로 이름 충돌 방지

### 3-2. ServiceLoader 등록

```
META-INF/services/io.github.molelabs.aspectk.compat.CompatContext$Factory
→ io.github.molelabs.aspectk.compat.k2220.CompatContextImpl$Factory
```

---

## Phase 4: 위임 기반 버전별 구현

### 4-1. 위임 체인

```
k230 --delegates-to--> k2221
k2221 --delegates-to--> k2220
k2220 (베이스, 모든 메서드 직접 구현)
```

### 4-2. 각 모듈의 build.gradle.kts 패턴

```kotlin
plugins { id("org.jetbrains.kotlin.jvm") }

dependencies {
    val kotlinVersion = providers.fileContents(
        layout.projectDirectory.file("version.txt")
    ).asText.map { it.trim() }
    compileOnly(kotlinVersion.map { "org.jetbrains.kotlin:kotlin-compiler:$it" })
    compileOnly(libs.kotlin.stdlib)
    api(project(":aspectk-core-compat"))                  // 베이스 인터페이스
    implementation(project(":aspectk-core-compat:k2220")) // 이전 버전 위임 대상
}
```

### 4-3. 위임 구현 패턴

```kotlin
// k2221/CompatContextImpl.kt
package io.github.molelabs.aspectk.compat.k2221

import io.github.molelabs.aspectk.compat.CompatContext
import io.github.molelabs.aspectk.compat.k2220.CompatContextImpl as DelegateType

class CompatContextImpl : CompatContext by DelegateType() {
    // 2.2.21에서 변경된 API만 override

    class Factory : CompatContext.Factory {
        override val minVersion = "2.2.21"
        override fun create(): CompatContext = CompatContextImpl()
    }
}
```

---

## Phase 5: aspectk-core 통합

### 5-1. aspectk-core의 의존성 변경

```kotlin
// aspectk-core/build.gradle.kts
dependencies {
    implementation(project(":aspectk-core-compat"))
    // 모든 버전별 구현체를 shade (런타임에 ServiceLoader가 탐색)
    runtimeOnly(project(":aspectk-core-compat:k2220"))
    runtimeOnly(project(":aspectk-core-compat:k2221"))
    runtimeOnly(project(":aspectk-core-compat:k230"))
}
```

### 5-2. aspectk-core 코드 마이그레이션

```kotlin
// Before (직접 호출)
IrGenerationExtension.registerExtension(project, extension)

// After (compat 레이어 경유)
val compat = CompatContext.create()
compat.registerIrExtension(configuration, extension)
```

### 5-3. 마이그레이션 우선순위

1. **즉시 래핑** — 이미 버전 간 변경이 확인된 API (예: `IrGenerationExtension.registerExtension`)
2. **예방적 래핑** — 구현체 클래스 직접 사용 (`IrFunctionImpl`, `IrClassReferenceImpl`)
3. **나중에 필요 시 추가** — 안정적인 API (`irCall`, `irGet` 등 빌더 DSL)

---

## Phase 6: Shading 설정

### 6-1. Shadow Plugin 적용

`aspectk-core-compat` 모듈들은 **별도로 publish되지 않는다**. `aspectk-core` JAR에 shade(포함)된다.

```kotlin
// aspectk-core/build.gradle.kts
plugins {
    id("com.gradleup.shadow") // 또는 com.github.johnrengelman.shadow
}

tasks.shadowJar {
    configurations = listOf(project.configurations.runtimeOnly.get())
    relocate("io.github.molelabs.aspectk.compat", "io.github.molelabs.aspectk.core.compat.shaded")
}
```

---

## Phase 7: 테스트

### 7-1. 단위 테스트
- `KotlinToolingVersion` 파싱/비교 테스트
- `CompatContext.create()` ServiceLoader 해석 테스트
- 각 버전별 Factory의 `minVersion` 검증

### 7-2. 통합 테스트
- 기존 `aspectk-core-tests` 모듈의 테스트가 모든 지원 버전에서 통과하는지 확인
- CI에서 각 Kotlin 버전별 매트릭스 테스트

---

## Phase 8: CI/CD

### 8-1. Kotlin 버전 매트릭스
```yaml
strategy:
  matrix:
    kotlin-version: ['2.2.20', '2.2.21', '2.3.0']
```

### 8-2. 새 Kotlin 버전 지원 추가 시 워크플로우
1. `aspectk-core-compat/k<version>/` 디렉토리 생성
2. `version.txt` 작성
3. `CompatContextImpl.kt` 작성 (이전 버전에 위임, 변경점만 override)
4. `META-INF/services/` 등록
5. CI 매트릭스에 버전 추가
6. 기존 테스트 실행하여 검증

---

## 타임라인

| Phase | 설명 | 의존성 |
|-------|------|--------|
| 1 | 모듈 구조 생성 | — |
| 2 | CompatContext 인터페이스 설계 | Phase 1 |
| 3 | k2220 베이스 구현 | Phase 2 |
| 4 | k2221 위임 구현 | Phase 3 |
| 5 | aspectk-core 통합 | Phase 4 |
| 6 | Shading 설정 | Phase 5 |
| 7 | 테스트 | Phase 5 |
| 8 | CI 매트릭스 | Phase 7 |

---

## 주의사항

1. **최소 래핑 원칙**: 변경이 확인된 API만 래핑한다. 불필요한 추상화는 유지보수 부담을 늘린다.
2. **`compileOnly` 의존성**: 각 버전 모듈은 `kotlin-compiler`를 `compileOnly`로 선언한다. 런타임에는 호스트 컴파일러가 제공.
3. **Shade 필수**: compat 모듈은 별도 publish하지 않는다. `aspectk-core` JAR에 포함.
4. **`version.txt` 컨벤션**: 각 버전 모듈의 루트에 타겟 Kotlin 버전을 plain text로 기록.
5. **ServiceLoader 파일명**: `io.github.molelabs.aspectk.compat.CompatContext$Factory` (`$` 주의).
