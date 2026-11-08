# Versioning

droid-mcp is before 1.0. Any minor release (0.x) may change public APIs, tool names, parameters or results when that makes the library better. Patch releases (0.x.y) only fix bugs.

Every release's notes on [GitHub Releases](https://github.com/stixez/droid-mcp/releases) start with **Before you upgrade**, which lists anything you need to change. Read it before bumping a minor version, and rebuild instead of swapping in new artifacts.

Releases are JitPack builds of git tags named `vMAJOR.MINOR.PATCH`.

## Change checks in CI

Two checks make API changes deliberate and visible in review. They don't promise that nothing changes.

| Check | Watches | After an intended change |
|---|---|---|
| `apiCheck` | Each library's public JVM API (`<module>/api/<module>.api`) | `./gradlew :<module>:apiDump`, commit the diff |
| `ToolContractTest` | Every tool's name, annotations, parameters and result keys (`droid-mcp-all/api/tool-contract.txt`) | `./gradlew :droid-mcp-all:testDebugUnitTest -PupdateToolContract`, commit the diff |
