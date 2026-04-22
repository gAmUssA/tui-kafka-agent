plugins {
    java
    application
    id("com.gradleup.shadow") version "9.0.0-beta12"
    id("org.graalvm.buildtools.native") version "0.10.4"
}

group = "com.example"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // TUI framework
    implementation("com.williamcallahan:tui4j:0.3.3")

    // JLine FFM terminal provider for Java 21+ (macOS native terminal access)
    implementation("org.jline:jline-terminal-ffm:3.26.1")

    // LangChain4j core + providers + MCP
    implementation("dev.langchain4j:langchain4j:1.11.0")
    implementation("dev.langchain4j:langchain4j-anthropic:1.11.0")
    implementation("dev.langchain4j:langchain4j-ollama:1.11.0")
    implementation("dev.langchain4j:langchain4j-mcp:1.11.0-beta19")

    // Configuration
    implementation("org.yaml:snakeyaml:2.2")

    // Logging
    implementation("org.slf4j:slf4j-simple:2.0.9")
}

application {
    mainClass = "com.example.agent.AgentApp"
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    archiveBaseName.set("kafka-agent")
    archiveClassifier.set("")
    archiveVersion.set("")
    mergeServiceFiles()
}

// GraalVM native-image configuration (requires GraalVM JDK)
graalvmNative {
    toolchainDetection = true
    binaries {
        named("main") {
            imageName = "kafka-agent"
            mainClass = "com.example.agent.AgentApp"
            javaLauncher = javaToolchains.launcherFor {
                languageVersion = JavaLanguageVersion.of(21)
                vendor = JvmVendorSpec.matching("GraalVM Community")
            }
            buildArgs.addAll(
                "--no-fallback",
                "--enable-native-access=ALL-UNNAMED",
                "-H:+ReportExceptionStackTraces"
            )
        }
    }
}

// jlink custom runtime image (fallback if native-image not available)
tasks.register<Exec>("jlinkImage") {
    group = "distribution"
    description = "Create a minimal JRE + app bundle using jlink"
    dependsOn("shadowJar")

    val jlinkOutput = layout.buildDirectory.dir("jlink-image")
    val javaExec = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(21)
    }.get().executablePath.asFile.parentFile

    doFirst {
        delete(jlinkOutput)
    }

    commandLine(
        "${javaExec}/jlink",
        "--add-modules", "java.base,java.net.http,java.logging,java.management,java.naming,java.sql,jdk.crypto.ec",
        "--strip-debug",
        "--compress", "zip-6",
        "--no-header-files",
        "--no-man-pages",
        "--output", jlinkOutput.get().asFile.absolutePath
    )

    doLast {
        // Create launch script inside the jlink image
        val launcher = file("${jlinkOutput.get().asFile}/bin/kafka-agent")
        launcher.writeText("""
            |#!/usr/bin/env bash
            |SCRIPT_DIR="$(cd "$(dirname "${'$'}{BASH_SOURCE[0]}")" && pwd)"
            |exec "${'$'}SCRIPT_DIR/java" --enable-native-access=ALL-UNNAMED -jar "${'$'}SCRIPT_DIR/../../build/libs/kafka-agent.jar" "${'$'}@"
        """.trimMargin())
        launcher.setExecutable(true)
        println("jlink image created at: ${jlinkOutput.get().asFile}")
    }
}
