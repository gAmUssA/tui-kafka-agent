## ADDED Requirements

### Requirement: submitFlinkSql tool
`FlinkTools` SHALL have a `@Tool("Submit a Flink SQL statement to Confluent Cloud")` method that submits SQL and returns results or status.

#### Scenario: SQL executes successfully
- **WHEN** `submitFlinkSql("SELECT * FROM orders LIMIT 5")` is called
- **THEN** the statement is submitted, polled for completion, and results returned as a string

#### Scenario: SQL has syntax error
- **WHEN** invalid SQL is submitted
- **THEN** the error message from Flink is returned as a string

### Requirement: listStatements tool
`FlinkTools` SHALL have a `@Tool("List running Flink SQL statements")` method returning active statements.

#### Scenario: Statements exist
- **WHEN** `listStatements()` is called
- **THEN** a string listing statement names, SQL snippets, and statuses is returned
