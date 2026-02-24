## Why

The agent needs to interact with Confluent Cloud Kafka clusters. LangChain4j `@Tool` methods let the AI call Kafka admin operations when the user asks about topics, consumer groups, or wants to produce test messages.

## What Changes

- Create `KafkaTools` class with `@Tool` annotated methods
- Implement: `listTopics()`, `describeTopic(name)`, `produceMessage(topic, key, value)`, `getConsumerLag(groupId)`
- Wire KafkaTools into the AiServices builder
- Use Confluent Cloud REST API or Admin Client for operations

## Non-goals

- No Kafka consumer streaming — that's a separate change
- No schema registry integration
- No topic creation/deletion (read-heavy, safe operations only)

## Capabilities

### New Capabilities
- `kafka-tools`: LangChain4j @Tool methods for Kafka cluster operations

### Modified Capabilities
