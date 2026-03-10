import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.1.10"
    id("org.jetbrains.compose") version "1.7.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.10"
    kotlin("plugin.serialization") version "2.1.10"
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
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    // Database - Exposed ORM
    val exposedVersion = "0.58.0"
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")

    // SQLite JDBC driver
    implementation("org.xerial:sqlite-jdbc:3.47.1.0")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.15")
    implementation("org.slf4j:slf4j-api:2.0.16")

    // Dependency Injection - Koin
    val koinVersion = "4.0.2"
    implementation("io.insert-koin:koin-core:$koinVersion")
    implementation("io.insert-koin:koin-compose:$koinVersion")

    // Serialization (for JSON jira_tickets field)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("io.mockk:mockk:1.13.14")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation(compose.desktop.uiTestJUnit4)
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

            // App icon — uncomment when icon files are available:
            // windows {
            //     iconFile.set(project.file("src/main/resources/icons/devtrack.ico"))
            // }
            // linux {
            //     iconFile.set(project.file("src/main/resources/icons/devtrack.png"))
            // }

            windows {
                menuGroup = "DevTrack"
                dirChooser = true
                shortcut = true
                perUserInstall = true
                upgradeUuid = "f0a8b2c4-d6e8-4a0b-9c2e-1f3a5b7d9e0f"
            }

            linux {
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
