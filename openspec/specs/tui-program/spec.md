## ADDED Requirements

### Requirement: Program enters alternate screen
The application SHALL create a tui4j `Program` with alternate screen buffer enabled so terminal history is preserved.

#### Scenario: Clean terminal on exit
- **WHEN** the program starts and then exits
- **THEN** the original terminal content is restored

### Requirement: Program runs AgentModel
The Program SHALL use `AgentModel` as its root Model, calling `init()` on startup.

#### Scenario: Program starts
- **WHEN** `AgentApp.main()` is called
- **THEN** the tui4j Program enters the event loop with AgentModel
