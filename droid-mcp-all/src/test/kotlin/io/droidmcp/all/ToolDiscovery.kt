package io.droidmcp.all

import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.shell.NoOpShellBackend
import io.droidmcp.shell.ShellBackend
import io.droidmcp.shell.ShellPolicy
import io.github.classgraph.ClassGraph
import io.mockk.mockk
import io.mockk.mockkClass
import java.lang.reflect.Constructor

/** Finds every [McpTool] on the test classpath and builds it with relaxed mocks for its dependencies. */
internal object ToolDiscovery {

    /** One instance of every concrete tool class, sorted by tool name. */
    fun discoverTools(): List<McpTool> =
        ClassGraph().acceptPackages("io.droidmcp").enableClassInfo().scan().use { scan ->
            scan.getClassesImplementing(McpTool::class.java.name)
                .filter { !it.isAbstract && !it.isInterface && !it.isAnonymousInnerClass }
                .map { instantiate(it.loadClass()) }
        }.sortedBy { it.name }

    private fun instantiate(type: Class<*>): McpTool {
        type.kotlin.objectInstance?.let { return it as McpTool }
        val ctor = type.declaredConstructors
            .filterNot(Constructor<*>::isSynthetic)
            .maxBy { it.parameterCount }
            .apply { isAccessible = true }
        val args = ctor.parameterTypes.map(::argumentFor).toTypedArray()
        return ctor.newInstance(*args) as McpTool
    }

    private fun argumentFor(type: Class<*>): Any? = when {
        Context::class.java.isAssignableFrom(type) -> mockk<Context>(relaxed = true)
        type == ShellBackend::class.java -> NoOpShellBackend
        type == ShellPolicy::class.java -> ShellPolicy.RECOMMENDED
        type == Boolean::class.javaPrimitiveType -> false
        type == Int::class.javaPrimitiveType -> 0
        type == Long::class.javaPrimitiveType -> 0L
        type == String::class.java -> ""
        type == Function0::class.java -> { -> null }
        else -> mockkClass(type.kotlin, relaxed = true)
    }
}
