package io.droidmcp.tools.abi

import kotlinx.validation.api.dump
import kotlinx.validation.api.filterOutNonPublic
import kotlinx.validation.api.loadApiFromJvmClasses
import java.io.File
import java.util.jar.JarFile

/**
 * `AbiDump <output.api> [classes dir or jar]...` — writes the public API of the given
 * compiled classes in binary-compatibility-validator's `.api` format (empty for no classes).
 */
fun main(args: Array<String>) {
    require(args.isNotEmpty()) { "usage: AbiDump <output.api> [classes dir or jar]..." }
    val output = File(args[0])
    val roots = args.drop(1).map(::File).filter { it.exists() }

    val jars = roots.filter { it.isFile }.map(::JarFile)
    try {
        val classStreams = sequence {
            roots.filter { it.isDirectory }.forEach { dir ->
                dir.walkTopDown()
                    .filter { it.isFile && it.extension == "class" && it.name != "module-info.class" }
                    .sortedBy { it.relativeTo(dir).path }
                    .forEach { yield(it.inputStream()) }
            }
            jars.forEach { jar ->
                jar.entries().asSequence()
                    .filter { it.name.endsWith(".class") && !it.name.startsWith("META-INF/") }
                    .sortedBy { it.name }
                    .forEach { yield(jar.getInputStream(it)) }
            }
        }
        val api = classStreams.loadApiFromJvmClasses().filterOutNonPublic()
        output.parentFile.mkdirs()
        output.bufferedWriter().use { api.dump(it) }
    } finally {
        jars.forEach(JarFile::close)
    }
}
