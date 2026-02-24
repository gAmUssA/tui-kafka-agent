## Context

Confluent Cloud Flink is managed via REST API. Statements are submitted asynchronously and results fetched by polling the statement status.

## Goals / Non-Goals

**Goals:**
- Submit Flink SQL and return results
- List running/completed statements

**Non-Goals:**
- No statement result streaming
- No catalog/database management

## Decisions

**HTTP client for Flink REST API**: Use `java.net.http.HttpClient` (built into Java 21). No additional dependency needed. Confluent Flink REST API uses bearer token auth.

**Statement names auto-generated**: Generate unique statement names using a prefix + timestamp to avoid collisions.

**Poll for results with timeout**: After submitting, poll the statement status until COMPLETED or FAILED, with a 30-second timeout.

## Risks / Trade-offs

- [Flink statements may run longer than 30s] → Return partial status; user can ask again
