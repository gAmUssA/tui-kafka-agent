## ADDED Requirements

### Requirement: Pending tool results buffer
`AgentModel` SHALL maintain a `pendingToolResults` list field of type `List<ChatEntry>` to buffer tool results during streaming.

#### Scenario: Buffer initialized empty
- **WHEN** `AgentModel` is constructed
- **THEN** `pendingToolResults` is an empty list

### Requirement: Error handler flushes pending tools
The `ErrorMessage` handler SHALL flush `pendingToolResults` to `chatHistory` before adding the error entry.

#### Scenario: Error with pending tools
- **WHEN** `ErrorMessage` is received and `pendingToolResults` contains entries
- **THEN** all pending tool results are added to `chatHistory`
- **AND** the error entry is added after them
- **AND** `pendingToolResults` is cleared

### Requirement: Transient area shows pending tool results
`refreshViewport()` SHALL render each entry in `pendingToolResults` in the transient streaming area, before the spinner and partial response.

#### Scenario: Pending tool visible during streaming
- **WHEN** `refreshViewport()` is called with non-empty `pendingToolResults`
- **THEN** each pending tool result is rendered using `ToolResultView.render()` (for named tools) or badge+styled text (for unnamed tools)
- **AND** they appear between committed history and the spinner/streaming cursor
