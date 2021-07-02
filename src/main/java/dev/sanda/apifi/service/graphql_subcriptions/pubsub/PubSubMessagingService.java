package dev.sanda.apifi.service.graphql_subcriptions.pubsub;

import static dev.sanda.apifi.service.graphql_subcriptions.EntityCollectionSubscriptionEndpoints.ON_REMOVE_FROM;
import static dev.sanda.apifi.service.graphql_subcriptions.SubscriptionEndpoints.ON_DELETE;

public interface PubSubMessagingService {
  void publishToTopic(String topic, Object payload);
  void registerTopicHandler(String topic, PubSubTopicHandler messageHandler);
  void removeTopicHandler(String topic, String handlerId);

  static boolean isOnDeleteOrRemove(String topic) {
    return (
      topic.endsWith(ON_DELETE.getStringValue()) ||
      topic.endsWith(ON_REMOVE_FROM.getStringValue())
    );
  }
}
