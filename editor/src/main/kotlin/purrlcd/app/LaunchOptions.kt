package purrlcd.app

import java.io.File

data class LaunchOptions(val engine: File?, val data: File, val startEngine: Boolean)

fun parseLaunchOptions(args: Array<String>): LaunchOptions {
    fun arg(name: String) = args.indexOf(name).takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }
    val launch = System.getProperty("jpackage.app-path")?.let(::File)
    val root = launch?.parentFile?.parentFile ?: File(System.getProperty("user.dir"))
    val engine = arg("--engine")?.let(::File) ?: listOf(
        File(root, "engine/PurrLCD.exe"), File(root, "../engine/PurrLCD.exe"), File(root, "PurrLCD.exe")
    ).firstOrNull { it.isFile }
    return LaunchOptions(engine, arg("--data")?.let(::File) ?: File(root, "data"), "--no-engine-start" !in args)
}
