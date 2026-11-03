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
    // Terminal UI: TamboUI toolkit on the JLine 3 backend (the backend brings JLine itself)
    implementation(platform("dev.tamboui:tamboui-bom:0.4.0"))
    implementation("dev.tamboui:tamboui-toolkit")
    implementation("dev.tamboui:tamboui-toolkit-markdown")
    implementation("dev.tamboui:tamboui-jline3-backend")

    // LangChain4j core + providers (GA, aligned by the BOM)
    implementation(platform("dev.langchain4j:langchain4j-bom:1.20.0"))
    implementation("dev.langchain4j:langchain4j")
    implementation("dev.langchain4j:langchain4j-anthropic")
    implementation("dev.langchain4j:langchain4j-ollama")
    // MCP client is still a beta artifact; the BOM does not manage it — pin explicitly
    // and keep every beta on the same number as the core release (1.20.0 ↔ beta30).
    implementation("dev.langchain4j:langchain4j-mcp:1.20.0-beta30")
    // Multi-agent orchestration (supervisor, sequence, loop) — same beta train as MCP
    implementation("dev.langchain4j:langchain4j-agentic:1.20.0-beta30")
    // Agent Skills (SKILL.md with progressive disclosure) — the flink-sql skill lives in resources/skills
    implementation("dev.langchain4j:langchain4j-skills:1.20.0-beta30")

    // Configuration
    implementation("org.yaml:snakeyaml:2.2")

    // Logging
    implementation("org.slf4j:slf4j-simple:2.0.9")
}

application {
    mainClass = "com.example.agent.AgentApp"
}

// Keep parameter names in bytecode: langchain4j-agentic binds @V arguments by name and
// silently passes null without this flag.
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-parameters")
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
