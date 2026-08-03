plugins {
    id("java-library")
}

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.20.1-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.7")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")

    // Loaded at runtime through Spigot LibraryLoader; kept compileOnly here.
    compileOnly("net.kyori:adventure-api:4.14.0")
    compileOnly("net.kyori:adventure-text-minimessage:4.14.0")
    compileOnly("net.kyori:adventure-text-serializer-legacy:4.14.0")
    compileOnly("net.kyori:adventure-text-serializer-plain:4.14.0")

    // Runtime libraries are declared in plugin.yml and loaded by Spigot LibraryLoader.
    compileOnly("com.zaxxer:HikariCP:6.3.0")
    compileOnly("org.xerial:sqlite-jdbc:3.50.3.0")
    compileOnly("com.mysql:mysql-connector-j:9.4.0")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(17)
}

tasks {
    jar {
        archiveFileName.set("CloudTitle-${version}.jar")
    }

    processResources {
        val props = mapOf("version" to version)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
