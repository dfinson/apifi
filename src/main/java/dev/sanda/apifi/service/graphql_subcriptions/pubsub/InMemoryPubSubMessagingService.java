package dev.sanda.apifi.service.graphql_subcriptions.pubsub;

import jakarta.transaction.Transactional;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class InMemoryPubSubMessagingService implements PubSubMessagingService {

  private final Map<String, Map<String, PubSubTopicHandler>> topicHandlers = new ConcurrentHashMap<>();

  @Override
  @SneakyThrows
  @Transactional
  public void publishToTopic(String topic, Object payload) {
    if (topicHandlers.containsKey(topic)) {
      synchronized (topicHandlers.get(topic)) {
        topicHandlers
          .get(topic)
          .values()
          .forEach(
            handler ->
              handler.handleDataInTransaction(
                payload,
                PubSubMessagingService.isOnDeleteOrRemove(topic)
              )
          );
      }
    }
  }

  @Override
  public void registerTopicHandler(String topic, PubSubTopicHandler handler) {
    topicHandlers.putIfAbsent(topic, new ConcurrentHashMap<>());
    synchronized (topicHandlers.get(topic)) {
      topicHandlers.get(topic).put(handler.getId(), handler);
    }
  }

  @Override
  public void removeTopicHandler(String topic, String handlerId) {
    try {
      synchronized (topicHandlers.get(topic)) {
        topicHandlers.get(topic).remove(handlerId);
      }
    } catch (NullPointerException ignored) {}
  }
}
