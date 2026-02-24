## Why

The agent needs to submit and manage Flink SQL statements on Confluent Cloud. This enables stream processing queries directly from the chat interface.

## What Changes

- Create `FlinkTools` class with `@Tool` annotated methods
- Implement: `submitFlinkSql(String sql)`, `listStatements()`
- Use Confluent Cloud Flink REST API for operations

## Non-goals

- No Flink SQL editor with syntax highlighting
- No long-running statement monitoring

## Capabilities

### New Capabilities
- `flink-tools`: LangChain4j @Tool methods for Flink SQL operations

### Modified Capabilities
