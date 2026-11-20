package com.debanshu.shaderlab.shaderx.parameter

/** Value kinds supported by the additive strict parameter contract. */
public enum class ParameterKind { FLOAT, COLOR, BOOLEAN }

/** Finite out-of-range floats are rejected unless clamping is explicitly requested. */
public enum class ParameterRangePolicy { REJECT, CLAMP }

/** Optional presentation hints, independent of a particular editor or serialization format. */
public data class ParameterMetadata(
    public val label: String,
    public val description: String? = null,
    public val order: Int = 0,
    public val semanticRole: String? = null,
) {
    init { require(label.isNotBlank()) { "Parameter label must not be blank" } }
}

/**
 * A typed key owned by one declaration. Keys use reference identity, not string equality.
 * Reuse declarations across instances; a same-name key created elsewhere is not interchangeable.
 * [ownerId] identifies the effect type, not a persisted composite instance or playback session.
 */
public class ParameterKey<T : Any> internal constructor(
    public val ownerId: String,
    public val id: String,
    public val kind: ParameterKind,
) {
    override fun toString(): String = "$ownerId.$id ($kind)"
}

/** A scalar definition. Only [FloatParameterDefinition] has numeric bounds or step hints. */
public sealed class ParameterDefinition<T : Any> protected constructor(
    ownerId: String,
    id: String,
    kind: ParameterKind,
    public val defaultValue: T,
    public val metadata: ParameterMetadata,
) {
    public val key: ParameterKey<T> = ParameterKey(ownerId, id, kind)

    init {
        require(ownerId.isNotBlank()) { "Parameter owner must not be blank" }
        require(id.isNotBlank()) { "Parameter ID for '$ownerId' must not be blank" }
    }

    internal fun decode(value: ParameterValue): T {
        if (value.kind() != key.kind) wrongType(value)
        val scalar: Any = when (value) {
            is ParameterValue.FloatValue -> value.value
            is ParameterValue.ColorValue -> value.color
            is ParameterValue.BooleanValue -> value.enabled
        }
        @Suppress("UNCHECKED_CAST")
        return scalar as T
    }
    internal abstract fun validate(value: ParameterValue, policy: ParameterRangePolicy): ParameterValue
    internal fun wrongType(value: ParameterValue): Nothing =
        throw IllegalArgumentException("Parameter '$key' requires ${key.kind}; received ${value.kind()}")
    internal fun defaultParameterValue(): ParameterValue = when (val value = defaultValue) {
        is Float -> ParameterValue.FloatValue(value)
        is Long -> ParameterValue.ColorValue(value)
        is Boolean -> ParameterValue.BooleanValue(value)
        else -> error("Unsupported definition '$key'")
    }
}

/**
 * A finite float with optional inclusive finite bounds. Step and decimal places are display hints;
 * validation never rounds or quantizes values. Bounds are copied at construction.
 */
public class FloatParameterDefinition(
    ownerId: String,
    id: String,
    defaultValue: Float,
    range: ClosedFloatingPointRange<Float>? = null,
    metadata: ParameterMetadata = ParameterMetadata(id),
    public val unit: String? = null,
    public val step: Float? = null,
    public val decimalPlaces: Int = 1,
) : ParameterDefinition<Float>(ownerId, id, ParameterKind.FLOAT, defaultValue, metadata) {
    public val range: ClosedFloatingPointRange<Float>? = range?.let { it.start..it.endInclusive }

    init {
        val bounds = this.range
        require(bounds == null || (bounds.start.isFinite() && bounds.endInclusive.isFinite() && bounds.start <= bounds.endInclusive)) {
            "Parameter '$key' requires ordered finite bounds"
        }
        require(step == null || (step.isFinite() && step > 0f)) { "Parameter '$key' requires a positive finite step" }
        require(decimalPlaces in 0..9) { "Parameter '$key' decimalPlaces must be in 0..9" }
        validate(ParameterValue.FloatValue(defaultValue), ParameterRangePolicy.REJECT)
    }

    internal override fun validate(value: ParameterValue, policy: ParameterRangePolicy): ParameterValue {
        val number = decode(value)
        require(number.isFinite()) { "Parameter '$key' requires a finite float; received $number" }
        val bounds = range ?: return value
        if (number in bounds) return value
        require(policy == ParameterRangePolicy.CLAMP) { "Parameter '$key' value $number is outside $bounds" }
        return ParameterValue.FloatValue(number.coerceIn(bounds))
    }
}

