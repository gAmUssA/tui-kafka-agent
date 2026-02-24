## Context

Confluent Cloud clusters are accessible via the Kafka Admin Client (with SASL_SSL) or the Confluent Cloud REST API. The agent needs to call these during conversations.

## Goals / Non-Goals

**Goals:**
- Four @Tool methods the AI can invoke during chat
- Clean string responses suitable for AI to interpret and summarize

**Non-Goals:**
- No streaming consumer
- No destructive operations (no delete topic, no alter configs)

## Decisions

**Kafka AdminClient over REST API**: AdminClient is the standard Java API, works with Confluent Cloud via SASL_SSL. Already well-documented. Add `kafka-clients` dependency.

**String return values**: Each tool returns a human-readable string. The AI interprets and reformats for the user. No structured objects needed.

**Tool descriptions matter**: The `@Tool` annotation description guides the AI on when to call each tool. Keep descriptions clear and specific.

## Risks / Trade-offs

- [Admin operations may timeout on slow clusters] → Set reasonable timeout (10s); return error string on failure rather than throwing
