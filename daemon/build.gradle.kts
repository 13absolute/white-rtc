plugins {
    id("application")
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.koin.compiler)
    alias(libs.plugins.kotlin.serialization)
    id("org.graalvm.buildtools.native")
}

group = "com.whitertc"
version = "1.0.0"

application {
    mainClass.set("com.whitertc.daemon.MainKt")
    applicationDefaultJvmArgs = listOf(
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
        "--add-opens=java.base/java.net=ALL-UNNAMED",
        "--enable-native-access=ALL-UNNAMED"
    )
}

kotlin {
    jvmToolchain(21)
}

graalvmNative {
    binaries {
        named("main") {
            imageName.set("white-proxy-daemon")
            mainClass.set("com.whitertc.daemon.MainKt")
            buildArgs.addAll(
                "--no-fallback",
                "-O3",
                "-march=compatibility",
                "-H:+ReportExceptionStackTraces",
                "--enable-native-access=ALL-UNNAMED",
                "-H:+UnlockExperimentalVMOptions",
                "--initialize-at-build-time=kotlin.DeprecationLevel"
            )
        }
    }
    toolchainDetection.set(false)
}

koinCompiler {
    userLogs = true
    debugLogs = false
    unsafeDslChecks = false
}

dependencies {
    api("com.github.13absolute.whitertc-core:core:1.0.0")
    implementation(libs.logback)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.jna)
    implementation(libs.jna.platform)

    // Koin
    implementation(libs.koin.core)
    implementation(libs.koin.annotations)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.register<Jar>("fatJar") {
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "com.whitertc.daemon.MainKt"
    }
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
}
