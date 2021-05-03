package dev.sanda.apifi.service.graphql_subcriptions.pubsub.redis_pubsub;

public interface MessagePublisher {
  void publish(String topic, String message);
}
