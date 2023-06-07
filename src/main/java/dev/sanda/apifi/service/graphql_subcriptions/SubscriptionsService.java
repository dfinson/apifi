package dev.sanda.apifi.service.graphql_subcriptions;

import dev.sanda.apifi.service.graphql_subcriptions.pubsub.PubSubMessagingService;
import dev.sanda.apifi.service.graphql_subcriptions.pubsub.PubSubTopicHandler;
import dev.sanda.apifi.utils.ConfigValues;
import dev.sanda.datafi.reflection.runtime_services.ReflectionCache;
import dev.sanda.datafi.service.DataManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.FluxSink;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class SubscriptionsService {

  private final PubSubMessagingService pubSubMessagingService;
  private final ReflectionCache reflectionCache;
  private final ConfigValues configValues;

  @SuppressWarnings("rawtypes")
  public void registerTopicSubscriber(
    String topic,
    String id,
    FluxSink downStreamSubscriber,
    DataManager dataManager
  ) {
    pubSubMessagingService.registerTopicHandler(
      topic,
      new PubSubTopicHandler(
        id,
        downStreamSubscriber,
        dataManager,
        reflectionCache,
        configValues
      )
    );
  }

  public void removeTopicSubscriber(String topic, String id) {
    pubSubMessagingService.removeTopicHandler(topic, id);
  }

  public void publishToTopic(String topic, Object payload) {
    pubSubMessagingService.publishToTopic(topic, payload);
  }
}