/** Packed unsigned 32-bit sRGB ARGB, including alpha. No numeric range is exposed. */
public class ColorParameterDefinition(
    ownerId: String,
    id: String,
    defaultValue: Long,
    metadata: ParameterMetadata = ParameterMetadata(id),
) : ParameterDefinition<Long>(ownerId, id, ParameterKind.COLOR, defaultValue, metadata) {
    init { validate(ParameterValue.ColorValue(defaultValue), ParameterRangePolicy.REJECT) }
    internal override fun validate(value: ParameterValue, policy: ParameterRangePolicy): ParameterValue {
        require(decode(value) in 0L..0xFFFFFFFFL) { "Parameter '$key' requires unsigned 32-bit sRGB ARGB" }
        return value
    }
}

/** A strict boolean: the new API never converts floats to toggles. */
public class BooleanParameterDefinition(
    ownerId: String,
    id: String,
    defaultValue: Boolean,
    metadata: ParameterMetadata = ParameterMetadata(id),
) : ParameterDefinition<Boolean>(ownerId, id, ParameterKind.BOOLEAN, defaultValue, metadata) {
    internal override fun validate(value: ParameterValue, policy: ParameterRangePolicy): ParameterValue {
        decode(value)
        return value
    }
}

/**
 * Ordered effect-scoped declarations. Input and returned lists are snapshots. Duplicate IDs,
 * mixed owners and invalid definitions fail immediately with [IllegalArgumentException].
 * No renderer, editor, reflection, or serialization service is needed to hold values.
 */
public class ParameterSchema(public val ownerId: String, definitions: List<ParameterDefinition<*>>) {
    private val declarations: List<ParameterDefinition<*>> = definitions.toList()
    private val byId: Map<String, ParameterDefinition<*>>
    public val definitions: List<ParameterDefinition<*>> get() = declarations.toList()

    init {
        require(ownerId.isNotBlank()) { "Schema owner must not be blank" }
        val indexed = linkedMapOf<String, ParameterDefinition<*>>()
        for (definition in declarations) {
            require(definition.key.ownerId == ownerId) { "Schema '$ownerId' cannot own key '${definition.key}'" }
            require(indexed.put(definition.key.id, definition) == null) { "Schema '$ownerId' has duplicate ID '${definition.key.id}'" }
        }
        byId = indexed
    }

    /** Complete immutable values initialized from the declared defaults. */
    public fun defaults(): ParameterValues = ParameterValues(this, declarations.associate { it.key.id to it.defaultParameterValue() })

    /** Lookup for dynamic editors. Unknown IDs fail rather than returning a placeholder. */
    public fun definition(id: String): ParameterDefinition<*> = byId[id]
        ?: throw IllegalArgumentException("Schema '$ownerId' has no parameter '$id'")

    internal fun <T : Any> definition(key: ParameterKey<T>): ParameterDefinition<T> {
        val declared = byId[key.id]
        require(declared?.key === key) { "Schema '$ownerId' does not own key '$key'; reuse its declared key" }
        @Suppress("UNCHECKED_CAST")
        return declared as ParameterDefinition<T>
    }

    public companion object {
        /**
         * Snapshot legacy descriptors into a new strict schema. Legacy descriptor/update behavior
         * is unchanged. Retain the returned schema to reuse its keys. Invalid legacy definitions
         * are rejected here; color and toggle placeholder ranges are deliberately ignored.
         */
        public fun fromLegacy(ownerId: String, parameters: List<ParameterSpec>): ParameterSchema =
            ParameterSchema(ownerId, parameters.map { spec ->
                val metadata = ParameterMetadata(spec.label)
                when (spec) {
                    is FloatParameter -> FloatParameterDefinition(ownerId, spec.id, spec.defaultValue, spec.range, metadata, decimalPlaces = spec.decimalPlaces)
                    is PixelParameter -> FloatParameterDefinition(ownerId, spec.id, spec.defaultValue, spec.range, metadata, unit = "px")
                    is PercentageParameter -> FloatParameterDefinition(ownerId, spec.id, spec.defaultValue, spec.range, metadata, unit = "%")
                    is ColorParameter -> ColorParameterDefinition(ownerId, spec.id, spec.defaultColor, metadata)
                    is ToggleParameter -> BooleanParameterDefinition(ownerId, spec.id, spec.isEnabledByDefault, metadata)
                }
            })
    }
}

