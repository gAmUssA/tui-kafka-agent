## 1. Theme

- [ ] 1.1 Create `Theme.java` with static lipgloss Style constants for each role color
- [ ] 1.2 Add border styles for tool result boxes and header bar

## 2. View Helpers

- [ ] 2.1 Create `HeaderView.java` — renders "kafka-agent | <model> | <n> tools" bar
- [ ] 2.2 Create `ChatView.java` — renders ChatEntry list with role-specific colors and tool result borders

## 3. Layout Composition

- [ ] 3.1 Update `AgentModel.view()` to compose header + viewport + input vertically
- [ ] 3.2 Calculate viewport height dynamically (terminal height - header height - input height)
- [ ] 3.3 Verify: full styled layout visible with colored messages and header bar
