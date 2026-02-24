## 1. Dependencies

- [ ] 1.1 Add `org.apache.kafka:kafka-clients` dependency to build.gradle.kts

## 2. Kafka Admin Setup

- [ ] 2.1 Create `KafkaTools.java` with AdminClient initialization from AppConfig (bootstrap servers, SASL_SSL)
- [ ] 2.2 Add 10-second timeout for all admin operations

## 3. Tool Methods

- [ ] 3.1 Implement `listTopics()` — list names + partition counts
- [ ] 3.2 Implement `describeTopic(String topicName)` — partitions, replication, configs
- [ ] 3.3 Implement `produceMessage(String topic, String key, String value)` — produce and return offset
- [ ] 3.4 Implement `getConsumerLag(String groupId)` — lag per partition

## 4. Integration

- [ ] 4.1 Wire `KafkaTools` into AiServices builder `.tools(new KafkaTools(config))`
- [ ] 4.2 Verify: ask "what topics do I have?" → AI calls listTopics → results displayed
