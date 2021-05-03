package dev.sanda.apifi.service.graphql_subcriptions.pubsub;

public interface PubSubMessagingService {
  void publishToTopic(String topic, Object payload);
  void registerTopicHandler(String topic, PubSubTopicHandler messageHandler);
  void removeTopicHandler(String topic, String handlerId);
}
