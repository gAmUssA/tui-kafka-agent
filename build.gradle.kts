plugins {
    java
    application
    id("com.gradleup.shadow") version "9.0.0-beta12"
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

    // LangChain4j core + Anthropic
    implementation("dev.langchain4j:langchain4j:1.9.1")
    implementation("dev.langchain4j:langchain4j-anthropic:1.9.1")

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
