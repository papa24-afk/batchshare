plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("net.lingala.zip4j:zip4j:2.11.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.7.3")
}

compose.desktop {
    application {
        mainClass = "com.batchshare.desktop.MainKt"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi, org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe)
            packageName = "BatchShare"
            packageVersion = "1.0.0"
            description = "BatchShare for Windows - Universal Staging Tray & Fast Archive Bundler"
            copyright = "© 2026 BatchShare"
            vendor = "papa24-afk"
            windows {
                iconFile.set(project.file("src/main/resources/icons/icon.ico"))
                menu = true
                shortcut = true
                dirChooser = true
            }
        }
    }
}
