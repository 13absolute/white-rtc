plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.koin.compiler) apply false
    alias(libs.plugins.google.devtools.ksp) apply false
    alias(libs.plugins.androidx.room3) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.graalvm.native) apply false
}

tasks.register("runDesktop") {
    group = "desktop"
    description = "Запуск десктопного приложения на ПК"
    dependsOn(":desktopApp:run")
}

tasks.register("runDaemon") {
    group = "daemon"
    description = "Запуск привилегированного демона VPN"
    dependsOn(":daemon:run")
}

tasks.register("packageLinuxDeb") {
    group = "desktop"
    description = "Сборка пакета Linux (.deb)"
    dependsOn(":desktopApp:packageDeb")
}

tasks.register("packageWindows") {
    group = "desktop"
    description = "Сборка всех дистрибутивов Windows (.msi и .exe)"
    dependsOn(":desktopApp:packageMsi", ":desktopApp:packageExe")
}
