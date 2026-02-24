## ADDED Requirements

### Requirement: listTopics tool
`KafkaTools` SHALL have a `@Tool("List all Kafka topics in the Confluent Cloud cluster")` method returning a string listing all topics with partition counts.

#### Scenario: Topics exist
- **WHEN** the AI calls `listTopics()`
- **THEN** a string listing each topic name and partition count is returned

#### Scenario: Cluster unreachable
- **WHEN** the Kafka admin client cannot connect
- **THEN** an error string is returned (not an exception thrown)

### Requirement: describeTopic tool
`KafkaTools` SHALL have a `@Tool` method `describeTopic(String topicName)` returning topic details including partitions, replication factor, and configs.

#### Scenario: Topic exists
- **WHEN** `describeTopic("orders")` is called
- **THEN** a string with partition count, replication factor, and key configs is returned

### Requirement: produceMessage tool
`KafkaTools` SHALL have a `@Tool` method `produceMessage(String topic, String key, String value)` that sends a message and returns confirmation.

#### Scenario: Produce succeeds
- **WHEN** `produceMessage("test-topic", "key1", "{\"data\": 1}")` is called
- **THEN** the message is produced and a confirmation string with offset is returned

### Requirement: getConsumerLag tool
`KafkaTools` SHALL have a `@Tool` method `getConsumerLag(String groupId)` returning lag per partition.

#### Scenario: Consumer group exists
- **WHEN** `getConsumerLag("my-group")` is called
- **THEN** a string listing lag per topic-partition is returned
