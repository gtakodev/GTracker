import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.1.20"
    id("org.jetbrains.compose") version "1.10.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20"
    kotlin("plugin.serialization") version "2.1.20"
}

group = "com.devtrack"
version = "1.0.0"

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    // Compose Desktop
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material3:material3:1.9.0")
    implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")

    // Database - Exposed DSL (no DAO layer used)
    val exposedVersion = "1.2.0"
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")

    // SQLite JDBC driver with SQLCipher/SQLite-Multiple-Ciphers encryption support
    // Replaces org.xerial:sqlite-jdbc — same API, adds PRAGMA key support
    implementation("io.github.willena:sqlite-jdbc:3.51.2.0")

    // JNA — used by WindowsKeyStore (DPAPI via Crypt32.dll)
    implementation("net.java.dev.jna:jna:5.17.0")
    implementation("net.java.dev.jna:jna-platform:5.17.0")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.32")
    implementation("org.slf4j:slf4j-api:2.0.17")

    // Dependency Injection - Koin
    val koinVersion = "4.0.2"
    implementation("io.insert-koin:koin-core:$koinVersion")
    implementation("io.insert-koin:koin-compose:$koinVersion")

    // Serialization (for JSON jira_tickets field)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")

    // Testing
    testImplementation(platform("org.junit:junit-bom:5.14.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.mockk:mockk:1.14.9")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("org.jetbrains.compose.ui:ui-test-junit4:1.10.3")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

compose.desktop {
    application {
        mainClass = "com.devtrack.app.MainKt"

        // JVM args for desktop performance
        jvmArgs += listOf(
            "-Xmx512m",
            "-Xms128m",
        )

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb, TargetFormat.AppImage)
            packageName = "DevTrack"
            packageVersion = "1.0.0"
            description = "Developer Work Intelligence Tool — Time Tracking & Reporting"
            vendor = "DevTrack"
            copyright = "2026 DevTrack"
            licenseFile.set(project.file("LICENSE"))

            // Include JDK modules needed by the app (minimal JRE via jlink)
            modules(
                "java.sql",           // JDBC / SQLite
                "java.naming",        // Exposed ORM internals
                "java.management",    // JMX (used by logback)
                "jdk.unsupported",    // sun.misc.Unsafe (used by coroutines)
            )

            windows {
                iconFile.set(project.file("src/main/resources/icons/devtrack.ico"))
                menuGroup = "DevTrack"
                dirChooser = true
                shortcut = true
                perUserInstall = true
                upgradeUuid = "f0a8b2c4-d6e8-4a0b-9c2e-1f3a5b7d9e0f"
            }

            linux {
                iconFile.set(project.file("src/main/resources/icons/devtrack.png"))
                packageName = "devtrack"
                debMaintainer = "devtrack@localhost"
                appCategory = "Development"
                menuGroup = "Development"
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
