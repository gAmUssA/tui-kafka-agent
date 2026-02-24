## Why

We need a working Gradle project structure before any feature work can begin. This establishes the build system, dependency versions, Java 21 toolchain, and package layout that all subsequent changes build on.

## What Changes

- Create `build.gradle.kts` with all dependencies: tui4j 0.2.5, LangChain4j 1.9.1, langchain4j-anthropic, SnakeYAML 2.2, SLF4J 2.0.9
- Create `settings.gradle.kts` with project name `kafka-agent-tui`
- Configure Java 21 toolchain and `application` plugin with main class
- Create base package structure under `src/main/java/com/example/agent/`
- Create minimal `AgentApp.java` entry point

## Non-goals

- No TUI code — that comes in a separate change
- No AI/LangChain4j integration code — just the dependency declarations
- No configuration loading — just the project skeleton

## Capabilities

### New Capabilities
- `gradle-build`: Gradle build configuration with all project dependencies and Java 21 toolchain
- `app-entry-point`: Main application entry point and base package structure

### Modified Capabilities

## Impact

- Creates new project files at the repository root (`build.gradle.kts`, `settings.gradle.kts`)
- Creates `src/main/java/com/example/agent/` directory tree with sub-packages: `tui/`, `agent/`, `tools/`, `config/`
- All subsequent changes depend on this project structure
