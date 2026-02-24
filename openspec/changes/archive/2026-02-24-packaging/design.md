## Context

Java apps typically have slow startup (1-3s for JVM). For a TUI app launched frequently from the terminal, instant startup is important for UX.

## Goals / Non-Goals

**Goals:**
- Sub-second startup time
- Single-file or single-directory distribution
- Install with a simple copy or symlink

**Non-Goals:**
- No auto-update mechanism
- No platform-specific packaging formats

## Decisions

**GraalVM native-image as primary**: Produces a single native binary. Best startup time. Use the `org.graalvm.buildtools.native` Gradle plugin.

**jlink as fallback**: If native-image has issues with tui4j or LangChain4j reflection, fall back to jlink which produces a self-contained JRE + app bundle.

**Reflection configuration for native-image**: SnakeYAML and LangChain4j use reflection. Will need `reflect-config.json` in META-INF/native-image.

**Shell wrapper script**: `kafka-agent` script in project root that either runs the native binary or falls back to `java -jar`.

## Risks / Trade-offs

- [tui4j terminal handling may not work with native-image] → Test early; jlink is the safe fallback
- [LangChain4j reflection heavy] → May need extensive native-image configuration
