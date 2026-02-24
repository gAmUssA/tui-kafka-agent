## Context

This is a greenfield Java project. No existing code or build files exist. We need to establish the Gradle build and package layout that all subsequent features build on.

## Goals / Non-Goals

**Goals:**
- Working `./gradlew run` that compiles and executes the main class
- All dependencies declared and resolvable from Maven Central
- Package structure ready for TUI, agent, tools, and config code

**Non-Goals:**
- No application logic beyond a main method
- No test infrastructure yet

## Decisions

**Gradle Kotlin DSL over Groovy**: Matches tui4j's own build. Better IDE support and type safety.

**Flat dependency declaration**: All deps in a single `build.gradle.kts` — no version catalogs. The project is small enough that a catalog adds complexity without benefit.

**`com.example.agent` base package**: Simple starting point. Can be renamed later if the project gets a proper group ID.

**Java 21 via toolchain**: Ensures consistent JDK regardless of what's installed locally. Required for virtual threads used later in streaming bridge.

## Risks / Trade-offs

- [tui4j 0.2.5 is young] → Pin exact version; it's on Maven Central and Brief proves it works
- [LangChain4j evolves fast] → Pin to 1.9.1; upgrade deliberately
