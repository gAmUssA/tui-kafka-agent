## 1. Flink REST Client

- [ ] 1.1 Create `FlinkTools.java` with `HttpClient` configured for Confluent Cloud Flink REST API
- [ ] 1.2 Add auth header using Confluent Cloud API key/secret from AppConfig

## 2. Tool Methods

- [ ] 2.1 Implement `submitFlinkSql(String sql)` — submit statement, poll for results, return formatted string
- [ ] 2.2 Implement `listStatements()` — fetch and format active statements

## 3. Integration

- [ ] 3.1 Wire `FlinkTools` into AiServices builder
- [ ] 3.2 Verify: ask "run SELECT * FROM orders LIMIT 5" → AI calls submitFlinkSql → results displayed
