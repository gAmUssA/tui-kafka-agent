## 1. Tool Result Buffering (AgentModel.java)

- [x] 1.1 Add `pendingToolResults` field (`List<ChatEntry>`) to `AgentModel`
- [x] 1.2 Modify `ToolCompleteMessage` handler to buffer tool results in `pendingToolResults` instead of committing to `chatHistory`
- [x] 1.3 Modify `StreamCompleteMessage` handler to flush `pendingToolResults` after committing agent text
- [x] 1.4 Modify `ErrorMessage` handler to flush `pendingToolResults` before adding error entry

## 2. Transient Streaming Area (AgentModel.java)

- [x] 2.1 Update `refreshViewport()` to render `pendingToolResults` in the transient area (before spinner and partial response)
- [x] 2.2 Update streaming partial response to use per-line rendering with cursor on last line

## 3. Multi-line Chat Rendering (ChatView.java)

- [x] 3.1 Add `appendStyledLines()` helper method that splits on `\n` and renders each line with `Style.render()` + `"  "` indent
- [x] 3.2 Update AGENT entry rendering to use badge on own line + `appendStyledLines()`
- [x] 3.3 Update USER entry rendering to use badge on own line + `appendStyledLines()`
- [x] 3.4 Update ERROR entry rendering to use badge on own line + `appendStyledLines()`
- [x] 3.5 Update non-boxed TOOL entry rendering to use badge on own line + `appendStyledLines()`

## 4. Verification

- [x] 4.1 Run `./gradlew shadowJar` — confirm BUILD SUCCESSFUL
- [ ] 4.2 Manual test: connect to MCP, run `/topics`, verify agent text appears before tool result box
- [ ] 4.3 Manual test: verify multi-line agent responses have consistent indent on all lines
- [ ] 4.4 Manual test: verify `/help`, `/clear`, `/tools` commands still work correctly
- [ ] 4.5 Manual test: verify error messages with newlines render correctly
