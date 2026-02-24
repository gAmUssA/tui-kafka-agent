## 1. AgentModel

- [x] 1.1 Create `AgentModel.java` implementing tui4j `Model` with stub `init()`, `update()`, `view()`
- [x] 1.2 Handle Ctrl+C KeyMsg → return `Cmd.quit()`
- [x] 1.3 Implement `view()` returning a placeholder "kafka-agent" screen

## 2. Program Wiring

- [x] 2.1 Update `AgentApp.main()` to create a tui4j `Program` with `AgentModel`
- [x] 2.2 Enable alternate screen buffer via `Program.withAltScreen()`
- [x] 2.3 Run `./gradlew run` — confirm TUI appears and Ctrl+C exits cleanly
