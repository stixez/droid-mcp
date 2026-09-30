import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.api.variant.ScopedArtifacts
import javax.inject.Inject

plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.dokka)
}

subprojects {
    plugins.withId("com.android.library") {
        apply(plugin = "maven-publish")
        apply(plugin = "org.jetbrains.dokka")

        // Android documentation plugin enriches Dokka output for Android symbols.
        dependencies {
            add("dokkaPlugin", rootProject.libs.dokka.android.plugin)
        }

        extensions.configure<LibraryExtension> {
            publishing {
                singleVariant("release") {
                    withSourcesJar()
                }
            }
        }

        // ---- Public API guard: apiDump / apiCheck (see docs/VERSIONING.md) ----
        val apiFile = layout.projectDirectory.file("api/${project.name}.api")
        val builtApi = layout.buildDirectory.file("api/${project.name}.api")
        val abiDumpTool = configurations.create("abiDumpTool") {
            isCanBeConsumed = false
            isCanBeResolved = true
        }
        dependencies { add(abiDumpTool.name, project(":abi-dump")) }
        val dumpTask = tasks.register<ApiDumpTask>("apiBuild") {
            toolClasspath.from(abiDumpTool)
            output.set(builtApi)
        }
        extensions.configure<LibraryAndroidComponentsExtension> {
            onVariants(selector().withBuildType("release")) { variant ->
                variant.artifacts.forScope(ScopedArtifacts.Scope.PROJECT)
                    .use(dumpTask)
                    .toGet(ScopedArtifact.CLASSES, ApiDumpTask::jars, ApiDumpTask::dirs)
            }
        }
        tasks.register<Copy>("apiDump") {
            group = "verification"
            description = "Writes the public API to api/${project.name}.api (commit the result)."
            from(dumpTask.flatMap { it.output })
            into(layout.projectDirectory.dir("api"))
        }
        val apiCheck = tasks.register("apiCheck") {
            group = "verification"
            description = "Fails if the public API differs from api/${project.name}.api."
            val expected = apiFile.asFile
            val actual = dumpTask.flatMap { it.output }
            inputs.file(actual)
            doLast {
                val built = actual.get().asFile.readText()
                check(expected.exists()) {
                    "No API baseline at ${expected.relativeTo(rootDir)}. Run ./gradlew :${project.name}:apiDump and commit it."
                }
                check(expected.readText() == built) {
                    "Public API of ${project.name} changed. If intended, run ./gradlew :${project.name}:apiDump, " +
                        "review the diff of ${expected.relativeTo(rootDir)} and commit it (see docs/VERSIONING.md)."
                }
            }
        }
        tasks.named("check") { dependsOn(apiCheck) }

        // afterEvaluate so the release component is registered by AGP before we reference it
        afterEvaluate {
            extensions.configure<PublishingExtension> {
                publications {
                    create<MavenPublication>("release") {
                        from(components["release"])
                        groupId = "io.droidmcp"
                        artifactId = project.name
                        version = providers.gradleProperty("VERSION_NAME").get()
                    }
                }
            }
        }
    }
}

// ---- Dokka: aggregate API docs for every published library module ----
// Excludes :sample-app (the demo application) and :droid-mcp-all (a sourceless
// convenience aggregator). Output: build/dokka/html. Generate with:
//   ./gradlew :dokkaGenerate
dokka {
    moduleName.set("droid-mcp")
}

dependencies {
    subprojects
        .filter { it.name != "sample-app" && it.name != "droid-mcp-all" && it.name != "abi-dump" }
        .forEach { dokka(it) }
}

/** Runs tools/abi-dump over a variant's compiled classes to produce a BCV-format `.api` file. */
abstract class ApiDumpTask : DefaultTask() {
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val jars: ListProperty<RegularFile>

    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dirs: ListProperty<Directory>

    @get:Classpath
    abstract val toolClasspath: ConfigurableFileCollection

    @get:OutputFile
    abstract val output: RegularFileProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun dump() {
        val roots = jars.get().map { it.asFile } + dirs.get().map { it.asFile }
        execOperations.javaexec {
            classpath = toolClasspath
            mainClass.set("io.droidmcp.tools.abi.AbiDumpKt")
            args(listOf(output.get().asFile.absolutePath) + roots.map { it.absolutePath })
        }
    }
}
