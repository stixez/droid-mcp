import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.Lint
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
    plugins.withId("com.android.application") {
        extensions.configure<ApplicationExtension> {
            lint { droidMcpLintDefaults(project) }
        }
    }

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
            lint { droidMcpLintDefaults(project) }
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

/**
 * Shared Android lint policy for every module (libraries + sample-app). Lint is a blocking CI
 * gate: any issue not recorded in the module's `lint-baseline.xml` fails the build. After fixing
 * baselined issues (or when a new one is intentionally accepted), regenerate with
 * `./gradlew updateLintBaseline` and commit the diff.
 */
fun Lint.droidMcpLintDefaults(project: Project) {
    baseline = project.file("lint-baseline.xml")
    abortOnError = true
    warningsAsErrors = false
    // Each module lints only its own sources; sample-app must not re-lint all 54 libraries.
    checkDependencies = false
    // HTML and XML reports are always generated on AGP 9.
    // Version-freshness checks depend on what upstream has published *today*, so they'd break a
    // green build whenever a new release ships. Dependabot (.github/dependabot.yml) owns this.
    disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "OldTargetApi")
    if (project.path == ":droid-mcp-core") {
        // Lint's androidx ExperimentalDetector crashes on McpProtocolImpl.kt under K2 UAST
        // ("Unexpected owner function: null", AGP 9.4.1 / Kotlin 2.4). Core uses no androidx
        // @RequiresOptIn APIs (the Kotlin compiler still enforces kotlin.RequiresOptIn), so the
        // two lint-only checks are safe to drop here. Re-enable when lint is fixed.
        disable += setOf("UnsafeOptInUsageError", "UnsafeOptInUsageWarning")
    }
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
