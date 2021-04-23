package dev.sanda.apifi.service.graphql_subcriptions.pubsub;

import reactor.core.publisher.FluxSink;

import java.util.Set;

public interface PubSubMessagingService {
    Set<String> registeredTopics();
    void publishToTopic(String topic, Object payload);
    void registerTopicHandler(String topic, PubSubTopicHandler messageHandler);
    void removeTopicHandler(String topic, FluxSink downStreamSubscriber);
}
