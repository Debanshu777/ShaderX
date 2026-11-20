package com.debanshu.shaderlab.shaderx.parameter

import com.debanshu.shaderlab.shaderx.effect.ShaderEffect

/**
 * Strictly snapshot this schema's current effect values. Legacy constructors/copy may contain
 * invalid values; this adapter rejects them without changing the effect or its legacy APIs.
 */
public fun ParameterSchema.valuesFrom(effect: ShaderEffect): ParameterValues {
    requireOwner(effect)
    var result = defaults()
    for (definition in definitions) {
        val id = definition.key.id
        val value = effect.getTypedParameterValue(id)
            ?: throw IllegalArgumentException("Effect '${effect.id}' has no readable parameter '$id'")
        result = result.withValue(id, value)
    }
    return result
}

/**
 * Apply a complete strict snapshot through the public legacy update interface, returning a new
 * effect. Both owner and target descriptors are validated before dispatch. No abstract interface
 * requirement is added. Custom implementations must honor their declared immutable update contract.
 * Time, playback and other non-parameter state are left to the target's existing copy behavior.
 */
public fun ParameterValues.applyTo(effect: ShaderEffect): ShaderEffect {
    schema.requireOwner(effect)
    val target = ParameterSchema.fromLegacy(effect.id, effect.parameters)
    val snapshot = snapshot()
    for ((id, value) in snapshot) target.definition(id).validate(value, ParameterRangePolicy.REJECT)
    var updated = effect
    for ((id, value) in snapshot) updated = updated.withTypedParameter(id, value)
    return updated
}

private fun ParameterSchema.requireOwner(effect: ShaderEffect) {
    require(ownerId == effect.id) { "Schema '$ownerId' cannot read or update effect '${effect.id}'" }
}
