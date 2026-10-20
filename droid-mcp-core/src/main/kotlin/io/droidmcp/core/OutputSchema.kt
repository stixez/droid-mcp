package io.droidmcp.core

/**
 * Builds the JSON Schema a tool advertises as [McpTool.outputSchema], and checks result maps
 * against it.
 *
 * ```kotlin
 * override val outputSchema = OutputSchema.obj {
 *     integer("count")
 *     array("events") {
 *         integer("id")
 *         string("title", nullable = true)
 *         string("end", nullable = true)
 *     }
 * }
 * ```
 *
 * Every field is listed in `required` unless declared `optional = true` (for keys a tool only
 * sometimes returns). `nullable = true` allows the key to hold JSON `null`. Objects stay open
 * (no `additionalProperties: false`), so adding a result key later is not a breaking change.
 */
object OutputSchema {

    /** An object schema built by [block]. Use as the top-level [McpTool.outputSchema]. */
    fun obj(block: ObjectBuilder.() -> Unit): Map<String, Any> = ObjectBuilder().apply(block).build()

    /** A `string` schema, for [ObjectBuilder.array] items. */
    fun string(): Map<String, Any> = mapOf("type" to "string")

    /** An `integer` schema, for [ObjectBuilder.array] items. */
    fun integer(): Map<String, Any> = mapOf("type" to "integer")

    /** A `number` schema, for [ObjectBuilder.array] items. */
    fun number(): Map<String, Any> = mapOf("type" to "number")

    /** A `boolean` schema, for [ObjectBuilder.array] items. */
    fun boolean(): Map<String, Any> = mapOf("type" to "boolean")

    /** Declares the properties of one object schema. */
    class ObjectBuilder internal constructor() {
        private val properties = linkedMapOf<String, Map<String, Any>>()
        private val required = mutableListOf<String>()

        fun string(name: String, description: String? = null, nullable: Boolean = false, optional: Boolean = false) =
            field(name, typed("string", nullable, description), optional)

        fun integer(name: String, description: String? = null, nullable: Boolean = false, optional: Boolean = false) =
            field(name, typed("integer", nullable, description), optional)

        fun number(name: String, description: String? = null, nullable: Boolean = false, optional: Boolean = false) =
            field(name, typed("number", nullable, description), optional)

        fun boolean(name: String, description: String? = null, nullable: Boolean = false, optional: Boolean = false) =
            field(name, typed("boolean", nullable, description), optional)

        /** A nested object whose own properties are declared by [block]. */
        fun obj(
            name: String,
            description: String? = null,
            nullable: Boolean = false,
            optional: Boolean = false,
            block: ObjectBuilder.() -> Unit,
        ) = field(name, withMeta(ObjectBuilder().apply(block).build(), nullable, description), optional)

        /** A free-form object (arbitrary keys), e.g. a settings dump or extras bundle. */
        fun map(name: String, description: String? = null, nullable: Boolean = false, optional: Boolean = false) =
            field(name, typed("object", nullable, description), optional)

        /** An array of objects, each declared by [block]. */
        fun array(
            name: String,
            description: String? = null,
            nullable: Boolean = false,
            optional: Boolean = false,
            block: ObjectBuilder.() -> Unit,
        ) = arrayOf(name, ObjectBuilder().apply(block).build(), description, nullable, optional)

        /** An array whose items match [items] (e.g. [OutputSchema.string]). */
        fun arrayOf(
            name: String,
            items: Map<String, Any>,
            description: String? = null,
            nullable: Boolean = false,
            optional: Boolean = false,
        ) = field(name, withMeta(mapOf("type" to "array", "items" to items), nullable, description), optional)

        /** A value of any JSON type — for keys whose type legitimately varies (e.g. a setting's value). */
        fun any(name: String, description: String? = null, optional: Boolean = false) =
            field(name, buildMap { description?.let { put("description", it) } }, optional)

        private fun field(name: String, schema: Map<String, Any>, optional: Boolean) {
            require(name !in properties) { "duplicate output field '$name'" }
            properties[name] = schema
            if (!optional) required += name
        }

        internal fun build(): Map<String, Any> = buildMap {
            put("type", "object")
            put("properties", properties.toMap())
            if (required.isNotEmpty()) put("required", required.toList())
        }

        private fun typed(type: String, nullable: Boolean, description: String?): Map<String, Any> =
            withMeta(mapOf("type" to type), nullable, description)

        private fun withMeta(schema: Map<String, Any>, nullable: Boolean, description: String?): Map<String, Any> =
            buildMap {
                putAll(schema)
                if (nullable) put("type", listOf(schema["type"], "null"))
                description?.let { put("description", it) }
            }
    }

    /**
     * Where [value] breaks [schema], as `path: problem` strings; empty when it conforms. Covers
     * the subset [obj] emits (`type`, `properties`, `required`, `items`). Meant for tests that
     * pin a tool's real output to its declared schema.
     */
    fun violations(schema: Map<String, Any>, value: Any?): List<String> =
        mutableListOf<String>().also { check(schema, value, "$", it) }

    private fun check(schema: Map<String, Any>, value: Any?, path: String, out: MutableList<String>) {
        val types = when (val t = schema["type"]) {
            null -> return // unconstrained
            is String -> listOf(t)
            is List<*> -> t.filterIsInstance<String>()
            else -> return
        }
        val actual = jsonTypeOf(value)
        // A JSON integer also satisfies "number".
        val matches = actual in types || (actual == "integer" && "number" in types)
        if (!matches) {
            out += "$path: expected ${types.joinToString("|")}, got $actual"
            return
        }
        when (value) {
            is Map<*, *> -> {
                (schema["required"] as? List<*>)?.forEach { key ->
                    if (key is String && !value.containsKey(key)) out += "$path.$key: missing required key"
                }
                (schema["properties"] as? Map<*, *>)?.forEach { (key, sub) ->
                    @Suppress("UNCHECKED_CAST")
                    if (key is String && value.containsKey(key) && sub is Map<*, *>) {
                        check(sub as Map<String, Any>, value[key], "$path.$key", out)
                    }
                }
            }
            is Iterable<*>, is Array<*> -> {
                @Suppress("UNCHECKED_CAST")
                val items = schema["items"] as? Map<String, Any> ?: return
                val elements = if (value is Array<*>) value.asIterable() else value as Iterable<*>
                elements.forEachIndexed { i, element -> check(items, element, "$path[$i]", out) }
            }
        }
    }

    /** The JSON type [value] serialises to (mirroring the protocol's `toJsonElement`). */
    private fun jsonTypeOf(value: Any?): String = when (value) {
        null -> "null"
        is Boolean -> "boolean"
        is Byte, is Short, is Int, is Long -> "integer"
        is Float, is Double -> if (value.toDouble().let { it == Math.floor(it) && !it.isInfinite() }) "integer" else "number"
        is Number -> "number"
        is Map<*, *> -> "object"
        is Iterable<*>, is Array<*> -> "array"
        else -> "string"
    }
}
