---
# kafka-agent-exyv
title: Activity rows stay 'working…' after a turn fails
status: todo
type: bug
priority: low
created_at: 2026-11-04T22:00:53Z
updated_at: 2026-11-04T22:00:53Z
---

When an agentic turn fails (`brewops: Failed to invoke agent method`), the Activity pane keeps
the in-flight rows as `▸ <agent> working…` forever: no AgentFinished event is published for them.
Seen on 2026-11-03 after the Act 3 failure; the stale rows also confused my rehearsal script.

## Todo
- [ ] On a Failed event, close every open `working…` row for that turn (mark them failed or aborted)
- [ ] Keep the failure row (`✖ brewops: …`) as the last row of the turn
- [ ] Test on the row model: a Failed event after AgentStarted leaves no `working…` row
