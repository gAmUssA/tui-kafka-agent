## ADDED Requirements

### Requirement: Native image build
The project SHALL support building a GraalVM native image via `./gradlew nativeCompile`.

#### Scenario: Native binary produced
- **WHEN** `./gradlew nativeCompile` completes
- **THEN** a native binary exists at `build/native/nativeCompile/kafka-agent-tui`

### Requirement: Startup time under 1 second
The native binary SHALL start and show the TUI in under 1 second.

#### Scenario: Fast startup
- **WHEN** the native binary is launched
- **THEN** the TUI is visible within 1 second

### Requirement: jlink fallback
The project SHALL support `./gradlew jlink` producing a self-contained runtime image as fallback.

#### Scenario: jlink image produced
- **WHEN** `./gradlew jlink` completes
- **THEN** a runnable image exists at `build/jlink/`

### Requirement: Shell wrapper script
A `kafka-agent` shell script SHALL be provided that runs the native binary if present, otherwise falls back to `java -jar`.

#### Scenario: Script runs native
- **WHEN** the native binary exists and the script is executed
- **THEN** the native binary is launched
