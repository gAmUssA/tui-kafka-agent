---
# kafka-agent-nhnh
title: SQL submitter reports success before the statement is RUNNING
status: todo
type: bug
priority: normal
created_at: 2026-11-04T22:00:53Z
updated_at: 2026-11-04T22:00:53Z
---

Seen in the Haiku rehearsal on 2026-11-03: Act 3 reported the alert job as created, but
`brewery-temp-anomaly-detection` was FAILED (`Column 'stage' not found`). The critic passed the
SQL, and the submitter never confirmed the statement reached RUNNING, so the presenter told the
audience it worked. Haiku also once said "Alerts are flowing" before any hot readings existed.

## Todo
- [ ] After create-flink-statement, poll the statement phase until RUNNING or FAILED (bounded)
- [ ] On FAILED, return the status detail as the result instead of success (FlinkTypes result)
- [ ] Presenter context: pass the confirmed phase, never an assumed one
- [ ] Test: a FAILED phase from the health check yields a failure result
