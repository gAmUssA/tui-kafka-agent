## ADDED Requirements

### Requirement: Main entry point
The application SHALL have a `com.example.agent.AgentApp` class with a `main` method that prints "kafka-agent starting" to stdout.

#### Scenario: Application starts
- **WHEN** the main class is executed
- **THEN** stdout contains "kafka-agent starting"

### Requirement: Package structure
The project SHALL create the following package directories under `src/main/java/com/example/agent/`:
- `tui/` — for TUI model, views, messages
- `agent/` — for LangChain4j service and streaming bridge
- `tools/` — for @Tool classes
- `config/` — for configuration loading

#### Scenario: Packages exist
- **WHEN** the project is created
- **THEN** all four sub-packages exist and contain at least a `package-info.java` or placeholder class
