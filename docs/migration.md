---
title: Migrate ShaderX effects and assess release readiness
description: Upgrade to ShaderX 0.2.0 definitions, strict schemas, playback and saved projects while reviewing source migration, JVM linkage, Android requirements and platform limits.
---

# Migration and release readiness

This guide covers upgrades to **ShaderX 0.2.0**. See [publication status](index.md#releases-and-development-docs) and the [stable release checklist](release-plan.md). Remote `main`, this checkout, staged validation artifacts and the deployed playground are separate states.

SX15 deliberately retains **0.1.3** as its frozen compatibility baseline, using the actual
[0.1.3 POM](https://repo.maven.apache.org/maven2/io/github/debanshu777/shaderx/0.1.3/shaderx-0.1.3.pom).
The release preparation separately froze and ran actual **0.1.4** consumers against the 0.2.0
JVM jar. Its descriptor audit reviewed 108 public/protected classes and 963 members with zero
unresolved supported consumer descriptors. The older fixture also passed again unchanged.
The [release audit](https://github.com/Debanshu777/ShaderX/blob/main/validation/evidence/release-0.2.0-jvm-compatibility.json) records exact inputs, commands and artifact hashes. These are bounded JVM linkage checks, not Android/Klib binary or physical-GPU guarantees.

Use a built-in with `Modifier.shaderEffect(GrayscaleEffect())` without a registry, editor or
serialization dependency. Adopt the new APIs where their explicit validation, playback or input
contracts help; existing manual effects remain supported.

## Compatibility window and kinds of change

The original reviewed window is **published 0.1.3 → the upgraded library**, not a promise about
all older or future versions. The separate 0.1.4 audit covers the latest-stable JVM baseline.
Existing manual `RuntimeShaderEffect`/`AnimatedShaderEffect` interfaces,
legacy constructor/copy validation and legacy parameter update semantics remain supported.
The internal abstract effect bases still cannot be implemented from external modules.
No deprecation/removal deadline has been selected. Keep the original 0.1.3 fixture and latest-stable 0.1.4 audit distinct.

| Change class | Migration decision |
| --- | --- |
| Additive source APIs | Definitions, schemas, playback, capability reports and background/pipeline/input/export contracts are additions in 0.2.0. Recompile adopting consumers against that coordinate or its uniquely versioned local validation artifact. |
| Source exhaustiveness | Review sealed `Uniform` and enum `when` handlers below. New values need explicit handling; new input kinds are not silently downcast to old kinds. |
| Binary linkage | SX15 compiles and freezes real 0.1.3 JVM consumers and third-party factories, then runs unchanged bytecode with current artifacts only. The separate 0.1.4 release audit also runs frozen published session/texture/tile-mode calls. Original before/after failures and class-loading proof belong to the [frozen JVM compatibility record](https://github.com/Debanshu777/ShaderX/blob/main/validation/evidence/sx15-release-and-migration.json); current release commands are in the [contributor guide](contributing.md#frozen-jvm-migration-check). Android DEX/AAR and Kotlin/Native/Wasm Klib binary compatibility are separate unproved gates. |
| Wave behavior versus linkage | Wave is now a regular class rather than a Kotlin data class; data-class reflection metadata is not preserved. Copy/components/concrete-return methods are retained for linkage and `copy` preserves every supplied property. Configuration-only equality/hash excludes time; the actual 0.1.3 JVM bytecode already excluded time too. Use playback/seek for timeline changes rather than relying on time-only equality to invalidate rendering. |
| Behavioral additions | New strict APIs reject invalid values and foreign keys. Existing manual updates retain their semantics. `SUPPORTED` describes a route, not successful shader compilation or allocation. |
| Deliberate bounded source restriction | Defined content adapters require literal top-level `uniform shader content;`. Comments/whitespace are accepted; macros/preprocessor, quoted tokens, comma/array declarations are conservatively rejected. Source-free backgrounds/nodes and legacy manual sources retain their routes. |
| Toolchain/adoption | Older published artifacts require minSdk 33/Java 21 output. The 0.2.0 AAR offers native blur at 31, runtime shaders at 33, Java 17 output/minCompileSdk 35; contributors still use JDK 21. Independent minimum AGP proof remains open. |
| Saved sample formats | Sample-local version checks are explicit; source/form drafts are not validation certificates or serialized native state. See saved-project rules below. |

The published-jar audit found avoidable descriptor regressions in the evolving checkout:
`WaveEffect`'s concrete-return `withTime`/`withTypedParameter` and data-style copy/components/string
surface, plus the published Compose `shaderEffect`/`shaderEffectWithResult` overloads after adding
`frameDataKey`. SX15 restores those old entry points. Visible compatibility overloads retain old JVM calls and positional/trailing/named callbacks.
The live-input overload requires an explicit `frameDataKey`, preventing typed callbacks from
silently binding to `Any?`. Ordinary quickstarts need no deprecated call. Inherited `equals`/`hashCode` resolve the old JVM
descriptors; this linkage result alone is not a behavioral proof (see the Wave row above). Compiler-generated `access$…` helpers and internal `LruCache` machinery are
inventoried separately from supported public Kotlin contracts.

`ShaderFactory.createAnimatedSession` has a default unsupported result using an existing
error, so an old third-party factory can decline session creation instead of failing through
new abstract dispatch. Built-in native overrides remain. This is a capability fallback, not a
retrofit animated renderer for custom factories. The unchanged-bytecode run passes; its exact compiler/dependency combination and limits
are recorded in the SX15 evidence rather than inferred from recompiling the source.

Passing an old scalar or result to an already compiled handler differs from passing a **new**
`Uniform` subtype or enum value to its old exhaustive `when`: the latter can throw
`NoWhenBranchMatchedException`. A preserved method descriptor does not establish semantic
compatibility for newly introduced values. Recompile and extend those handlers deliberately.

### Exhaustive-handler source inventory

Compared with 0.1.3, `Uniform` adds `TextureUniform`, `DataUniform` and `NestedUniform`;
`TextureUniform` is already present in 0.1.4. Retain existing `FloatUniform`, `IntUniform`,
`ColorUniform` and `MatrixUniform` handling. The capability and declaration enums below are
part of the new authoring/rendering contracts;
if adopting an intermediate checkout, include the later members too:

| Enum | Current members to review |
| --- | --- |
| `ShaderOperation` | `CONTENT_RENDER`, `ANIMATED_SESSION`, `IMAGE_EXPORT`, `BACKGROUND_RENDER` |
| `ShaderInput` | `IMAGE_TEXTURE`, `RAW_DATA_TEXTURE`, `NESTED_SHADER` |
| `ShaderSupportStatus` | `SUPPORTED`, `UNSUPPORTED`, `UNKNOWN` |
| `ShaderSupportReason` | `CAPABILITIES_NOT_PROVIDED`, `ANDROID_API_33_REQUIRED`, `ANDROID_EXPORT_UNAVAILABLE`, `COMPOSITE_EXPORT_UNAVAILABLE`, `CHAINING_UNAVAILABLE`, `COMPOSITE_ANIMATION_UNAVAILABLE`, `ANIMATED_EFFECT_REQUIRED`, `NATIVE_EFFECT_UNAVAILABLE`, `BACKGROUND_UNAVAILABLE`, `RAW_DATA_UNAVAILABLE`, `NESTED_SHADER_UNAVAILABLE`, `EMPTY_COMPOSITE`, `ANDROID_API_31_REQUIRED` |
| `ShaderBindingKind` | `CONSTANT`, `RESOLUTION`, `TIME`, `FLOAT_PARAMETER`, `COLOR_PARAMETER`, `BOOLEAN_PARAMETER`, `IMAGE`, `FRAME_FLOAT`, `FRAME_IMAGE`, `DATA`, `NESTED`, `FRAME_DATA`, `FRAME_NESTED` |
| `ParameterKind` / `ParameterRangePolicy` | `FLOAT`, `COLOR`, `BOOLEAN` / `REJECT`, `CLAMP` |
| `DataSampling` / `ShaderTileMode` | `NEAREST`, `LINEAR` / `CLAMP`, `REPEAT`, `MIRROR` |

`ShaderError` retains its existing variants. Capability failures reuse those errors rather than
adding another sealed subtype. Renderers should fail explicitly for unsupported input kinds;
editor code should display the support message rather than treating enum identifiers as prose.

## Choose manual, minimal or editable authoring

Manual interfaces remain useful when you control unusual uniform/update behavior. This complete
parameter-free identity effect uses the retained manual runtime contract:

```kotlin
import com.debanshu.shaderlab.shaderx.effect.RuntimeShaderEffect
import com.debanshu.shaderlab.shaderx.parameter.ParameterSpec
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import com.debanshu.shaderlab.shaderx.uniform.Uniform

object ManualIdentity : RuntimeShaderEffect {
    override val id: String = "example_identity"
    override val displayName: String = "Identity"
    override val parameters: List<ParameterSpec> = emptyList()
    override val shaderSource: String = """
        uniform shader content;
        half4 main(float2 p) { return content.eval(p); }
    """.trimIndent()
    override fun buildUniforms(width: Float, height: Float): List<Uniform> = emptyList()
    override fun withTypedParameter(parameterId: String, value: ParameterValue): RuntimeShaderEffect = this
    override fun getParameterValue(parameterId: String): Float = 0f
    override fun getTypedParameterValue(parameterId: String): ParameterValue? = null
}
```

The equivalent minimal declaration needs only identity and a named `program`:

```kotlin
import com.debanshu.shaderlab.shaderx.authoring.ShaderDefinition
import com.debanshu.shaderlab.shaderx.authoring.ShaderProgram

val identityDefinition = ShaderDefinition(
    id = "example_identity",
    program = ShaderProgram("""
        uniform shader content;
        half4 main(float2 p) { return content.eval(p); }
    """.trimIndent()),
)
val identity = identityDefinition.effect()
```

`displayName` defaults to `id`. Retain the definition/program rather than reconstructing them
every frame: identity is referential, and a new declaration requests configuration replacement.
For constants, use `ShaderBinding.float("amount", 0.5f)`; for editable values, declare a schema
and retain its exact key. This complete editable version exposes a strict opacity control:

```kotlin
import com.debanshu.shaderlab.shaderx.authoring.DefinedShaderEffect
import com.debanshu.shaderlab.shaderx.authoring.ShaderBinding
import com.debanshu.shaderlab.shaderx.authoring.ShaderDefinition
import com.debanshu.shaderlab.shaderx.authoring.ShaderProgram
import com.debanshu.shaderlab.shaderx.parameter.FloatParameterDefinition
import com.debanshu.shaderlab.shaderx.parameter.ParameterRangePolicy
import com.debanshu.shaderlab.shaderx.parameter.ParameterSchema

object EditableOpacity {
    val amount = FloatParameterDefinition("example_opacity", "amount", 1f, 0f..1f)
    val definition = ShaderDefinition(
        id = "example_opacity",
        program = ShaderProgram(
            source = """
                uniform shader content;
                uniform float opacity;
                half4 main(float2 p) { return content.eval(p) * opacity; }
            """.trimIndent(),
            bindings = listOf(ShaderBinding.float("opacity", amount.key)),
        ),
        schema = ParameterSchema("example_opacity", listOf(amount)),
    )
    fun effect(value: Float = 1f): DefinedShaderEffect = definition.effect().withValue(amount.key, value)
}

val halfOpacity = EditableOpacity.effect(0.5f)
val clamped = halfOpacity.withValue(EditableOpacity.amount.key, 2f, ParameterRangePolicy.CLAMP)
val opacity: Float = clamped[EditableOpacity.amount.key] // 1f
```

The default policy rejects out-of-range, wrong-type, non-finite and foreign-key updates before
rendering. Explicit `CLAMP` affects finite numeric ranges only; it never converts NaN, colors or
Booleans. Step/decimal metadata does not quantize stored values. Packed colors are unsigned
32-bit sRGB ARGB; structural colors replace session state and Android static declarations may
compile a fresh shader for safe ownership. Do not infer zero compilation or allocation.

### Add strict boundaries around a legacy effect

You can adopt validation without replacing existing effects or changing their constructors:

```kotlin
import com.debanshu.shaderlab.shaderx.effect.impl.GrayscaleEffect
import com.debanshu.shaderlab.shaderx.parameter.ParameterSchema
import com.debanshu.shaderlab.shaderx.parameter.ParameterValue
import com.debanshu.shaderlab.shaderx.parameter.applyTo
import com.debanshu.shaderlab.shaderx.parameter.valuesFrom

val original = GrayscaleEffect(intensity = 0.5f)
val schema = ParameterSchema.fromLegacy(original.id, original.parameters)
val values = schema.valuesFrom(original)
val updated = values.withValue("intensity", ParameterValue.FloatValue(0.75f)).applyTo(original)
```

This adapter validates a complete snapshot and applies it through the existing public immutable
update contract. Invalid legacy constructor/copy values are rejected **at this new boundary**;
legacy coercion/unknown-ID behavior is not rewritten. Keep the schema object to reuse its keys.
Full manual typed-update code is in the [custom effect guide](custom-effects.md#low-level-custom-effect).

## Separate playback, input revisions and native ownership

The existing animated `shaderEffect(effect)` path retains its old timing and live-input polling.
Use the additive controlled path when pause/resume, seek and phase through size/configuration
replacement matter. This component takes a stable live provider and explicit revision:

```kotlin
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.debanshu.shaderlab.shaderx.authoring.ShaderBinding
import com.debanshu.shaderlab.shaderx.authoring.ShaderDefinition
import com.debanshu.shaderlab.shaderx.authoring.ShaderFrameProvider
import com.debanshu.shaderlab.shaderx.authoring.ShaderProgram
import com.debanshu.shaderlab.shaderx.compose.controlledShaderEffect
import com.debanshu.shaderlab.shaderx.compose.rememberShaderPlayback

private val liveDefinition = ShaderDefinition(
    "example_live",
    program = ShaderProgram(
        """
            uniform shader content;
            uniform float time;
            uniform float amount;
            half4 main(float2 p) {
                return content.eval(p + float2(sin(time) * amount, 0.0));
            }
        """.trimIndent(),
        listOf(ShaderBinding.time(), ShaderBinding.frameFloat("amount")),
    ),
)

@Composable
fun LiveLayer(provider: ShaderFrameProvider, revision: Long, active: Boolean, reducedMotion: Boolean) {
    val playback = rememberShaderPlayback(active = active, reducedMotion = reducedMotion)
    val effect = remember(provider) { liveDefinition.animatedEffect(frameProvider = provider) }
    Box(Modifier.size(120.dp).controlledShaderEffect(effect, playback, inputRevision = revision)) {
        // Draw the content to filter here.
    }
    // UI event handlers can set playback.isPlaying, playback.speed or playback.seekTo(2.0).
}
```

Retain the provider; publish a complete immutable snapshot, then increment `revision`. For this
example it returns exactly `listOf(FloatUniform("amount", finiteAmount))`, importing
`com.debanshu.shaderlab.shaderx.uniform.FloatUniform`. A paused unchanged revision requests no
continuous refresh. `frameImage`, `frameData` and `frameNested` require their exact declared
`TextureUniform`, `DataUniform` and `NestedUniform` kinds; colors remain structural. The host
supplies visibility/lifecycle and reduced-motion policy. Share playback to synchronize consumers,
not mutable native uniforms; native replacement does not reset its phase.

For a manual session, always apply the **returned refresh result**:

```kotlin
import androidx.compose.ui.graphics.RenderEffect
import com.debanshu.shaderlab.shaderx.effect.AnimatedShaderEffect
import com.debanshu.shaderlab.shaderx.factory.ShaderSession
import com.debanshu.shaderlab.shaderx.result.ShaderResult

fun refreshFilter(session: ShaderSession, effect: AnimatedShaderEffect): ShaderResult<RenderEffect> =
    session.refresh(effect.withTime(2f), 120f, 120f)
```

`currentRenderEffect` on legacy native sessions exposes their initial filter; a defined session
exposes its latest successful snapshot. The return from `refresh()` is the portable update
contract. Close sessions when done; further refresh fails and session property access throws. A previously
returned immutable `RenderEffect` retains its native inputs independently; this does not permit
using the closed session or promise collection timing. Compose modifiers close their sessions, while
manually created factories belong to their caller. See [ownership details](compose.md).

## Query the requested operation and choose fallback

A legacy third-party factory without `ShaderCapabilityProvider` reports `UNKNOWN`; do not turn
that into `false` or skip the attempt. Native compilation or allocation can fail even after
`SUPPORTED`. This complete helper returns both support and observable static recovery:

```kotlin
import com.debanshu.shaderlab.shaderx.effect.ShaderEffect
import com.debanshu.shaderlab.shaderx.factory.ShaderFactory
import com.debanshu.shaderlab.shaderx.factory.ShaderFallbackPolicy
import com.debanshu.shaderlab.shaderx.factory.ShaderOperation
import com.debanshu.shaderlab.shaderx.factory.ShaderRenderOutcome
import com.debanshu.shaderlab.shaderx.factory.ShaderSupport
import com.debanshu.shaderlab.shaderx.factory.createRenderEffectWithFallback
import com.debanshu.shaderlab.shaderx.factory.querySupport

fun staticSnapshot(factory: ShaderFactory, effect: ShaderEffect): Pair<ShaderSupport, ShaderRenderOutcome> {
    val support = factory.querySupport(ShaderOperation.CONTENT_RENDER, effect)
    val outcome = factory.createRenderEffectWithFallback(
        effect, 120f, 120f, ShaderFallbackPolicy.PreserveContent,
    )
    return support to outcome
}
```

Inspect `outcome.fallbackUsed`/`cause`; successful null means preserve the whole original layer.
`Substitute(effect)` tries one complete replacement; required chain children are never silently
removed. This helper is a static snapshot, not an animation driver. Query export and backgrounds
through their dedicated contracts. Public Android export remains unsupported. See the
[platform matrix](platforms.md) and [bounded Skia export guide](image-processing.md).

## Restore saved projects and rebind application inputs

These codecs belong to **ShaderLab**, not a public core serialization protocol. Retain an
original file before migrating an application's schema; decode into a candidate and replace
state only after all checks succeed.

| Sample format | Accepted migration and rejection behavior |
| --- | --- |
| `ShaderLab-Preset 1` | Requires the registered definition ID, matching schema version and exact typed key set. Unknown versions/definitions, schema drift and invalid values fail with reset/migrate/register guidance; no automatic guessing or partial application. |
| `ShaderLab-Pipeline 1` | Restores ordered children with stable keys only after every child's version/parameters validate. Unknown versions and invalid children reject the whole replacement. |
| `ShaderLab-Workbench 3` | Writes source/form/current values, output kind and input descriptions. Reads versions 1/2/3: v1 defaults output to CONTENT; v1/v2 have no additional input requirements. Unknown versions, malformed/oversized files and unknown kinds reject. Restored drafts require Run again and have no last-good certification. |

Rebinding is deliberate: no saved file or generated Kotlin literal embeds application images,
raw byte payloads, callbacks, native sessions or secrets. Generated image/frame/data/nested
functions accept caller inputs. Recreate borrowed `ImageBitmap` ownership through the host,
construct `Rgba8Data` from application bytes, construct source-free `ShaderNode` snapshots with
explicit child time, and pass retained frame providers. Publish a new revision after complete
live-input replacement. See the complete [caller-input examples](custom-effects.md#numerical-data-and-nested-inputs-in-development)
and [sample save/export workflow](samples.md).

Workbench **CONTENT** rendering still uses its legacy/UI-factory route and is disabled on Android;
**BACKGROUND** rendering has an independent source-free route. Successful Wasm/Skia preview is
not AGSL certification. Missing authored-content input was guarded after source review of an
AOSP native error path; it was not intentionally reproduced as a crash. The separately observed
SX10 unknown-alias native abort is distinct evidence.

## Release readiness and remaining gates

The [platform guide](platforms.md#validation-coverage) describes supported operations and validation
limits. The [integration validation record](https://github.com/Debanshu777/ShaderX/blob/main/validation/evidence/sx12-final-validation.json)
records core/common metadata, current/staged/legacy generated consumers, real software-emulator
API 31/API 36 HWUI and Compose window checks. The [frozen JVM compatibility record](https://github.com/Debanshu777/ShaderX/blob/main/validation/evidence/sx15-release-and-migration.json)
records final JVM binary compatibility and documentation checks.

Source compilation, unchanged JVM binary execution, CPU raster pixels, simulator execution and
software-emulator native windows are separate evidence. Physical Android/iOS GPU output,
long-running native memory/concurrency, frame budgets, independent minimum AGP and Android/Klib
binary ABI are still external gates. Local workflow validation is not a remote GitHub Actions
run. No commit/push/merge, hosted preview deployment or Central publication follows from these
local results.

The stable release checklist covers the final source/artifact audit, verification of every
published target and clean consumer resolution. Installation, KDoc, changelog and `llms.txt`
are prepared together for 0.2.0; publication status is tracked centrally. Follow the
[maintainer release procedure](maintaining.md#release-workflow) and keep unavailable hardware,
performance and binary-compatibility evidence separate from completed checks.
