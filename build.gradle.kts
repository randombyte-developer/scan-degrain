import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.compose") version "2.4.21"
    kotlin("plugin.serialization") version "2.4.21"
    id("org.jetbrains.compose") version "1.12.1"
}

val opencv = "4.14.0-1.5.14"
val javacpp = "1.5.14"
val openblas = "0.3.34-1.5.14"
val platform = "linux-x86_64"

dependencies {
    implementation(compose.desktop.linux_x64)
    implementation(compose.material3)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.apache.commons:commons-imaging:1.0.0-alpha6")
    implementation("org.bytedeco:opencv:$opencv")
    runtimeOnly("org.bytedeco:opencv:$opencv:$platform")
    runtimeOnly("org.bytedeco:openblas:$openblas:$platform")
    runtimeOnly("org.bytedeco:javacpp:$javacpp:$platform")
}

kotlin {
    jvmToolchain(21)
}

compose.desktop {
    application {
        mainClass = "MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Deb)
            packageName = "negative-tool"
            packageVersion = "1.0.0"
            modules("java.instrument", "java.management", "jdk.unsupported")
        }
    }
}
