import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.serialization") version "2.4.20"
    id("org.jetbrains.compose") version "1.12.1"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20"
}

kotlin { jvmToolchain(21) }
layout.buildDirectory.set(file("../.work/editor-build/build"))

dependencies {
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material:material:1.12.1")
    implementation("org.jetbrains.compose.components:components-resources:1.12.1")
    implementation("org.jetbrains.compose.ui:ui-tooling-preview:1.12.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    testImplementation(kotlin("test"))
}

compose.resources {
    packageOfResClass = "purrlcd.resources"
    publicResClass = true
}

compose.desktop {
    application {
        mainClass = "purrlcd.MainKt"
        jvmArgs += listOf("-Xms32m", "-Xmx256m", "-XX:+UseSerialGC", "-Dfile.encoding=UTF-8")
        nativeDistributions {
            targetFormats(TargetFormat.Exe)
            packageName = "PurrLCDEditor"
            packageVersion = "0.1.0"
            description = "PurrLCD — редактор экрана"
            vendor = "PurrLCD"
            modules("java.desktop", "java.logging", "jdk.unsupported")
        }
    }
}
