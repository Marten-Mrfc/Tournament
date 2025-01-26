import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.util.capitalizeDecapitalize.capitalizeAsciiOnly

plugins {
    id("java")
    kotlin("jvm") version "1.9.22"
    `maven-publish`
    id("io.github.goooler.shadow") version "8.1.7"
}

group = "dev.marten_mrfcyt"
version = "1.0"

repositories {
    mavenCentral()
    maven("https://javadoc.jitpack.io")
    maven("https://repo.codemc.io/repository/maven-releases/")
    maven("https://repo.codemc.io/repository/maven-snapshots/")
    maven("https://repo.opencollab.dev/maven-snapshots/")
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://libraries.minecraft.net")
    maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
    maven("https://repo.jorisg.com/releases")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("com.github.marten-mrfc:LirandAPI:621cd466ce")
    compileOnly("net.kyori:adventure-text-minimessage:4.13.1")
    compileOnly("com.mojang:brigadier:1.0.18")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk9:1.6.0")
    compileOnly("com.gufli.kingdomcraft.starter:api:7.1.1")
}

val targetJavaVersion = 21
kotlin {
    jvmToolchain(targetJavaVersion)
}

tasks.build {
    dependsOn("shadowJar")
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}

task<ShadowJar>("buildAndMove") {
    dependsOn("shadowJar")

    group = "build"
    description = "Builds the jar and moves it to the server folder"

    doLast {
        val jar = file("build/libs/${project.name}-${version}-all.jar")
        val server = file("server/plugins/${project.name.capitalizeAsciiOnly()}-${version}.jar")

        if (server.exists()) {
            server.delete()
        }

        jar.copyTo(server, overwrite = true)
    }
}
