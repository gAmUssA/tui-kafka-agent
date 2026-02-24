## 1. Gradle Build Files

- [x] 1.1 Create `settings.gradle.kts` with project name `kafka-agent-tui`
- [x] 1.2 Create `build.gradle.kts` with `java` and `application` plugins, Java 21 toolchain, all five dependencies, and mainClass set to `com.example.agent.AgentApp`
- [x] 1.3 Generate Gradle wrapper (`gradle wrapper --gradle-version 8.5`)

## 2. Package Structure

- [x] 2.1 Create `src/main/java/com/example/agent/AgentApp.java` with main method printing "kafka-agent starting"
- [x] 2.2 Create `src/main/java/com/example/agent/tui/package-info.java`
- [x] 2.3 Create `src/main/java/com/example/agent/agent/package-info.java`
- [x] 2.4 Create `src/main/java/com/example/agent/tools/package-info.java`
- [x] 2.5 Create `src/main/java/com/example/agent/config/package-info.java`

## 3. Verify

- [x] 3.1 Run `./gradlew build` — confirm zero errors
- [x] 3.2 Run `./gradlew run` — confirm "kafka-agent starting" in output
