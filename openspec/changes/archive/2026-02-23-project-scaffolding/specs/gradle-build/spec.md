## ADDED Requirements

### Requirement: Gradle build with Kotlin DSL
The project SHALL use Gradle with Kotlin DSL (`build.gradle.kts`) and define a `settings.gradle.kts` with project name `kafka-agent-tui`.

#### Scenario: Project compiles successfully
- **WHEN** running `./gradlew build`
- **THEN** the project compiles with zero errors

### Requirement: Java 21 toolchain
The build SHALL configure a Java 21 toolchain via `java.toolchain.languageVersion`.

#### Scenario: Correct Java version
- **WHEN** the project is built
- **THEN** it uses Java 21 regardless of the system default JDK

### Requirement: All dependencies declared
The build SHALL declare these implementation dependencies:
- `com.williamcallahan:tui4j:0.2.5`
- `dev.langchain4j:langchain4j:1.9.1`
- `dev.langchain4j:langchain4j-anthropic:1.9.1`
- `org.yaml:snakeyaml:2.2`
- `org.slf4j:slf4j-simple:2.0.9`

#### Scenario: Dependencies resolve
- **WHEN** running `./gradlew dependencies`
- **THEN** all five dependencies resolve from Maven Central

### Requirement: Application plugin configured
The build SHALL apply the `application` plugin with `mainClass` set to `com.example.agent.AgentApp`.

#### Scenario: Runnable application
- **WHEN** running `./gradlew run`
- **THEN** the application starts and exits cleanly
