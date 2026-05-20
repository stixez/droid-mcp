package io.droidmcp.shell

/**
 * Test double — host registers stubbed `(command, args) → ShellResult`
 * responses, the tool's `execute` walks through normally. Lets us assert
 * argv assembly + output parsing without spinning up a real Shizuku/root
 * binder.
 */
internal class FakeShellBackend(
    override val name: String = "Fake",
    private val available: Boolean = true,
) : ShellBackend {

    val invocations = mutableListOf<Pair<String, List<String>>>()
    private val stubs = mutableMapOf<Pair<String, List<String>>, ShellResult>()
    private var throwOnNext: ShellException? = null

    fun stub(command: String, args: List<String>, result: ShellResult) {
        stubs[command to args] = result
    }

    fun stubAlwaysSucceed(stdout: String = "Success") {
        stubs.clear()
        defaultResult = ShellResult.ofText(0, stdout, "")
    }

    fun stubAlwaysFail(stderr: String = "Failure", exitCode: Int = 1) {
        stubs.clear()
        defaultResult = ShellResult.ofText(exitCode, "", stderr)
    }

    fun throwOnce(exception: ShellException) {
        throwOnNext = exception
    }

    private var defaultResult: ShellResult = ShellResult.ofText(0, "Success", "")

    override fun isAvailable(): Boolean = available

    override suspend fun exec(command: String, args: List<String>): ShellResult {
        invocations += command to args
        throwOnNext?.let {
            throwOnNext = null
            throw it
        }
        return stubs[command to args] ?: defaultResult
    }
}
