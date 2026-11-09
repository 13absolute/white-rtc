import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.koin.compiler)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm()

    sourceSets {
        jvmMain.dependencies {
            implementation(project(":app:shared"))
            implementation(compose.desktop.currentOs)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.koin.core)
            implementation(libs.koin.annotations)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.logback)
        }
    }
}

koinCompiler {
    userLogs = true
    strictSafety = true
}

val desktopVersionName = "1.0.0"

compose.desktop {
    application {
        mainClass = "com.whitertc.desktop.MainKt"
        nativeDistributions {
            targetFormats(
                TargetFormat.Deb,
                TargetFormat.Msi,
                TargetFormat.Exe
            )
            modules(
                "java.base",
                "java.desktop",
                "java.logging",
                "java.naming",
                "java.sql",
                "java.xml",
                "java.management",
                "java.instrument",
                "jdk.unsupported",
                "jdk.unsupported.desktop",
                "jdk.crypto.ec"
            )
            packageName = "WhiteProxy"
            packageVersion = desktopVersionName
            description = "WhiteRTC Multipath VPN Client"
            copyright = "© 2026 WhiteRTC"
            vendor = "WhiteRTC"

            linux {
                debPackageVersion = desktopVersionName
                menuGroup = "Network"
                appCategory = "Network"
                iconFile.set(project.file("../app/shared/src/commonMain/composeResources/drawable/app_icon.png"))
            }

            windows {
                menuGroup = "WhiteRTC"
                upgradeUuid = "6c810d29-a1b2-4d53-93fb-98cb9b6cb592"
                iconFile.set(project.file("src/jvmMain/resources/icons/app_icon.ico"))
            }
        }
    }
}

tasks.matching { it.name == "run" || it.name == "runDistributable" }.configureEach {
    dependsOn(":daemon:installDist")
}
