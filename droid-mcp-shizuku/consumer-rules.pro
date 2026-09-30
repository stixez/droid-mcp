# Consumer ProGuard/R8 rules for droid-mcp-shizuku, applied to the host app's
# minified build.
#
# ShizukuShellBackend reflectively calls the private static
# `Shizuku.newProcess(String[], String[], String)` (Shizuku-API v13). Without
# these rules R8 may strip or rename it and every shell tool fails with
# shell_spawn_failed in release builds.
-keepclassmembers class rikka.shizuku.Shizuku {
    private static *** newProcess(...);
}
-keep class rikka.shizuku.ShizukuRemoteProcess { *; }