/**
 * An immutable, complete set of schema values. All updates return new values; failures leave the
 * original intact. [snapshot] returns a defensive map of scalar [ParameterValue] objects.
 */
public class ParameterValues internal constructor(
    public val schema: ParameterSchema,
    values: Map<String, ParameterValue>,
) {
    private val values: Map<String, ParameterValue> = values.toMap()

    public operator fun <T : Any> get(key: ParameterKey<T>): T = schema.definition(key).decode(value(key.id))

    /** Dynamic read with the same unknown-key diagnostics as an update. */
    public fun value(id: String): ParameterValue {
        schema.definition(id)
        return values.getValue(id)
    }

    public fun snapshot(): Map<String, ParameterValue> = values.toMap()

    /** Strict typed update; [policy] only affects finite numeric bounds. */
    public fun <T : Any> withValue(key: ParameterKey<T>, value: T, policy: ParameterRangePolicy = ParameterRangePolicy.REJECT): ParameterValues {
        val definition = schema.definition(key)
        val boxed = when (value) {
            is Float -> ParameterValue.FloatValue(value)
            is Long -> ParameterValue.ColorValue(value)
            is Boolean -> ParameterValue.BooleanValue(value)
            else -> throw IllegalArgumentException("Parameter '$key' requires ${key.kind}; received unsupported value")
        }
        return withValue(definition.key.id, boxed, policy)
    }

    /** Dynamic adapter for legacy IDs/editor values. Types are checked without coercion. */
    public fun withValue(id: String, value: ParameterValue, policy: ParameterRangePolicy = ParameterRangePolicy.REJECT): ParameterValues {
        val validated = schema.definition(id).validate(value, policy)
        return ParameterValues(schema, values + (id to validated))
    }

    /** Reset one declared key, preserving all other values. */
    public fun <T : Any> reset(key: ParameterKey<T>): ParameterValues {
        val definition = schema.definition(key)
        return withValue(key, definition.defaultValue)
    }

    /** Reset the complete value set to schema defaults. */
    public fun reset(): ParameterValues = schema.defaults()

    override fun equals(other: Any?): Boolean = other is ParameterValues && schema === other.schema && values == other.values
    override fun hashCode(): Int = 31 * schema.hashCode() + values.hashCode()
}

private fun ParameterValue.kind(): ParameterKind = when (this) {
    is ParameterValue.FloatValue -> ParameterKind.FLOAT
    is ParameterValue.ColorValue -> ParameterKind.COLOR
    is ParameterValue.BooleanValue -> ParameterKind.BOOLEAN
}

// Preserve the legacy presentation subclasses for the two built-in pilots. The strict
// declaration remains the source for metadata, bounds and defaults; no handler cast is public.
internal fun FloatParameterDefinition.legacyFloat(): FloatParameter =
    FloatParameter(key.id, metadata.label, requireNotNull(range), defaultValue, decimalPlaces)
internal fun FloatParameterDefinition.legacyPixels(): PixelParameter =
    PixelParameter(key.id, metadata.label, requireNotNull(range), defaultValue)
internal fun FloatParameterDefinition.legacyPercentage(): PercentageParameter =
    PercentageParameter(key.id, metadata.label, defaultValue)
internal fun ColorParameterDefinition.legacyColor(): ColorParameter = ColorParameter(key.id, metadata.label, defaultValue)
internal fun BooleanParameterDefinition.legacyToggle(): ToggleParameter = ToggleParameter(key.id, metadata.label, defaultValue)
